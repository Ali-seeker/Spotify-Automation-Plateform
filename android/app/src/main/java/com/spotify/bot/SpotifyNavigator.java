package com.spotify.bot;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class SpotifyNavigator {

    private static final String TAG = "SpotifyBotNav";
    private static final String SPOTIFY_PACKAGE = SpotifyAccessibilityService.SPOTIFY_PACKAGE;

    private static final long STABILIZATION_TIMEOUT_MS = 3000;
    private static final long UI_LOAD_WAIT_TIMEOUT_MS = 2500;
    private static final long POLL_INTERVAL_MS = 300;
    private static final int MAX_UNKNOWN_RECOVERY_ATTEMPTS = 3;

    public interface NavigationCallback {
        void onResult(boolean success, SpotifyScreen finalScreen, String reasonCode);
    }

    private static String getIsoUtcTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    /**
     * Detects the current Spotify screen by inspecting the Accessibility UI tree hierarchy.
     */
    public static SpotifyScreen detectCurrentScreen(AccessibilityNodeInfo root) {
        if (root == null) return SpotifyScreen.UNKNOWN_SCREEN;

        CharSequence pkg = root.getPackageName();
        if (pkg == null || !SPOTIFY_PACKAGE.equals(pkg.toString())) {
            return SpotifyScreen.UNKNOWN_SCREEN;
        }

        // 1. NOW_PLAYING (Fullscreen player)
        if (isNowPlayingScreen(root)) {
            return SpotifyScreen.NOW_PLAYING;
        }

        // 2. SEARCH_RESULTS (Search input active with results list)
        if (isSearchResultsScreen(root)) {
            return SpotifyScreen.SEARCH_RESULTS;
        }

        // 3. SEARCH (Search tab landing page / search bar)
        if (isSearchScreen(root)) {
            return SpotifyScreen.SEARCH;
        }

        // 4. ARTIST_PAGE
        if (isArtistPageScreen(root)) {
            return SpotifyScreen.ARTIST_PAGE;
        }

        // 5. ALBUM_PAGE
        if (isAlbumPageScreen(root)) {
            return SpotifyScreen.ALBUM_PAGE;
        }

        // 6. PLAYLIST_PAGE
        if (isPlaylistPageScreen(root)) {
            return SpotifyScreen.PLAYLIST_PAGE;
        }

        // 7. LIBRARY
        if (isLibraryScreen(root)) {
            return SpotifyScreen.LIBRARY;
        }

        // 8. HOME
        if (isHomeScreen(root)) {
            return SpotifyScreen.HOME;
        }

        // Fallback: If bottom navigation tabs exist, treat as top-level screen
        if (hasBottomNavigation(root)) {
            return SpotifyScreen.HOME;
        }

        return SpotifyScreen.UNKNOWN_SCREEN;
    }

    // --- NAVIGATION API METHODS ---

    public static void goToSearch(Context context, String runId, NavigationCallback callback) {
        navigateTo(context, runId, SpotifyScreen.SEARCH, callback);
    }

    public static void goToHome(Context context, String runId, NavigationCallback callback) {
        navigateTo(context, runId, SpotifyScreen.HOME, callback);
    }

    public static void goToNowPlaying(Context context, String runId, NavigationCallback callback) {
        navigateTo(context, runId, SpotifyScreen.NOW_PLAYING, callback);
    }

    public static void goToLibrary(Context context, String runId, NavigationCallback callback) {
        navigateTo(context, runId, SpotifyScreen.LIBRARY, callback);
    }

    private static void navigateTo(Context context, String runId, SpotifyScreen targetScreen, NavigationCallback callback) {
        new Thread(() -> {
            String stepName = "GO_TO_" + targetScreen.name();
            Log.i(TAG, String.format("[%s] NAVIGATION_STARTED from=ANY to=%s run_id=%s",
                    getIsoUtcTimestamp(), targetScreen.name(), runId));

            emitStepStarted(context, runId, stepName);

            SpotifyAccessibilityService service = SpotifyAccessibilityService.getInstance();
            if (service == null) {
                Log.e(TAG, "Accessibility Service unavailable for navigation.");
                emitStepFailed(context, runId, "ACCESSIBILITY_SERVICE_UNAVAILABLE");
                if (callback != null) callback.onResult(false, SpotifyScreen.UNKNOWN_SCREEN, "ACCESSIBILITY_SERVICE_UNAVAILABLE");
                return;
            }

            // 1. Ensure Spotify Foreground
            if (!SpotifyAccessibilityService.isSpotifyForeground()) {
                Log.w(TAG, "Spotify not in foreground. Attempting launch...");
                final boolean[] launched = {false};
                SpotifyLauncher.launchSpotify(context, (success, msg) -> launched[0] = success);
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

                if (!SpotifyAccessibilityService.isSpotifyForeground()) {
                    emitStepFailed(context, runId, "SPOTIFY_NOT_FOREGROUND");
                    if (callback != null) callback.onResult(false, SpotifyScreen.UNKNOWN_SCREEN, "SPOTIFY_NOT_FOREGROUND");
                    return;
                }
            }

            // 2. Poll for UI tree stabilization after Spotify launch (up to 2.5 seconds)
            long startLoad = System.currentTimeMillis();
            AccessibilityNodeInfo root = null;
            SpotifyScreen currentScreen = SpotifyScreen.UNKNOWN_SCREEN;
            AccessibilityNodeInfo targetNode = null;

            while (System.currentTimeMillis() - startLoad < UI_LOAD_WAIT_TIMEOUT_MS) {
                if (root != null) root.recycle();
                root = service.getRootInActiveWindow();
                if (root != null) {
                    currentScreen = detectCurrentScreen(root);
                    targetNode = findTargetNavigationNode(root, targetScreen);

                    if (currentScreen != SpotifyScreen.UNKNOWN_SCREEN || targetNode != null) {
                        break;
                    }
                }
                try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
            }

            Log.i(TAG, String.format("[%s] SCREEN_DETECTED screen=%s (UI tree loaded in %d ms)",
                    getIsoUtcTimestamp(), currentScreen.name(), System.currentTimeMillis() - startLoad));

            // 3. Pre-check: Already on target screen?
            if (currentScreen == targetScreen || (targetScreen == SpotifyScreen.SEARCH && currentScreen == SpotifyScreen.SEARCH_RESULTS)) {
                Log.i(TAG, String.format("[%s] NAVIGATION_SUCCESS already on target=%s", getIsoUtcTimestamp(), currentScreen.name()));
                if (targetNode != null) targetNode.recycle();
                if (root != null) root.recycle();
                emitStepOk(context, runId, stepName);
                if (callback != null) callback.onResult(true, currentScreen, null);
                return;
            }

            // 4. If Target Node still not found and screen is UNKNOWN, attempt BACK recovery
            if (targetNode == null && currentScreen == SpotifyScreen.UNKNOWN_SCREEN) {
                if (root != null) root.recycle();
                currentScreen = recoverFromUnknownScreen(service);

                root = service.getRootInActiveWindow();
                targetNode = findTargetNavigationNode(root, targetScreen);

                if (targetNode == null) {
                    Log.e(TAG, String.format("[%s] NAVIGATION_FAILED target=%s reason_code=UNKNOWN_SCREEN",
                            getIsoUtcTimestamp(), targetScreen.name()));
                    if (root != null) root.recycle();
                    emitStepFailed(context, runId, "UNKNOWN_SCREEN");
                    if (callback != null) callback.onResult(false, SpotifyScreen.UNKNOWN_SCREEN, "UNKNOWN_SCREEN");
                    return;
                }
            }

            if (targetNode == null) {
                Log.e(TAG, String.format("[%s] NAVIGATION_FAILED target=%s reason_code=NAVIGATION_FAILED (Node not found)",
                        getIsoUtcTimestamp(), targetScreen.name()));
                if (root != null) root.recycle();
                emitStepFailed(context, runId, "NAVIGATION_FAILED");
                if (callback != null) callback.onResult(false, currentScreen, "NAVIGATION_FAILED");
                return;
            }

            // 5. Click Navigation Node
            AccessibilityNodeInfo clickable = findClickableAncestor(targetNode);
            boolean clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);

            if (clickable != targetNode) clickable.recycle();
            targetNode.recycle();
            if (root != null) root.recycle();

            if (!clicked) {
                Log.e(TAG, String.format("[%s] NAVIGATION_FAILED target=%s reason_code=NAVIGATION_FAILED (Click rejected)",
                        getIsoUtcTimestamp(), targetScreen.name()));
                emitStepFailed(context, runId, "NAVIGATION_FAILED");
                if (callback != null) callback.onResult(false, currentScreen, "NAVIGATION_FAILED");
                return;
            }

            // 6. UI Stabilization (Up to 3 seconds polling element presence)
            long startWait = System.currentTimeMillis();
            SpotifyScreen finalScreen = SpotifyScreen.UNKNOWN_SCREEN;
            boolean stabilized = false;

            while (System.currentTimeMillis() - startWait < STABILIZATION_TIMEOUT_MS) {
                try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}

                AccessibilityNodeInfo freshRoot = service.getRootInActiveWindow();
                if (freshRoot != null) {
                    finalScreen = detectCurrentScreen(freshRoot);
                    freshRoot.recycle();

                    if (finalScreen == targetScreen || (targetScreen == SpotifyScreen.SEARCH && finalScreen == SpotifyScreen.SEARCH_RESULTS)) {
                        stabilized = true;
                        long elapsedMs = System.currentTimeMillis() - startWait;
                        Log.i(TAG, String.format("[%s] SCREEN_STABILIZED screen=%s elapsed_ms=%d",
                                getIsoUtcTimestamp(), finalScreen.name(), elapsedMs));
                        break;
                    }
                }
            }

            if (stabilized) {
                Log.i(TAG, String.format("[%s] NAVIGATION_SUCCESS target=%s", getIsoUtcTimestamp(), finalScreen.name()));
                emitStepOk(context, runId, stepName);
                if (callback != null) callback.onResult(true, finalScreen, null);
            } else {
                Log.e(TAG, String.format("[%s] NAVIGATION_FAILED target=%s timeout_ms=%d actual_screen=%s",
                        getIsoUtcTimestamp(), targetScreen.name(), STABILIZATION_TIMEOUT_MS, finalScreen.name()));
                emitStepFailed(context, runId, "NAVIGATION_FAILED");
                if (callback != null) callback.onResult(false, finalScreen, "NAVIGATION_FAILED");
            }

        }).start();
    }

    private static SpotifyScreen recoverFromUnknownScreen(SpotifyAccessibilityService service) {
        for (int attempt = 1; attempt <= MAX_UNKNOWN_RECOVERY_ATTEMPTS; attempt++) {
            Log.w(TAG, String.format("[%s] UNKNOWN_SCREEN detected. Attempting recovery back navigation %d/%d",
                    getIsoUtcTimestamp(), attempt, MAX_UNKNOWN_RECOVERY_ATTEMPTS));

            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}

            AccessibilityNodeInfo freshRoot = service.getRootInActiveWindow();
            SpotifyScreen detected = detectCurrentScreen(freshRoot);
            if (freshRoot != null) freshRoot.recycle();

            if (detected != SpotifyScreen.UNKNOWN_SCREEN) {
                Log.i(TAG, String.format("[%s] UNKNOWN_SCREEN_RECOVERED screen=%s on attempt %d",
                        getIsoUtcTimestamp(), detected.name(), attempt));
                return detected;
            }
        }
        return SpotifyScreen.UNKNOWN_SCREEN;
    }

    // --- SCREEN DETECTORS ---

    private static boolean hasBottomNavigation(AccessibilityNodeInfo root) {
        if (root == null) return false;
        AccessibilityNodeInfo searchTab = findSearchTabNode(root);
        if (searchTab != null) {
            searchTab.recycle();
            return true;
        }
        AccessibilityNodeInfo homeTab = findHomeTabNode(root);
        if (homeTab != null) {
            homeTab.recycle();
            return true;
        }
        return false;
    }

    private static boolean isNowPlayingScreen(AccessibilityNodeInfo root) {
        String[] viewIds = {
                "com.spotify.music:id/player_controls",
                "com.spotify.music:id/now_playing_bar_title",
                "com.spotify.music:id/now_playing_view",
                "com.spotify.music:id/npv_content"
        };
        for (String id : viewIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) n.recycle();
                return true;
            }
        }
        return false;
    }

    private static boolean isSearchResultsScreen(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> editTexts = root.findAccessibilityNodeInfosByViewId("com.spotify.music:id/query");
        if (editTexts == null || editTexts.isEmpty()) {
            editTexts = root.findAccessibilityNodeInfosByViewId("com.spotify.music:id/find_search_field");
        }
        if (editTexts != null && !editTexts.isEmpty()) {
            boolean hasText = false;
            for (AccessibilityNodeInfo n : editTexts) {
                CharSequence text = n.getText();
                if (text != null && text.length() > 0 && !"What do you want to listen to?".equalsIgnoreCase(text.toString())) {
                    hasText = true;
                }
                n.recycle();
            }
            if (hasText) return true;
        }

        String[] resultContainerIds = {
                "com.spotify.music:id/search_results_container",
                "com.spotify.music:id/search_results",
                "com.spotify.music:id/result_list"
        };
        for (String id : resultContainerIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) n.recycle();
                return true;
            }
        }
        return false;
    }

    private static boolean isSearchScreen(AccessibilityNodeInfo root) {
        String[] viewIds = {
                "com.spotify.music:id/find_search_field",
                "com.spotify.music:id/query",
                "com.spotify.music:id/search_edit_text",
                "com.spotify.music:id/search_text_input",
                "com.spotify.music:id/search_uri"
        };
        for (String id : viewIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) n.recycle();
                return true;
            }
        }

        String[] searchTexts = {
                "What do you want to listen to?",
                "Browse all",
                "Explore"
        };
        for (String t : searchTexts) {
            List<AccessibilityNodeInfo> textNodes = root.findAccessibilityNodeInfosByText(t);
            if (textNodes != null && !textNodes.isEmpty()) {
                for (AccessibilityNodeInfo n : textNodes) n.recycle();
                return true;
            }
        }

        // Check if Search tab node is checked/selected
        AccessibilityNodeInfo searchTab = findSearchTabNode(root);
        if (searchTab != null) {
            boolean isChecked = isTabCheckedOrSelected(searchTab);
            searchTab.recycle();
            if (isChecked) return true;
        }

        return false;
    }

    private static boolean isHomeScreen(AccessibilityNodeInfo root) {
        String[] viewIds = {
                "com.spotify.music:id/home_tab",
                "com.spotify.music:id/bottom_navigation_home"
        };
        for (String id : viewIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n.isSelected() || n.isChecked()) {
                        n.recycle();
                        return true;
                    }
                    n.recycle();
                }
            }
        }

        AccessibilityNodeInfo homeTab = findHomeTabNode(root);
        if (homeTab != null) {
            boolean isChecked = isTabCheckedOrSelected(homeTab);
            homeTab.recycle();
            if (isChecked) return true;
        }

        String[] texts = {"Good morning", "Good afternoon", "Good evening", "Recently played", "Made for you"};
        for (String t : texts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(t);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) n.recycle();
                return true;
            }
        }
        return false;
    }

    private static boolean isLibraryScreen(AccessibilityNodeInfo root) {
        String[] viewIds = {
                "com.spotify.music:id/library_tab",
                "com.spotify.music:id/bottom_navigation_library"
        };
        for (String id : viewIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (n.isSelected() || n.isChecked()) {
                        n.recycle();
                        return true;
                    }
                    n.recycle();
                }
            }
        }

        AccessibilityNodeInfo libTab = findLibraryTabNode(root);
        if (libTab != null) {
            boolean isChecked = isTabCheckedOrSelected(libTab);
            libTab.recycle();
            if (isChecked) return true;
        }

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Your Library");
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo n : nodes) n.recycle();
            return true;
        }
        return false;
    }

    private static boolean isTabCheckedOrSelected(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isChecked() || node.isSelected()) return true;
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            boolean pVal = parent.isChecked() || parent.isSelected();
            parent.recycle();
            if (pVal) return true;
        }
        return false;
    }

    private static boolean isArtistPageScreen(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Monthly listeners");
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo n : nodes) n.recycle();
            return true;
        }
        return false;
    }

    private static boolean isAlbumPageScreen(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Album");
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo n : nodes) n.recycle();
            return true;
        }
        return false;
    }

    private static boolean isPlaylistPageScreen(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Playlist");
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo n : nodes) n.recycle();
            return true;
        }
        return false;
    }

    // --- NODE FINDERS ---

    private static AccessibilityNodeInfo findTargetNavigationNode(AccessibilityNodeInfo root, SpotifyScreen targetScreen) {
        if (root == null) return null;

        switch (targetScreen) {
            case SEARCH:
                return findSearchTabNode(root);
            case HOME:
                return findHomeTabNode(root);
            case LIBRARY:
                return findLibraryTabNode(root);
            case NOW_PLAYING:
                return findNowPlayingBarNode(root);
            default:
                return null;
        }
    }

    private static AccessibilityNodeInfo findSearchTabNode(AccessibilityNodeInfo root) {
        String[] ids = {
                "com.spotify.music:id/search_tab",
                "com.spotify.music:id/bottom_navigation_search",
                "com.spotify.music:id/find_search_field",
                "com.spotify.music:id/search"
        };
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }
        return findNodeByDfs(root, "search, tab", "search");
    }

    private static AccessibilityNodeInfo findHomeTabNode(AccessibilityNodeInfo root) {
        String[] ids = {
                "com.spotify.music:id/home_tab",
                "com.spotify.music:id/bottom_navigation_home"
        };
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }
        return findNodeByDfs(root, "home, tab", "home");
    }

    private static AccessibilityNodeInfo findLibraryTabNode(AccessibilityNodeInfo root) {
        String[] ids = {
                "com.spotify.music:id/library_tab",
                "com.spotify.music:id/bottom_navigation_library"
        };
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }
        return findNodeByDfs(root, "your library, tab", "library");
    }

    private static AccessibilityNodeInfo findNowPlayingBarNode(AccessibilityNodeInfo root) {
        String[] ids = {
                "com.spotify.music:id/now_playing_bar",
                "com.spotify.music:id/now_playing_bar_title",
                "com.spotify.music:id/mini_player"
        };
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findNodeByDfs(AccessibilityNodeInfo node, String... keywords) {
        if (node == null) return null;

        CharSequence descChar = node.getContentDescription();
        CharSequence textChar = node.getText();
        String resId = node.getViewIdResourceName();

        String desc = descChar != null ? descChar.toString().toLowerCase() : "";
        String text = textChar != null ? textChar.toString().toLowerCase() : "";
        String res = resId != null ? resId.toLowerCase() : "";

        for (String kw : keywords) {
            if (desc.contains(kw) || text.contains(kw) || res.contains(kw)) {
                return AccessibilityNodeInfo.obtain(node);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeByDfs(child, keywords);
                child.recycle();
                if (result != null) return result;
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findClickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo curr = node;
        while (curr != null && !curr.isClickable()) {
            AccessibilityNodeInfo parent = curr.getParent();
            if (parent == null) break;
            if (curr != node) curr.recycle();
            curr = parent;
        }
        return curr != null ? curr : AccessibilityNodeInfo.obtain(node);
    }

    // --- STEP EVENT HELPERS ---

    private static void emitStepStarted(Context context, String runId, String stepName) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "STEP_STARTED");
            event.put("run_id", runId);
            JSONObject payload = new JSONObject();
            payload.put("step_index", 1);
            payload.put("step_name", stepName);
            event.put("payload", payload);

            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException ignored) {}
    }

    private static void emitStepOk(Context context, String runId, String stepName) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "STEP_OK");
            event.put("run_id", runId);
            JSONObject payload = new JSONObject();
            payload.put("step_index", 1);
            payload.put("step_name", stepName);
            event.put("payload", payload);

            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException ignored) {}
    }

    private static void emitStepFailed(Context context, String runId, String reasonCode) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "STEP_FAILED");
            event.put("run_id", runId);
            JSONObject payload = new JSONObject();
            payload.put("step_index", 1);
            payload.put("reason_code", reasonCode);
            event.put("payload", payload);

            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException ignored) {}
    }
}

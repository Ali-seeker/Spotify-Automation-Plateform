package com.spotify.bot;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SpotifyClicker {

    private static final String TAG = SpotifyAccessibilityService.TAG;
    private static final String SPOTIFY_PACKAGE = SpotifyAccessibilityService.SPOTIFY_PACKAGE;
    private static final long POST_CLICK_TIMEOUT_MS = 4000;
    private static final long SEARCH_POLL_TIMEOUT_MS = 4000;
    private static final long POLL_INTERVAL_MS = 500;
    private static final long RETRY_DELAY_MS = 1000;

    public interface ClickCallback {
        void onResult(boolean success, String message, String reasonCode);
    }

    private static class AttemptResult {
        boolean success;
        String message;
        String reasonCode;

        AttemptResult(boolean success, String message, String reasonCode) {
            this.success = success;
            this.message = message;
            this.reasonCode = reasonCode;
        }
    }

    public static void clickSearchWithRetry(ClickCallback callback) {
        new Thread(() -> {
            logStructured("CLICK_SEARCH_OPERATION", "REQUESTED", null, 1, false);

            SpotifyAccessibilityService service = SpotifyAccessibilityService.getInstance();
            if (service == null) {
                logStructured("CLICK_SEARCH_OPERATION", "FAILED", "ACCESSIBILITY_SERVICE_UNAVAILABLE", 1, false);
                if (callback != null) callback.onResult(false, "Accessibility Service is not connected.", "ACCESSIBILITY_SERVICE_UNAVAILABLE");
                return;
            }

            if (!SpotifyAccessibilityService.isSpotifyForeground()) {
                logStructured("CLICK_SEARCH_OPERATION", "FAILED", "SPOTIFY_NOT_FOREGROUND", 1, false);
                if (callback != null) callback.onResult(false, "Spotify is not currently in the foreground.", "SPOTIFY_NOT_FOREGROUND");
                return;
            }

            logStructured("VERIFY_FOREGROUND", "SUCCESS", null, 1, false);

            // --- ATTEMPT 1 ---
            AttemptResult attempt1 = performClickAttempt(service, 1, false);
            if (attempt1.success) {
                logStructured("CLICK_SEARCH_OPERATION", "SUCCESS", null, 1, false);
                if (callback != null) callback.onResult(true, attempt1.message, null);
                return;
            }

            // --- RETRY PREPARATION (Wait 1000 ms before attempt 2) ---
            logStructured("RETRY_ACTION", "STARTED", attempt1.reasonCode, 2, true);
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // --- ATTEMPT 2 ---
            AttemptResult attempt2 = performClickAttempt(service, 2, true);
            if (attempt2.success) {
                logStructured("CLICK_SEARCH_OPERATION", "SUCCESS", null, 2, true);
                if (callback != null) callback.onResult(true, attempt2.message, null);
            } else {
                logStructured("CLICK_SEARCH_OPERATION", "FAILED", attempt2.reasonCode, 2, true);
                if (callback != null) callback.onResult(false, attempt2.message, attempt2.reasonCode);
            }

        }).start();
    }

    private static AttemptResult performClickAttempt(SpotifyAccessibilityService service, int attempt, boolean isRetry) {
        logStructured("SEARCH_ELEMENT", "STARTED", null, attempt, isRetry);

        AccessibilityNodeInfo targetNode = null;
        AccessibilityNodeInfo activeRoot = null;
        long searchStart = System.currentTimeMillis();

        while (System.currentTimeMillis() - searchStart < SEARCH_POLL_TIMEOUT_MS) {
            activeRoot = service.getRootInActiveWindow();
            if (activeRoot != null) {
                targetNode = findSearchNode(activeRoot);
                if (targetNode != null) {
                    break;
                }
                activeRoot.recycle();
                activeRoot = null;
            }
            try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
        }

        if (targetNode == null) {
            logStructured("SEARCH_ELEMENT", "FAILED", "UI_ELEMENT_NOT_FOUND", attempt, isRetry);
            return new AttemptResult(false, "Search element not found in UI tree.", "UI_ELEMENT_NOT_FOUND");
        }

        logStructured("SEARCH_ELEMENT", "FOUND", null, attempt, isRetry);

        // Find clickable node (itself or ancestor)
        AccessibilityNodeInfo clickableNode = targetNode;
        while (clickableNode != null && !clickableNode.isClickable()) {
            AccessibilityNodeInfo parent = clickableNode.getParent();
            if (parent == null) break;
            if (clickableNode != targetNode) {
                clickableNode.recycle();
            }
            clickableNode = parent;
        }

        if (clickableNode == null) {
            clickableNode = targetNode;
        }

        logStructured("CLICK_SEARCH", "PERFORMED", null, attempt, isRetry);

        boolean clickResult = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);

        if (clickableNode != targetNode) {
            clickableNode.recycle();
        }
        targetNode.recycle();
        if (activeRoot != null) {
            activeRoot.recycle();
        }

        if (!clickResult) {
            logStructured("CLICK_SEARCH", "FAILED", "ACTION_CLICK_REJECTED", attempt, isRetry);
            return new AttemptResult(false, "Accessibility API rejected ACTION_CLICK.", "ACTION_CLICK_REJECTED");
        }

        // Post-click fresh UI re-fetching & verification
        logStructured("REFETCH_UI_TREE", "SUCCESS", null, attempt, isRetry);
        logStructured("VERIFY_STATE", "STARTED", null, attempt, isRetry);

        long verifyStart = System.currentTimeMillis();
        boolean stateVerified = false;

        while (System.currentTimeMillis() - verifyStart < POST_CLICK_TIMEOUT_MS) {
            AccessibilityNodeInfo freshRoot = service.getRootInActiveWindow();
            if (freshRoot != null) {
                if (isSearchUiActive(freshRoot)) {
                    stateVerified = true;
                    freshRoot.recycle();
                    break;
                }
                freshRoot.recycle();
            }
            try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
        }

        if (stateVerified) {
            logStructured("VERIFY_STATE", "SUCCESS", null, attempt, isRetry);
            return new AttemptResult(true, "Search element clicked and post-click state verified successfully!", null);
        } else {
            logStructured("VERIFY_STATE", "FAILED", "UNEXPECTED_STATE", attempt, isRetry);
            return new AttemptResult(false, "Click executed, but expected Search UI state did not load.", "UNEXPECTED_STATE");
        }
    }

    private static AccessibilityNodeInfo findSearchNode(AccessibilityNodeInfo root) {
        if (root == null) return null;

        String[] viewIds = new String[] {
            "com.spotify.music:id/search_tab",
            "com.spotify.music:id/search_button",
            "com.spotify.music:id/find_search_field",
            "com.spotify.music:id/query",
            "com.spotify.music:id/search_uri",
            "com.spotify.music:id/bottom_navigation_search",
            "com.spotify.music:id/search"
        };

        for (String id : viewIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }

        AccessibilityNodeInfo match = findSearchNodeDFS(root);
        if (match != null) return match;

        String[] textQueries = new String[] { "Search", "search", "Find", "Search, tab" };
        for (String q : textQueries) {
            List<AccessibilityNodeInfo> textNodes = root.findAccessibilityNodeInfosByText(q);
            if (textNodes != null && !textNodes.isEmpty()) {
                AccessibilityNodeInfo found = textNodes.get(0);
                for (int i = 1; i < textNodes.size(); i++) textNodes.get(i).recycle();
                return found;
            }
        }

        return null;
    }

    private static AccessibilityNodeInfo findSearchNodeDFS(AccessibilityNodeInfo node) {
        if (node == null) return null;

        CharSequence descChar = node.getContentDescription();
        CharSequence textChar = node.getText();
        String resId = node.getViewIdResourceName();

        String desc = descChar != null ? descChar.toString().toLowerCase() : "";
        String text = textChar != null ? textChar.toString().toLowerCase() : "";
        String res = resId != null ? resId.toLowerCase() : "";

        if (desc.contains("search") || text.contains("search") || res.contains("search_tab") || desc.contains("find")) {
            return AccessibilityNodeInfo.obtain(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findSearchNodeDFS(child);
                child.recycle();
                if (result != null) return result;
            }
        }
        return null;
    }

    private static boolean isSearchUiActive(AccessibilityNodeInfo root) {
        if (root == null) return false;

        String[] viewIds = new String[] {
            "com.spotify.music:id/query",
            "com.spotify.music:id/find_search_field",
            "com.spotify.music:id/search_edit_text",
            "com.spotify.music:id/search_text_input"
        };

        for (String id : viewIds) {
            List<AccessibilityNodeInfo> searchInputs = root.findAccessibilityNodeInfosByViewId(id);
            if (searchInputs != null && !searchInputs.isEmpty()) {
                for (AccessibilityNodeInfo n : searchInputs) n.recycle();
                return true;
            }
        }

        List<AccessibilityNodeInfo> textNodes = root.findAccessibilityNodeInfosByText("What do you want to listen to?");
        if (textNodes != null && !textNodes.isEmpty()) {
            for (AccessibilityNodeInfo n : textNodes) n.recycle();
            return true;
        }

        return false;
    }

    private static void logStructured(String action, String result, String reasonCode, int attempt, boolean isRetry) {
        String isoTimestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("timestamp=").append(isoTimestamp);
        sb.append(" action=").append(action);
        sb.append(" result=").append(result);
        if (attempt > 0) sb.append(" attempt=").append(attempt);
        if (isRetry) sb.append(" retry=true delay_ms=").append(RETRY_DELAY_MS);
        if (reasonCode != null && !reasonCode.isEmpty()) sb.append(" reason_code=").append(reasonCode);

        if ("FAILED".equals(result)) {
            Log.e(TAG, sb.toString());
        } else {
            Log.d(TAG, sb.toString());
        }
    }
}

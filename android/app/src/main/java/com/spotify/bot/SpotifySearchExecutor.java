package com.spotify.bot;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class SpotifySearchExecutor {

    private static final String TAG = "SpotifyBotSearch";

    private static final long POLL_INTERVAL_MS = 400;
    private static final long RESULTS_TIMEOUT_MS = 4000;
    private static final long POST_INPUT_DELAY_MS = 1500;
    private static final long NAV_TIMEOUT_MS = 25000; // 25s timeout for human-paced navigation

    public interface SearchCallback {
        void onResult(boolean success, String matchedTitle, String reasonCode);
    }

    private static String getIsoUtcTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public static void executeSearch(Context context, JSONObject command, SearchCallback callback) {
        new Thread(() -> {
            String runId = command.optString("run_id", "");
            String searchQuery = command.optString("search_query", "");
            JSONObject actionParams = command.optJSONObject("action_params");

            String expectedTitle = searchQuery;
            String filter = null;

            if (actionParams != null) {
                String pTitle = actionParams.optString("expected_title", null);
                if (pTitle != null && !pTitle.trim().isEmpty()) {
                    expectedTitle = pTitle;
                }
                String pFilter = actionParams.optString("filter", null);
                if (pFilter != null && !pFilter.trim().isEmpty()) {
                    filter = pFilter.toUpperCase(Locale.ROOT);
                }
            }

            Log.i(TAG, String.format("[%s] SEARCH_EXECUTION_STARTED query='%s' expected_title='%s' filter='%s' run_id=%s",
                    getIsoUtcTimestamp(), searchQuery, expectedTitle, filter, runId));

            SpotifyAccessibilityService service = SpotifyAccessibilityService.getInstance();
            if (service == null) {
                emitStepFailed(context, runId, 1, "ACCESSIBILITY_SERVICE_UNAVAILABLE");
                if (callback != null) callback.onResult(false, null, "ACCESSIBILITY_SERVICE_UNAVAILABLE");
                return;
            }

            // SUB-STEP 1: Navigate to Search Tab using SpotifyNavigator (SpotifyNavigator emits step events)
            final boolean[] navResult = {false};
            final String[] navReason = {null};
            final SpotifyScreen[] finalNavScreen = {SpotifyScreen.UNKNOWN_SCREEN};

            Object navLock = new Object();
            synchronized (navLock) {
                SpotifyNavigator.goToSearch(context, runId, (success, screen, reason) -> {
                    synchronized (navLock) {
                        navResult[0] = success;
                        navReason[0] = reason;
                        finalNavScreen[0] = screen;
                        navLock.notifyAll();
                    }
                });
                try { navLock.wait(NAV_TIMEOUT_MS); } catch (InterruptedException ignored) {}
            }

            if (!navResult[0]) {
                String rCode = navReason[0] != null ? navReason[0] : "NAVIGATION_FAILED";
                Log.e(TAG, "Search navigation failed: " + rCode);
                if (callback != null) callback.onResult(false, null, rCode);
                return;
            }

            // SUB-STEP 2: Locate Search Input Field
            emitStepStarted(context, runId, 2, "LOCATE_SEARCH_FIELD");
            AccessibilityNodeInfo searchInputNode = locateAndActivateSearchInput(service);

            if (searchInputNode == null) {
                Log.e(TAG, "Search input field not found in UI tree after polling.");
                emitStepFailed(context, runId, 2, "SEARCH_FIELD_NOT_FOUND");
                if (callback != null) callback.onResult(false, null, "SEARCH_FIELD_NOT_FOUND");
                return;
            }
            emitStepOk(context, runId, 2, "LOCATE_SEARCH_FIELD");

            // SUB-STEP 3: Clear & Set Search Query using ACTION_SET_TEXT
            emitStepStarted(context, runId, 3, "SET_SEARCH_QUERY");

            // Focus search field if needed
            searchInputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

            Bundle clearArgs = new Bundle();
            clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            searchInputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs);

            try { Thread.sleep(400); } catch (InterruptedException ignored) {}

            Bundle setTextArgs = new Bundle();
            setTextArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, searchQuery);
            boolean textSetSuccess = searchInputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArgs);
            searchInputNode.recycle();

            try { Thread.sleep(POST_INPUT_DELAY_MS); } catch (InterruptedException ignored) {}

            // Re-fetch fresh UI tree to verify text setting
            AccessibilityNodeInfo freshRoot = service.getRootInActiveWindow();
            AccessibilityNodeInfo verifyNode = locateSearchInputField(freshRoot);

            boolean textVerified = false;
            String actualText = "";
            if (verifyNode != null) {
                CharSequence currentTextChar = verifyNode.getText();
                actualText = currentTextChar != null ? currentTextChar.toString() : "";
                if (actualText.equalsIgnoreCase(searchQuery) || actualText.toLowerCase().contains(searchQuery.toLowerCase())) {
                    textVerified = true;
                }
                verifyNode.recycle();
            }
            if (freshRoot != null) freshRoot.recycle();

            if (!textSetSuccess || !textVerified) {
                Log.e(TAG, String.format("Query verification failed. expected='%s', actual='%s'", searchQuery, actualText));
                emitStepFailed(context, runId, 3, "QUERY_VERIFICATION_FAILED");
                if (callback != null) callback.onResult(false, null, "QUERY_VERIFICATION_FAILED");
                return;
            }
            emitStepOk(context, runId, 3, "SET_SEARCH_QUERY");

            // SUB-STEP 4: Submit Search
            emitStepStarted(context, runId, 4, "SUBMIT_SEARCH");
            freshRoot = service.getRootInActiveWindow();
            AccessibilityNodeInfo submitNode = locateSearchInputField(freshRoot);
            if (submitNode != null) {
                submitNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                submitNode.recycle();
            }
            if (freshRoot != null) freshRoot.recycle();

            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
            emitStepOk(context, runId, 4, "SUBMIT_SEARCH");

            // SUB-STEP 5: Wait for Search Results (Condition-based polling up to 4 seconds)
            emitStepStarted(context, runId, 5, "WAIT_FOR_RESULTS");
            long startWait = System.currentTimeMillis();
            boolean resultsDetected = false;
            List<SpotifySearchResultParser.ResultItem> resultItems = null;

            while (System.currentTimeMillis() - startWait < RESULTS_TIMEOUT_MS) {
                freshRoot = service.getRootInActiveWindow();
                if (freshRoot != null) {
                    resultItems = SpotifySearchResultParser.parseSearchResults(freshRoot);
                    freshRoot.recycle();
                    if (resultItems != null && !resultItems.isEmpty()) {
                        resultsDetected = true;
                        break;
                    }
                }
                try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
            }

            if (!resultsDetected || resultItems == null || resultItems.isEmpty()) {
                Log.e(TAG, "No search results detected within timeout.");
                emitStepFailed(context, runId, 5, "NO_RESULTS_FOUND");
                if (callback != null) callback.onResult(false, null, "NO_RESULTS_FOUND");
                return;
            }
            emitStepOk(context, runId, 5, "WAIT_FOR_RESULTS");

            // SUB-STEP 6: Optional Filter Application
            if (filter != null && !filter.isEmpty()) {
                emitStepStarted(context, runId, 6, "APPLY_FILTER");
                boolean filterSuccess = applyResultFilter(service, filter);
                if (!filterSuccess) {
                    Log.e(TAG, "Filter application failed for filter: " + filter);
                    emitStepFailed(context, runId, 6, "FILTER_FAILED");
                    if (callback != null) callback.onResult(false, null, "FILTER_FAILED");
                    return;
                }
                // Re-fetch result items post-filter
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                freshRoot = service.getRootInActiveWindow();
                if (freshRoot != null) {
                    resultItems = SpotifySearchResultParser.parseSearchResults(freshRoot);
                    freshRoot.recycle();
                }
                emitStepOk(context, runId, 6, "APPLY_FILTER");
            }

            // SUB-STEP 7: Fuzzy Result Matching
            emitStepStarted(context, runId, 7, "MATCH_RESULT");
            SpotifyFuzzyMatcher.MatchResult bestMatch = null;
            SpotifySearchResultParser.ResultItem bestCandidate = null;

            for (SpotifySearchResultParser.ResultItem item : resultItems) {
                SpotifyFuzzyMatcher.MatchResult eval = SpotifyFuzzyMatcher.evaluateMatch(expectedTitle, item.title);
                if (eval.isMatched) {
                    if (bestMatch == null || eval.score > bestMatch.score) {
                        bestMatch = eval;
                        bestCandidate = item;
                    }
                }
            }

            if (bestMatch == null || bestCandidate == null) {
                Log.e(TAG, "No result candidate matched expected title: " + expectedTitle);
                emitStepFailed(context, runId, 7, "RESULT_MATCH_FAILED");
                if (callback != null) callback.onResult(false, null, "RESULT_MATCH_FAILED");
                return;
            }

            Log.i(TAG, String.format("[%s] RESULT_MATCHED title='%s' score=%.2f",
                    getIsoUtcTimestamp(), bestCandidate.title, bestMatch.score));
            emitStepOk(context, runId, 7, "MATCH_RESULT");

            if (callback != null) callback.onResult(true, bestCandidate.title, null);

        }).start();
    }

    static AccessibilityNodeInfo locateSearchInputField(AccessibilityNodeInfo root) {
        return findActiveSearchNode(root);
    }

    static AccessibilityNodeInfo locateAndActivateSearchInput(SpotifyAccessibilityService service) {
        long start = System.currentTimeMillis();
        long timeoutMs = 12000;

        while (System.currentTimeMillis() - start < timeoutMs) {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root != null) {
                // 1. Direct search for active EditText / search input
                AccessibilityNodeInfo activeInput = findActiveSearchNode(root);
                if (activeInput != null && isEditableInput(activeInput)) {
                    root.recycle();
                    return activeInput;
                }

                // 2. Pure Accessibility Node Click on "What do you want to listen to?" placeholder or search tab
                AccessibilityNodeInfo searchBoxTextNode = findNodeByDfs(root, "what do you want to listen to", "artists, songs");
                if (searchBoxTextNode != null) {
                    performClickOnNodeOrAncestor(searchBoxTextNode);
                    searchBoxTextNode.recycle();
                } else {
                    AccessibilityNodeInfo searchTab = findSearchTabNode(root);
                    if (searchTab != null) {
                        performClickOnNodeOrAncestor(searchTab);
                        searchTab.recycle();
                    } else {
                        List<AccessibilityNodeInfo> composeViews = root.findAccessibilityNodeInfosByViewId("com.spotify.music:id/compose_view");
                        if (composeViews != null && !composeViews.isEmpty()) {
                            performClickOnNodeOrAncestor(composeViews.get(0));
                            for (AccessibilityNodeInfo n : composeViews) n.recycle();
                        }
                    }
                }
                root.recycle();

                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}

                AccessibilityNodeInfo fresh = service.getRootInActiveWindow();
                if (fresh != null) {
                    AccessibilityNodeInfo freshInput = findActiveSearchNode(fresh);
                    if (freshInput != null && isEditableInput(freshInput)) {
                        fresh.recycle();
                        return freshInput;
                    }
                    if (freshInput != null) {
                        fresh.recycle();
                        return freshInput;
                    }
                    fresh.recycle();
                }
            }
            try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
        }
        return null;
    }

    private static boolean isEditableInput(AccessibilityNodeInfo node) {
        if (node == null) return false;
        CharSequence cls = node.getClassName();
        if (cls != null && cls.toString().contains("EditText")) return true;
        if (node.isEditable()) return true;
        String res = node.getViewIdResourceName();
        if (res != null && (res.contains("query") || res.contains("search_edit_text") || res.contains("search_text_input") || res.contains("filter_compose"))) return true;
        return false;
    }

    static AccessibilityNodeInfo findActiveSearchNode(AccessibilityNodeInfo root) {
        if (root == null) return null;

        // 1. Check filter_compose container (Jetpack Compose search header)
        List<AccessibilityNodeInfo> filterContainers = root.findAccessibilityNodeInfosByViewId("com.spotify.music:id/filter_compose");
        if (filterContainers != null && !filterContainers.isEmpty()) {
            for (AccessibilityNodeInfo container : filterContainers) {
                AccessibilityNodeInfo editInFilter = findFirstEditText(container);
                if (editInFilter != null) {
                    for (AccessibilityNodeInfo c : filterContainers) c.recycle();
                    return editInFilter;
                }
                container.recycle();
            }
        }

        // 2. Direct EditText search across entire tree
        AccessibilityNodeInfo editText = findFirstEditText(root);
        if (editText != null) return editText;

        // 3. Known candidate view IDs
        String[] candidateIds = {
                "com.spotify.music:id/query",
                "com.spotify.music:id/search_edit_text",
                "com.spotify.music:id/search_text_input",
                "com.spotify.music:id/find_search_field",
                "com.spotify.music:id/search_view",
                "com.spotify.music:id/search_field",
                "com.spotify.music:id/search_box"
        };

        for (String id : candidateIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }

        // 4. Fallback text/desc search
        String[] candidateTexts = {"What do you want to listen to?", "Search", "search", "Artists, songs, or podcasts"};
        for (String t : candidateTexts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(t);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }

        return null;
    }

    private static AccessibilityNodeInfo findSearchTabNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        String[] ids = {
                "com.spotify.music:id/search_tab",
                "com.spotify.music:id/bottom_navigation_search"
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

    private static AccessibilityNodeInfo findFirstEditText(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence cls = node.getClassName();
        if (cls != null && cls.toString().contains("EditText")) {
            return AccessibilityNodeInfo.obtain(node);
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findFirstEditText(child);
                child.recycle();
                if (found != null) return found;
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findNodeByDfs(AccessibilityNodeInfo node, String... keywords) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        CharSequence text = node.getText();
        String descStr = desc != null ? desc.toString().toLowerCase(Locale.ROOT) : "";
        String textStr = text != null ? text.toString().toLowerCase(Locale.ROOT) : "";
        for (String kw : keywords) {
            if (!kw.isEmpty() && (descStr.contains(kw) || textStr.contains(kw))) {
                return AccessibilityNodeInfo.obtain(node);
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findNodeByDfs(child, keywords);
                child.recycle();
                if (res != null) return res;
            }
        }
        return null;
    }

    private static boolean performClickOnNodeOrAncestor(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // 1. Direct click on node if clickable
        if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }

        // 2. Traverse up parent hierarchy (up to 6 levels)
        AccessibilityNodeInfo current = node.getParent();
        for (int i = 0; i < 6 && current != null; i++) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                current.recycle();
                return true;
            }
            AccessibilityNodeInfo next = current.getParent();
            current.recycle();
            current = next;
        }

        // 3. Traverse down children (1-level)
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (child.isClickable() && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }

        // 4. Default attempt on original node
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private static boolean applyResultFilter(SpotifyAccessibilityService service, String filterName) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(filterName);
        if (nodes != null && !nodes.isEmpty()) {
            AccessibilityNodeInfo filterNode = nodes.get(0);
            boolean clicked = filterNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            for (int i = 0; i < nodes.size(); i++) nodes.get(i).recycle();
            root.recycle();
            return clicked;
        }

        root.recycle();
        return false;
    }

    // --- TELEMETRY EMITTERS ---

    private static void emitStepStarted(Context context, String runId, int stepIndex, String stepName) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "STEP_STARTED");
            event.put("run_id", runId);
            JSONObject payload = new JSONObject();
            payload.put("step_index", stepIndex);
            payload.put("step_name", stepName);
            event.put("payload", payload);

            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException ignored) {}
    }

    private static void emitStepOk(Context context, String runId, int stepIndex, String stepName) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "STEP_OK");
            event.put("run_id", runId);
            JSONObject payload = new JSONObject();
            payload.put("step_index", stepIndex);
            payload.put("step_name", stepName);
            event.put("payload", payload);

            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException ignored) {}
    }

    private static void emitStepFailed(Context context, String runId, int stepIndex, String reasonCode) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "STEP_FAILED");
            event.put("run_id", runId);
            JSONObject payload = new JSONObject();
            payload.put("step_index", stepIndex);
            payload.put("reason_code", reasonCode);
            event.put("payload", payload);

            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException ignored) {}
    }
}

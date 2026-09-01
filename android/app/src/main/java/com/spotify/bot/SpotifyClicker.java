package com.spotify.bot;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class SpotifyClicker {

    private static final String TAG = SpotifyAccessibilityService.TAG;
    private static final String SPOTIFY_PACKAGE = SpotifyAccessibilityService.SPOTIFY_PACKAGE;
    private static final long TIMEOUT_MS = 5000; // 5 seconds post-click verification
    private static final long SEARCH_POLL_TIMEOUT_MS = 6000; // 6 seconds search polling loop
    private static final long POLL_INTERVAL_MS = 500;

    public interface ClickCallback {
        void onResult(boolean success, String message);
    }

    public static void clickSearch(ClickCallback callback) {
        new Thread(() -> {
            Log.d(TAG, "Click action requested");

            SpotifyAccessibilityService service = SpotifyAccessibilityService.getInstance();
            if (service == null) {
                Log.e(TAG, "Accessibility Service unavailable");
                Log.e(TAG, "Click action failed");
                if (callback != null) callback.onResult(false, "Accessibility Service is not connected.");
                return;
            }

            if (!SpotifyAccessibilityService.isSpotifyForeground()) {
                Log.e(TAG, "Spotify not foreground");
                Log.e(TAG, "Click action failed");
                if (callback != null) callback.onResult(false, "Spotify is not currently in the foreground.");
                return;
            }

            Log.d(TAG, "Spotify foreground verified");
            Log.d(TAG, "Target selector being searched");

            // Polling loop: Wait for Spotify UI to render and find the Search node
            AccessibilityNodeInfo targetNode = null;
            AccessibilityNodeInfo activeRoot = null;
            long searchStart = System.currentTimeMillis();

            while (System.currentTimeMillis() - searchStart < SEARCH_POLL_TIMEOUT_MS) {
                activeRoot = service.getRootInActiveWindow();
                if (activeRoot != null) {
                    targetNode = findSearchNode(activeRoot);
                    if (targetNode != null) {
                        Log.d(TAG, "Root UI node obtained");
                        break;
                    }
                    activeRoot.recycle();
                    activeRoot = null;
                }
                try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
            }

            if (targetNode == null) {
                Log.e(TAG, "Target node not found");
                // Dump visible UI nodes for diagnosis if search failed
                AccessibilityNodeInfo debugRoot = service.getRootInActiveWindow();
                if (debugRoot != null) {
                    Log.d(TAG, "--- BEGIN SPOTIFY UI NODE DUMP ---");
                    dumpNodes(debugRoot, 0);
                    Log.d(TAG, "--- END SPOTIFY UI NODE DUMP ---");
                    debugRoot.recycle();
                }
                Log.e(TAG, "Click action failed");
                if (callback != null) callback.onResult(false, "Spotify Search element could not be found.");
                return;
            }

            Log.d(TAG, "Target node found");

            // Resolve clickable node (node itself or nearest clickable ancestor)
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

            Log.d(TAG, "Target node validated");
            Log.d(TAG, "ACTION_CLICK requested");

            boolean clickResult = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "ACTION_CLICK result: " + clickResult);

            if (clickableNode != targetNode) {
                clickableNode.recycle();
            }
            targetNode.recycle();
            if (activeRoot != null) {
                activeRoot.recycle();
            }

            if (!clickResult) {
                Log.e(TAG, "Click action failed");
                if (callback != null) callback.onResult(false, "ACTION_CLICK failed to execute on node.");
                return;
            }

            // Post-click verification
            Log.d(TAG, "Waiting for post-click state");
            long verifyStart = System.currentTimeMillis();
            boolean stateChanged = false;

            while (System.currentTimeMillis() - verifyStart < TIMEOUT_MS) {
                AccessibilityNodeInfo newRoot = service.getRootInActiveWindow();
                if (newRoot != null) {
                    if (isSearchUiActive(newRoot)) {
                        stateChanged = true;
                        newRoot.recycle();
                        break;
                    }
                    newRoot.recycle();
                }
                try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
            }

            if (stateChanged) {
                Log.d(TAG, "Expected UI state detected");
                Log.d(TAG, "Click verification successful");
                if (callback != null) callback.onResult(true, "Search element clicked and verified successfully!");
            } else {
                Log.w(TAG, "Post-click verification timeout");
                Log.d(TAG, "Click verification completed with state timeout warning");
                if (callback != null) callback.onResult(true, "Click executed, but UI change took longer to observe.");
            }

        }).start();
    }

    private static AccessibilityNodeInfo findSearchNode(AccessibilityNodeInfo root) {
        if (root == null) return null;

        // Strategy 1: Search by common Spotify resource IDs
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

        // Strategy 2: DFS traversal by Content Description or Text or Resource Name
        AccessibilityNodeInfo match = findSearchNodeDFS(root);
        if (match != null) return match;

        // Strategy 3: Search by exact or partial text
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

        List<AccessibilityNodeInfo> searchInputs = root.findAccessibilityNodeInfosByViewId("com.spotify.music:id/query");
        if (searchInputs != null && !searchInputs.isEmpty()) {
            for (AccessibilityNodeInfo n : searchInputs) n.recycle();
            return true;
        }

        List<AccessibilityNodeInfo> searchFields = root.findAccessibilityNodeInfosByViewId("com.spotify.music:id/find_search_field");
        if (searchFields != null && !searchFields.isEmpty()) {
            for (AccessibilityNodeInfo n : searchFields) n.recycle();
            return true;
        }

        return false;
    }

    private static void dumpNodes(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 4) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String resId = node.getViewIdResourceName();

        if (text != null || desc != null || resId != null) {
            Log.d(TAG, String.format("UI Node [d=%d]: id=[%s], text=[%s], desc=[%s], clickable=[%b]",
                depth, resId, text, desc, node.isClickable()));
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                dumpNodes(child, depth + 1);
                child.recycle();
            }
        }
    }
}

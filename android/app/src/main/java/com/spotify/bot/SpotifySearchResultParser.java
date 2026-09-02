package com.spotify.bot;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public class SpotifySearchResultParser {

    public static class ResultItem {
        public final String title;
        public final String subtitle;
        public final String contentDescription;
        public final String resourceId;
        public final AccessibilityNodeInfo node;

        public ResultItem(String title, String subtitle, String contentDescription, String resourceId, AccessibilityNodeInfo node) {
            this.title = title;
            this.subtitle = subtitle;
            this.contentDescription = contentDescription;
            this.resourceId = resourceId;
            this.node = node;
        }
    }

    /**
     * Parses the search result items from the Accessibility UI tree.
     */
    public static List<ResultItem> parseSearchResults(AccessibilityNodeInfo root) {
        List<ResultItem> results = new ArrayList<>();
        if (root == null) return results;

        collectResultNodes(root, results);
        return results;
    }

    private static void collectResultNodes(AccessibilityNodeInfo node, List<ResultItem> results) {
        if (node == null) return;

        CharSequence descChar = node.getContentDescription();
        CharSequence textChar = node.getText();
        String resId = node.getViewIdResourceName();

        String desc = descChar != null ? descChar.toString() : "";
        String text = textChar != null ? textChar.toString() : "";
        String resourceId = resId != null ? resId : "";

        // Check if node is clickable row or result item container
        if (node.isClickable() && (!desc.isEmpty() || !text.isEmpty())) {
            // Exclude bottom navigation bar items and top search field
            if (!desc.toLowerCase().contains("tab") && !resourceId.contains("find_search_field") && !resourceId.contains("query")) {
                String title = !text.isEmpty() ? text : desc;
                results.add(new ResultItem(title, "", desc, resourceId, AccessibilityNodeInfo.obtain(node)));
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectResultNodes(child, results);
                child.recycle();
            }
        }
    }
}

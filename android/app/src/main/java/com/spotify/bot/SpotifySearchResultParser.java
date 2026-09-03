package com.spotify.bot;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpotifySearchResultParser {

    public static class ResultItem {
        public final String title;
        public final String subtitle;
        public final String contentDescription;
        public final String resourceId;
        public final String itemType; // "ARTIST", "SONG", "PLAYLIST", "ALBUM", "PODCAST", "UNKNOWN"
        public final AccessibilityNodeInfo node;

        public ResultItem(String title, String subtitle, String contentDescription, String resourceId, AccessibilityNodeInfo node) {
            this(title, subtitle, contentDescription, resourceId, determineItemType(subtitle, contentDescription), node);
        }

        public ResultItem(String title, String subtitle, String contentDescription, String resourceId, String itemType, AccessibilityNodeInfo node) {
            this.title = title != null ? title : "";
            this.subtitle = subtitle != null ? subtitle : "";
            this.contentDescription = contentDescription != null ? contentDescription : "";
            this.resourceId = resourceId != null ? resourceId : "";
            this.itemType = itemType != null ? itemType : determineItemType(subtitle, contentDescription);
            this.node = node;
        }

        public static String determineItemType(String subtitle, String desc) {
            String combined = ((subtitle != null ? subtitle : "") + " " + (desc != null ? desc : "")).toLowerCase(Locale.ROOT);
            if (combined.contains("artist")) return "ARTIST";
            if (combined.contains("song") || combined.contains("track")) return "SONG";
            if (combined.contains("playlist")) return "PLAYLIST";
            if (combined.contains("album")) return "ALBUM";
            if (combined.contains("podcast") || combined.contains("show") || combined.contains("episode")) return "PODCAST";
            return "UNKNOWN";
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
        if (node.isClickable() && (!desc.isEmpty() || !text.isEmpty() || node.getChildCount() > 0)) {
            String descLower = desc.toLowerCase(Locale.ROOT);
            if (!descLower.contains("tab") && !resourceId.contains("find_search_field") && !resourceId.contains("filter_compose")) {
                // Check if this is a search suggestion row (has tap_ahead_button or query child with no subtitle/artwork)
                boolean isSuggestion = hasChildWithId(node, "com.spotify.music:id/tap_ahead_button")
                        || (hasChildWithId(node, "com.spotify.music:id/query") && !hasChildWithId(node, "com.spotify.music:id/artwork") && !hasChildWithId(node, "com.spotify.music:id/subtitle"));

                String title = getChildTextById(node, "com.spotify.music:id/title");
                String subtitle = getChildTextById(node, "com.spotify.music:id/subtitle");

                if (title.isEmpty()) {
                    List<String> childTexts = new ArrayList<>();
                    extractTexts(node, childTexts);
                    title = !text.isEmpty() ? text : (!childTexts.isEmpty() ? childTexts.get(0) : desc);
                    if (subtitle.isEmpty() && childTexts.size() > 1) {
                        subtitle = childTexts.get(1);
                    }
                }

                if (!title.isEmpty()) {
                    String itemType = isSuggestion ? "SUGGESTION" : ResultItem.determineItemType(subtitle, desc);
                    results.add(new ResultItem(title, subtitle, desc, resourceId, itemType, AccessibilityNodeInfo.obtain(node)));
                }
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

    private static boolean hasChildWithId(AccessibilityNodeInfo node, String viewId) {
        if (node == null) return false;
        List<AccessibilityNodeInfo> nodes = node.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo n : nodes) n.recycle();
            return true;
        }
        return false;
    }

    private static String getChildTextById(AccessibilityNodeInfo node, String viewId) {
        if (node == null) return "";
        List<AccessibilityNodeInfo> nodes = node.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes != null && !nodes.isEmpty()) {
            AccessibilityNodeInfo n = nodes.get(0);
            CharSequence t = n.getText();
            String result = t != null ? t.toString().trim() : "";
            for (AccessibilityNodeInfo item : nodes) item.recycle();
            return result;
        }
        return "";
    }

    private static void extractTexts(AccessibilityNodeInfo node, List<String> list) {
        if (node == null) return;
        CharSequence t = node.getText();
        if (t != null && t.length() > 0) {
            String str = t.toString().trim();
            if (!str.isEmpty() && !list.contains(str)) {
                list.add(str);
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractTexts(child, list);
                child.recycle();
            }
        }
    }
}

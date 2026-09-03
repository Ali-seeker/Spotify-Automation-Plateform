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
            // Exclude bottom navigation bar items and top search field
            String descLower = desc.toLowerCase(Locale.ROOT);
            if (!descLower.contains("tab") && !resourceId.contains("find_search_field") && !resourceId.contains("query")) {
                List<String> childTexts = new ArrayList<>();
                extractTexts(node, childTexts);
                String title = !text.isEmpty() ? text : (!childTexts.isEmpty() ? childTexts.get(0) : desc);
                String subtitle = childTexts.size() > 1 ? childTexts.get(1) : "";
                if (title.isEmpty() && !desc.isEmpty()) {
                    title = desc;
                }
                if (!title.isEmpty()) {
                    results.add(new ResultItem(title, subtitle, desc, resourceId, AccessibilityNodeInfo.obtain(node)));
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

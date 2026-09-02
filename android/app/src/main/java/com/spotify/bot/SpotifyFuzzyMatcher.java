package com.spotify.bot;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class SpotifyFuzzyMatcher {

    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.50;

    public static class MatchResult {
        public final boolean isMatched;
        public final double score;
        public final String matchedTitle;
        public final String expectedTitle;

        public MatchResult(boolean isMatched, double score, String matchedTitle, String expectedTitle) {
            this.isMatched = isMatched;
            this.score = score;
            this.matchedTitle = matchedTitle;
            this.expectedTitle = expectedTitle;
        }
    }

    /**
     * Normalizes a text string: converts to lowercase, strips accents, removes punctuation,
     * and collapses multiple spaces.
     */
    public static String normalize(String input) {
        if (input == null) return "";
        // Convert to lowercase
        String str = input.toLowerCase(Locale.ROOT).trim();
        // Strip accents (Unicode NFD)
        str = Normalizer.normalize(str, Normalizer.Form.NFD);
        str = str.replaceAll("\\p{M}", "");
        // Remove punctuation
        str = PUNCTUATION_PATTERN.matcher(str).replaceAll(" ");
        // Collapse spaces
        str = MULTI_SPACE_PATTERN.matcher(str).replaceAll(" ").trim();
        return str;
    }

    /**
     * Computes similarity score between expected text and target text (0.0 to 1.0).
     */
    public static double computeSimilarity(String expected, String target) {
        String normExpected = normalize(expected);
        String normTarget = normalize(target);

        if (normExpected.isEmpty() || normTarget.isEmpty()) {
            return 0.0;
        }

        // 1. Exact match
        if (normExpected.equals(normTarget)) {
            return 1.0;
        }

        // 2. Starts with / Contains match
        if (normTarget.startsWith(normExpected) || normExpected.startsWith(normTarget)) {
            return 0.95;
        }
        if (normTarget.contains(normExpected) || normExpected.contains(normTarget)) {
            return 0.85;
        }

        // 3. Levenshtein Distance Ratio
        int distance = levenshteinDistance(normExpected, normTarget);
        int maxLen = Math.max(normExpected.length(), normTarget.length());
        if (maxLen == 0) return 1.0;

        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Evaluates a candidate title against an expected title.
     */
    public static MatchResult evaluateMatch(String expectedTitle, String candidateTitle) {
        double score = computeSimilarity(expectedTitle, candidateTitle);
        boolean isMatch = score >= MIN_CONFIDENCE_THRESHOLD;
        return new MatchResult(isMatch, score, candidateTitle, expectedTitle);
    }

    private static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[len1][len2];
    }
}

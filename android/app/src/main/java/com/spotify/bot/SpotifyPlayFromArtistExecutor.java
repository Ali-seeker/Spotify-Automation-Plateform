package com.spotify.bot;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class SpotifyPlayFromArtistExecutor {

    private static final String TAG = "SpotifyBotPlayArtist";

    // Timing constants
    private static final long POLL_INTERVAL_MS = 350;
    private static final long SEARCH_RESULTS_TIMEOUT_MS = 5000;
    private static final long SCREEN_TRANSITION_TIMEOUT_MS = 8000;
    private static final long PLAYBACK_VERIFICATION_TIMEOUT_MS = 10000; // 10s strict timeout
    private static final long POST_CLICK_DELAY_MS = 1500;
    private static final long NAV_TIMEOUT_MS = 25000;

    public interface PlayCallback {
        void onResult(boolean success, String message, String reasonCode);
    }

    public enum PlayMode {
        CATALOG,
        THIS_IS,
        RADIO
    }

    private static String getIsoUtcTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    /**
     * Entry point for executing spotify.play_from_artist action.
     */
    public static void executePlayFromArtist(Context context, JSONObject command, PlayCallback callback) {
        new Thread(() -> {
            String runId = command.optString("run_id", "");
            String searchQuery = command.optString("search_query", "").trim();
            JSONObject actionParams = command.optJSONObject("action_params");

            String artistName = searchQuery;
            String playModeStr = "CATALOG";

            if (actionParams != null) {
                String pArtist = actionParams.optString("artist_name", null);
                if (pArtist != null && !pArtist.trim().isEmpty()) {
                    artistName = pArtist.trim();
                } else {
                    String pTitle = actionParams.optString("expected_title", null);
                    if (pTitle != null && !pTitle.trim().isEmpty()) {
                        artistName = pTitle.trim();
                    }
                }

                String pMode = actionParams.optString("play_mode", null);
                if (pMode != null && !pMode.trim().isEmpty()) {
                    playModeStr = pMode.trim().toUpperCase(Locale.ROOT);
                }
            }

            // 1. VALIDATE INPUT PARAMETERS
            if (artistName.isEmpty()) {
                Log.e(TAG, "Validation failed: artist_name is empty.");
                emitStepFailed(context, runId, 1, "INVALID_ACTION_PARAMS", null);
                if (callback != null) callback.onResult(false, "Artist name is required.", "INVALID_ACTION_PARAMS");
                return;
            }

            PlayMode playMode;
            try {
                playMode = PlayMode.valueOf(playModeStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Validation failed: Invalid play_mode '" + playModeStr + "'. Supported: CATALOG, THIS_IS, RADIO");
                emitStepFailed(context, runId, 1, "INVALID_PLAY_MODE", null);
                if (callback != null) callback.onResult(false, "Invalid play_mode: " + playModeStr, "INVALID_PLAY_MODE");
                return;
            }

            Log.i(TAG, String.format("[%s] PLAY_FROM_ARTIST_STARTED artist='%s' play_mode=%s run_id=%s",
                    getIsoUtcTimestamp(), artistName, playMode.name(), runId));

            SpotifyAccessibilityService service = SpotifyAccessibilityService.getInstance();
            if (service == null) {
                emitStepFailed(context, runId, 1, "ACCESSIBILITY_SERVICE_UNAVAILABLE", null);
                if (callback != null) callback.onResult(false, "Accessibility Service is not connected.", "ACCESSIBILITY_SERVICE_UNAVAILABLE");
                return;
            }

            // -------------------------------------------------------------
            // STEP 1: SEARCH FOR ARTIST
            // -------------------------------------------------------------
            emitStepStarted(context, runId, 1, "SEARCH_ARTIST");

            // Navigate to Search Tab using SpotifyNavigator
            final boolean[] navResult = {false};
            final String[] navReason = {null};
            Object navLock = new Object();

            synchronized (navLock) {
                SpotifyNavigator.goToSearch(context, runId, (success, screen, reason) -> {
                    synchronized (navLock) {
                        navResult[0] = success;
                        navReason[0] = reason;
                        navLock.notifyAll();
                    }
                });
                try { navLock.wait(NAV_TIMEOUT_MS); } catch (InterruptedException ignored) {}
            }

            if (!navResult[0]) {
                String rCode = navReason[0] != null ? navReason[0] : "NAVIGATION_FAILED";
                Log.e(TAG, "Search navigation failed: " + rCode);
                emitStepFailed(context, runId, 1, rCode, null);
                if (callback != null) callback.onResult(false, "Failed to navigate to search.", rCode);
                return;
            }

            // Locate & Activate Search Field with Bounded Polling
            AccessibilityNodeInfo searchInputNode = locateAndActivateSearchInput(service);

            if (searchInputNode == null) {
                Log.e(TAG, "Search input field not found in UI tree after polling.");
                emitStepFailed(context, runId, 1, "SEARCH_FIELD_NOT_FOUND", null);
                if (callback != null) callback.onResult(false, "Search field not found.", "SEARCH_FIELD_NOT_FOUND");
                return;
            }

            // Set Search Query
            searchInputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Bundle clearArgs = new Bundle();
            clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            searchInputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs);

            try { Thread.sleep(400); } catch (InterruptedException ignored) {}

            Bundle setTextArgs = new Bundle();
            setTextArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, artistName);
            boolean textSet = searchInputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArgs);
            searchInputNode.recycle();

            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

            // Submit Search / Focus Results
            AccessibilityNodeInfo freshRoot = service.getRootInActiveWindow();
            if (freshRoot != null) {
                AccessibilityNodeInfo submitNode = findActiveSearchNode(freshRoot);
                if (submitNode != null) {
                    submitNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    submitNode.recycle();
                }
                freshRoot.recycle();
            }

            // Wait for Search Results
            long startWait = System.currentTimeMillis();
            List<SpotifySearchResultParser.ResultItem> resultItems = null;

            while (System.currentTimeMillis() - startWait < SEARCH_RESULTS_TIMEOUT_MS) {
                freshRoot = service.getRootInActiveWindow();
                if (freshRoot != null) {
                    resultItems = SpotifySearchResultParser.parseSearchResults(freshRoot);
                    freshRoot.recycle();
                    if (resultItems != null && !resultItems.isEmpty()) {
                        break;
                    }
                }
                try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
            }

            if (resultItems == null || resultItems.isEmpty()) {
                Log.e(TAG, "No search results returned for query: " + artistName);
                emitStepFailed(context, runId, 1, "NO_RESULTS_FOUND", null);
                if (callback != null) callback.onResult(false, "No results found for artist.", "NO_RESULTS_FOUND");
                return;
            }

            emitStepOk(context, runId, 1, "SEARCH_ARTIST");

            // -------------------------------------------------------------
            // STEP 2: SELECT MATCHING ARTIST
            // -------------------------------------------------------------
            emitStepStarted(context, runId, 2, "SELECT_ARTIST");

            SpotifySearchResultParser.ResultItem matchedArtistItem = findBestArtistMatch(resultItems, artistName);

            if (matchedArtistItem == null) {
                Log.e(TAG, "No candidate matched artist name: " + artistName);
                emitStepFailed(context, runId, 2, "ARTIST_NOT_FOUND", null);
                if (callback != null) callback.onResult(false, "Matching artist not found in search results.", "ARTIST_NOT_FOUND");
                return;
            }

            Log.i(TAG, String.format("[%s] ARTIST_MATCHED title='%s' subtitle='%s' type='%s'",
                    getIsoUtcTimestamp(), matchedArtistItem.title, matchedArtistItem.subtitle, matchedArtistItem.itemType));

            // Click the matching artist result
            boolean clickSuccess = performClickOnNodeOrAncestor(matchedArtistItem.node);

            // Recycle nodes from search result items
            for (SpotifySearchResultParser.ResultItem item : resultItems) {
                if (item.node != null) {
                    item.node.recycle();
                }
            }

            if (!clickSuccess) {
                // If initial node click failed, re-fetch fresh root and try re-finding
                freshRoot = service.getRootInActiveWindow();
                if (freshRoot != null) {
                    List<SpotifySearchResultParser.ResultItem> freshResults = SpotifySearchResultParser.parseSearchResults(freshRoot);
                    SpotifySearchResultParser.ResultItem freshMatch = findBestArtistMatch(freshResults, artistName);
                    if (freshMatch != null && freshMatch.node != null) {
                        clickSuccess = performClickOnNodeOrAncestor(freshMatch.node);
                    }
                    for (SpotifySearchResultParser.ResultItem item : freshResults) {
                        if (item.node != null) item.node.recycle();
                    }
                    freshRoot.recycle();
                }
            }

            if (!clickSuccess) {
                Log.e(TAG, "Failed to click matched artist result node.");
                emitStepFailed(context, runId, 2, "RESULT_SELECTION_FAILED", null);
                if (callback != null) callback.onResult(false, "Failed to click artist search result.", "RESULT_SELECTION_FAILED");
                return;
            }

            emitStepOk(context, runId, 2, "SELECT_ARTIST");

            // -------------------------------------------------------------
            // STEP 3: OPEN & VERIFY ARTIST PAGE
            // -------------------------------------------------------------
            emitStepStarted(context, runId, 3, "OPEN_ARTIST_PAGE");
            try { Thread.sleep(POST_CLICK_DELAY_MS); } catch (InterruptedException ignored) {}

            long startNavWait = System.currentTimeMillis();
            boolean artistPageLoaded = false;
            SpotifyScreen detectedScreen = SpotifyScreen.UNKNOWN_SCREEN;

            while (System.currentTimeMillis() - startNavWait < SCREEN_TRANSITION_TIMEOUT_MS) {
                freshRoot = service.getRootInActiveWindow();
                if (freshRoot != null) {
                    detectedScreen = SpotifyNavigator.detectCurrentScreen(freshRoot);
                    if (detectedScreen == SpotifyScreen.ARTIST_PAGE || isArtistPageCustomCheck(freshRoot, artistName)) {
                        artistPageLoaded = true;
                        freshRoot.recycle();
                        break;
                    }
                    freshRoot.recycle();
                }
                try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
            }

            if (!artistPageLoaded) {
                Log.e(TAG, String.format("[%s] ARTIST_PAGE_NAVIGATION_FAILED screen=%s", getIsoUtcTimestamp(), detectedScreen.name()));
                String reason = detectedScreen == SpotifyScreen.UNKNOWN_SCREEN ? "UNKNOWN_SCREEN" : "NAVIGATION_FAILED";
                emitStepFailed(context, runId, 3, reason, null);
                if (callback != null) callback.onResult(false, "Failed to navigate to Artist Page.", reason);
                return;
            }

            emitStepOk(context, runId, 3, "OPEN_ARTIST_PAGE");

            // -------------------------------------------------------------
            // STEP 4: EXECUTE PLAYBACK MODE (CATALOG, THIS_IS, RADIO)
            // -------------------------------------------------------------
            boolean playbackTriggered = false;
            String playStepName = "PLAY_" + playMode.name();

            switch (playMode) {
                case CATALOG:
                    emitStepStarted(context, runId, 4, "PLAY_CATALOG");
                    playbackTriggered = executeCatalogMode(service, artistName);
                    if (playbackTriggered) {
                        emitStepOk(context, runId, 4, "PLAY_CATALOG");
                    } else {
                        emitStepFailed(context, runId, 4, "UI_ELEMENT_NOT_FOUND", null);
                        if (callback != null) callback.onResult(false, "Shuffle play button not found on Artist Page.", "UI_ELEMENT_NOT_FOUND");
                        return;
                    }
                    break;

                case THIS_IS:
                    emitStepStarted(context, runId, 4, "OPEN_THIS_IS_PLAYLIST");
                    boolean playlistOpened = openThisIsPlaylist(service, context, runId, artistName);
                    if (!playlistOpened) {
                        emitStepFailed(context, runId, 4, "PLAYLIST_NOT_FOUND", null);
                        if (callback != null) callback.onResult(false, "Could not locate 'This Is " + artistName + "' playlist.", "PLAYLIST_NOT_FOUND");
                        return;
                    }
                    emitStepOk(context, runId, 4, "OPEN_THIS_IS_PLAYLIST");

                    emitStepStarted(context, runId, 5, "PLAY_THIS_IS");
                    playbackTriggered = triggerPlaylistPlayback(service);
                    if (playbackTriggered) {
                        emitStepOk(context, runId, 5, "PLAY_THIS_IS");
                    } else {
                        emitStepFailed(context, runId, 5, "UI_ELEMENT_NOT_FOUND", null);
                        if (callback != null) callback.onResult(false, "Play button not found on This Is playlist page.", "UI_ELEMENT_NOT_FOUND");
                        return;
                    }
                    break;

                case RADIO:
                    emitStepStarted(context, runId, 4, "PLAY_RADIO");
                    playbackTriggered = executeRadioMode(service, context, runId, artistName);
                    if (playbackTriggered) {
                        emitStepOk(context, runId, 4, "PLAY_RADIO");
                    } else {
                        emitStepFailed(context, runId, 4, "RADIO_NOT_FOUND", null);
                        if (callback != null) callback.onResult(false, "Could not start Artist Radio.", "RADIO_NOT_FOUND");
                        return;
                    }
                    break;
            }

            // -------------------------------------------------------------
            // STEP 5: VERIFY PLAYBACK (Max 10 Seconds Strict Timeout)
            // -------------------------------------------------------------
            int verifyStepIndex = (playMode == PlayMode.THIS_IS) ? 6 : 5;
            emitStepStarted(context, runId, verifyStepIndex, "VERIFY_PLAYBACK");

            PlaybackVerificationResult verification = verifyPlaybackStarted(service, artistName, PLAYBACK_VERIFICATION_TIMEOUT_MS);

            if (verification.isSuccess) {
                Log.i(TAG, String.format("[%s] PLAYBACK_VERIFIED artist='%s' track='%s' elapsed_ms=%d",
                        getIsoUtcTimestamp(), verification.detectedArtist, verification.detectedTrack, verification.elapsedMs));
                emitStepOk(context, runId, verifyStepIndex, "VERIFY_PLAYBACK");
                if (callback != null) callback.onResult(true, "Playback successfully started and verified for " + artistName, null);
            } else {
                Log.e(TAG, String.format("[%s] PLAYBACK_VERIFICATION_FAILED reason=PLAYBACK_DID_NOT_START elapsed_ms=%d",
                        getIsoUtcTimestamp(), verification.elapsedMs));

                JSONObject diag = new JSONObject();
                try {
                    diag.put("artist_name", artistName);
                    diag.put("play_mode", playMode.name());
                    diag.put("last_detected_screen", verification.lastScreen != null ? verification.lastScreen.name() : "UNKNOWN");
                    diag.put("now_playing_visible", verification.nowPlayingVisible);
                    diag.put("detected_artist", verification.detectedArtist != null ? verification.detectedArtist : "");
                    diag.put("detected_track", verification.detectedTrack != null ? verification.detectedTrack : "");
                    diag.put("elapsed_time_ms", verification.elapsedMs);
                } catch (JSONException ignored) {}

                emitStepFailed(context, runId, verifyStepIndex, "PLAYBACK_DID_NOT_START", diag);
                if (callback != null) callback.onResult(false, "Playback did not start within 10 seconds.", "PLAYBACK_DID_NOT_START");
            }

        }).start();
    }

    // --- PLAY MODE IMPLEMENTATIONS ---

    /**
     * CATALOG Mode: Locate and click Shuffle Play / Play button on Artist Page.
     */
    private static boolean executeCatalogMode(SpotifyAccessibilityService service, String artistName) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo playButton = findArtistPlayButton(root, artistName);
        root.recycle();

        if (playButton == null) {
            return false;
        }

        boolean clicked = performClickOnNodeOrAncestor(playButton);
        playButton.recycle();
        return clicked;
    }

    /**
     * THIS_IS Mode: Locate "This Is <Artist>" playlist and start playback.
     */
    private static boolean openThisIsPlaylist(SpotifyAccessibilityService service, Context context, String runId, String artistName) {
        String expectedPlaylistTitle = "This Is " + artistName;
        AccessibilityNodeInfo root = service.getRootInActiveWindow();

        AccessibilityNodeInfo playlistNode = null;
        if (root != null) {
            playlistNode = findThisIsPlaylistNode(root, expectedPlaylistTitle);
            root.recycle();
        }

        // If not in immediate view on Artist Page, perform search fallback for "This Is <Artist>"
        if (playlistNode == null) {
            Log.i(TAG, "This Is playlist not visible on Artist Page. Attempting direct search: " + expectedPlaylistTitle);
            return fallbackSearchAndOpenPlaylist(service, context, runId, expectedPlaylistTitle);
        }

        boolean clicked = performClickOnNodeOrAncestor(playlistNode);
        playlistNode.recycle();

        if (!clicked) return false;

        // Wait for playlist screen stabilization
        try { Thread.sleep(POST_CLICK_DELAY_MS); } catch (InterruptedException ignored) {}
        long startWait = System.currentTimeMillis();
        while (System.currentTimeMillis() - startWait < SCREEN_TRANSITION_TIMEOUT_MS) {
            AccessibilityNodeInfo fresh = service.getRootInActiveWindow();
            if (fresh != null) {
                SpotifyScreen screen = SpotifyNavigator.detectCurrentScreen(fresh);
                if (screen == SpotifyScreen.PLAYLIST_PAGE || isPlaylistPageCustomCheck(fresh, expectedPlaylistTitle)) {
                    fresh.recycle();
                    return true;
                }
                fresh.recycle();
            }
            try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
        }

        return true;
    }

    private static boolean fallbackSearchAndOpenPlaylist(SpotifyAccessibilityService service, Context context, String runId, String playlistQuery) {
        // Go to search and enter query
        final boolean[] searchNav = {false};
        Object lock = new Object();
        synchronized (lock) {
            SpotifyNavigator.goToSearch(context, runId, (success, screen, reason) -> {
                synchronized (lock) {
                    searchNav[0] = success;
                    lock.notifyAll();
                }
            });
            try { lock.wait(NAV_TIMEOUT_MS); } catch (InterruptedException ignored) {}
        }
        if (!searchNav[0]) return false;

        AccessibilityNodeInfo input = locateAndActivateSearchInput(service);
        if (input == null) return false;

        Bundle clearArgs = new Bundle();
        clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs);

        try { Thread.sleep(400); } catch (InterruptedException ignored) {}

        Bundle setTextArgs = new Bundle();
        setTextArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, playlistQuery);
        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArgs);
        input.recycle();

        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

        // Find results
        AccessibilityNodeInfo freshRoot = service.getRootInActiveWindow();
        if (freshRoot == null) return false;

        List<SpotifySearchResultParser.ResultItem> results = SpotifySearchResultParser.parseSearchResults(freshRoot);
        freshRoot.recycle();

        if (results.isEmpty()) return false;

        SpotifySearchResultParser.ResultItem bestPlaylist = null;
        for (SpotifySearchResultParser.ResultItem item : results) {
            SpotifyFuzzyMatcher.MatchResult match = SpotifyFuzzyMatcher.evaluateMatch(playlistQuery, item.title);
            if (match.isMatched || item.title.toLowerCase(Locale.ROOT).contains(playlistQuery.toLowerCase(Locale.ROOT))
                    || playlistQuery.toLowerCase(Locale.ROOT).contains(item.title.toLowerCase(Locale.ROOT))) {
                bestPlaylist = item;
                break;
            }
        }

        boolean clicked = false;
        if (bestPlaylist != null && bestPlaylist.node != null) {
            clicked = performClickOnNodeOrAncestor(bestPlaylist.node);
        }
        for (SpotifySearchResultParser.ResultItem item : results) {
            if (item.node != null) item.node.recycle();
        }

        if (clicked) {
            try { Thread.sleep(POST_CLICK_DELAY_MS); } catch (InterruptedException ignored) {}
            long startWait = System.currentTimeMillis();
            while (System.currentTimeMillis() - startWait < SCREEN_TRANSITION_TIMEOUT_MS) {
                AccessibilityNodeInfo fresh = service.getRootInActiveWindow();
                if (fresh != null) {
                    SpotifyScreen screen = SpotifyNavigator.detectCurrentScreen(fresh);
                    if (screen == SpotifyScreen.PLAYLIST_PAGE || isPlaylistPageCustomCheck(fresh, playlistQuery)) {
                        fresh.recycle();
                        return true;
                    }
                    fresh.recycle();
                }
                try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
            }
        }
        return clicked;
    }

    private static boolean triggerPlaylistPlayback(SpotifyAccessibilityService service) {
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;

        // 1. Try finding playlist green play button in UI tree
        AccessibilityNodeInfo playBtn = findPlaylistPlayButton(root);
        if (playBtn != null) {
            boolean clicked = performClickOnNodeOrAncestor(playBtn);
            playBtn.recycle();
            root.recycle();
            if (clicked) return true;
        } else {
            root.recycle();
        }

        // 2. Gesture tap on green play button on Playlist header (Right side: X: 88%, Y: 56%)
        Log.i(TAG, "Dispatching tap gesture on Playlist play button (0.88, 0.56)...");
        boolean tapped = service.clickCoordinatesRatio(0.88f, 0.56f);
        if (tapped) return true;

        // 3. Fallback: Click first track in playlist
        AccessibilityNodeInfo fresh = service.getRootInActiveWindow();
        if (fresh != null) {
            AccessibilityNodeInfo firstTrack = findFirstTrackInList(fresh);
            if (firstTrack != null) {
                boolean trackClicked = performClickOnNodeOrAncestor(firstTrack);
                firstTrack.recycle();
                fresh.recycle();
                return trackClicked;
            }
            fresh.recycle();
        }

        return false;
    }

    /**
     * RADIO Mode: Locate and start Artist Radio.
     */
    private static boolean executeRadioMode(SpotifyAccessibilityService service, Context context, String runId, String artistName) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;

        // 1. Try finding direct Radio button/option on page
        AccessibilityNodeInfo radioNode = findArtistRadioNode(root, artistName);
        if (radioNode != null) {
            boolean clicked = performClickOnNodeOrAncestor(radioNode);
            radioNode.recycle();
            root.recycle();
            if (clicked) return true;
        }

        // 2. Try Context Menu (3-dots overflow menu on Artist Page)
        AccessibilityNodeInfo contextMenuBtn = findContextMenuButton(root);
        if (contextMenuBtn != null) {
            boolean menuOpened = performClickOnNodeOrAncestor(contextMenuBtn);
            contextMenuBtn.recycle();
            root.recycle();

            if (menuOpened) {
                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                AccessibilityNodeInfo menuRoot = service.getRootInActiveWindow();
                if (menuRoot != null) {
                    AccessibilityNodeInfo radioOption = findArtistRadioNode(menuRoot, artistName);
                    if (radioOption != null) {
                        boolean clicked = performClickOnNodeOrAncestor(radioOption);
                        radioOption.recycle();
                        menuRoot.recycle();
                        return clicked;
                    }
                    menuRoot.recycle();
                }
            }
        } else {
            root.recycle();
        }

        // 3. Fallback: Search "<Artist> Radio"
        String radioQuery = artistName + " Radio";
        Log.i(TAG, "Artist Radio not found in menu/page. Fallback search: " + radioQuery);
        return fallbackSearchAndOpenPlaylist(service, context, runId, radioQuery);
    }

    // --- PLAYBACK VERIFICATION (10 Seconds Strict) ---

    public static class PlaybackVerificationResult {
        public final boolean isSuccess;
        public final boolean nowPlayingVisible;
        public final String detectedArtist;
        public final String detectedTrack;
        public final SpotifyScreen lastScreen;
        public final long elapsedMs;

        public PlaybackVerificationResult(boolean isSuccess, boolean nowPlayingVisible, String detectedArtist,
                                          String detectedTrack, SpotifyScreen lastScreen, long elapsedMs) {
            this.isSuccess = isSuccess;
            this.nowPlayingVisible = nowPlayingVisible;
            this.detectedArtist = detectedArtist;
            this.detectedTrack = detectedTrack;
            this.lastScreen = lastScreen;
            this.elapsedMs = elapsedMs;
        }
    }

    /**
     * Polls Accessibility UI tree for up to timeoutMs to verify playback actually started.
     */
    public static PlaybackVerificationResult verifyPlaybackStarted(SpotifyAccessibilityService service, String expectedArtist, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        boolean nowPlayingDetected = false;
        String matchedArtist = "";
        String matchedTrack = "";
        SpotifyScreen lastScreen = SpotifyScreen.UNKNOWN_SCREEN;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root != null) {
                lastScreen = SpotifyNavigator.detectCurrentScreen(root);

                // Signal 1: Check Now Playing Bar / Player View / Pause State
                NowPlayingInfo npInfo = inspectNowPlayingInfo(root);
                if (npInfo.isVisible) {
                    nowPlayingDetected = true;
                    matchedTrack = npInfo.trackTitle;
                    matchedArtist = npInfo.artistName;

                    // Signal 2: Match expected artist or track
                    boolean artistMatch = isArtistMatch(expectedArtist, npInfo.artistName, npInfo.trackTitle, npInfo.fullDescription);

                    if (artistMatch || npInfo.isPlayingState) {
                        root.recycle();
                        long elapsed = System.currentTimeMillis() - startTime;
                        return new PlaybackVerificationResult(true, true, matchedArtist, matchedTrack, lastScreen, elapsed);
                    }
                }
                root.recycle();
            }
            try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new PlaybackVerificationResult(false, nowPlayingDetected, matchedArtist, matchedTrack, lastScreen, elapsed);
    }

    public static class NowPlayingInfo {
        public boolean isVisible = false;
        public boolean isPlayingState = false;
        public String trackTitle = "";
        public String artistName = "";
        public String fullDescription = "";
    }

    private static NowPlayingInfo inspectNowPlayingInfo(AccessibilityNodeInfo root) {
        NowPlayingInfo info = new NowPlayingInfo();
        if (root == null) return info;

        String[] playerIds = {
                "com.spotify.music:id/now_playing_bar",
                "com.spotify.music:id/now_playing_bar_title",
                "com.spotify.music:id/now_playing_bar_container",
                "com.spotify.music:id/mini_player",
                "com.spotify.music:id/player_controls",
                "com.spotify.music:id/now_playing_view",
                "com.spotify.music:id/npv_content"
        };

        for (String id : playerIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                info.isVisible = true;
                for (AccessibilityNodeInfo n : nodes) {
                    CharSequence desc = n.getContentDescription();
                    CharSequence text = n.getText();
                    if (desc != null) info.fullDescription += " " + desc.toString();
                    if (text != null) info.fullDescription += " " + text.toString();
                    n.recycle();
                }
                break;
            }
        }

        // Check for Pause button (which confirms active playback)
        String[] pauseIds = {
                "com.spotify.music:id/pause_button",
                "com.spotify.music:id/button_play_and_pause",
                "com.spotify.music:id/play_pause_button"
        };
        for (String pid : pauseIds) {
            List<AccessibilityNodeInfo> pNodes = root.findAccessibilityNodeInfosByViewId(pid);
            if (pNodes != null && !pNodes.isEmpty()) {
                for (AccessibilityNodeInfo pn : pNodes) {
                    CharSequence desc = pn.getContentDescription();
                    if (desc != null && desc.toString().toLowerCase(Locale.ROOT).contains("pause")) {
                        info.isVisible = true;
                        info.isPlayingState = true;
                    }
                    pn.recycle();
                }
            }
        }

        // Look for track and artist text views in player
        List<AccessibilityNodeInfo> titleNodes = root.findAccessibilityNodeInfosByViewId("com.spotify.music:id/now_playing_bar_title");
        if (titleNodes != null && !titleNodes.isEmpty()) {
            CharSequence t = titleNodes.get(0).getText();
            if (t != null) info.trackTitle = t.toString();
            for (AccessibilityNodeInfo n : titleNodes) n.recycle();
        }

        List<AccessibilityNodeInfo> artistNodes = root.findAccessibilityNodeInfosByViewId("com.spotify.music:id/now_playing_bar_artist");
        if (artistNodes != null && !artistNodes.isEmpty()) {
            CharSequence a = artistNodes.get(0).getText();
            if (a != null) info.artistName = a.toString();
            for (AccessibilityNodeInfo n : artistNodes) n.recycle();
        }

        return info;
    }

    public static boolean isArtistMatch(String expectedArtist, String actualArtist, String actualTrack, String fullDescription) {
        if (expectedArtist == null || expectedArtist.isEmpty()) return false;

        String normExpected = SpotifyFuzzyMatcher.normalize(expectedArtist);

        if (actualArtist != null && !actualArtist.isEmpty()) {
            String normActual = SpotifyFuzzyMatcher.normalize(actualArtist);
            if (normActual.contains(normExpected) || normExpected.contains(normActual)) {
                return true;
            }
            if (SpotifyFuzzyMatcher.computeSimilarity(expectedArtist, actualArtist) >= 0.50) {
                return true;
            }
        }

        if (fullDescription != null && !fullDescription.isEmpty()) {
            String normDesc = SpotifyFuzzyMatcher.normalize(fullDescription);
            if (normDesc.contains(normExpected)) {
                return true;
            }
        }

        return false;
    }

    // --- UI SELECTOR & HEURISTIC HELPERS ---

    public static SpotifySearchResultParser.ResultItem findBestArtistMatch(List<SpotifySearchResultParser.ResultItem> items, String expectedArtist) {
        if (items == null || items.isEmpty() || expectedArtist == null) return null;

        SpotifySearchResultParser.ResultItem bestCandidate = null;
        double bestScore = -1.0;

        for (SpotifySearchResultParser.ResultItem item : items) {
            double baseSimilarity = SpotifyFuzzyMatcher.computeSimilarity(expectedArtist, item.title);
            double totalScore = baseSimilarity;

            // Prioritize items explicitly typed as ARTIST
            if ("ARTIST".equalsIgnoreCase(item.itemType) || item.contentDescription.toLowerCase(Locale.ROOT).contains("artist")
                    || item.subtitle.toLowerCase(Locale.ROOT).contains("artist")) {
                totalScore += 0.25;
            }

            // Reject items explicitly marked as SONG or PODCAST if not an exact match
            if ("SONG".equalsIgnoreCase(item.itemType) || "PODCAST".equalsIgnoreCase(item.itemType)) {
                totalScore -= 0.20;
            }

            if (baseSimilarity >= 0.50 && totalScore > bestScore) {
                bestScore = totalScore;
                bestCandidate = item;
            }
        }

        return bestCandidate;
    }

    private static AccessibilityNodeInfo findArtistPlayButton(AccessibilityNodeInfo root, String artistName) {
        String[] playButtonIds = {
                "com.spotify.music:id/play_button",
                "com.spotify.music:id/button_play_and_pause",
                "com.spotify.music:id/play_pause_button",
                "com.spotify.music:id/context_menu_header_play_button",
                "com.spotify.music:id/shuffle_button",
                "com.spotify.music:id/action_play",
                "com.spotify.music:id/header_play_button",
                "com.spotify.music:id/play_button_layout"
        };

        for (String id : playButtonIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }

        // Semantic content description / text lookup
        String[] playTexts = {
                "Shuffle play",
                "Play",
                "Shuffle",
                "Play " + artistName,
                "Shuffle play " + artistName
        };

        for (String text : playTexts) {
            List<AccessibilityNodeInfo> textNodes = root.findAccessibilityNodeInfosByText(text);
            if (textNodes != null && !textNodes.isEmpty()) {
                AccessibilityNodeInfo found = textNodes.get(0);
                for (int i = 1; i < textNodes.size(); i++) textNodes.get(i).recycle();
                return found;
            }
        }

        return findNodeByDfs(root, "shuffle play", "play");
    }

    private static AccessibilityNodeInfo findThisIsPlaylistNode(AccessibilityNodeInfo root, String expectedPlaylistTitle) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(expectedPlaylistTitle);
        if (nodes != null && !nodes.isEmpty()) {
            AccessibilityNodeInfo found = nodes.get(0);
            for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
            return found;
        }

        return findNodeByDfs(root, expectedPlaylistTitle.toLowerCase(Locale.ROOT), "this is");
    }

    private static AccessibilityNodeInfo findPlaylistPlayButton(AccessibilityNodeInfo root) {
        String[] playButtonIds = {
                "com.spotify.music:id/button_play_and_pause",
                "com.spotify.music:id/play_button",
                "com.spotify.music:id/play_pause_button",
                "com.spotify.music:id/header_play_button"
        };

        for (String id : playButtonIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }

        return findNodeByDfs(root, "play", "shuffle play");
    }

    private static AccessibilityNodeInfo findFirstTrackInList(AccessibilityNodeInfo root) {
        if (root == null) return null;
        String[] trackIds = {
                "com.spotify.music:id/track_row",
                "com.spotify.music:id/row_view",
                "com.spotify.music:id/title",
                "com.spotify.music:id/track_title"
        };
        for (String id : trackIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findArtistRadioNode(AccessibilityNodeInfo root, String artistName) {
        String[] radioKeywords = {
                artistName.toLowerCase(Locale.ROOT) + " radio",
                "artist radio",
                "go to artist radio",
                "radio"
        };

        for (String kw : radioKeywords) {
            AccessibilityNodeInfo node = findNodeByDfs(root, kw);
            if (node != null) return node;
        }
        return null;
    }

    private static AccessibilityNodeInfo findContextMenuButton(AccessibilityNodeInfo root) {
        String[] ids = {
                "com.spotify.music:id/context_menu_button",
                "com.spotify.music:id/more_button",
                "com.spotify.music:id/overflow_button",
                "com.spotify.music:id/action_more"
        };
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo found = nodes.get(0);
                for (int i = 1; i < nodes.size(); i++) nodes.get(i).recycle();
                return found;
            }
        }
        return findNodeByDfs(root, "more options", "more");
    }

    private static boolean isArtistPageCustomCheck(AccessibilityNodeInfo root, String artistName) {
        String[] keywords = {
                "Monthly listeners",
                "monthly listeners",
                "Follow",
                "Following",
                "Popular",
                "Discography",
                "Shuffle",
                "Verified Artist",
                "verified artist",
                artistName
        };
        for (String kw : keywords) {
            if (kw != null && !kw.isEmpty()) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(kw);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo n : nodes) n.recycle();
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPlaylistPageCustomCheck(AccessibilityNodeInfo root, String playlistTitle) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Playlist");
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo n : nodes) n.recycle();
            return true;
        }
        return false;
    }

    private static AccessibilityNodeInfo findNodeByDfs(AccessibilityNodeInfo node, String... keywords) {
        if (node == null) return null;

        CharSequence descChar = node.getContentDescription();
        CharSequence textChar = node.getText();
        String resId = node.getViewIdResourceName();

        String desc = descChar != null ? descChar.toString().toLowerCase(Locale.ROOT) : "";
        String text = textChar != null ? textChar.toString().toLowerCase(Locale.ROOT) : "";
        String res = resId != null ? resId.toLowerCase(Locale.ROOT) : "";

        for (String kw : keywords) {
            if (!kw.isEmpty() && (desc.contains(kw) || text.contains(kw) || res.contains(kw))) {
                return AccessibilityNodeInfo.obtain(node);
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeByDfs(child, keywords);
                if (result != null) {
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo locateAndActivateSearchInput(SpotifyAccessibilityService service) {
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

                // 2. Try node-based click on "What do you want to listen to?" or search tab
                AccessibilityNodeInfo searchBoxTextNode = findNodeByDfs(root, "what do you want to listen to", "artists, songs");
                if (searchBoxTextNode != null) {
                    performClickOnNodeOrAncestor(searchBoxTextNode);
                    searchBoxTextNode.recycle();
                } else {
                    AccessibilityNodeInfo searchTab = findSearchTabNode(root);
                    if (searchTab != null) {
                        performClickOnNodeOrAncestor(searchTab);
                        searchTab.recycle();
                    }
                }
                root.recycle();

                // 3. Dispatch physical touch gesture on the white search box (Center X: 50%, Y: 17%)
                Log.i(TAG, "Dispatching tap gesture on white search box at (0.50, 0.17)...");
                service.clickCoordinatesRatio(0.50f, 0.17f);

                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

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

                // Backup: Tap search tab at bottom (X: 30%, Y: 95%)
                service.clickCoordinatesRatio(0.30f, 0.95f);
                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
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

    private static AccessibilityNodeInfo findActiveSearchNode(AccessibilityNodeInfo root) {
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

    private static boolean performClickOnNodeOrAncestor(AccessibilityNodeInfo node) {
        if (node == null) return false;

        AccessibilityNodeInfo clickable = node;
        while (clickable != null && !clickable.isClickable()) {
            AccessibilityNodeInfo parent = clickable.getParent();
            if (parent == null) break;
            if (clickable != node) clickable.recycle();
            clickable = parent;
        }

        if (clickable == null) clickable = node;

        boolean clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (clickable != node) clickable.recycle();

        return clicked;
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

            Log.i(TAG, String.format("[%s] STEP_STARTED [Step %d]: %s", getIsoUtcTimestamp(), stepIndex, stepName));
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

            Log.i(TAG, String.format("[%s] STEP_OK [Step %d]: %s", getIsoUtcTimestamp(), stepIndex, stepName));
            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException ignored) {}
    }

    private static void emitStepFailed(Context context, String runId, int stepIndex, String reasonCode, JSONObject extraPayload) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", "STEP_FAILED");
            event.put("run_id", runId);

            JSONObject payload = extraPayload != null ? extraPayload : new JSONObject();
            payload.put("step_index", stepIndex);
            payload.put("reason_code", reasonCode);
            event.put("payload", payload);

            Log.e(TAG, String.format("[%s] STEP_FAILED [Step %d]: reason_code=%s", getIsoUtcTimestamp(), stepIndex, reasonCode));
            WebSocketClientManager.getInstance(context).sendEventPayload(event);
        } catch (JSONException ignored) {}
    }
}

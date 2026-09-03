package com.spotify.bot;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SpotifyPlayFromArtistTest {

    @Test
    public void testPlayModeEnumValues() {
        assertEquals(SpotifyPlayFromArtistExecutor.PlayMode.CATALOG, SpotifyPlayFromArtistExecutor.PlayMode.valueOf("CATALOG"));
        assertEquals(SpotifyPlayFromArtistExecutor.PlayMode.THIS_IS, SpotifyPlayFromArtistExecutor.PlayMode.valueOf("THIS_IS"));
        assertEquals(SpotifyPlayFromArtistExecutor.PlayMode.RADIO, SpotifyPlayFromArtistExecutor.PlayMode.valueOf("RADIO"));
    }

    @Test
    public void testResultItemTypeDetermination() {
        assertEquals("ARTIST", SpotifySearchResultParser.ResultItem.determineItemType("Artist", "Taylor Swift, Artist"));
        assertEquals("SONG", SpotifySearchResultParser.ResultItem.determineItemType("Song • Taylor Swift", "Cruel Summer"));
        assertEquals("PLAYLIST", SpotifySearchResultParser.ResultItem.determineItemType("Playlist • Spotify", "This Is Taylor Swift"));
        assertEquals("ALBUM", SpotifySearchResultParser.ResultItem.determineItemType("Album • 2024", "THE TORTURED POETS DEPARTMENT"));
        assertEquals("PODCAST", SpotifySearchResultParser.ResultItem.determineItemType("Podcast", "Show about music"));
        assertEquals("UNKNOWN", SpotifySearchResultParser.ResultItem.determineItemType("", ""));
    }

    @Test
    public void testFindBestArtistMatchPrioritizesArtistType() {
        List<SpotifySearchResultParser.ResultItem> items = new ArrayList<>();
        // Song with exact artist name
        items.add(new SpotifySearchResultParser.ResultItem("Taylor Swift", "Song • Track", "Taylor Swift, Song", "res_song", "SONG", null));
        // Artist row
        items.add(new SpotifySearchResultParser.ResultItem("Taylor Swift", "Artist", "Taylor Swift, Artist", "res_artist", "ARTIST", null));
        // Playlist
        items.add(new SpotifySearchResultParser.ResultItem("This Is Taylor Swift", "Playlist", "This Is Taylor Swift, Playlist", "res_playlist", "PLAYLIST", null));

        SpotifySearchResultParser.ResultItem bestMatch = SpotifyPlayFromArtistExecutor.findBestArtistMatch(items, "Taylor Swift");

        assertNotNull(bestMatch);
        assertEquals("Taylor Swift", bestMatch.title);
        assertEquals("ARTIST", bestMatch.itemType);
        assertEquals("res_artist", bestMatch.resourceId);
    }

    @Test
    public void testFindBestArtistMatchFuzzyMatching() {
        List<SpotifySearchResultParser.ResultItem> items = new ArrayList<>();
        items.add(new SpotifySearchResultParser.ResultItem("The Weeknd", "Artist", "The Weeknd, Artist", "res_1", "ARTIST", null));
        items.add(new SpotifySearchResultParser.ResultItem("Drake", "Artist", "Drake, Artist", "res_2", "ARTIST", null));

        SpotifySearchResultParser.ResultItem match = SpotifyPlayFromArtistExecutor.findBestArtistMatch(items, "Weeknd");
        assertNotNull(match);
        assertEquals("The Weeknd", match.title);

        SpotifySearchResultParser.ResultItem noMatch = SpotifyPlayFromArtistExecutor.findBestArtistMatch(items, "Eminem");
        assertNull(noMatch);
    }

    @Test
    public void testIsArtistMatchForNowPlaying() {
        // Direct match
        assertTrue(SpotifyPlayFromArtistExecutor.isArtistMatch("Taylor Swift", "Taylor Swift", "Anti-Hero", "Now Playing: Anti-Hero by Taylor Swift"));

        // Case insensitivity
        assertTrue(SpotifyPlayFromArtistExecutor.isArtistMatch("taylor swift", "TAYLOR SWIFT", "Cardigan", ""));

        // Substring / Combined text in subtitle
        assertTrue(SpotifyPlayFromArtistExecutor.isArtistMatch("Taylor Swift", "Taylor Swift • 1989", "Blank Space", ""));

        // Description contains expected artist
        assertTrue(SpotifyPlayFromArtistExecutor.isArtistMatch("Taylor Swift", "", "Cruel Summer", "Now playing Cruel Summer by Taylor Swift on Spotify"));

        // Unrelated artist returns false
        assertFalse(SpotifyPlayFromArtistExecutor.isArtistMatch("Taylor Swift", "Ed Sheeran", "Shape of You", "Now playing Shape of You by Ed Sheeran"));
    }

    @Test
    public void testFuzzyMatcherForThisIsPlaylist() {
        String expected = "This Is Taylor Swift";
        String candidate1 = "This Is Taylor Swift";
        String candidate2 = "Taylor Swift Radio";

        SpotifyFuzzyMatcher.MatchResult res1 = SpotifyFuzzyMatcher.evaluateMatch(expected, candidate1);
        SpotifyFuzzyMatcher.MatchResult res2 = SpotifyFuzzyMatcher.evaluateMatch(expected, candidate2);

        assertTrue(res1.isMatched);
        assertEquals(1.0, res1.score, 0.001);
        assertTrue(res1.score > res2.score);
    }
}

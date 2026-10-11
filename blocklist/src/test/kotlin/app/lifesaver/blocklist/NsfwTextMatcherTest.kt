package app.lifesaver.blocklist

import app.lifesaver.blocklist.NsfwTextMatcher.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NsfwTextMatcherTest {
    private fun match(
        vararg texts: String,
        browser: Boolean = false,
    ) = NsfwTextMatcher.match(texts.toList(), browser)

    @Test
    fun matchesNsfwTag() {
        assertEquals(Reason.NsfwWord, match("Some post title", "NSFW"))
        assertEquals(Reason.NsfwWord, match("#nsfw-pics"))
    }

    @Test
    fun matchesAdultMarkerAndPhrases() {
        assertEquals(Reason.AdultMarker, match("18+"))
        assertEquals(Reason.AdultPhrase, match("This channel is age-restricted"))
        assertEquals(Reason.AdultPhrase, match("You must be 18 or older"))
        assertEquals(Reason.AdultPhrase, match("Mature content"))
    }

    @Test
    fun matchesExplicitSubredditNames() {
        assertEquals(Reason.SubredditName, match("r/GoneWild"))
        assertEquals(Reason.SubredditName, match("Posted in r/some_porn_sub"))
    }

    @Test
    fun ignoresOrdinaryText() {
        assertNull(match("r/AskReddit", "r/classicrock", "Home", "Popular", "Constitution", "Essex", "1800 views"))
        assertNull(match("Gaming", "r/Genshin_Impact", "Trending in tech"))
    }

    @Test
    fun doesNotMatchInsideOtherWords() {
        assertNull(match("transnsfw"))
        assertNull(match("Apple 118+ items"))
    }

    @Test
    fun browserNeedsRedditOrDiscordHost() {
        assertNull(match("NSFW", "example.com", browser = true))
        assertEquals(Reason.NsfwWord, match("NSFW", "reddit.com", browser = true))
        assertEquals(Reason.NsfwWord, match("NSFW", "www.reddit.com/r/foo", browser = true))
    }

    @Test
    fun emptyInputMatchesNothing() {
        assertNull(NsfwTextMatcher.match(emptyList()))
    }
}

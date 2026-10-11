package app.lifesaver.blocklist

/**
 * Decides whether the visible on-screen text of a Reddit / Discord screen shows an NSFW label.
 * Text only: an unlabeled image is not detected. Nothing here stores or logs the text.
 */
object NsfwTextMatcher {
    enum class Reason { NsfwWord, AdultMarker, AdultPhrase, SubredditName }

    private val nsfwWord = Regex("(?<![a-z0-9])nsfw")
    private val adultMarker = Regex("(?<![a-z0-9])18\\+")
    private val subreddit = Regex("(?<![a-z0-9])r/([a-z0-9_]+)")

    private val phrases =
        listOf(
            "not safe for work",
            "adult content",
            "adult only",
            "age-restricted",
            "age restricted",
            "over 18",
            "must be 18",
            "18 or older",
            "18 years or older",
            "mature content",
        )

    /** Substrings of subreddit names that are explicit on their own. */
    private val subredditTerms =
        listOf(
            "nsfw",
            "porn",
            "hentai",
            "gonewild",
            "onlyfans",
            "xxx",
            "nude",
            "lewd",
            "slut",
            "milf",
            "boobs",
            "tits",
            "thot",
            "fetish",
            "bdsm",
            "erotic",
            "rule34",
            "hotwife",
            "cumsluts",
            "ecchi",
        )

    private val browserHosts = listOf("reddit.com", "redd.it", "discord.com", "discord.gg", "discordapp.com")

    /** Returns the first reason found, or null. [texts] are visible node texts / descriptions. */
    fun match(
        texts: List<String>,
        isBrowser: Boolean = false,
    ): Reason? {
        val lowered = texts.map { it.lowercase() }
        if (isBrowser && lowered.none { text -> browserHosts.any { it in text } }) {
            return null
        }
        for (text in lowered) {
            matchOne(text)?.let { return it }
        }
        return null
    }

    private fun matchOne(text: String): Reason? =
        when {
            nsfwWord.containsMatchIn(text) -> Reason.NsfwWord
            adultMarker.containsMatchIn(text) -> Reason.AdultMarker
            phrases.any { it in text } -> Reason.AdultPhrase
            subreddit.findAll(text).any { m -> subredditTerms.any { it in m.groupValues[1] } } ->
                Reason.SubredditName
            else -> null
        }
}

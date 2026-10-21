package com.sleuth.browser.browser

/**
 * Turns whatever the user typed, or said, into a place to go.
 *
 * Both ways of asking end up in the same place: the words are cleaned of
 * conversational padding, and they are either an address or a search. There is
 * no third kind of answer.
 */
object Omnibox {

    /** The address to open, or null when the words are not worth opening. */
    fun targetFor(raw: String, engineId: String?): String? {
        val cleaned = SilentGuard.sanitize(raw)
        if (cleaned.isBlank()) return null
        val url = UrlNormalizer.toUrl(cleaned)
        if (url != null) return url
        return SearchEngines.searchUrl(engineId, cleaned)
    }
}

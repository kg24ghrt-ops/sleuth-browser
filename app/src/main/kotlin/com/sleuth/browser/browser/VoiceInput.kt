package com.sleuth.browser.browser

/**
 * The spoken side of the omnibox.
 *
 * Sleuth does not answer anybody and it never speaks. It listens, writes the
 * words down and treats them exactly like something that was typed: a place
 * to go, or a thing to look up. The words are cleaned up here so the rest of
 * the browser only ever sees a plain query.
 */
object VoiceInput {

    /** The words people say before the actual query. They are dropped. */
    private val COMMAND_PREFIXES = listOf(
        "search for ",
        "search ",
        "look up ",
        "look for ",
        "go to ",
        "navigate to ",
        "take me to ",
        "open up ",
        "open ",
        "visit ",
        "browse ",
        "show me "
    )

    /** Punctuation a recognizer leaves at the end of a sentence. */
    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', '…', ' ')

    /**
     * Cleans up what was heard: spacing, the odd command word and the
     * punctuation the recognizer adds when it thinks you finished a sentence.
     */
    fun normalize(raw: String?): String {
        var text = (raw ?: "").trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return ""
        val lower = text.lowercase()
        for (prefix in COMMAND_PREFIXES) {
            if (!lower.startsWith(prefix)) continue
            val rest = text.substring(prefix.length).trim()
            if (rest.length >= 2) {
                text = rest
                break
            }
        }
        text = text.trimEnd(*TRAILING_PUNCTUATION)
        return text.trim()
    }

    /**
     * Picks the best of the alternatives the recognizer offered. The first
     * one is normally the best, but a spoken address beats a spoken sentence.
     */
    fun pick(candidates: List<String>?): String {
        if (candidates.isNullOrEmpty()) return ""
        val cleaned = ArrayList<String>()
        for (candidate in candidates) {
            val text = normalize(candidate)
            if (text.isNotEmpty()) cleaned.add(text)
        }
        if (cleaned.isEmpty()) return ""
        for (text in cleaned) {
            if (UrlNormalizer.isProbablyUrl(text)) return text
        }
        return cleaned[0]
    }

    /** True when there is something worth searching for. */
    fun isUsable(text: String?): Boolean = !text.isNullOrBlank()
}

package com.sleuth.browser.browser

/**
 * Sleuth never talks. Anything that looks like a request for a conversation is
 * quietly downgraded to a plain search so the user is never answered, asked
 * back, or lectured.
 */
object SilentGuard {

    private val CONVERSATIONAL_PREFIXES = listOf(
        "hey sleuth",
        "hi sleuth",
        "sleuth,",
        "sleuth:",
        "ask sleuth",
        "ask sleuth,",
        "chat with sleuth",
        "talk to sleuth",
        "tell me",
        "ask:",
        "chat:",
        "hey assistant",
        "assistant:",
        "gpt:"
    )

    private val QUESTION_OPENERS = listOf("can you", "could you", "would you", "do you", "are you", "will you")

    fun isConversationAttempt(input: String): Boolean {
        val lower = input.trim().lowercase()
        if (lower.isEmpty()) return false
        if (CONVERSATIONAL_PREFIXES.any { lower.startsWith(it) }) return true
        if (QUESTION_OPENERS.any { lower.startsWith(it) }) return true
        return false
    }

    /** Strips conversational padding so the rest is treated as a plain query. */
    fun sanitize(input: String): String {
        var text = input.trim()
        var changed = true
        var guard = 0
        while (changed && guard < 8) {
            guard++
            changed = false
            val lower = text.lowercase()
            for (prefix in CONVERSATIONAL_PREFIXES) {
                if (lower.startsWith(prefix)) {
                    text = text.substring(prefix.length).trim().trimStart(',', ':', ';', ' ').trim()
                    changed = true
                    break
                }
            }
        }
        return text
    }
}

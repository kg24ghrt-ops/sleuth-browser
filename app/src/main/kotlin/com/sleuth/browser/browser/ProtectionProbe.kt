package com.sleuth.browser.browser

/**
 * Turns the answer of the page probe into something a person can read. Kept
 * free of Android types so it can be tested on the jvm.
 */
object ProtectionProbe {

    /** Codes the probe can report. */
    const val CODE_SELECT = "select"
    const val CODE_ONCOPY = "oncopy"
    const val CODE_ONCUT = "oncut"
    const val CODE_ONCONTEXTMENU = "oncontextmenu"
    const val CODE_PRINT = "print"

    fun codes(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val parts = raw.split(",")
        val out = ArrayList<String>()
        for (part in parts) {
            val code = part.trim().lowercase()
            if (code.isEmpty()) continue
            if (!out.contains(code)) out.add(code)
        }
        return out
    }

    fun label(code: String): String = when (code.lowercase()) {
        CODE_SELECT -> "Blocked text selection"
        CODE_ONCOPY -> "Blocked copying with an inline handler"
        CODE_ONCUT -> "Blocked cutting with an inline handler"
        CODE_ONCONTEXTMENU -> "Blocked the context menu with an inline handler"
        CODE_PRINT -> "Hides the page behind a print stylesheet"
        else -> "Blocked the page in an unknown way"
    }

    fun describe(codes: List<String>): String {
        if (codes.isEmpty()) return ""
        val labels = ArrayList<String>()
        for (code in codes) {
            labels.add(label(code))
        }
        return labels.joinToString("\n")
    }

    fun summary(codes: List<String>): String {
        if (codes.isEmpty()) return ""
        if (codes.size == 1) return label(codes[0])
        return "${codes.size} protections blocked"
    }
}

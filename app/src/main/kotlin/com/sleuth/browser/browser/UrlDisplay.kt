package com.sleuth.browser.browser

/**
 * A tidier way of showing an address in the bar. The full address is still
 * what the browser opens, this is only what the user reads.
 */
object UrlDisplay {

    /** Longer than this and the address is cut in the middle. */
    private const val LONG = 56

    fun short(url: String): String {
        var text = url.trim()
        if (text.isEmpty()) return ""
        if (text.startsWith("https://")) {
            text = text.substring(8)
        } else if (text.startsWith("http://")) {
            text = text.substring(7)
        }
        if (text.startsWith("www.")) {
            text = text.substring(4)
        }
        while (text.length > 1 && text.endsWith("/")) {
            text = text.dropLast(1)
        }
        if (text.length <= LONG) return text
        return text.take(38) + "…" + text.takeLast(14)
    }
}

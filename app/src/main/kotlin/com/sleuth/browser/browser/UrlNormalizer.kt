package com.sleuth.browser.browser

/**
 * Turns whatever was typed in the omnibox into something that can be opened.
 *
 * The rules are deliberately conservative: a string only becomes a URL when it
 * clearly looks like one, otherwise it is handed to the search engine.
 */
object UrlNormalizer {

    private val BLOCKED_SCHEMES = listOf(
        "javascript:",
        "data:",
        "file:",
        "content:",
        "intent:",
        "blob:",
        "market:"
    )

    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")

    fun isBlockedScheme(input: String): Boolean {
        val lower = input.trim().lowercase()
        return BLOCKED_SCHEMES.any { lower.startsWith(it) }
    }

    fun isProbablyUrl(input: String): Boolean {
        val text = input.trim()
        if (text.isEmpty() || text.contains(" ")) return false
        if (isBlockedScheme(text)) return false
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return true
        if (lower.startsWith("about:")) return true

        val authority = text.substringBefore("/").substringBefore("?").substringBefore("#")
        if (authority.isEmpty()) return false

        val host = if (authority.contains(":")) authority.substringBefore(":") else authority
        if (host.isEmpty()) return false
        if (host.equals("localhost", ignoreCase = true)) return true
        if (isIpv4(host)) return true
        if (!host.contains(".")) return false

        val tld = host.substringAfterLast(".")
        if (tld.length < 2) return false
        if (!tld.all { it.isLetter() }) return false
        return true
    }

    /** Returns a navigable url, or null when the text is not addressable. */
    fun toUrl(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (isBlockedScheme(text)) return null
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return text
        if (lower.startsWith("about:")) return text
        if (!isProbablyUrl(text)) return null

        val authority = text.substringBefore("/").substringBefore("?")
        val host = if (authority.contains(":")) authority.substringBefore(":") else authority
        val scheme = if (LOCAL_HOSTS.contains(host.lowercase()) || isIpv4(host)) "http://" else "https://"
        return scheme + text
    }

    fun isIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return false
            if (!part.all { it.isDigit() }) return false
            if (part.toInt() > 255) return false
        }
        return true
    }

    fun hostOf(url: String): String {
        val text = url.trim()
        val afterScheme = if (text.contains("://")) text.substringAfter("://") else text
        val authority = afterScheme.substringBefore("/").substringBefore("?").substringBefore("#")
        val host = if (authority.contains(":")) authority.substringBefore(":") else authority
        return host.lowercase()
    }

    fun isSecure(url: String): Boolean = url.trim().lowercase().startsWith("https://")

    fun isLocal(url: String): Boolean = hostOf(url) in LOCAL_HOSTS
}

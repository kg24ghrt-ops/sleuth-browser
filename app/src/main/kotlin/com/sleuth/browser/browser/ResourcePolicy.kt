package com.sleuth.browser.browser

/**
 * Decides what the browser is allowed to download. Trackers are always
 * dropped, and in lite mode the heavy media that makes pages crawl is
 * skipped as well.
 */
enum class LoadMode { FULL, LITE }

object ResourcePolicy {

    private val HEAVY_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "avif", "bmp", "ico", "svg",
        "mp4", "webm", "mkv", "mov", "avi", "m4v", "m3u8", "mpd", "ogv",
        "mp3", "ogg", "oga", "wav", "flac", "aac", "m4a", "opus",
        "woff", "woff2", "ttf", "otf", "eot"
    )

    fun isHeavyMedia(mimeType: String?, url: String): Boolean {
        val mime = (mimeType ?: "").lowercase()
        if (mime.startsWith("image/") || mime.startsWith("video/") ||
            mime.startsWith("audio/") || mime.startsWith("font/")
        ) {
            return true
        }
        val path = url.substringBefore("?").substringBefore("#")
        val extension = path.substringAfterLast(".", "").lowercase()
        if (extension.isEmpty() || extension.length > 5) return false
        return extension in HEAVY_EXTENSIONS
    }

    fun shouldBlock(mode: LoadMode, blockTrackers: Boolean, mimeType: String?, url: String): Boolean {
        if (blockTrackers && TrackerBlocker.shouldBlock(url)) return true
        if (mode == LoadMode.LITE && isHeavyMedia(mimeType, url)) return true
        return false
    }

    fun modeFor(lite: Boolean): LoadMode = if (lite) LoadMode.LITE else LoadMode.FULL
}

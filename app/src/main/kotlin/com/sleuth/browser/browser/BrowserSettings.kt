package com.sleuth.browser.browser

import android.content.Context
import android.content.SharedPreferences

class BrowserSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sleuth_prefs", Context.MODE_PRIVATE)

    var searchEngine: String
        get() = prefs.getString(KEY_SEARCH_ENGINE, "duckduckgo") ?: "duckduckgo"
        set(value) = prefs.edit().putString(KEY_SEARCH_ENGINE, value).apply()

    var darkPages: Boolean
        get() = prefs.getBoolean(KEY_DARK_PAGES, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_PAGES, value).apply()

    var javascript: Boolean
        get() = prefs.getBoolean(KEY_JAVASCRIPT, true)
        set(value) = prefs.edit().putBoolean(KEY_JAVASCRIPT, value).apply()

    var cookies: Boolean
        get() = prefs.getBoolean(KEY_COOKIES, true)
        set(value) = prefs.edit().putBoolean(KEY_COOKIES, value).apply()

    var blockTrackers: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_TRACKERS, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_TRACKERS, value).apply()

    var mixedContent: Boolean
        get() = prefs.getBoolean(KEY_MIXED_CONTENT, true)
        set(value) = prefs.edit().putBoolean(KEY_MIXED_CONTENT, value).apply()

    var geolocation: Boolean
        get() = prefs.getBoolean(KEY_GEOLOCATION, false)
        set(value) = prefs.edit().putBoolean(KEY_GEOLOCATION, value).apply()

    var desktopMode: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_MODE, value).apply()

    var saveToGallery: Boolean
        get() = prefs.getBoolean(KEY_SAVE_GALLERY, false)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_GALLERY, value).apply()

    var fontScale: Int
        get() = prefs.getInt(KEY_FONT_SCALE, 100)
        set(value) = prefs.edit().putInt(KEY_FONT_SCALE, value).apply()

    var incognito: Boolean
        get() = prefs.getBoolean(KEY_INCOGNITO, false)
        set(value) = prefs.edit().putBoolean(KEY_INCOGNITO, value).apply()

    var armedCaseId: Long
        get() = prefs.getLong(KEY_ARMED_CASE, 0L)
        set(value) = prefs.edit().putLong(KEY_ARMED_CASE, value).apply()

    var homeUrl: String
        get() = prefs.getString(KEY_HOME, "https://duckduckgo.com") ?: "https://duckduckgo.com"
        set(value) = prefs.edit().putString(KEY_HOME, value).apply()

    var blockedSites: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_SITES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED_SITES, value).apply()

    fun isSiteBlocked(url: String): Boolean {
        val host = UrlNormalizer.hostOf(url)
        if (host.isEmpty()) return false
        return blockedSites.any { it == host || host.endsWith(".$it") }
    }

    fun blockSite(url: String) {
        val host = UrlNormalizer.hostOf(url)
        if (host.isEmpty()) return
        val current = HashSet(blockedSites)
        current.add(host)
        blockedSites = current
    }

    companion object {
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val KEY_DARK_PAGES = "dark_pages"
        const val KEY_JAVASCRIPT = "javascript"
        const val KEY_COOKIES = "cookies"
        const val KEY_BLOCK_TRACKERS = "block_trackers"
        const val KEY_MIXED_CONTENT = "mixed_content"
        const val KEY_GEOLOCATION = "geolocation"
        const val KEY_DESKTOP_MODE = "desktop_mode"
        const val KEY_SAVE_GALLERY = "save_to_gallery"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_INCOGNITO = "incognito"
        const val KEY_ARMED_CASE = "armed_case"
        const val KEY_HOME = "home_url"
        const val KEY_BLOCKED_SITES = "blocked_sites"
    }
}

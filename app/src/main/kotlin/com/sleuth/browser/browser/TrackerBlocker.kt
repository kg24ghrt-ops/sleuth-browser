package com.sleuth.browser.browser

/**
 * A small, denylist based tracker filter. Requests to known analytics and ad
 * hosts are dropped before they leave the device.
 */
object TrackerBlocker {

    private val TRACKER_HOSTS = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googleadservices.com",
        "adservice.google.com",
        "analytics.google.com",
        "facebook.net",
        "connect.facebook.net",
        "scorecardresearch.com",
        "quantserve.com",
        "outbrain.com",
        "taboola.com",
        "hotjar.com",
        "criteo.com",
        "amplitude.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "branch.io",
        "adjust.com",
        "appsflyer.com",
        "onesignal.com",
        "sentry.io",
        "newrelic.com",
        "bugsnag.com",
        "fullstory.com",
        "clarity.ms",
        "adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "demdex.net",
        "bluekai.com",
        "everesttech.net",
        "yieldmo.com",
        "teads.tv",
        "spotxchange.com",
        "smartadserver.com",
        "sharethrough.com",
        "adform.net",
        "zemanta.com",
        "moatads.com",
        "adsafeprotected.com",
        "adsrvr.org",
        "3lift.com",
        "media.net",
        "turn.com",
        "criteo.net",
        "revcontent.com",
        "mgid.com",
        "outbrain.com",
        "popads.net",
        "propellerads.com"
    )

    fun isTrackerHost(host: String): Boolean {
        val lower = host.trim().lowercase()
        if (lower.isEmpty()) return false
        return TRACKER_HOSTS.any { lower == it || lower.endsWith(".$it") }
    }

    fun shouldBlock(url: String): Boolean {
        val host = UrlNormalizer.hostOf(url)
        if (host.isEmpty()) return false
        return isTrackerHost(host)
    }
}

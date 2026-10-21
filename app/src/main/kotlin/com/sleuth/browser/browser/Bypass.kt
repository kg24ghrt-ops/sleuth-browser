package com.sleuth.browser.browser

/**
 * Which sites are left alone.
 *
 * The user can tell the browser to stop standing over a site, so a page that
 * misbehaves under the guard never gets in the way of ordinary browsing. The
 * rule is kept here, free of Android types, so it can be tested and so the
 * script and the settings always agree.
 */
object Bypass {

    /** True when this host is one of the sites that are left alone. */
    fun covers(host: String, bypassed: Set<String>): Boolean {
        if (host.isEmpty()) return false
        return bypassed.any { it.isNotEmpty() && (it == host || host.endsWith(".$it")) }
    }

    /** True when this address belongs to a site that is left alone. */
    fun coversUrl(url: String, bypassed: Set<String>): Boolean =
        covers(UrlNormalizer.hostOf(url), bypassed)
}

package com.sleuth.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerBlockerTest {

    @Test
    fun `known trackers are blocked`() {
        assertTrue(TrackerBlocker.shouldBlock("https://www.google-analytics.com/analytics.js"))
        assertTrue(TrackerBlocker.shouldBlock("https://stats.doubleclick.net/j/collect"))
        assertTrue(TrackerBlocker.shouldBlock("https://connect.facebook.net/en_US/fbevents.js"))
    }

    @Test
    fun `normal sites are left alone`() {
        assertFalse(TrackerBlocker.shouldBlock("https://example.com/index.html"))
        assertFalse(TrackerBlocker.shouldBlock("https://duckduckgo.com/?q=test"))
        assertFalse(TrackerBlocker.shouldBlock(""))
    }

    @Test
    fun `sub domains are matched`() {
        assertTrue(TrackerBlocker.isTrackerHost("stats.doubleclick.net"))
        assertFalse(TrackerBlocker.isTrackerHost("notdoubleclick.net"))
    }
}

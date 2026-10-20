package com.sleuth.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourcePolicyTest {

    @Test
    fun `trackers are always dropped`() {
        assertTrue(ResourcePolicy.shouldBlock(LoadMode.FULL, true, "application/javascript", "https://www.google-analytics.com/analytics.js"))
        assertTrue(ResourcePolicy.shouldBlock(LoadMode.LITE, true, null, "https://stats.doubleclick.net/j/collect"))
    }

    @Test
    fun `trackers can be allowed through`() {
        assertFalse(ResourcePolicy.shouldBlock(LoadMode.FULL, false, null, "https://www.google-analytics.com/analytics.js"))
    }

    @Test
    fun `full mode keeps media`() {
        assertFalse(ResourcePolicy.shouldBlock(LoadMode.FULL, true, "image/png", "https://example.com/logo.png"))
        assertFalse(ResourcePolicy.shouldBlock(LoadMode.FULL, true, "video/mp4", "https://example.com/clip.mp4"))
    }

    @Test
    fun `lite mode drops heavy media`() {
        assertTrue(ResourcePolicy.shouldBlock(LoadMode.LITE, true, "image/png", "https://example.com/logo.png"))
        assertTrue(ResourcePolicy.shouldBlock(LoadMode.LITE, true, "video/mp4", "https://example.com/clip.mp4"))
        assertTrue(ResourcePolicy.shouldBlock(LoadMode.LITE, true, "font/woff2", "https://example.com/font.woff2"))
    }

    @Test
    fun `lite mode keeps the documents that make a page work`() {
        assertFalse(ResourcePolicy.shouldBlock(LoadMode.LITE, true, "text/html", "https://example.com/page.html"))
        assertFalse(ResourcePolicy.shouldBlock(LoadMode.LITE, true, "application/javascript", "https://example.com/app.js"))
        assertFalse(ResourcePolicy.shouldBlock(LoadMode.LITE, true, "text/css", "https://example.com/app.css"))
    }

    @Test
    fun `media detection falls back to the file extension`() {
        assertTrue(ResourcePolicy.isHeavyMedia(null, "https://example.com/a/photo.JPG?size=2"))
        assertTrue(ResourcePolicy.isHeavyMedia(null, "https://example.com/sound.m4a"))
        assertFalse(ResourcePolicy.isHeavyMedia(null, "https://example.com/readme"))
        assertFalse(ResourcePolicy.isHeavyMedia(null, "https://example.com/index.html"))
    }

    @Test
    fun `mode selection is direct`() {
        assertTrue(ResourcePolicy.modeFor(true) == LoadMode.LITE)
        assertTrue(ResourcePolicy.modeFor(false) == LoadMode.FULL)
    }
}

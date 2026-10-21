package com.sleuth.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BypassTest {

    @Test
    fun `the site itself is left alone`() {
        assertTrue(Bypass.coversUrl("https://example.com/a", setOf("example.com")))
        assertTrue(Bypass.coversUrl("https://example.com", setOf("example.com")))
    }

    @Test
    fun `the pages under it are left alone too`() {
        assertTrue(Bypass.coversUrl("https://news.example.com", setOf("example.com")))
    }

    @Test
    fun `a site that only looks the same is not on the list`() {
        assertFalse(Bypass.coversUrl("https://notexample.com", setOf("example.com")))
        assertFalse(Bypass.coversUrl("https://example.com.evil.test", setOf("example.com")))
    }

    @Test
    fun `an empty list leaves nobody alone`() {
        assertFalse(Bypass.coversUrl("https://example.com", emptySet()))
        assertFalse(Bypass.coversUrl("", setOf("example.com")))
    }
}

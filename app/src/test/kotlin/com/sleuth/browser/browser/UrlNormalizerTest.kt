package com.sleuth.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun `plain urls stay on https`() {
        assertEquals("https://example.com", UrlNormalizer.toUrl("example.com"))
        assertEquals("https://example.com/path?q=1", UrlNormalizer.toUrl("https://example.com/path?q=1"))
        assertEquals("http://example.com", UrlNormalizer.toUrl("http://example.com"))
    }

    @Test
    fun `localhost and ip addresses use http`() {
        assertEquals("http://localhost:8080", UrlNormalizer.toUrl("localhost:8080"))
        assertEquals("http://127.0.0.1", UrlNormalizer.toUrl("127.0.0.1"))
    }

    @Test
    fun `search phrases are not urls`() {
        assertNull(UrlNormalizer.toUrl("who is on my wifi"))
        assertNull(UrlNormalizer.toUrl("how to cook rice"))
        assertFalse(UrlNormalizer.isProbablyUrl("a b c"))
        assertFalse(UrlNormalizer.isProbablyUrl("example"))
    }

    @Test
    fun `dangerous schemes are refused`() {
        assertNull(UrlNormalizer.toUrl("javascript:alert(1)"))
        assertNull(UrlNormalizer.toUrl("file:///etc/passwd"))
        assertNull(UrlNormalizer.toUrl("data:text/html,<h1>x</h1>"))
        assertNull(UrlNormalizer.toUrl("content://media/external/file/1"))
        assertTrue(UrlNormalizer.isBlockedScheme("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun `host extraction ignores ports and paths`() {
        assertEquals("example.com", UrlNormalizer.hostOf("https://example.com:8443/a/b?c=d"))
        assertEquals("example.com", UrlNormalizer.hostOf("example.com"))
        assertTrue(UrlNormalizer.isSecure("https://example.com"))
        assertFalse(UrlNormalizer.isSecure("http://example.com"))
    }

    @Test
    fun `ip validation`() {
        assertTrue(UrlNormalizer.isIpv4("192.168.0.1"))
        assertFalse(UrlNormalizer.isIpv4("192.168.0.256"))
        assertFalse(UrlNormalizer.isIpv4("192.168.0"))
    }
}

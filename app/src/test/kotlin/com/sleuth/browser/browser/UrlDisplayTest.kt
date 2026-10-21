package com.sleuth.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDisplayTest {

    @Test
    fun `the address is shown without the noise`() {
        assertEquals("example.com", UrlDisplay.short("https://example.com"))
        assertEquals("example.com", UrlDisplay.short("https://www.example.com/"))
        assertEquals("example.com/a/b", UrlDisplay.short("https://example.com/a/b"))
        assertEquals("example.com", UrlDisplay.short("http://example.com"))
    }

    @Test
    fun `a long address is cut in the middle`() {
        val long = "https://example.com/" + "a".repeat(80)
        val short = UrlDisplay.short(long)
        assertTrue(short.length < long.length)
        assertTrue(short.contains("…"))
        assertTrue(short.startsWith("example.com"))
    }

    @Test
    fun `nothing stays nothing`() {
        assertEquals("", UrlDisplay.short(""))
        assertEquals("", UrlDisplay.short("   "))
    }

    @Test
    fun `a search is left readable`() {
        assertEquals("duckduckgo.com/?q=news", UrlDisplay.short("https://duckduckgo.com/?q=news"))
    }
}

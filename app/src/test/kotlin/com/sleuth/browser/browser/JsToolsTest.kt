package com.sleuth.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsToolsTest {

    @Test
    fun `the page text is cut inside the page`() {
        val script = JsTools.extractTextScript()
        assertTrue(script.contains("createTreeWalker"))
        assertTrue(script.contains("limit"))
        assertTrue(script.contains("textContent"))
    }

    @Test
    fun `the find script quotes the query`() {
        assertTrue(JsTools.findScript("a\"b").contains("\\\"")) 
        assertTrue(JsTools.findScript("plain").contains("plain"))
    }

    @Test
    fun `the answer of the page is unwrapped`() {
        assertEquals("hello", JsTools.decodeString("\"hello\""))
        assertEquals("a\nb", JsTools.decodeString("\"a\\nb\""))
        assertEquals("", JsTools.decodeString(null))
        assertEquals("", JsTools.decodeString("  "))
        assertEquals("12", JsTools.decodeString("12"))
    }
}

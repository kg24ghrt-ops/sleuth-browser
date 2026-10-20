package com.sleuth.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentGuardTest {

    @Test
    fun `conversational padding is stripped`() {
        assertEquals("weather in oslo", SilentGuard.sanitize("hey sleuth, weather in oslo"))
        assertEquals("weather in oslo", SilentGuard.sanitize("Sleuth: weather in oslo"))
        assertEquals("weather in oslo", SilentGuard.sanitize("ask sleuth weather in oslo"))
        assertEquals("who is on my wifi", SilentGuard.sanitize("tell me who is on my wifi"))
    }

    @Test
    fun `normal input is untouched`() {
        assertEquals("example.com", SilentGuard.sanitize("example.com"))
        assertEquals("news today", SilentGuard.sanitize("news today"))
    }

    @Test
    fun `conversation attempts are detected`() {
        assertTrue(SilentGuard.isConversationAttempt("hey sleuth"))
        assertTrue(SilentGuard.isConversationAttempt("can you help"))
        assertFalse(SilentGuard.isConversationAttempt("example.com"))
        assertFalse(SilentGuard.isConversationAttempt("news today"))
    }
}

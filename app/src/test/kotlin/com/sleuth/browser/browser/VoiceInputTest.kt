package com.sleuth.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputTest {

    @Test
    fun `the words are cleaned up`() {
        assertEquals("weather in oslo", VoiceInput.normalize("  weather   in oslo  "))
        assertEquals("weather in oslo", VoiceInput.normalize("weather in oslo."))
        assertEquals("weather in oslo", VoiceInput.normalize("weather in oslo!"))
    }

    @Test
    fun `the command word is dropped`() {
        assertEquals("weather in oslo", VoiceInput.normalize("search for weather in oslo"))
        assertEquals("weather in oslo", VoiceInput.normalize("go to weather in oslo"))
        assertEquals("example.com", VoiceInput.normalize("open example.com"))
    }

    @Test
    fun `a short word is never dropped`() {
        assertEquals("open a", VoiceInput.normalize("open a"))
        assertEquals("", VoiceInput.normalize(""))
        assertEquals("", VoiceInput.normalize(null))
    }

    @Test
    fun `the first usable alternative is taken`() {
        assertEquals("weather in oslo", VoiceInput.pick(listOf("", "  ", "weather in oslo")))
        assertEquals("weather in oslo", VoiceInput.pick(listOf("weather in oslo", "oslo weather")))
    }

    @Test
    fun `a spoken address beats a spoken sentence`() {
        assertEquals("example.com", VoiceInput.pick(listOf("example dot com", "example.com")))
    }

    @Test
    fun `nothing heard is nothing usable`() {
        assertEquals("", VoiceInput.pick(null))
        assertEquals("", VoiceInput.pick(emptyList()))
        assertEquals("", VoiceInput.pick(listOf("   ")))
        assertFalse(VoiceInput.isUsable(""))
        assertFalse(VoiceInput.isUsable(null))
        assertTrue(VoiceInput.isUsable("weather"))
    }
}

package com.sleuth.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OmniboxTest {

    @Test
    fun `an address stays an address`() {
        assertEquals("https://example.com", Omnibox.targetFor("example.com", "duckduckgo"))
        assertEquals(
            "https://example.com/a?q=1",
            Omnibox.targetFor("https://example.com/a?q=1", "duckduckgo")
        )
    }

    @Test
    fun `a phrase becomes a search`() {
        assertEquals(
            "https://duckduckgo.com/?q=who+is+on+my+wifi",
            Omnibox.targetFor("who is on my wifi", "duckduckgo")
        )
        assertEquals(
            "https://search.brave.com/search?q=who+is+on+my+wifi",
            Omnibox.targetFor("who is on my wifi", "brave")
        )
    }

    @Test
    fun `conversation is still only a search`() {
        assertEquals(
            "https://duckduckgo.com/?q=weather+in+oslo",
            Omnibox.targetFor("hey sleuth, weather in oslo", "duckduckgo")
        )
    }

    @Test
    fun `nothing to open gives nothing back`() {
        assertNull(Omnibox.targetFor("", "duckduckgo"))
        assertNull(Omnibox.targetFor("   ", "duckduckgo"))
    }

    @Test
    fun `a dangerous scheme is never opened`() {
        val target = Omnibox.targetFor("javascript:alert(1)", "duckduckgo")
        assertEquals("https://duckduckgo.com/?q=javascript%3Aalert%281%29", target)
    }
}

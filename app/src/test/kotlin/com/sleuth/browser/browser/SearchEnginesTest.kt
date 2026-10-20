package com.sleuth.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEnginesTest {

    @Test
    fun `default engine is duckduckgo`() {
        assertEquals("duckduckgo", SearchEngines.byId(null).id)
        assertEquals("duckduckgo", SearchEngines.byId("unknown").id)
    }

    @Test
    fun `queries are encoded`() {
        val url = SearchEngines.searchUrl("duckduckgo", "who is on my wifi")
        assertTrue(url.startsWith("https://duckduckgo.com/?q="))
        assertTrue(url.contains("who+is+on+my+wifi") || url.contains("who%20is%20on%20my%20wifi"))
    }

    @Test
    fun `every engine has a template`() {
        for (engine in SearchEngines.ALL) {
            assertTrue(engine.template.contains("%s"))
        }
    }
}

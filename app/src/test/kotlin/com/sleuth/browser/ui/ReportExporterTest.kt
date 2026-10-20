package com.sleuth.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExporterTest {

    private val evidence = listOf(
        ReportEvidence(
            id = 1L,
            kind = "SCREENSHOT",
            title = "Front page <script>",
            url = "https://example.com/?a=1&b=2",
            note = "note with \"quotes\" and & ampersands",
            createdAt = 1700000000000L,
            imageBase64 = "AAAA"
        ),
        ReportEvidence(
            id = 2L,
            kind = "NOTE",
            title = "Second note",
            url = "",
            note = "plain",
            createdAt = 1700000060000L
        )
    )

    @Test
    fun `html report carries the case and every item`() {
        val html = ReportExporter.buildHtml("Operation Nightjar", "CASE-0001", "watch the pier", 1700000000000L, evidence)
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("Operation Nightjar"))
        assertTrue(html.contains("CASE-0001"))
        assertTrue(html.contains("watch the pier"))
        assertTrue(html.contains("Second note"))
        assertTrue(html.contains("data:image/png;base64,AAAA"))
    }

    @Test
    fun `html escapes injected markup`() {
        val html = ReportExporter.buildHtml("Operation Nightjar", "CASE-0001", "", 0L, evidence)
        assertTrue(!html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `json report is well formed for a parser`() {
        val json = ReportExporter.buildJson("Operation Nightjar", "CASE-0001", "notes", 1700000000000L, evidence)
        val parsed = org.json.JSONObject(json)
        assertEquals("CASE-0001", parsed.getString("code"))
        assertEquals("Operation Nightjar", parsed.getString("title"))
        assertEquals(2, parsed.getJSONArray("evidence").length())
        assertEquals(
            "note with \"quotes\" and & ampersands",
            parsed.getJSONArray("evidence").getJSONObject(0).getString("note")
        )
    }

    @Test
    fun `escaping helpers behave`() {
        assertEquals("&amp;&lt;&gt;", ReportExporter.escapeHtml("&<>"))
        assertEquals("a\\\"b", ReportExporter.escapeJson("a\"b"))
    }
}

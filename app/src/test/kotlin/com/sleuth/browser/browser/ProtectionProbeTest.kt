package com.sleuth.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionProbeTest {

    @Test
    fun `codes are split and cleaned`() {
        assertEquals(listOf("select", "oncopy"), ProtectionProbe.codes("select,oncopy"))
        assertEquals(listOf("select"), ProtectionProbe.codes(" select "))
        assertEquals(emptyList<String>(), ProtectionProbe.codes(""))
        assertEquals(emptyList<String>(), ProtectionProbe.codes(null))
    }

    @Test
    fun `repeated codes are counted once`() {
        assertEquals(listOf("select"), ProtectionProbe.codes("select,select"))
    }

    @Test
    fun `every code has a readable label`() {
        for (code in listOf("select", "oncopy", "oncut", "oncontextmenu", "print")) {
            assertTrue(ProtectionProbe.label(code).isNotBlank())
        }
        assertTrue(ProtectionProbe.label("mystery").isNotBlank())
    }

    @Test
    fun `a report lists every trick`() {
        val text = ProtectionProbe.describe(listOf("select", "oncopy"))
        assertTrue(text.contains("selection"))
        assertTrue(text.contains("copying"))
        assertEquals(2, text.lines().size)
    }

    @Test
    fun `a single trick gets its own name and several get a count`() {
        assertEquals("Blocked text selection", ProtectionProbe.summary(listOf("select")))
        assertTrue(ProtectionProbe.summary(listOf("select", "print")).contains("2"))
        assertEquals("", ProtectionProbe.summary(emptyList()))
    }
}

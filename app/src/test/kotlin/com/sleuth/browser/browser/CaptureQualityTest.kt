package com.sleuth.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureQualityTest {

    private val dark = 0xFF101010.toInt()
    private val darkTwo = 0xFF202020.toInt()

    @Test
    fun `a flat picture is treated as blank`() {
        assertTrue(CaptureQuality.isUniform(listOf(dark, dark, dark)))
    }

    @Test
    fun `a picture with content is kept`() {
        assertFalse(CaptureQuality.isUniform(listOf(dark, darkTwo, dark)))
    }

    @Test
    fun `too few samples mean nothing to judge`() {
        assertTrue(CaptureQuality.isUniform(listOf(dark)))
        assertTrue(CaptureQuality.isUniform(emptyList()))
    }
}

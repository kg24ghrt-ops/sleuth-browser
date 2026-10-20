package com.sleuth.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureQualityTest {

    @Test
    fun `a flat picture is treated as blank`() {
        assertTrue(CaptureQuality.isUniform(listOf(0xFF101010, 0xFF101010, 0xFF101010)))
    }

    @Test
    fun `a picture with content is kept`() {
        assertFalse(CaptureQuality.isUniform(listOf(0xFF101010, 0xFF202020, 0xFF101010)))
    }

    @Test
    fun `too few samples mean nothing to judge`() {
        assertTrue(CaptureQuality.isUniform(listOf(0xFF101010)))
        assertTrue(CaptureQuality.isUniform(emptyList()))
    }
}

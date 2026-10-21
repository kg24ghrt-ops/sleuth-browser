package com.sleuth.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockScriptTest {

    @Test
    fun `the page never hears about visibility or focus`() {
        assertTrue(UnlockScript.UNLOCK.contains("'visibilitychange'"))
        assertTrue(UnlockScript.UNLOCK.contains("'focus'"))
        assertTrue(UnlockScript.UNLOCK.contains("'blur'"))
        assertTrue(UnlockScript.UNLOCK.contains("'pagehide'"))
        assertTrue(UnlockScript.UNLOCK.contains("'pageshow'"))
    }

    @Test
    fun `copying and pasting stay invisible to the page`() {
        assertTrue(UnlockScript.UNLOCK.contains("'copy'"))
        assertTrue(UnlockScript.UNLOCK.contains("'cut'"))
        assertTrue(UnlockScript.UNLOCK.contains("'paste'"))
        assertTrue(UnlockScript.UNLOCK.contains("'selectstart'"))
        assertTrue(UnlockScript.UNLOCK.contains("'select'"))
    }

    @Test
    fun `the official capture surfaces are taken away`() {
        assertTrue(UnlockScript.UNLOCK.contains("getDisplayMedia"))
        assertTrue(UnlockScript.UNLOCK.contains("getScreenDetails"))
    }

    @Test
    fun `the page cannot read the clipboard`() {
        assertTrue(UnlockScript.UNLOCK.contains("readText"))
    }

    @Test
    fun `the page cannot hear that the text was selected`() {
        assertTrue(UnlockScript.UNLOCK.contains("'selectionchange'"))
    }

    @Test
    fun `the capture surfaces are locked so they cannot come back`() {
        assertTrue(UnlockScript.UNLOCK.contains("configurable: false"))
        assertTrue(UnlockScript.UNLOCK.contains("writable: false"))
    }

    @Test
    fun `the original tools are kept before the page can change them`() {
        assertTrue(UnlockScript.UNLOCK.contains("Event.prototype.stopImmediatePropagation"))
        assertTrue(UnlockScript.UNLOCK.contains("Object.defineProperty"))
    }

    @Test
    fun `the page cannot see the browser leave the screen`() {
        assertTrue(UnlockScript.UNLOCK.contains("visibilityState"))
        assertTrue(UnlockScript.UNLOCK.contains("hasFocus"))
    }

    @Test
    fun `the page cannot read the clipboard in any way`() {
        assertTrue(UnlockScript.UNLOCK.contains("readText"))
        assertTrue(UnlockScript.UNLOCK.contains("'read'"))
    }

    @Test
    fun `a caught trick is written down for the probe`() {
        assertTrue(UnlockScript.UNLOCK.contains("CODES"))
        assertTrue(UnlockScript.PROBE.contains("CODES"))
    }

    @Test
    fun `the unlock rule is the one the page cannot outrank`() {
        assertTrue(UnlockScript.UNLOCK.contains("!important"))
        assertTrue(UnlockScript.UNLOCK.contains("user-select:text"))
    }

    @Test
    fun `the script leaves no obvious mark behind`() {
        assertFalse(UnlockScript.UNLOCK.contains("window.__"))
        assertFalse(UnlockScript.UNLOCK.contains("data-sleuth"))
        assertFalse(UnlockScript.UNLOCK.contains("id='"))
        assertFalse(UnlockScript.PROBE.contains("window.__"))
    }

    @Test
    fun `the reassert script is the same lock`() {
        assertTrue(UnlockScript.UNLOCK.contains("user-select:text"))
        assertTrue(UnlockScript.REASSERT.contains("user-select:text"))
        assertTrue(UnlockScript.REASSERT.contains("getDisplayMedia"))
    }

    @Test
    fun `a site that is left alone is not touched`() {
        val script = UnlockScript.forBypassed(setOf("example.com"))
        assertTrue(script.contains("'example.com'"))
        assertTrue(script.contains("location.hostname"))
        assertFalse(UnlockScript.UNLOCK.contains("'example.com'"))
    }

    @Test
    fun `the light run only puts the lock back`() {
        assertTrue(UnlockScript.REASSERT.contains("LIGHT = true"))
        assertTrue(UnlockScript.UNLOCK.contains("LIGHT = false"))
    }

    @Test
    fun `the first sweep waits for a quiet moment`() {
        assertTrue(UnlockScript.UNLOCK.contains("requestIdleCallback"))
    }

    @Test
    fun `the probe looks for the usual tricks`() {
        assertTrue(UnlockScript.PROBE.contains("oncopy"))
        assertTrue(UnlockScript.PROBE.contains("oncut"))
        assertTrue(UnlockScript.PROBE.contains("oncontextmenu"))
        assertTrue(UnlockScript.PROBE.contains("print"))
        assertTrue(UnlockScript.PROBE.contains("user-select"))
    }
}

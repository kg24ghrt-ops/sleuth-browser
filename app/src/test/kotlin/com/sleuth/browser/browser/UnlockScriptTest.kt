package com.sleuth.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
        assertTrue(UnlockScript.UNLOCK.contains("'dragstart'"))
    }

    @Test
    fun `the page cannot stop the copy before it happens`() {
        assertTrue(UnlockScript.UNLOCK.contains("'beforecopy'"))
        assertTrue(UnlockScript.UNLOCK.contains("'beforecut'"))
    }

    @Test
    fun `a screen cannot be asked for through the camera either`() {
        assertTrue(UnlockScript.UNLOCK.contains("getUserMedia"))
        assertTrue(UnlockScript.UNLOCK.contains("displaySurface"))
    }

    @Test
    fun `a refusal looks like the thing it replaces`() {
        assertTrue(UnlockScript.UNLOCK.contains("[native code]"))
    }

    @Test
    fun `the masks keep the shape the browser gave them`() {
        assertTrue(UnlockScript.UNLOCK.contains("get: visible"))
        assertTrue(UnlockScript.UNLOCK.contains("enumerable: true"))
    }

    @Test
    fun `the marker has no fixed name to look for`() {
        assertFalse(UnlockScript.UNLOCK.contains("__sl_"))
        assertFalse(UnlockScript.PROBE.contains("__sl_"))
    }

    @Test
    fun `a hostile page is not fought for ever`() {
        assertTrue(UnlockScript.UNLOCK.contains("rebuilds >"))
    }

    @Test
    fun `a frame does not carry the whole guard`() {
        assertTrue(UnlockScript.UNLOCK.contains("window.top === window"))
        assertTrue(UnlockScript.UNLOCK.contains("TOP && state && window.MutationObserver"))
    }

    @Test
    fun `the guard listens on the window only`() {
        assertFalse(UnlockScript.UNLOCK.contains("document.addEventListener"))
    }

    @Test
    fun `the locks are put on the prototype where the method lives`() {
        assertTrue(UnlockScript.UNLOCK.contains("Object.getPrototypeOf"))
        assertTrue(UnlockScript.UNLOCK.contains("lockMethod"))
    }

    @Test
    fun `the careful way of reading a function is dressed up as well`() {
        assertTrue(UnlockScript.UNLOCK.contains("Function.prototype.toString"))
        assertTrue(UnlockScript.UNLOCK.contains("nativeToString.call(this)"))
    }

    @Test
    fun `a page cannot hide a handler where the copy cannot reach it`() {
        assertTrue(UnlockScript.UNLOCK.contains("EventTarget.prototype.addEventListener"))
        assertTrue(UnlockScript.UNLOCK.contains("Event.prototype.preventDefault"))
    }

    @Test
    fun `a rule without an adopted sheet goes to the end of the document`() {
        assertTrue(UnlockScript.UNLOCK.contains("parent.appendChild(element)"))
    }

    @Test
    fun `the rule goes into an adopted style sheet`() {
        assertTrue(UnlockScript.UNLOCK.contains("adoptedStyleSheets"))
        assertTrue(UnlockScript.UNLOCK.contains("new CSSStyleSheet"))
    }

    @Test
    fun `the notes are kept under symbols`() {
        assertTrue(UnlockScript.UNLOCK.contains("Symbol.for"))
        assertTrue(UnlockScript.PROBE.contains("Symbol.for"))
    }

    @Test
    fun `everything the guard touches keeps the shape the browser gave it`() {
        assertTrue(UnlockScript.UNLOCK.contains("writable: true"))
        assertTrue(UnlockScript.UNLOCK.contains("enumerable: true"))
        assertTrue(UnlockScript.UNLOCK.contains("configurable: true"))
    }

    @Test
    fun `only a real event is stopped`() {
        assertTrue(UnlockScript.UNLOCK.contains("isTrusted"))
    }

    @Test
    fun `the same script is not built twice`() {
        val first = UnlockScript.forBypassed(setOf("example.com"))
        val second = UnlockScript.forBypassed(setOf("example.com"))
        assertSame(first, second)
        assertFalse(UnlockScript.forBypassed(setOf("other.test")) === first)
    }

    @Test
    fun `the capture surfaces cannot be put back`() {
        assertTrue(UnlockScript.UNLOCK.contains("lockMethod"))
        assertTrue(UnlockScript.UNLOCK.contains("Object.getPrototypeOf"))
        assertTrue(UnlockScript.UNLOCK.contains("value: value, writable: true"))
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

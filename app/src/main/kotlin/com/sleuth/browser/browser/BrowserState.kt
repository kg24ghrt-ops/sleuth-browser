package com.sleuth.browser.browser

/**
 * The page the browser is looking at right now. Shared between the browser
 * screen and the case screens so a page can be filed as evidence without any
 * conversation about it.
 */
object BrowserState {

    @Volatile
    var currentTitle: String = ""

    @Volatile
    var currentUrl: String = ""

    @Volatile
    var currentExcerpt: String = ""
}

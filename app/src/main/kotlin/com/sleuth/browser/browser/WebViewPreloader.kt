package com.sleuth.browser.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView

/**
 * Spinning up the browser engine costs a few hundred milliseconds. Doing it
 * while the app is still opening means the first page is asked for on an
 * engine that is already running.
 */
object WebViewPreloader {

    private var warmed = false

    fun warm(context: Context) {
        if (warmed) return
        warmed = true
        try {
            val webView = WebView(context.applicationContext)
            webView.layout(0, 0, 1, 1)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                } catch (error: Throwable) {
                    // the throwaway view is only there to start the engine
                }
            }, 5000L)
        } catch (error: Throwable) {
            // devices without a WebView provider simply skip the warm up
        }
    }
}

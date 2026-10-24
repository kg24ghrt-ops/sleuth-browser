package com.sleuth.browser

import android.app.Application
import com.sleuth.browser.browser.GeckoViewFactory
import com.sleuth.browser.browser.WebViewPreloader

class SleuthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Sleuth.init(this)
        // Initialize GeckoRuntime early so preloader can reuse it
        GeckoViewFactory.init(this)
        if (Sleuth.settings.prewarm) {
            // start the browser engine while the first screen is still being built
            WebViewPreloader.warm(this)
        }
    }
}

package com.sleuth.browser.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoSession

object WebViewPreloader {

    private var warmed = false

    fun warm(context: Context) {
        if (warmed) return
        warmed = true
        try {
            // Use the shared GeckoRuntime from GeckoViewFactory instead of creating our own
            val runtime = GeckoViewFactory.getRuntime() ?: GeckoRuntime.create(context)
            val geckoView = GeckoView(context)
            val session = GeckoSession()
            session.open(runtime)
            geckoView.setSession(session)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    session.close()
                    (geckoView.parent as? ViewGroup)?.removeView(geckoView)
                } catch (error: Throwable) {
                }
            }, 5000L)
        } catch (error: Throwable) {
        }
    }
}
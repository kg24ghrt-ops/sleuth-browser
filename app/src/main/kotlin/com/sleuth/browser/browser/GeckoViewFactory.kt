package com.sleuth.browser.browser

import android.content.Context
import android.util.Log
import android.webkit.ValueCallback
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.PromptDelegate
import org.mozilla.geckoview.WebExtensionController
import java.util.WeakHashMap

interface BrowserHost {
    fun onPageStarted(url: String, title: String)
    fun onPageFinished(url: String, title: String)
    fun onProgressChanged(progress: Int)
    fun onTitleChanged(title: String)
    fun onNewTabRequest(url: String)
    fun onFileChooser(callback: ValueCallback<Array<android.net.Uri>>, params: Any): Boolean
    fun onJsDialog(message: String, respond: (Boolean) -> Unit)
    fun onPageError(url: String, description: String)
    fun onExternalUrl(url: String): Boolean
}

object GeckoViewFactory {

    private const val TAG = "GeckoViewFactory"

    private var geckoRuntime: GeckoRuntime? = null
    private val guards = WeakHashMap<GeckoSession, String>()

    const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64; rv:123.0) Gecko/20100101 Firefox/123.0"
    const val MOBILE_UA =
        "Mozilla/5.0 (Android 12; Mobile; rv:123.0) Gecko/123.0 Firefox/123.0"

    fun init(context: Context) {
        if (geckoRuntime != null) return
        val settings = GeckoRuntimeSettings.Builder()
            .applicationContext(context)
            .build()
        geckoRuntime = GeckoRuntime.create(settings)
    }

    fun getRuntime(): GeckoRuntime? = geckoRuntime

    fun create(context: Context, settings: BrowserSettings, host: BrowserHost): GeckoView {
        val runtime = geckoRuntime ?: return GeckoView(context).also { init(context) }
        val session = GeckoSession()
        session.open(runtime)

        val geckoView = GeckoView(context)
        geckoView.setSession(session)
        configure(session, geckoView, settings, host)
        return geckoView
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        session: GeckoSession,
        geckoView: GeckoView,
        settings: BrowserSettings,
        host: BrowserHost
    ) {
        geckoView.setBackgroundColor(0xFF0D1117.toInt())

        val sessionSettings = session.settings
        sessionSettings.allowJavascript = settings.javascript
        sessionSettings.allowLoadLocalContent = false
        sessionSettings.allowThirdPartyCookies = !settings.blockTrackers
        sessionSettings.userAgentMode = if (settings.desktopMode) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        sessionSettings.overrideUserAgent = if (settings.desktopMode) DESKTOP_UA else MOBILE_UA
        sessionSettings.textZoom = (settings.fontScale * 100).toInt()

        val contentBlocking = if (settings.blockTrackers) {
            ContentBlocking.STRICT
        } else {
            ContentBlocking.NONE
        }
        sessionSettings.contentBlocking = contentBlocking
        sessionSettings.allowJavaScriptOpenWindows = false

        installUnlock(session, settings)

        session.progressDelegate = object : GeckoSession.ProgressDelegate() {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                host.onProgressChanged(progress)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate() {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                host.onTitleChanged(title ?: "")
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {}

            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.ContentDelegate.LoadRequest): GeckoSession.ContentDelegate.LoadResponse {
                val url = request.uri
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (settings.isSiteBlocked(url)) {
                        return GeckoSession.ContentDelegate.LoadResponse.Deny
                    }
                    if (settings.isProtectionsOn(url)) {
                        session.evaluateJavaScript(
                            UnlockScript.forBypassed(settings.bypassedSites),
                            GeckoSession.JavaScriptResult { _, _ -> }
                        )
                    }
                }
                return GeckoSession.ContentDelegate.LoadResponse.Allow
            }

            override fun onPageStart(session: GeckoSession, url: String?) {
                host.onPageStarted(url ?: "", "")
            }

            override fun onPageStop(session: GeckoSession, success: Boolean, url: String?) {
                host.onPageFinished(url ?: "", "")
            }

            override fun onPageError(session: GeckoSession, url: String?, error: String?) {
                host.onPageError(url ?: "", error ?: "")
            }
        }

        session.promptDelegate = object : PromptDelegate() {
            override fun onPrompt(
                session: GeckoSession,
                origin: String,
                message: String,
                defaultValue: String?,
                callback: PromptDelegate.PromptCallback
            ) {
                host.onJsDialog(message) { ok ->
                    callback.confirm(ok, defaultValue ?: "")
                }
            }

            override fun onAuthPrompt(
                session: GeckoSession,
                origin: String,
                realm: String,
                callback: PromptDelegate.AuthPromptCallback
            ) {
                callback.dismiss()
            }
        }

        geckoView.setDelegate(object : GeckoView.Delegate() {
            override fun onExternalLoadRequest(view: GeckoView, request: GeckoView.ExternalLoadRequest): Boolean {
                return host.onExternalUrl(request.uri)
            }

            override fun onCreateWindow(
                view: GeckoView,
                windowType: Int,
                callback: GeckoView.CreateWindowCallback
            ): Boolean {
                callback.dismiss()
                return false
            }
        })
    }

    fun installUnlock(session: GeckoSession, settings: BrowserSettings) {
        if (!settings.overrideProtections) return
        val script = UnlockScript.forBypassed(settings.bypassedSites)
        guards[session] = script

        WebExtensionController.getInstance(session.runtime).registerContentScript(
            "unlock-script",
            listOf("*"),
            script,
            WebExtensionController.ContentScriptRunAt.DOCUMENT_START,
            listOf(WebExtensionController.ContentScriptWorld.MAIN)
        )
    }

    fun removeUnlock(session: GeckoSession) {
        val script = guards.remove(session) ?: return
        try {
            WebExtensionController.getInstance(session.runtime).unregisterContentScript("unlock-script")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to remove unlock script", e)
        }
    }
}
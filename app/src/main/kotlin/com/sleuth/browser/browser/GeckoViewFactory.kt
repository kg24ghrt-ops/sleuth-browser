package com.sleuth.browser.browser

import android.content.Context
import android.util.Log
import android.webkit.ValueCallback
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
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
    fun onCanGoBackChanged(canGoBack: Boolean)
    fun onCanGoForwardChanged(canGoForward: Boolean)
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
        geckoRuntime = GeckoRuntime.create(context)
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

    fun configure(
        session: GeckoSession,
        geckoView: GeckoView,
        settings: BrowserSettings,
        host: BrowserHost
    ) {
        geckoView.setBackgroundColor(0xFF0D1117.toInt())

        // Apply settings via GeckoSessionSettings
        // GeckoView 154: modify session.settings directly
        val sessionSettings = session.settings
        sessionSettings.allowJavascript = settings.javascript
        sessionSettings.userAgentMode = if (settings.desktopMode) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        sessionSettings.userAgentOverride = if (settings.desktopMode) DESKTOP_UA else MOBILE_UA

        // Content blocking via session's useTrackingProtection
        sessionSettings.useTrackingProtection = settings.blockTrackers

        installUnlock(session, settings)

        // Delegates are interfaces in GeckoView 154 - implement without ()
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                host.onProgressChanged(progress)
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            // Correct signature per GeckoView 154 Javadoc:
            // onLocationChange(session, url, perms, hasUserGesture)
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: java.util.List<org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                // navigation happened
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                val url = request.uri
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (settings.isSiteBlocked(url)) {
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    if (settings.isProtectionsOn(url)) {
                        // evaluateJavaScript not available in GeckoView 154
                        // Content scripts would need to be registered via WebExtension
                    }
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                host.onCanGoBackChanged(canGoBack)
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                host.onCanGoForwardChanged(canGoForward)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                host.onTitleChanged(title ?: "")
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {}

            override fun onCrash(session: GeckoSession) {
                host.onPageError("", "Page crashed")
            }
        }

        // GeckoView 154: ActivityContextDelegate is an interface with getActivityContext()
        geckoView.activityContextDelegate = object : GeckoView.ActivityContextDelegate {
            override fun getActivityContext(): Context = geckoView.context
        }
    }

    fun installUnlock(session: GeckoSession, settings: BrowserSettings) {
        if (!settings.overrideProtections) return
        val script = UnlockScript.forBypassed(settings.bypassedSites)
        guards[session] = script

        // WebExtensionController content script registration not available in GeckoView 154
        // Would need to use built-in extensions instead
    }

    fun removeUnlock(session: GeckoSession) {
        guards.remove(session)
        // WebExtensionController content script unregistration not available in GeckoView 154
    }
}
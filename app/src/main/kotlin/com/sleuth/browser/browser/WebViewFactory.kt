package com.sleuth.browser.browser

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Message
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

interface BrowserHost {
    fun onPageStarted(url: String, title: String)
    fun onPageFinished(url: String, title: String)
    fun onProgressChanged(progress: Int)
    fun onTitleChanged(title: String)
    fun onNewTabRequest(url: String)
    fun onFileChooser(callback: ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    fun onJsDialog(message: String, respond: (Boolean) -> Unit)
    fun onPageError(url: String, description: String)
    fun onExternalUrl(url: String): Boolean
}

object WebViewFactory {

    const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    fun create(context: Context, settings: BrowserSettings, host: BrowserHost): WebView {
        val webView = WebView(context)
        configure(webView, settings, host)
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView, settings: BrowserSettings, host: BrowserHost) {
        webView.setBackgroundColor(0xFF0D1117.toInt())
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false

        val webSettings = webView.settings
        webSettings.javaScriptEnabled = settings.javascript
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.textZoom = settings.fontScale
        webSettings.mediaPlaybackRequiresUserGesture = true
        webSettings.allowFileAccess = false
        webSettings.allowContentAccess = false
        webSettings.javaScriptCanOpenWindowsAutomatically = false
        webSettings.setSupportMultipleWindows(true)
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.userAgentString = if (settings.desktopMode) DESKTOP_UA else MOBILE_UA
        webSettings.mixedContentMode = if (settings.mixedContent) {
            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        } else {
            WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webSettings.setGeolocationEnabled(settings.geolocation)
        webSettings.blockNetworkImage = settings.liteMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webSettings.safeBrowsingEnabled = settings.safeBrowsing
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            webSettings.setAlgorithmicDarkeningAllowed(settings.darkPages)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            webSettings.forceDark = if (settings.darkPages) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                host.onPageStarted(url, view.title ?: "")
            }

            override fun onPageFinished(view: WebView, url: String) {
                host.onPageFinished(url, view.title ?: "")
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    host.onPageError(request.url?.toString() ?: "", error.description?.toString() ?: "")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url?.toString() ?: return false
                val lower = url.lowercase()
                if (lower.startsWith("http://") || lower.startsWith("https://")) {
                    if (settings.isSiteBlocked(url)) return true
                    return false
                }
                if (lower == "about:blank") return false
                return host.onExternalUrl(url)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url?.toString() ?: return null
                if (request.isForMainFrame) return null
                val mode = ResourcePolicy.modeFor(settings.liteMode)
                if (ResourcePolicy.shouldBlock(mode, settings.blockTrackers, null, url)) {
                    return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                host.onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                host.onTitleChanged(title)
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                // only a real tap may open a tab, never a script
                if (!isUserGesture) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(view.context)
                WebViewFactory.configure(popup, settings, host)
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url?.toString() ?: return true
                        host.onNewTabRequest(url)
                        v.postDelayed({ v.destroy() }, 1500L)
                        return true
                    }
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<android.net.Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                return host.onFileChooser(filePathCallback, fileChooserParams)
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                host.onJsDialog(message) { ok -> if (ok) result.confirm() else result.cancel() }
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                host.onJsDialog(message) { ok -> if (ok) result.confirm() else result.cancel() }
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                if (settings.geolocation) callback.invoke(origin, true, false) else callback.invoke(origin, false, false)
            }
        }
    }
}

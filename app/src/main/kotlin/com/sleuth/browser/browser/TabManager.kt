package com.sleuth.browser.browser

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView

class BrowserTab(val id: Long, val webView: WebView) {
    var title: String = ""
    var url: String = ""
}

class TabManager(
    private val context: Context,
    private val settings: BrowserSettings,
    private val host: BrowserHost,
    private val maxTabs: Int = 10
) {

    private val tabs = ArrayList<BrowserTab>()
    private var nextId = 1L

    var currentIndex: Int = -1
        private set

    val size: Int get() = tabs.size

    fun newTab(url: String?): BrowserTab {
        val webView = WebViewFactory.create(context, settings, host)
        val tab = BrowserTab(nextId++, webView)
        tabs.add(tab)
        while (tabs.size > maxTabs) {
            val dropped = tabs.removeAt(0)
            dropped.webView.stopLoading()
            dropped.webView.destroy()
        }
        if (!url.isNullOrBlank()) {
            webView.loadUrl(url)
        }
        return tab
    }

    fun current(): BrowserTab? = tabs.getOrNull(currentIndex)

    fun select(index: Int) {
        if (index in tabs.indices) currentIndex = index
    }

    fun tabsSnapshot(): List<BrowserTab> = ArrayList(tabs)

    fun allUrls(): List<String> = tabs.map { it.url }.filter { it.isNotBlank() }

    fun at(index: Int): BrowserTab? = tabs.getOrNull(index)

    fun indexOf(tab: BrowserTab): Int = tabs.indexOf(tab)

    fun closeAt(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        tabs.removeAt(index)
        detach(tab)
        tab.webView.stopLoading()
        tab.webView.destroy()
        if (currentIndex >= tabs.size) currentIndex = tabs.size - 1
        if (currentIndex < 0 && tabs.isNotEmpty()) currentIndex = 0
    }

    fun destroyAll() {
        for (tab in tabs) {
            detach(tab)
            tab.webView.stopLoading()
            tab.webView.destroy()
        }
        tabs.clear()
        currentIndex = -1
    }

    private fun detach(tab: BrowserTab) {
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
    }
}

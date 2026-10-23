package com.sleuth.browser.browser

import android.content.Context
import android.view.ViewGroup
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class BrowserTab(
    val id: Long,
    val geckoView: GeckoView,
    val session: GeckoSession
) {
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
        val geckoView = GeckoViewFactory.create(context, settings, host)
        val session = geckoView.session!!
        val tab = BrowserTab(nextId++, geckoView, session)
        tabs.add(tab)
        while (tabs.size > maxTabs) {
            val dropped = tabs.removeAt(0)
            dropped.session.close()
            dropped.geckoView.setSession(null)
        }
        if (!url.isNullOrBlank()) {
            session.loadUri(url)
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
        tab.session.close()
        tab.geckoView.setSession(null)
        if (currentIndex >= tabs.size) currentIndex = tabs.size - 1
        if (currentIndex < 0 && tabs.isNotEmpty()) currentIndex = 0
    }

    fun destroyAll() {
        for (tab in tabs) {
            detach(tab)
            tab.session.close()
            tab.geckoView.setSession(null)
        }
        tabs.clear()
        currentIndex = -1
    }

    fun refreshGuards() {
        for (tab in tabsSnapshot()) {
            GeckoViewFactory.installUnlock(tab.session, settings)
        }
    }

    private fun detach(tab: BrowserTab) {
        (tab.geckoView.parent as? ViewGroup)?.removeView(tab.geckoView)
    }
}
package com.sleuth.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sleuth.browser.browser.BrowserHost
import com.sleuth.browser.browser.BrowserState
import com.sleuth.browser.browser.BrowserTab
import com.sleuth.browser.browser.JsTools
import com.sleuth.browser.browser.MediaSaver
import com.sleuth.browser.browser.ScreenshotCapturer
import com.sleuth.browser.browser.SearchEngines
import com.sleuth.browser.browser.SilentGuard
import com.sleuth.browser.browser.TabManager
import com.sleuth.browser.browser.UrlNormalizer
import com.sleuth.browser.browser.WebViewFactory
import com.sleuth.browser.data.CaseFile
import com.sleuth.browser.data.EvidenceType
import com.sleuth.browser.databinding.ActivityMainBinding
import com.sleuth.browser.databinding.ItemTabBinding
import com.sleuth.browser.databinding.SheetTabsBinding
import com.sleuth.browser.ui.CasesActivity
import com.sleuth.browser.ui.EvidenceLabActivity
import com.sleuth.browser.ui.HistoryActivity
import com.sleuth.browser.ui.SettingsActivity
import java.io.File

class MainActivity : AppCompatActivity(), BrowserHost {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabManager: TabManager
    private val handler = Handler(Looper.getMainLooper())
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var lastRecordedUrl: String = ""
    private var lastHistoryUrl: String = ""
    private var lastHistoryAt: Long = 0L
    private var extractionToken: Int = 0
    private var timersPaused: Boolean = false
    private var lastDesktopMode: Boolean = false
    private var armedCaseCache: CaseFile? = null
    private var tabSheet: BottomSheetDialog? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback
            fileChooserCallback = null
            if (callback == null) return@registerForActivityResult
            val uris = if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val collected = ArrayList<Uri>()
                data?.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) {
                        collected.add(clip.getItemAt(index).uri)
                    }
                }
                if (collected.isEmpty() && data?.data != null) collected.add(data.data!!)
                collected.toTypedArray()
            } else {
                null
            }
            callback.onReceiveValue(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }

        tabManager = TabManager(this, Sleuth.settings, this)
        setupChrome()

        val deepLink = intent?.data?.toString()
        val start = deepLink ?: savedInstanceState?.getString(KEY_LAST_URL) ?: Sleuth.settings.homeUrl
        openInNewTab(start)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val webView = tabManager.current()?.webView
                if (webView != null && webView.canGoBack()) {
                    webView.goBack()
                } else if (tabManager.size > 1) {
                    tabManager.closeAt(tabManager.currentIndex)
                    val next = tabManager.current()
                    if (next != null) {
                        binding.webContainer.removeAllViews()
                        binding.webContainer.addView(next.webView)
                        tabManager.select(tabManager.indexOf(next))
                        updateChrome()
                    } else {
                        finish()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        tabManager.current()?.url?.let { outState.putString(KEY_LAST_URL, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.data?.toString()
        if (!data.isNullOrBlank()) {
            openInNewTab(data)
        }
    }

    override fun onResume() {
        super.onResume()
        if (timersPaused) {
            WebView.resumeTimers()
            timersPaused = false
        }
        tabManager.current()?.webView?.onResume()
        lastDesktopMode = Sleuth.settings.desktopMode
        applyWebSettings()
        refreshArmedCase()
        updateChrome()
    }

    override fun onPause() {
        super.onPause()
        if (!Sleuth.settings.pauseInBackground) return
        for (tab in tabManager.tabsSnapshot()) {
            tab.webView.onPause()
        }
        if (!timersPaused) {
            WebView.pauseTimers()
            timersPaused = true
        }
    }

    /** Pushes the current preferences into every open tab. */
    private fun applyWebSettings() {
        val settings = Sleuth.settings
        for (tab in tabManager.tabsSnapshot()) {
            val webSettings = tab.webView.settings
            webSettings.blockNetworkImage = settings.liteMode
            webSettings.textZoom = settings.fontScale
            webSettings.javaScriptEnabled = settings.javascript
            webSettings.userAgentString =
                if (settings.desktopMode) WebViewFactory.DESKTOP_UA else WebViewFactory.MOBILE_UA
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                webSettings.safeBrowsingEnabled = settings.safeBrowsing
            }
        }
    }

    private fun refreshArmedCase() {
        armedCaseCache = Sleuth.repository.armedCase()
    }

    // ------------------------------------------------------------------ chrome

    private fun setupChrome() {
        binding.urlBar.setOnEditorActionListener { _, actionId, event ->
            val go = actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            if (go) {
                navigate(binding.urlBar.text?.toString().orEmpty())
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.urlBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.urlBar.selectAll()
            }
        }

        binding.btnBack.setOnClickListener { tabManager.current()?.webView?.goBack() }
        binding.btnForward.setOnClickListener { tabManager.current()?.webView?.goForward() }
        binding.btnReload.setOnClickListener {
            val webView = tabManager.current()?.webView
            if (webView != null) webView.reload() else openInNewTab(Sleuth.settings.homeUrl)
        }
        binding.btnHome.setOnClickListener { openInNewTab(Sleuth.settings.homeUrl) }
        binding.btnCapture.setOnClickListener { captureViewport() }
        binding.btnCases.setOnClickListener { startActivity(Intent(this, CasesActivity::class.java)) }
        binding.caseChip.setOnClickListener { startActivity(Intent(this, CasesActivity::class.java)) }
        binding.btnTabs.setOnClickListener { showTabSheet() }
        binding.btnMore.setOnClickListener { showMenu(it) }
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.browser_menu, popup.menu)
        popup.menu.findItem(R.id.action_incognito)?.isChecked = Sleuth.settings.incognito
        popup.menu.findItem(R.id.action_desktop_site)?.isChecked = Sleuth.settings.desktopMode
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_tab -> {
                    openInNewTab(Sleuth.settings.homeUrl)
                    true
                }

                R.id.action_tab_switcher -> {
                    showTabSheet()
                    true
                }

                R.id.action_capture_viewport -> {
                    captureViewport()
                    true
                }

                R.id.action_full_page -> {
                    captureFullPage()
                    true
                }

                R.id.action_find -> {
                    showFindDialog()
                    true
                }

                R.id.action_cases -> {
                    startActivity(Intent(this, CasesActivity::class.java))
                    true
                }

                R.id.action_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }

                R.id.action_bookmarks -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }

                R.id.action_share -> {
                    sharePage()
                    true
                }

                R.id.action_incognito -> {
                    toggleIncognito()
                    true
                }

                R.id.action_desktop_site -> {
                    Sleuth.settings.desktopMode = !Sleuth.settings.desktopMode
                    lastDesktopMode = Sleuth.settings.desktopMode
                    applyWebSettings()
                    // only the page in front is reloaded, the other tabs stay as they are
                    tabManager.current()?.webView?.reload()
                    true
                }

                R.id.action_block_site -> {
                    blockCurrentSite()
                    true
                }

                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }

                R.id.action_rules -> {
                    showRules()
                    true
                }

                R.id.action_about -> {
                    showAbout()
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun showRules() {
        AlertDialog.Builder(this)
            .setTitle(R.string.rules_title)
            .setMessage(R.string.rules_body)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun showAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (error: Throwable) {
            "1.0.0"
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name_full)
            .setMessage("Sleuth $version\nAndroid WebView browser with captures and a local case system.\n\n" + getString(R.string.rules_body))
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun showFindDialog() {
        val webView = tabManager.current()?.webView ?: return
        val input = android.widget.EditText(this)
        input.hint = getString(R.string.search_hint)
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)
        AlertDialog.Builder(this)
            .setTitle(R.string.action_find)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val query = input.text?.toString()?.trim().orEmpty()
                if (query.isBlank()) return@setPositiveButton
                webView.evaluateJavascript(JsTools.findScript(query)) { raw ->
                    val result = JsTools.decodeString(raw)
                    if (result == "0") {
                        Toast.makeText(this, R.string.status_no_matches, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun sharePage() {
        val url = tabManager.current()?.url ?: return
        val title = tabManager.current()?.title.orEmpty()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, if (title.isBlank()) url else "$title\n$url")
        }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun blockCurrentSite() {
        val url = tabManager.current()?.url ?: return
        val host = UrlNormalizer.hostOf(url)
        if (host.isBlank()) return
        Sleuth.settings.blockSite(url)
        Toast.makeText(this, R.string.status_blocked, Toast.LENGTH_SHORT).show()
    }

    private fun toggleIncognito() {
        val enabled = !Sleuth.settings.incognito
        Sleuth.settings.incognito = enabled
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        } catch (error: Throwable) {
            // cookies are best effort
        }
        Toast.makeText(
            this,
            if (enabled) R.string.status_incognito else R.string.status_incognito_off,
            Toast.LENGTH_SHORT
        ).show()
        updateChrome()
    }

    // -------------------------------------------------------------------- tabs

    private fun openInNewTab(url: String?, switchTo: Boolean = true) {
        val tab = tabManager.newTab(url)
        val index = tabManager.indexOf(tab)
        if (switchTo) {
            attachTab(index)
        }
    }

    private fun attachTab(index: Int) {
        val tab = tabManager.at(index) ?: return
        binding.webContainer.removeAllViews()
        binding.webContainer.addView(tab.webView)
        tabManager.select(index)
        updateChrome()
    }

    private fun showTabSheet() {
        val sheetBinding = SheetTabsBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        tabSheet = dialog
        dialog.setContentView(sheetBinding.root)

        val adapter = TabAdapter()
        sheetBinding.tabList.layoutManager = LinearLayoutManager(this)
        sheetBinding.tabList.adapter = adapter
        adapter.submit(tabManager.tabsSnapshot())

        sheetBinding.btnNewTab.setOnClickListener {
            openInNewTab(Sleuth.settings.homeUrl)
            dialog.dismiss()
        }
        dialog.show()
    }

    private inner class TabAdapter : RecyclerView.Adapter<TabHolder>() {

        private val items = ArrayList<BrowserTab>()

        fun submit(newItems: List<BrowserTab>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabHolder {
            val view = ItemTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return TabHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: TabHolder, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class TabHolder(private val view: ItemTabBinding) : RecyclerView.ViewHolder(view.root) {

        fun bind(tab: BrowserTab) {
            view.tabTitle.text = tab.title.ifBlank { getString(R.string.hint_url) }
            view.tabUrl.text = tab.url
            view.root.setOnClickListener {
                attachTab(tabManager.indexOf(tab))
                tabSheet?.dismiss()
            }
            view.tabClose.setOnClickListener {
                tabManager.closeAt(tabManager.indexOf(tab))
                tabSheet?.dismiss()
            }
        }
    }

    // --------------------------------------------------------------- capturing

    private fun captureViewport() {
        val container = binding.webContainer
        if (container.width <= 0 || container.height <= 0) return
        ScreenshotCapturer.capture(this, container) { bitmap ->
            if (bitmap == null) {
                Toast.makeText(this, R.string.error_capture, Toast.LENGTH_SHORT).show()
                return@capture
            }
            openEvidenceLab(bitmap)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun captureFullPage() {
        val webView = tabManager.current()?.webView ?: return
        Toast.makeText(this, R.string.status_capturing, Toast.LENGTH_SHORT).show()
        webView.evaluateJavascript(
            "(function(){var d=document.documentElement;return d.scrollHeight+'|'+d.clientWidth;})();"
        ) { raw ->
            val parts = JsTools.decodeString(raw).split("|")
            val contentHeight = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val contentWidth = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (contentHeight <= 0 || contentWidth <= 0) {
                captureViewport()
                return@evaluateJavascript
            }
            stitchFullPage(webView, contentHeight, contentWidth)
        }
    }

    private fun stitchFullPage(webView: WebView, contentHeight: Int, contentWidth: Int) {
        val container = binding.webContainer
        val viewWidth = container.width
        val viewHeight = container.height
        if (viewWidth <= 0 || viewHeight <= 0) {
            captureViewport()
            return
        }
        val scale = if (contentWidth > 0) viewWidth.toFloat() / contentWidth else 1f
        val totalHeight = (contentHeight * scale).toInt().coerceIn(viewHeight, MAX_PAGE_HEIGHT)
        val output = try {
            Bitmap.createBitmap(viewWidth, totalHeight, Bitmap.Config.ARGB_8888)
        } catch (error: Throwable) {
            null
        }
        if (output == null) {
            captureViewport()
            return
        }
        val startScroll = webView.scrollY
        webView.scrollTo(0, 0)
        handler.postDelayed({
            stitchStep(webView, output, totalHeight, 0, viewHeight, scale, startScroll)
        }, 250)
    }

    private fun stitchStep(
        webView: WebView,
        output: Bitmap,
        totalHeight: Int,
        offset: Int,
        viewHeight: Int,
        scale: Float,
        startScroll: Int
    ) {
        if (offset >= totalHeight) {
            webView.scrollTo(0, startScroll)
            openEvidenceLab(output)
            return
        }
        val targetScroll = (offset / scale).toInt()
        webView.scrollTo(0, targetScroll)
        handler.postDelayed({
            ScreenshotCapturer.capture(this, binding.webContainer) { bitmap ->
                if (bitmap == null) {
                    webView.scrollTo(0, startScroll)
                    openEvidenceLab(output)
                    return@capture
                }
                val canvas = Canvas(output)
                canvas.drawBitmap(bitmap, 0f, offset.toFloat(), null)
                stitchStep(webView, output, totalHeight, offset + viewHeight, viewHeight, scale, startScroll)
            }
        }, 180)
    }

    private fun openEvidenceLab(bitmap: Bitmap) {
        val directory = File(cacheDir, "shots")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "capture-${System.currentTimeMillis()}.png")
        if (!MediaSaver.writeBitmap(file, bitmap)) {
            Toast.makeText(this, R.string.error_capture, Toast.LENGTH_SHORT).show()
            return
        }
        val title = tabManager.current()?.title.orEmpty()
        val url = tabManager.current()?.url.orEmpty()
        startActivity(EvidenceLabActivity.intent(this, file.absolutePath, title, url))
    }

    // ------------------------------------------------------------ browser host

    override fun onPageStarted(url: String, title: String) {
        binding.errorText.visibility = View.GONE
        if (!binding.urlBar.hasFocus() && binding.urlBar.text?.toString() != url) {
            binding.urlBar.setText(url)
        }
    }

    override fun onPageFinished(url: String, title: String) {
        binding.errorText.visibility = View.GONE
        val tab = tabManager.current()
        tab?.url = url
        tab?.title = title
        if (!binding.urlBar.hasFocus() && binding.urlBar.text?.toString() != url) {
            binding.urlBar.setText(url)
        }
        binding.titleText.text = title.ifBlank { url }
        BrowserState.currentUrl = url
        BrowserState.currentTitle = title

        val now = System.currentTimeMillis()
        val duplicate = url == lastHistoryUrl && now - lastHistoryAt < 2000L
        if (!duplicate) {
            lastHistoryUrl = url
            lastHistoryAt = now
            if (!Sleuth.settings.incognito) {
                // the write must never hold up the page
                SleuthIO.execute { Sleuth.db.recordVisit(url, title) }
            }
        }
        recordVisitForCase(url, title)
        updateChrome()
    }

    private fun recordVisitForCase(url: String, title: String) {
        if (Sleuth.settings.incognito) return
        val case = armedCaseCache ?: return
        if (url == lastRecordedUrl) return
        lastRecordedUrl = url
        val token = ++extractionToken
        val webView = tabManager.current()?.webView ?: return
        webView.evaluateJavascript(JsTools.extractTextScript()) { raw ->
            if (token != extractionToken) return@evaluateJavascript
            val text = JsTools.decodeString(raw).trim().take(1500)
            val note = text
            SleuthIO.execute {
                Sleuth.repository.addEvidence(
                    case.id,
                    EvidenceType.PAGE,
                    title.ifBlank { url },
                    url,
                    note,
                    ""
                )
            }
        }
    }

    override fun onProgressChanged(progress: Int) {
        binding.progressBar.progress = progress
    }

    override fun onTitleChanged(title: String) {
        binding.titleText.text = title
    }

    override fun onNewTabRequest(url: String) {
        openInNewTab(url)
    }

    override fun onFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = callback
        return try {
            val intent = params.createIntent()
            fileChooserLauncher.launch(intent)
            true
        } catch (error: Throwable) {
            fileChooserCallback = null
            false
        }
    }

    override fun onJsDialog(message: String, respond: (Boolean) -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton(R.string.action_cancel) { _, _ -> respond(false) }
            .setPositiveButton(R.string.action_ok) { _, _ -> respond(true) }
            .show()
    }

    override fun onPageError(url: String, description: String) {
        binding.errorText.visibility = View.VISIBLE
        binding.errorText.text = getString(R.string.error_no_network) + "\n" + url
    }

    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    override fun onExternalUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("mailto:") || lower.startsWith("tel:") || lower.startsWith("sms:") ||
            lower.startsWith("intent:") || lower.startsWith("market:") || lower.startsWith("geo:")
        ) {
            return try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            } catch (error: Throwable) {
                true
            }
        }
        return false
    }

    // ------------------------------------------------------------------ utils

    private fun navigate(rawInput: String) {
        val cleaned = SilentGuard.sanitize(rawInput)
        if (cleaned.isBlank()) return
        val url = UrlNormalizer.toUrl(cleaned)
        val target = if (url != null) {
            url
        } else {
            SearchEngines.searchUrl(Sleuth.settings.searchEngine, cleaned)
        }
        val webView = tabManager.current()?.webView
        if (webView != null) {
            // drop whatever is still downloading, the new page matters now
            webView.stopLoading()
            extractionToken++
            webView.loadUrl(target)
        } else {
            openInNewTab(target)
        }
    }

    private fun updateChrome() {
        val webView = tabManager.current()?.webView
        binding.btnBack.isEnabled = webView != null && webView.canGoBack()
        binding.btnForward.isEnabled = webView != null && webView.canGoForward()
        binding.btnBack.alpha = if (binding.btnBack.isEnabled) 1f else 0.35f
        binding.btnForward.alpha = if (binding.btnForward.isEnabled) 1f else 0.35f

        val armed = armedCaseCache
        binding.caseChip.text = if (armed != null) {
            armed.code + " ▸"
        } else {
            getString(R.string.menu_cases)
        }
        binding.caseChip.setTextColor(
            if (armed != null) getColor(R.color.signal_green) else getColor(R.color.amber)
        )
    }

    private fun hideKeyboard() {
        val focused = currentFocus ?: return
        val manager = getSystemService(InputMethodManager::class.java)
        manager?.hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    companion object {
        private const val KEY_LAST_URL = "last_url"
        private const val MAX_PAGE_HEIGHT = 9000
    }
}

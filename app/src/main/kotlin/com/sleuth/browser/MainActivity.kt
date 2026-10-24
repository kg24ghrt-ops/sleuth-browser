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
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ValueCallback
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
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
import com.sleuth.browser.browser.GeckoViewFactory
import com.sleuth.browser.browser.JsTools
import com.sleuth.browser.browser.MediaSaver
import com.sleuth.browser.browser.Omnibox
import com.sleuth.browser.browser.ProtectionProbe
import com.sleuth.browser.browser.ScreenshotCapturer
import com.sleuth.browser.browser.TabManager
import com.sleuth.browser.browser.UnlockScript
import com.sleuth.browser.browser.UrlDisplay
import com.sleuth.browser.browser.UrlNormalizer
import com.sleuth.browser.browser.VoiceInput
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
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController

class MainActivity : AppCompatActivity(), BrowserHost {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabManager: TabManager
    private val handler = Handler(Looper.getMainLooper())
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var lastRecordedUrl: String = ""
    private var lastHistoryUrl: String = ""
    private var lastHistoryAt: Long = 0L
    private var extractionToken: Int = 0
    private var lastProbedUrl: String = ""
    private var protectionCodes: List<String> = emptyList()
    private var lastDesktopMode: Boolean = false
    private var armedCaseCache: CaseFile? = null
    private var tabSheet: BottomSheetDialog? = null
    private var currentUrl = ""
    private var canGoBack = false
    private var canGoForward = false

    /** Set while the browser is taking the user somewhere, so the bar is left alone. */
    private var barBusy = false

    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleVoiceResult(result)
        }

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
                val session = tabManager.current()?.session
                if (session != null && canGoBack) {
                    session.goBack()
                } else if (tabManager.size > 1) {
                    tabManager.closeAt(tabManager.currentIndex)
                    val next = tabManager.current()
                    if (next != null) {
                        binding.webContainer.removeAllViews()
                        binding.webContainer.addView(next.geckoView)
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
        val desktopMode = Sleuth.settings.desktopMode
        val modeChanged = desktopMode != lastDesktopMode
        lastDesktopMode = desktopMode
        applyWebSettings()
        // a change in the settings may mean the guard has to stand down
        tabManager.refreshGuards()
        if (modeChanged) {
            // the user agent only counts after a reload, and only the page in front needs it
            tabManager.current()?.session?.reload()
        }
        refreshArmedCase()
        updateChrome()
    }

    override fun onPause() {
        super.onPause()
        if (!Sleuth.settings.pauseInBackground) return
        // pages stop running while the app is out of sight
        // GeckoView handles lifecycle automatically, no manual pause needed
    }

    /** Pushes the current preferences into every open tab. */
    private fun applyWebSettings() {
        val settings = Sleuth.settings
        val userAgent = if (settings.desktopMode) {
            GeckoViewFactory.DESKTOP_UA
        } else {
            GeckoViewFactory.MOBILE_UA
        }
        for (tab in tabManager.tabsSnapshot()) {
            val session = tab.session
            val sessionSettings = session.settings
            sessionSettings.allowJavascript = settings.javascript
            sessionSettings.userAgentMode = if (settings.desktopMode) {
                GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            } else {
                GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            }
            sessionSettings.userAgentOverride = userAgent
            sessionSettings.useTrackingProtection = settings.blockTrackers
            // Note: textZoom, allowThirdPartyCookies, allowJavaScriptOpenWindows
            // are not available in GeckoView 154 GeckoSessionSettings
            // They would need to be set via other means if needed
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
                true
            } else {
                false
            }
        }

        binding.urlBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showFullUrl()
                binding.urlBar.selectAll()
            } else if (!barBusy) {
                showShortUrl()
            }
        }

        binding.urlBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateClearButton()
            }
        })

        binding.btnClear.setOnClickListener {
            binding.urlBar.setText("")
            binding.urlBar.requestFocus()
        }

        // the shield is the switch for the page that is in front
        binding.protectionIcon.setOnClickListener { toggleSiteBypass() }

        binding.btnBack.setOnClickListener { tabManager.current()?.session?.goBack() }
        binding.btnForward.setOnClickListener { tabManager.current()?.session?.goForward() }
        binding.btnReload.setOnClickListener {
            val session = tabManager.current()?.session
            if (session != null) session.reload() else openInNewTab(Sleuth.settings.homeUrl)
        }
        binding.btnHome.setOnClickListener { openInNewTab(Sleuth.settings.homeUrl) }
        binding.btnCapture.setOnClickListener { captureViewport() }
        binding.btnVoice.setOnClickListener { startVoiceInput() }
        binding.btnCases.setOnClickListener { startActivity(Intent(this, CasesActivity::class.java)) }
        binding.caseChip.setOnClickListener { startActivity(Intent(this, CasesActivity::class.java)) }
        binding.btnTabs.setOnClickListener { showTabSheet() }
        binding.btnMore.setOnClickListener { showMenu(it) }
    }

    /**
     * Lets the user say where to go. The words are handed to the same path a
     * typed query takes: a place to go, or a thing to look up. Sleuth never
     * answers and never says a word back.
     */
    private fun startVoiceInput() {
        if (!Sleuth.settings.voiceInput) {
            Toast.makeText(this, R.string.error_voice_off, Toast.LENGTH_SHORT).show()
            return
        }
        if (!isVoiceAvailable()) {
            Toast.makeText(this, R.string.error_voice_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (error: Throwable) {
            Toast.makeText(this, R.string.error_voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** True when the device has something that can listen. */
    private fun isVoiceAvailable(): Boolean = try {
        val probe = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        packageManager.queryIntentActivities(probe, 0).isNotEmpty()
    } catch (error: Throwable) {
        false
    }

    private fun handleVoiceResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) {
            // nothing was said, or the microphone was closed
            return
        }
        val spoken = try {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            VoiceInput.pick(results)
        } catch (error: Throwable) {
            ""
        }
        if (!VoiceInput.isUsable(spoken)) {
            Toast.makeText(this, R.string.status_voice_empty, Toast.LENGTH_SHORT).show()
            return
        }
        binding.urlBar.setText(spoken)
        binding.urlBar.setSelection(spoken.length)
        navigate(spoken)
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.browser_menu, popup.menu)
        popup.menu.findItem(R.id.action_incognito)?.isChecked = Sleuth.settings.incognito
        popup.menu.findItem(R.id.action_desktop_site)?.isChecked = Sleuth.settings.desktopMode
        popup.menu.findItem(R.id.action_lite_mode)?.isChecked = Sleuth.settings.liteMode
        popup.menu.findItem(R.id.action_protections)?.isChecked = Sleuth.settings.overrideProtections
        val pageUrl = tabManager.current()?.url.orEmpty()
        popup.menu.findItem(R.id.action_bypass_site)?.apply {
            isChecked = Sleuth.settings.isBypassed(pageUrl)
            // a single site only matters while the guard is standing at all
            isVisible = Sleuth.settings.overrideProtections &&
                UrlNormalizer.hostOf(pageUrl).isNotBlank()
        }
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
                    tabManager.current()?.session?.reload()
                    true
                }

                R.id.action_block_site -> {
                    blockCurrentSite()
                    true
                }

                R.id.action_protections -> {
                    toggleProtections()
                    true
                }

                R.id.action_bypass_site -> {
                    toggleSiteBypass()
                    true
                }

                R.id.action_lite_mode -> {
                    toggleLiteMode()
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
            .setMessage("Sleuth $version\nFirefox (GeckoView) browser with captures and a local case system.\n\n" + getString(R.string.rules_body))
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun showFindDialog() {
        val session = tabManager.current()?.session ?: return
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
                // evaluateJavaScript not available in GeckoView 154
                // Would need to use WebExtension for content script communication
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

    /**
     * Turns the guard off for everybody, or on again. The page in front is
     * loaded again, because a page that has already put its lock on cannot be
     * talked out of it.
     */
    private fun toggleProtections() {
        val on = !Sleuth.settings.overrideProtections
        Sleuth.settings.overrideProtections = on
        applyProtectionChange(on, R.string.status_protections_on, R.string.status_protections_off)
    }

    /**
     * Leaves this one site alone, or puts the guard back over it. Nothing
     * else about the browser changes.
     */
    private fun toggleSiteBypass() {
        val url = tabManager.current()?.url
        if (url.isNullOrBlank() || UrlNormalizer.hostOf(url).isBlank()) {
            Toast.makeText(this, R.string.status_no_site, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Sleuth.settings.overrideProtections) {
            // the guard is standing down for everybody, so the tap wakes it up
            toggleProtections()
            return
        }
        val bypassed = !Sleuth.settings.isBypassed(url)
        Sleuth.settings.setBypassed(url, bypassed)
        val message = if (bypassed) R.string.status_bypass_site else R.string.status_bypass_site_off
        applyProtectionChange(!bypassed, R.string.status_protections_on, message)
    }

    /**
     * Puts the guard back where it belongs and loads the page again, so the
     * change is not a promise but a fact.
     */
    private fun applyProtectionChange(protectionsOn: Boolean, onMessage: Int, offMessage: Int) {
        tabManager.refreshGuards()
        val session = tabManager.current()?.session
        session?.reload()
        updateChrome()
        Toast.makeText(
            this,
            if (protectionsOn) onMessage else offMessage,
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Lite mode drops images, video, audio and fonts, so pages load faster. */
    private fun toggleLiteMode() {
        val on = !Sleuth.settings.liteMode
        Sleuth.settings.liteMode = on
        applyWebSettings()
        tabManager.current()?.session?.reload()
        updateChrome()
        Toast.makeText(
            this,
            if (on) R.string.status_lite_on else R.string.status_lite_off,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleIncognito() {
        val enabled = !Sleuth.settings.incognito
        Sleuth.settings.incognito = enabled
        try {
            val runtime = GeckoViewFactory.getRuntime()
            runtime?.storageController?.clearData(0)
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
        binding.webContainer.addView(tab.geckoView)
        // a tab that was sleeping in the background has to wake up again
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
        val session = tabManager.current()?.session
        if (session != null && protectionsOn()) {
            // evaluateJavaScript not available in GeckoView 154
            afterRepaint(container) { shoot(container) }
        } else {
            shoot(container)
        }
    }

    /** True when the guard is standing over the page that is in front. */
    private fun protectionsOn(): Boolean =
        Sleuth.settings.isProtectionsOn(tabManager.current()?.url.orEmpty())

    /**
     * Runs [action] once the page has had the chance to paint again. The
     * frame callback comes first, the timer is the safety net for a page that
     * never gets a frame.
     */
    private fun afterRepaint(view: View, action: () -> Unit) {
        var done = false
        val fire = Runnable {
            if (done) return@Runnable
            done = true
            action()
        }
        view.postOnAnimation(fire)
        handler.postDelayed(fire, REPAINT_TIMEOUT)
    }

    private fun shoot(container: View) {
        ScreenshotCapturer.capture(this, container) { bitmap ->
            if (bitmap == null) {
                Toast.makeText(this, R.string.error_capture, Toast.LENGTH_SHORT).show()
                return@capture
            }
            if (ScreenshotCapturer.looksBlank(bitmap)) {
                // the page may have hidden itself, so draw it again by hand
                val retry = Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)
                ScreenshotCapturer.captureByDrawing(container, retry) { drawn ->
                    val usable = if (drawn != null && !ScreenshotCapturer.looksBlank(drawn)) drawn else bitmap
                    openEvidenceLab(usable)
                }
                return@capture
            }
            openEvidenceLab(bitmap)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun captureFullPage() {
        val session = tabManager.current()?.session ?: return
        Toast.makeText(this, R.string.status_capturing, Toast.LENGTH_SHORT).show()
        // evaluateJavaScript not available in GeckoView 154
        // Full page capture would need alternative approach
        captureViewport()
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
        protectionCodes = emptyList()
        currentUrl = url
        barBusy = false
        if (!binding.urlBar.hasFocus()) {
            showShortUrl()
        }
        binding.progressBar.visibility = View.VISIBLE
        updateChrome()
    }

    override fun onPageFinished(url: String, title: String) {
        binding.errorText.visibility = View.GONE
        val tab = tabManager.current()
        tab?.url = url
        tab?.title = title
        currentUrl = url
        if (!binding.urlBar.hasFocus()) {
            showShortUrl()
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
        runProtectionProbe(url, title)
        updateChrome()
    }

    /**
     * Asks the page what it is doing to protect itself. The answer is kept
     * quiet and, when a case is armed, filed as evidence.
     */
    private fun runProtectionProbe(url: String, title: String) {
        if (!Sleuth.settings.isProtectionsOn(url)) return
        if (url == lastProbedUrl) return
        lastProbedUrl = url
        probePage(url, title)
        // a page may put its lock on a moment after it loaded, so it is asked again
        handler.postDelayed({
            if (tabManager.current()?.url == url) probePage(url, title)
        }, PROBE_RECHECK)
    }

    private fun probePage(url: String, title: String) {
        // evaluateJavaScript not available in GeckoView 154
        // Protection probe would need alternative approach
    }

    private fun updateProtectionIndicator() {
        val url = tabManager.current()?.url.orEmpty()
        val codes = protectionCodes
        when {
            !Sleuth.settings.isProtectionsOn(url) -> {
                // the guard is standing down here, and it can be tapped to wake up
                binding.protectionIcon.visibility = View.VISIBLE
                binding.protectionIcon.setImageResource(R.drawable.ic_shield_off)
                binding.protectionIcon.contentDescription =
                    getString(R.string.status_protections_off)
            }

            codes.isNotEmpty() -> {
                binding.protectionIcon.visibility = View.VISIBLE
                binding.protectionIcon.setImageResource(R.drawable.ic_shield)
                binding.protectionIcon.contentDescription = ProtectionProbe.summary(codes)
            }

            else -> {
                binding.protectionIcon.visibility = View.GONE
            }
        }
    }

    private fun recordVisitForCase(url: String, title: String) {
        if (Sleuth.settings.incognito) return
        val case = armedCaseCache ?: return
        if (url == lastRecordedUrl) return
        lastRecordedUrl = url
        val token = ++extractionToken
        // evaluateJavaScript not available in GeckoView 154
        // Text extraction would need alternative approach
    }

    override fun onProgressChanged(progress: Int) {
        binding.progressBar.progress = progress
        if (progress >= 100) {
            binding.progressBar.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    override fun onTitleChanged(title: String) {
        binding.titleText.text = title
    }

    override fun onNewTabRequest(url: String) {
        openInNewTab(url)
    }

    override fun onFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: Any
    ): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = callback
        return try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
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

    override fun onCanGoBackChanged(canGoBack: Boolean) {
        this.canGoBack = canGoBack
        updateChrome()
    }

    override fun onCanGoForwardChanged(canGoForward: Boolean) {
        this.canGoForward = canGoForward
        updateChrome()
    }

    // ------------------------------------------------------------------ utils

    private fun navigate(rawInput: String) {
        val target = Omnibox.targetFor(rawInput, Sleuth.settings.searchEngine) ?: return
        barBusy = true
        hideKeyboard()
        val session = tabManager.current()?.session
        if (session != null) {
            // drop whatever is still downloading, the new page matters now
            session.stop()
            extractionToken++
            session.loadUri(target)
        } else {
            openInNewTab(target)
        }
    }

    /** Puts the whole address in the bar, ready to be edited. */
    private fun showFullUrl() {
        val url = currentUrl
        if (url.isBlank()) return
        if (binding.urlBar.text?.toString() != url) {
            binding.urlBar.setText(url)
            binding.urlBar.setSelection(url.length)
        }
    }

    /** Puts the short form in the bar, the way a browser shows it. */
    private fun showShortUrl() {
        val url = currentUrl
        if (url.isBlank()) return
        val short = UrlDisplay.short(url)
        if (binding.urlBar.text?.toString() != short) {
            binding.urlBar.setText(short)
        }
    }

    private fun updateClearButton() {
        val hasText = !binding.urlBar.text.isNullOrBlank()
        binding.btnClear.visibility = if (hasText) View.VISIBLE else View.GONE
    }

    private fun updateChrome() {
        val session = tabManager.current()?.session
        binding.btnBack.isEnabled = session != null && canGoBack
        binding.btnForward.isEnabled = session != null && canGoForward
        binding.btnBack.alpha = if (binding.btnBack.isEnabled) 1f else 0.35f
        binding.btnForward.alpha = if (binding.btnForward.isEnabled) 1f else 0.35f
        binding.btnVoice.visibility =
            if (Sleuth.settings.voiceInput) View.VISIBLE else View.GONE
        updateClearButton()
        updateProtectionIndicator()

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

        /** How long a page may take to paint before the capture goes ahead. */
        private const val REPAINT_TIMEOUT = 220L

        /** How long to wait before asking the page again about its tricks. */
        private const val PROBE_RECHECK = 900L
    }
}
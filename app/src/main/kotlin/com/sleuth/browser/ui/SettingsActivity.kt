package com.sleuth.browser.ui

import android.os.Bundle
import android.view.View
import android.webkit.WebStorage
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.sleuth.browser.R
import com.sleuth.browser.Sleuth
import com.sleuth.browser.SleuthIO
import com.sleuth.browser.databinding.ActivitySettingsBinding
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "sleuth_prefs"
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<Preference>("version")?.let { preference ->
                val context = preference.context
                val version = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (error: Throwable) {
                    "1.0.0"
                }
                preference.summary = version
            }

            findPreference<Preference>("clear_history")?.setOnPreferenceClickListener {
                SleuthIO.execute { Sleuth.db.clearHistory() }
                true
            }

            findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                val context = context
                SleuthIO.execute {
                    try {
                        context?.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                        WebStorage.getInstance().deleteAllData()
                    } catch (error: Throwable) {
                        // cache is best effort
                    }
                }
                true
            }

            findPreference<Preference>("clear_all")?.setOnPreferenceClickListener {
                val context = context
                SleuthIO.execute {
                    try {
                        context?.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                        val evidenceRoot = File(context?.filesDir ?: File("/"), "evidence")
                        evidenceRoot.listFiles()?.forEach { it.deleteRecursively() }
                        WebStorage.getInstance().deleteAllData()
                    } catch (error: Throwable) {
                        // best effort
                    }
                    Sleuth.db.clearHistory()
                    for (bookmark in Sleuth.db.bookmarks("")) {
                        Sleuth.db.deleteBookmark(bookmark.id)
                    }
                    for (case in Sleuth.db.cases()) {
                        Sleuth.db.deleteCase(case.id)
                    }
                    Sleuth.repository.disarm()
                }
                true
            }
        }
    }
}

package com.sleuth.browser

import android.content.Context
import com.sleuth.browser.browser.BrowserSettings
import com.sleuth.browser.data.CaseRepository
import com.sleuth.browser.data.SleuthDb

/** Shared singletons so every screen works on the same record. */
object Sleuth {

    lateinit var db: SleuthDb
        private set
    lateinit var settings: BrowserSettings
        private set
    lateinit var repository: CaseRepository
        private set

    fun init(context: Context) {
        if (::db.isInitialized) return
        val app = context.applicationContext
        db = SleuthDb(app)
        settings = BrowserSettings(app)
        repository = CaseRepository(app, db, settings)
    }
}

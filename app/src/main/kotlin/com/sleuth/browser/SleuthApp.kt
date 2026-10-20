package com.sleuth.browser

import android.app.Application

class SleuthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Sleuth.init(this)
    }
}

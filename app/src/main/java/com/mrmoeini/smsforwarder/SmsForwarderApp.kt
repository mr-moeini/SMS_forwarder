package com.mrmoeini.smsforwarder

import android.app.Application

/** Applies the saved language as early as possible, before any Activity inflates its views. */
class SmsForwarderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocaleStore.applyStartup(this)
        ThemeStore.applyStartup(this)
    }
}

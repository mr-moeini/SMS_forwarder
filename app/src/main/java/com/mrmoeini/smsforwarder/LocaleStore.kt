package com.mrmoeini.smsforwarder

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The user's chosen app language ("en" or "fa"). Null means "not chosen yet" (first run). */
object LocaleStore {
    private const val KEY = "app_language"

    fun get(c: Context): String? = RuleStore.prefs(c).getString(KEY, null)

    fun isChosen(c: Context): Boolean = !get(c).isNullOrBlank()

    /** Persists the language and applies it immediately (recreate the current activity afterwards). */
    fun set(c: Context, lang: String) {
        RuleStore.prefs(c).edit().putString(KEY, lang).apply()
        apply(lang)
    }

    /** Re-applies the saved language; call once on app start before any UI inflates. */
    fun applyStartup(c: Context) {
        val lang = get(c) ?: return
        apply(lang)
    }

    private fun apply(lang: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    fun isPersian(c: Context): Boolean = get(c) == "fa"
}

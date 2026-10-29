package com.mrmoeini.smsforwarder

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** The user's chosen accent color theme and dark-mode preference. */
object ThemeStore {
    private const val KEY_ACCENT = "theme_accent"
    private const val KEY_NIGHT = "theme_night_mode"

    enum class Accent(val key: String, val labelRes: Int, val swatch: Int, val styleRes: Int) {
        BLUE("blue", R.string.theme_blue, 0xFF007AFF.toInt(), R.style.Theme_SMSForwarder),
        GREEN("green", R.string.theme_green, 0xFF34C759.toInt(), R.style.Theme_SMSForwarder_Green),
        PURPLE("purple", R.string.theme_purple, 0xFFAF52DE.toInt(), R.style.Theme_SMSForwarder_Purple),
        ORANGE("orange", R.string.theme_orange, 0xFFFF9500.toInt(), R.style.Theme_SMSForwarder_Orange)
    }

    fun accent(c: Context): Accent {
        val key = RuleStore.prefs(c).getString(KEY_ACCENT, Accent.BLUE.key)
        return Accent.values().firstOrNull { it.key == key } ?: Accent.BLUE
    }

    fun setAccent(c: Context, accent: Accent) {
        RuleStore.prefs(c).edit().putString(KEY_ACCENT, accent.key).apply()
    }

    fun styleResId(c: Context): Int = accent(c).styleRes

    /** 0 = follow system, 1 = light, 2 = dark. */
    fun nightMode(c: Context): Int = RuleStore.prefs(c).getInt(KEY_NIGHT, 0)

    fun setNightMode(c: Context, mode: Int) {
        RuleStore.prefs(c).edit().putInt(KEY_NIGHT, mode).apply()
        applyNightMode(mode)
    }

    fun applyStartup(c: Context) {
        applyNightMode(nightMode(c))
    }

    private fun applyNightMode(mode: Int) {
        val m = when (mode) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(m)
    }
}

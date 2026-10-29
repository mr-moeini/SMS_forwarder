package com.mrmoeini.smsforwarder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Applies the user's chosen color theme to every screen before it inflates. */
abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeStore.styleResId(this))
        super.onCreate(savedInstanceState)
    }
}

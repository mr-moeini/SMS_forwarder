package com.mrmoeini.smsforwarder

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.RadioButton

/** First-run language picker. Also reused by Settings (see SettingsActivity for the inline version). */
class LanguageActivity : BaseActivity() {

    companion object {
        const val EXTRA_FIRST_RUN = "first_run"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language)

        val rg = findViewById<RadioGroup>(R.id.rgLang)
        val rbFa = findViewById<RadioButton>(R.id.rbFa)
        val rbEn = findViewById<RadioButton>(R.id.rbEn)
        val current = LocaleStore.get(this)
        if (current == "fa") rbFa.isChecked = true else rbEn.isChecked = true

        findViewById<Button>(R.id.btnSaveLang).setOnClickListener {
            val lang = if (rg.checkedRadioButtonId == R.id.rbFa) "fa" else "en"
            LocaleStore.set(this, lang)
            if (intent.getBooleanExtra(EXTRA_FIRST_RUN, false)) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }
}

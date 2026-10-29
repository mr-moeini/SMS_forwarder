package com.mrmoeini.smsforwarder

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val rgLang = findViewById<RadioGroup>(R.id.rgLang)
        val rbEn = findViewById<RadioButton>(R.id.rbEn)
        val rbFa = findViewById<RadioButton>(R.id.rbFa)
        if (LocaleStore.get(this) == "fa") rbFa.isChecked = true else rbEn.isChecked = true
        rgLang.setOnCheckedChangeListener { _, id ->
            val lang = if (id == R.id.rbFa) "fa" else "en"
            if (lang != LocaleStore.get(this)) LocaleStore.set(this, lang)
        }

        val rgNight = findViewById<RadioGroup>(R.id.rgNight)
        when (ThemeStore.nightMode(this)) {
            1 -> findViewById<RadioButton>(R.id.rbNightLight).isChecked = true
            2 -> findViewById<RadioButton>(R.id.rbNightDark).isChecked = true
            else -> findViewById<RadioButton>(R.id.rbNightSystem).isChecked = true
        }
        rgNight.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.rbNightLight -> 1
                R.id.rbNightDark -> 2
                else -> 0
            }
            ThemeStore.setNightMode(this, mode)
        }

        buildAccentRow()
        buildHelpRow()
    }

    private fun buildAccentRow() {
        val row = findViewById<LinearLayout>(R.id.accentRow)
        row.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (44 * density).toInt()
        val margin = (10 * density).toInt()
        val current = ThemeStore.accent(this)

        for (accent in ThemeStore.Accent.values()) {
            val swatch = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = margin
            lp.marginEnd = margin
            swatch.layoutParams = lp
            val bg = android.graphics.drawable.GradientDrawable()
            bg.shape = android.graphics.drawable.GradientDrawable.OVAL
            bg.setColor(accent.swatch)
            if (accent == current) {
                bg.setStroke((3 * density).toInt(), getColor(R.color.ios_label))
            }
            swatch.background = bg
            swatch.setOnClickListener {
                if (ThemeStore.accent(this) != accent) {
                    ThemeStore.setAccent(this, accent)
                    recreate()
                }
            }
            row.addView(swatch)
        }
    }

    private fun buildHelpRow() {
        val container = findViewById<LinearLayout>(R.id.helpRowContainer)
        val row = layoutInflater.inflate(R.layout.row_home, container, false)
        row.findViewById<ImageView>(R.id.ivIcon).setImageResource(R.drawable.ic_help)
        val bg = android.graphics.drawable.GradientDrawable()
        bg.cornerRadius = 7 * resources.displayMetrics.density
        bg.setColor(0xFF5AC8FA.toInt())
        row.findViewById<ImageView>(R.id.ivIcon).background = bg
        row.findViewById<TextView>(R.id.tvRowTitle).setText(R.string.help_and_tutorial)
        row.setOnClickListener { startActivity(Intent(this, HelpActivity::class.java)) }
        container.addView(row)
    }
}

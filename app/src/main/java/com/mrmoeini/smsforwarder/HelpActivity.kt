package com.mrmoeini.smsforwarder

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class HelpActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.helpContainer)
        val persian = LocaleStore.isPersian(this)
        for (section in HelpContent.sections(persian)) {
            val v = layoutInflater.inflate(R.layout.item_help_section, container, false)
            v.findViewById<TextView>(R.id.tvSectionTitle).text = section.title
            v.findViewById<TextView>(R.id.tvSectionBody).text = section.body
            container.addView(v)
        }
    }
}

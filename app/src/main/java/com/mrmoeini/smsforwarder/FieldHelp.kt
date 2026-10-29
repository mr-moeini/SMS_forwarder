package com.mrmoeini.smsforwarder

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Attaches a small (i) icon inside a field; tapping it shows a compact popup with a short
 * explanation and a concrete example, in whichever language the app is currently using.
 */
object FieldHelp {

    fun attach(field: EditText, key: String) {
        val context = field.context
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_info_small)?.mutate()
        icon?.setTint(ContextCompat.getColor(context, R.color.ios_secondary))
        setEndDrawable(field, icon)
        field.setOnTouchListener { v, event ->
            val et = v as EditText
            val drawableEnd = et.compoundDrawablesRelative[2] ?: return@setOnTouchListener false
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val isRtl = et.layoutDirection == View.LAYOUT_DIRECTION_RTL
                val bounds = drawableEnd.bounds
                val hitZone = bounds.width() + et.paddingEnd
                val touchX = event.x
                val withinIcon = if (isRtl) touchX <= hitZone else touchX >= et.width - hitZone
                if (withinIcon) {
                    show(et, key)
                    et.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun setEndDrawable(field: EditText, icon: Drawable?) {
        field.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null)
        field.compoundDrawablePadding = (8 * field.resources.displayMetrics.density).toInt()
    }

    private fun show(anchor: View, key: String) {
        val context = anchor.context
        val persian = LocaleStore.isPersian(context)
        val content = HelpContent.field(key, persian)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.popup_field_help, null)
        view.findViewById<TextView>(R.id.tvHelpTitle).text = content.title
        view.findViewById<TextView>(R.id.tvHelpDesc).text = content.desc
        view.findViewById<TextView>(R.id.tvHelpExample).text = content.example

        val popup = PopupWindow(view, (280 * context.resources.displayMetrics.density).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.elevation = 10f
        popup.showAsDropDown(anchor, 0, 12)
    }
}

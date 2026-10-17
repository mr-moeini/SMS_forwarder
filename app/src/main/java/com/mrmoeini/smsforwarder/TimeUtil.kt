package com.mrmoeini.smsforwarder

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.icu.util.ULocale
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

object TimeUtil {

    fun format(min: Int): String = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

    fun mmss(totalSeconds: Long): String =
        String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)

    fun pickTime(context: Context, initialMin: Int, onPicked: (Int) -> Unit) {
        val start = if (initialMin in 0..1439) initialMin else 8 * 60
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            start / 60, start % 60, true
        ).show()
    }

    /** True when [nowMin] is inside [from, to). A window with from > to crosses midnight. */
    fun inWindow(nowMin: Int, from: Int, to: Int): Boolean =
        if (from == to) true
        else if (from < to) nowMin >= from && nowMin < to
        else nowMin >= from || nowMin < to

    /** Calendar.DAY_OF_WEEK values in the order they are shown to the user. */
    fun dayOrder(): IntArray =
        if (Locale.getDefault().language == "fa") {
            intArrayOf(
                Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
            )
        } else {
            intArrayOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
            )
        }

    private fun dayName(dow: Int): String =
        DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays[dow]

    fun daysText(mask: Int, everyDay: String): String {
        if ((mask and 127) == 127) return everyDay
        return dayOrder().filter { ((mask shr (it - 1)) and 1) == 1 }
            .joinToString(", ") { dayName(it) }
    }

    fun pickDays(context: Context, mask: Int, onPicked: (Int) -> Unit) {
        val order = dayOrder()
        val names = Array(7) { dayName(order[it]) }
        val checked = BooleanArray(7) { ((mask shr (order[it] - 1)) and 1) == 1 }
        AlertDialog.Builder(context)
            .setTitle(R.string.cf_days)
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.save) { _, _ ->
                var m = 0
                for (i in 0 until 7) if (checked[i]) m = m or (1 shl (order[i] - 1))
                onPicked(if (m == 0) 127 else m)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

/** Dates are shown in the Persian (Jalali) calendar when the device language is Persian. */
object DateUtil {

    private fun persianCal(): android.icu.util.Calendar =
        android.icu.util.Calendar.getInstance(ULocale("fa_IR@calendar=persian"))

    val useJalali: Boolean
        get() = Locale.getDefault().language == "fa"

    fun format(millis: Long): String {
        return if (useJalali) {
            val c = persianCal()
            c.timeInMillis = millis
            String.format(
                Locale.US, "%04d/%02d/%02d",
                c.get(android.icu.util.Calendar.YEAR),
                c.get(android.icu.util.Calendar.MONTH) + 1,
                c.get(android.icu.util.Calendar.DAY_OF_MONTH)
            )
        } else {
            val c = Calendar.getInstance()
            c.timeInMillis = millis
            String.format(
                Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
            )
        }
    }

    fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun endOfDay(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfDay(millis)
        c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis - 1
    }

    fun pick(context: Context, initial: Long, onPicked: (Long) -> Unit) {
        val base = if (initial > 0L) initial else System.currentTimeMillis()
        if (useJalali) {
            pickJalali(context, base, onPicked)
        } else {
            val c = Calendar.getInstance()
            c.timeInMillis = base
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    val r = Calendar.getInstance()
                    r.clear()
                    r.set(y, m, d)
                    onPicked(r.timeInMillis)
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun pickJalali(context: Context, base: Long, onPicked: (Long) -> Unit) {
        val cal = persianCal()
        cal.timeInMillis = base
        val y0 = cal.get(android.icu.util.Calendar.YEAR)
        val m0 = cal.get(android.icu.util.Calendar.MONTH) + 1
        val d0 = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)

        fun picker(min: Int, max: Int, value: Int): NumberPicker {
            val p = NumberPicker(context)
            p.minValue = min
            p.maxValue = max
            p.value = value
            p.wrapSelectorWheel = true
            return p
        }

        val py = picker(y0 - 3, y0 + 20, y0)
        val pm = picker(1, 12, m0)
        val pd = picker(1, 31, d0)
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        row.setPadding(pad, pad, pad, pad)
        row.addView(py)
        row.addView(pm)
        row.addView(pd)

        AlertDialog.Builder(context)
            .setView(row)
            .setPositiveButton(R.string.save) { _, _ ->
                val c = persianCal()
                c.set(py.value, pm.value - 1, 1)
                val maxDay = c.getActualMaximum(android.icu.util.Calendar.DAY_OF_MONTH)
                c.set(py.value, pm.value - 1, minOf(pd.value, maxDay))
                onPicked(c.timeInMillis)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

package com.mrmoeini.smsforwarder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Switches carrier call forwarding on/off with GSM codes (**21*number# / ##21#) following the
 * time windows of the rules, and schedules an alarm for the next start/end boundary.
 */
object CallForwardScheduler {

    private const val REQ = 4242

    private class Task(val mode: Int, val number: String?)

    private fun modeName(context: Context, mode: Int): String = context.getString(
        when (mode) {
            1 -> R.string.cf_mode_busy
            2 -> R.string.cf_mode_noanswer
            3 -> R.string.cf_mode_unreach
            else -> R.string.cf_mode_all
        }
    )

    /** Brings the carrier state in line with the rules, then schedules the next alarm. */
    fun reconcile(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        val rules = CallRuleStore.load(app)
        val now = Calendar.getInstance()

        val tasks = mutableListOf<Task>()
        for (mode in 0..3) {
            val active = rules.firstOrNull {
                it.mode == mode && it.destNumber.isNotBlank() && it.isActiveAt(now)
            }
            val desired = active?.destNumber
            val applied = CallRuleStore.getApplied(app, mode)
            if (desired != applied) tasks.add(Task(mode, desired))
        }
        scheduleNext(app, rules, now)

        if (tasks.isEmpty()) {
            onDone?.invoke()
            return
        }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            RuleStore.addLog(app, "CALL FORWARD: CALL_PHONE permission is not granted, cannot set forwarding")
            onDone?.invoke()
            return
        }
        runQueue(app, tasks, 0, onDone)
    }

    /** Turns off every forwarding mode this app has set (and clears the saved state). */
    fun turnOffAll(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        val tasks = mutableListOf<Task>()
        for (mode in 0..3) {
            if (CallRuleStore.getApplied(app, mode) != null) tasks.add(Task(mode, null))
        }
        if (tasks.isEmpty()) {
            // Nothing recorded: still send the "all calls" cancel code as a safety net.
            tasks.add(Task(0, null))
        }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            RuleStore.addLog(app, "CALL FORWARD: CALL_PHONE permission is not granted")
            onDone?.invoke()
            return
        }
        runQueue(app, tasks, 0, onDone)
    }

    private fun runQueue(app: Context, tasks: List<Task>, index: Int, onDone: (() -> Unit)?) {
        if (index >= tasks.size) {
            onDone?.invoke()
            return
        }
        val t = tasks[index]
        val code = if (t.number != null) CallForwardRule.enableCode(t.mode, t.number)
        else CallForwardRule.disableCode(t.mode)
        val label = if (t.number != null) "ON ${modeName(app, t.mode)} -> ${t.number}"
        else "OFF ${modeName(app, t.mode)}"

        sendUssd(app, code) { ok, text ->
            if (ok) {
                CallRuleStore.setApplied(app, t.mode, t.number)
                RuleStore.addLog(app, "CALL FORWARD $label : OK ${text.take(80)}")
            } else {
                RuleStore.addLog(app, "CALL FORWARD $label : FAILED $text")
            }
            // give the network a moment before the next code
            Handler(Looper.getMainLooper()).postDelayed({ runQueue(app, tasks, index + 1, onDone) }, 1500)
        }
    }

    private fun sendUssd(app: Context, code: String, onResult: (Boolean, String) -> Unit) {
        try {
            val tm = app.getSystemService(TelephonyManager::class.java)
            tm.sendUssdRequest(
                code,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence
                    ) {
                        onResult(true, response.toString())
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) {
                        onResult(false, "USSD error code $failureCode")
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            onResult(false, e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    private fun nextBoundary(rules: List<CallForwardRule>, now: Calendar): Long? {
        var best: Long? = null
        val nowMs = now.timeInMillis
        for (r in rules) {
            if (!r.enabled) continue
            for (d in 0..8) {
                for (t in intArrayOf(r.startMin, r.endMin)) {
                    val c = now.clone() as Calendar
                    c.add(Calendar.DAY_OF_YEAR, d)
                    c.set(Calendar.HOUR_OF_DAY, t / 60)
                    c.set(Calendar.MINUTE, t % 60)
                    c.set(Calendar.SECOND, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    val ms = c.timeInMillis
                    val cur = best
                    if (ms > nowMs && (cur == null || ms < cur)) best = ms
                }
            }
        }
        return best
    }

    private fun scheduleNext(app: Context, rules: List<CallForwardRule>, now: Calendar) {
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            app, REQ,
            Intent(app, CallForwardAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        val next = nextBoundary(rules, now) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    /** Short text describing what this app currently has set at the carrier. */
    fun statusText(context: Context): String {
        val parts = mutableListOf<String>()
        for (mode in 0..3) {
            val n = CallRuleStore.getApplied(context, mode)
            if (n != null) parts.add(modeName(context, mode) + " -> " + n)
        }
        return if (parts.isEmpty()) context.getString(R.string.cf_none) else parts.joinToString("; ")
    }
}

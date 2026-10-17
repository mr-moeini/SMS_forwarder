package com.mrmoeini.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import java.util.concurrent.atomic.AtomicBoolean

/** Fires at the start/end of a call-forward window and re-applies forwarding. */
class CallForwardAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        reconcileAsync(context)
    }
}

/** Re-schedules everything after reboot, app update, or clock/timezone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return

        reconcileAsync(context)

        val restartService = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (restartService && AnswerRuleStore.isEnabled(context)) {
            try {
                AnswerService.start(context)
            } catch (e: Exception) {
                RuleStore.addLog(context, "AUTO-ANSWER: could not start service: ${e.javaClass.simpleName}")
            }
        }
    }
}

/** Forwards phone-state broadcasts (ringing / off-hook / idle) to the running answer service. */
class CallStateReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        AnswerService.instance?.onPhoneState(state, number)
    }
}

private fun BroadcastReceiver.reconcileAsync(context: Context) {
    val pending = goAsync()
    val finished = AtomicBoolean(false)
    fun finish() {
        if (finished.compareAndSet(false, true)) pending.finish()
    }
    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 9000)
    CallForwardScheduler.reconcile(context) { finish() }
}

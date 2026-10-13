package com.mrmoeini.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager

/** Receives incoming SMS and forwards them according to the saved rules. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages == null || messages.isEmpty()) return

        val rules = RuleStore.load(context)
        if (rules.isEmpty()) return

        // Long SMS arrive as several parts: join parts that share the same sender.
        val bySender = messages.groupBy { it.originatingAddress ?: "" }
        for ((from, parts) in bySender) {
            if (from.isEmpty()) continue
            val body = parts.joinToString("") { it.messageBody ?: "" }
            for (rule in rules) {
                if (!rule.matches(from, body)) continue
                if (rule.destNumber.isBlank()) continue
                // Never forward a message back to the number it came from.
                val sameNumber = Rule.digitsOnly(rule.destNumber).takeLast(10) ==
                        Rule.digitsOnly(from).takeLast(10) &&
                        Rule.digitsOnly(from).isNotEmpty()
                if (sameNumber) continue
                forward(context, rule, from, body)
            }
        }
    }

    private fun forward(context: Context, rule: Rule, from: String, body: String) {
        val text = "[$from] $body"
        try {
            val sms = smsManager(context)
            val parts = sms.divideMessage(text)
            if (parts.size > 1) {
                sms.sendMultipartTextMessage(rule.destNumber, null, parts, null, null)
            } else {
                sms.sendTextMessage(rule.destNumber, null, text, null, null)
            }
            RuleStore.addLog(context, "$from -> ${rule.destNumber}  OK")
        } catch (e: Exception) {
            RuleStore.addLog(context, "$from -> ${rule.destNumber}  FAILED: ${e.javaClass.simpleName}")
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
}

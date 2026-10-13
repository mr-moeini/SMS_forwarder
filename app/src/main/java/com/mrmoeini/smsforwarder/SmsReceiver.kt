package com.mrmoeini.smsforwarder

import android.app.PendingIntent
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
        val subId = subscriptionId(intent)

        // Long SMS arrive as several parts: join parts that share the same sender.
        val bySender = messages.groupBy { it.originatingAddress ?: "" }
        for ((from, parts) in bySender) {
            if (from.isEmpty()) continue
            val body = parts.joinToString("") { it.messageBody ?: "" }

            val matched = rules.filter { it.destNumber.isNotBlank() && it.matches(from, body) }
            // Log every received SMS (sender only, never the text) so problems can be diagnosed.
            RuleStore.addLog(context, "RECV from $from  rules=${rules.size} matched=${matched.size}")

            for (rule in matched) {
                // Never forward a message back to the number it came from.
                val fromDigits = Rule.digitsOnly(from)
                val sameNumber = fromDigits.isNotEmpty() &&
                        Rule.digitsOnly(rule.destNumber).takeLast(10) == fromDigits.takeLast(10)
                if (sameNumber) {
                    RuleStore.addLog(context, "skip: destination equals sender ($from)")
                    continue
                }
                forward(context, rule, from, body, subId)
            }
        }
    }

    private fun forward(context: Context, rule: Rule, from: String, body: String, subId: Int) {
        val text = "[$from] $body"
        try {
            val sms = smsManager(context, subId)
            val parts = sms.divideMessage(text)
            val base = (System.currentTimeMillis() and 0xFFFFF).toInt() * 200
            if (parts.size > 1) {
                val sent = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sent.add(sentIntent(context, rule.destNumber, i + 1, parts.size, base + i))
                }
                sms.sendMultipartTextMessage(rule.destNumber, null, parts, sent, null)
            } else {
                sms.sendTextMessage(
                    rule.destNumber, null, text,
                    sentIntent(context, rule.destNumber, 1, 1, base), null
                )
            }
            RuleStore.addLog(context, "$from -> ${rule.destNumber}  queued")
        } catch (e: Exception) {
            RuleStore.addLog(
                context,
                "$from -> ${rule.destNumber}  FAILED: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    private fun sentIntent(
        context: Context, dest: String, part: Int, total: Int, requestCode: Int
    ): PendingIntent {
        val i = Intent(context, SmsSentReceiver::class.java).apply {
            action = SmsSentReceiver.ACTION_SENT
            putExtra(SmsSentReceiver.EXTRA_DEST, dest)
            putExtra(SmsSentReceiver.EXTRA_PART, part)
            putExtra(SmsSentReceiver.EXTRA_TOTAL, total)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
    }

    /** SIM (subscription) that received the message, or -1 if unknown. */
    private fun subscriptionId(intent: Intent): Int {
        val a = intent.getIntExtra("subscription", -1)
        if (a >= 0) return a
        return intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context, subId: Int): SmsManager {
        if (subId >= 0) {
            try {
                return SmsManager.getSmsManagerForSubscriptionId(subId)
            } catch (_: Exception) {
                // fall back to the default SIM below
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
    }
}

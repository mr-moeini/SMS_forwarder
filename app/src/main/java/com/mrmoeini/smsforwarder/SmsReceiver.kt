package com.mrmoeini.smsforwarder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/** Receives incoming SMS and forwards them (to a number or an API) according to the saved rules. */
class SmsReceiver : BroadcastReceiver() {

    private class Job(val from: String, val body: String, val rule: Rule)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages == null || messages.isEmpty()) return

        val app = context.applicationContext
        val rules = RuleStore.load(app)
        val subId = subscriptionId(intent)
        val timestamp = System.currentTimeMillis()

        val jobs = mutableListOf<Job>()
        // Long SMS arrive as several parts: join parts that share the same sender.
        val bySender = messages.groupBy { it.originatingAddress ?: "" }
        for ((from, parts) in bySender) {
            if (from.isEmpty()) continue
            val body = parts.joinToString("") { it.messageBody ?: "" }
            val matched = rules.filter { it.isConfigured() && it.matches(from, body) }
            // Log every received SMS (sender only, never the text) so problems can be diagnosed.
            RuleStore.addLog(app, "RECV from $from  rules=${rules.size} matched=${matched.size}")
            matched.forEach { jobs.add(Job(from, body, it)) }
        }
        if (jobs.isEmpty()) return

        val pending = goAsync()
        Thread {
            try {
                for (j in jobs) handle(app, j, subId, timestamp)
            } catch (e: Exception) {
                RuleStore.addLog(app, "ERROR ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handle(context: Context, job: Job, subId: Int, timestamp: Long) {
        val rule = job.rule
        val label = if (rule.name.isNotBlank()) rule.name else rule.sender
        val text = rule.extract(job.body)
        if (text == null) {
            RuleStore.addLog(context, "rule '$label': regex did not match, not forwarded")
            return
        }
        if (rule.action == Rule.ACTION_API) forwardToApi(context, rule, label, job.from, text, timestamp)
        else forwardSms(context, rule, job.from, text, subId)
    }

    // ---------------------------------------------------------------- API

    private fun forwardToApi(
        context: Context, rule: Rule, label: String, from: String, text: String, timestamp: Long
    ) {
        val deadline = SystemClock.elapsedRealtime() + 7000L   // a receiver must finish quickly
        val res = ApiClient.send(context, rule, from, text, timestamp, deadline)
        when {
            res.success -> RuleStore.addLog(context, "API '$label' OK (${res.message.take(40)})")
            res.retryable -> {
                RuleStore.addLog(context, "API '$label' failed (${res.message.take(60)}), queued for retry")
                ApiForwardWorker.enqueue(context, rule.id, from, text, timestamp)
            }
            else -> RuleStore.addLog(context, "API '$label' FAILED: ${res.message.take(80)}")
        }
    }

    // ---------------------------------------------------------------- SMS to a number

    private fun forwardSms(context: Context, rule: Rule, from: String, body: String, subId: Int) {
        // Never forward a message back to the number it came from.
        val fromDigits = Rule.digitsOnly(from)
        if (fromDigits.isNotEmpty() &&
            Rule.digitsOnly(rule.destNumber).takeLast(10) == fromDigits.takeLast(10)
        ) {
            RuleStore.addLog(context, "skip: destination equals sender ($from)")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            RuleStore.addLog(
                context,
                "$from -> ${rule.destNumber}  NOT SENT: SEND_SMS permission is not granted. " +
                    "Open the app and allow SMS (App info > Permissions > SMS)."
            )
            return
        }
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

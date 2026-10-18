package com.mrmoeini.smsforwarder

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Retries a failed API forward later (network available, exponential backoff). */
class ApiForwardWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val ruleId = inputData.getString(KEY_RULE) ?: return Result.failure()
        val from = inputData.getString(KEY_FROM) ?: ""
        val text = inputData.getString(KEY_TEXT) ?: ""
        val ts = inputData.getLong(KEY_TIME, System.currentTimeMillis())
        val rule = RuleStore.load(ctx).firstOrNull { it.id == ruleId }
        if (rule == null || rule.action != Rule.ACTION_API) return Result.failure()

        val res = ApiClient.send(ctx, rule, from, text, ts)
        val label = "API '" + (if (rule.name.isNotBlank()) rule.name else rule.sender) + "'"
        return when {
            res.success -> {
                RuleStore.addLog(ctx, "$label retry OK (${res.message.take(40)})")
                Result.success()
            }
            res.retryable && runAttemptCount < MAX_ATTEMPTS - 1 -> {
                RuleStore.addLog(ctx, "$label retry failed, will try again: ${res.message.take(60)}")
                Result.retry()
            }
            else -> {
                RuleStore.addLog(ctx, "$label GAVE UP: ${res.message.take(60)}")
                Result.failure()
            }
        }
    }

    companion object {
        private const val KEY_RULE = "rule"
        private const val KEY_FROM = "from"
        private const val KEY_TEXT = "text"
        private const val KEY_TIME = "time"
        private const val MAX_ATTEMPTS = 6

        fun enqueue(context: Context, ruleId: String, from: String, text: String, timestamp: Long) {
            val data = Data.Builder()
                .putString(KEY_RULE, ruleId)
                .putString(KEY_FROM, from)
                .putString(KEY_TEXT, text.take(4000))
                .putLong(KEY_TIME, timestamp)
                .build()
            val req = OneTimeWorkRequestBuilder<ApiForwardWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}

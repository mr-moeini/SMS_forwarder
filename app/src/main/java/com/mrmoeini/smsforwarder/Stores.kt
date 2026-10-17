package com.mrmoeini.smsforwarder

import android.content.Context
import org.json.JSONArray
import java.io.File

object CallRuleStore {
    private const val KEY = "call_rules"

    @Synchronized
    fun load(c: Context): MutableList<CallForwardRule> {
        val list = mutableListOf<CallForwardRule>()
        try {
            val arr = JSONArray(RuleStore.prefs(c).getString(KEY, "[]") ?: "[]")
            for (i in 0 until arr.length()) list.add(CallForwardRule.fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) {
        }
        return list
    }

    @Synchronized
    fun save(c: Context, rules: List<CallForwardRule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        RuleStore.prefs(c).edit().putString(KEY, arr.toString()).apply()
    }

    /** Number the app last set as forwarding target for [mode], or null if none. */
    fun getApplied(c: Context, mode: Int): String? =
        RuleStore.prefs(c).getString("cf_applied_$mode", null)

    fun setApplied(c: Context, mode: Int, value: String?) {
        val e = RuleStore.prefs(c).edit()
        if (value == null) e.remove("cf_applied_$mode") else e.putString("cf_applied_$mode", value)
        e.apply()
    }
}

object AnswerRuleStore {
    private const val KEY = "answer_rules"
    private const val KEY_ENABLED = "answer_enabled"

    @Synchronized
    fun load(c: Context): MutableList<AnswerRule> {
        val list = mutableListOf<AnswerRule>()
        try {
            val arr = JSONArray(RuleStore.prefs(c).getString(KEY, "[]") ?: "[]")
            for (i in 0 until arr.length()) list.add(AnswerRule.fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) {
        }
        return list
    }

    @Synchronized
    fun save(c: Context, rules: List<AnswerRule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        RuleStore.prefs(c).edit().putString(KEY, arr.toString()).apply()
    }

    fun isEnabled(c: Context): Boolean = RuleStore.prefs(c).getBoolean(KEY_ENABLED, false)

    fun setEnabled(c: Context, on: Boolean) {
        RuleStore.prefs(c).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun audioDir(c: Context): File {
        val d = File(c.filesDir, "answer_audio")
        d.mkdirs()
        return d
    }

    fun audioFile(c: Context, name: String): File = File(audioDir(c), File(name).name)

    /** Deletes an audio file when no rule references it any more. */
    fun deleteAudioIfUnused(c: Context, fileName: String) {
        if (fileName.isBlank()) return
        if (load(c).any { it.audioFile == fileName }) return
        audioFile(c, fileName).delete()
    }
}

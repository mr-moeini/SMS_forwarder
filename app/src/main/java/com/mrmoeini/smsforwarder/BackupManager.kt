package com.mrmoeini.smsforwarder

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup = one zip file: settings.json + audio/<recorded or chosen messages>. */
object BackupManager {

    private const val APP_ID = "call_and_sms_forwarder"

    fun export(context: Context, uri: Uri) {
        val json = JSONObject()
        json.put("app", APP_ID)
        json.put("version", 1)
        json.put("sms", JSONArray().also { a -> RuleStore.load(context).forEach { a.put(it.toJson()) } })
        json.put("callForward", JSONArray().also { a -> CallRuleStore.load(context).forEach { a.put(it.toJson()) } })
        json.put("callAnswer", JSONArray().also { a -> AnswerRuleStore.load(context).forEach { a.put(it.toJson()) } })
        json.put("answerEnabled", AnswerRuleStore.isEnabled(context))

        val out = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Cannot write to the chosen file")
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("settings.json"))
            zip.write(json.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val files = AnswerRuleStore.audioDir(context).listFiles() ?: emptyArray()
            for (f in files) {
                if (!f.isFile) continue
                zip.putNextEntry(ZipEntry("audio/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Replaces all settings with the backup content. Throws if the file is not a valid backup. */
    fun import(context: Context, uri: Uri) {
        var settings: String? = null
        val audio = mutableMapOf<String, ByteArray>()

        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot read the chosen file")
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    val buf = ByteArrayOutputStream()
                    zip.copyTo(buf)
                    if (name == "settings.json") {
                        settings = buf.toString("UTF-8")
                    } else if (name.startsWith("audio/")) {
                        val safe = File(name).name
                        if (safe.isNotEmpty()) audio[safe] = buf.toByteArray()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val text = settings ?: throw IllegalArgumentException("Not a backup file (settings.json missing)")
        val json = JSONObject(text)
        if (json.optString("app") != APP_ID) throw IllegalArgumentException("Not a backup of this app")

        val sms = mutableListOf<Rule>()
        json.optJSONArray("sms")?.let { a ->
            for (i in 0 until a.length()) sms.add(Rule.fromJson(a.getJSONObject(i)))
        }
        val cf = mutableListOf<CallForwardRule>()
        json.optJSONArray("callForward")?.let { a ->
            for (i in 0 until a.length()) cf.add(CallForwardRule.fromJson(a.getJSONObject(i)))
        }
        val answers = mutableListOf<AnswerRule>()
        json.optJSONArray("callAnswer")?.let { a ->
            for (i in 0 until a.length()) answers.add(AnswerRule.fromJson(a.getJSONObject(i)))
        }

        // Replace audio files.
        val dir = AnswerRuleStore.audioDir(context)
        dir.listFiles()?.forEach { it.delete() }
        for ((name, bytes) in audio) File(dir, name).writeBytes(bytes)

        RuleStore.save(context, sms)
        CallRuleStore.save(context, cf)
        AnswerRuleStore.save(context, answers)
        AnswerRuleStore.setEnabled(context, json.optBoolean("answerEnabled", false))
    }
}

package com.mrmoeini.smsforwarder

import org.json.JSONObject
import java.util.UUID

/**
 * A forwarding rule: when an SMS arrives from [sender] (a phone number or an
 * alphanumeric sender ID) and, optionally, contains [keyword], it is forwarded
 * to [destNumber].
 */
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sender: String,
    val senderName: String = "",
    val keyword: String,
    val destNumber: String,
    val destName: String,
    val enabled: Boolean = true
) {

    fun matches(from: String, body: String): Boolean {
        if (!enabled) return false
        if (!senderMatches(from)) return false
        if (keyword.isNotBlank() && !body.contains(keyword.trim(), ignoreCase = true)) return false
        return true
    }

    private fun senderMatches(from: String): Boolean {
        val filter = sender.trim()
        if (filter.isEmpty()) return false
        val filterDigits = digitsOnly(filter)
        return if (looksNumeric(filter) && filterDigits.isNotEmpty()) {
            val fromDigits = digitsOnly(from)
            // Compare the last 10 digits so +98912..., 0912... and 98912... all match.
            fromDigits.isNotEmpty() && fromDigits.takeLast(10) == filterDigits.takeLast(10)
        } else {
            from.trim().equals(filter, ignoreCase = true)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("sender", sender)
        put("senderName", senderName)
        put("keyword", keyword)
        put("destNumber", destNumber)
        put("destName", destName)
        put("enabled", enabled)
    }

    companion object {
        /** Keeps only digits, converting Persian/Arabic-Indic digits to ASCII 0-9. */
        fun digitsOnly(s: String): String = buildString {
            for (c in s) {
                val d = Character.digit(c, 10)
                if (d >= 0) append('0' + d)
            }
        }

        /** Like [digitsOnly] but keeps a '+' sign. Used for phone numbers. */
        fun toAsciiNumber(s: String): String = buildString {
            for (c in s) {
                if (c == '+') {
                    append(c)
                } else {
                    val d = Character.digit(c, 10)
                    if (d >= 0) append('0' + d)
                }
            }
        }

        /** True for things like "+98 912 123", "09121234567", "10008"; false for "BankName". */
        fun looksNumeric(s: String): Boolean {
            val t = s.trim()
            return t.isNotEmpty() && t.all { Character.digit(it, 10) >= 0 || it in "+-() " }
        }

        /** Phone numbers are normalized to ASCII digits; sender IDs (text) are kept as typed. */
        fun cleanSender(s: String): String =
            if (looksNumeric(s)) toAsciiNumber(s) else s.trim()

        fun fromJson(o: JSONObject): Rule = Rule(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            sender = o.optString("sender"),
            senderName = o.optString("senderName"),
            keyword = o.optString("keyword"),
            destNumber = o.optString("destNumber"),
            destName = o.optString("destName"),
            enabled = o.optBoolean("enabled", true)
        )
    }
}

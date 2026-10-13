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
        val isNumeric = filter.all { it.isDigit() || it in "+-() " }
        val filterDigits = digitsOnly(filter)
        return if (isNumeric && filterDigits.isNotEmpty()) {
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
        put("keyword", keyword)
        put("destNumber", destNumber)
        put("destName", destName)
        put("enabled", enabled)
    }

    companion object {
        fun digitsOnly(s: String): String = s.filter { it.isDigit() }

        fun fromJson(o: JSONObject): Rule = Rule(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            sender = o.optString("sender"),
            keyword = o.optString("keyword"),
            destNumber = o.optString("destNumber"),
            destName = o.optString("destName"),
            enabled = o.optBoolean("enabled", true)
        )
    }
}

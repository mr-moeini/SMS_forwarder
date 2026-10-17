package com.mrmoeini.smsforwarder

import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/**
 * Carrier call forwarding that is switched on at [startMin] and off at [endMin]
 * on the selected [days]. [mode]: 0 all calls, 1 busy, 2 no answer, 3 unreachable.
 */
data class CallForwardRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val destNumber: String,
    val destName: String = "",
    val mode: Int = 0,
    val startMin: Int = 8 * 60,
    val endMin: Int = 17 * 60,
    val days: Int = 127,
    val enabled: Boolean = true
) {

    private fun dayOn(dow: Int): Boolean = ((days shr (dow - 1)) and 1) == 1

    fun isActiveAt(now: Calendar): Boolean {
        if (!enabled) return false
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val dow = now.get(Calendar.DAY_OF_WEEK)
        val prev = if (dow == 1) 7 else dow - 1
        return when {
            startMin == endMin -> dayOn(dow)
            startMin < endMin -> dayOn(dow) && nowMin >= startMin && nowMin < endMin
            else -> (dayOn(dow) && nowMin >= startMin) || (dayOn(prev) && nowMin < endMin)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("destNumber", destNumber)
        put("destName", destName)
        put("mode", mode)
        put("startMin", startMin)
        put("endMin", endMin)
        put("days", days)
        put("enabled", enabled)
    }

    companion object {
        fun codeFor(mode: Int): String = when (mode) {
            1 -> "67"
            2 -> "61"
            3 -> "62"
            else -> "21"
        }

        fun enableCode(mode: Int, number: String): String = "**" + codeFor(mode) + "*" + number + "#"

        fun disableCode(mode: Int): String = "##" + codeFor(mode) + "#"

        fun fromJson(o: JSONObject): CallForwardRule = CallForwardRule(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            destNumber = o.optString("destNumber"),
            destName = o.optString("destName"),
            mode = o.optInt("mode", 0),
            startMin = o.optInt("startMin", 8 * 60),
            endMin = o.optInt("endMin", 17 * 60),
            days = o.optInt("days", 127),
            enabled = o.optBoolean("enabled", true)
        )
    }
}

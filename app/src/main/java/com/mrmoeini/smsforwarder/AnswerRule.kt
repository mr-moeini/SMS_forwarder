package com.mrmoeini.smsforwarder

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

data class Caller(val number: String, val name: String)

/**
 * Auto-answer rule. Time window ([timeFrom]..[timeTo], minutes of day, -1 = any time) and
 * date range ([dateFrom]..[dateTo], epoch millis, 0 = no limit / never expires).
 */
data class AnswerRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val allCalls: Boolean = true,
    val callers: List<Caller> = emptyList(),
    val timeFrom: Int = -1,
    val timeTo: Int = -1,
    val dateFrom: Long = 0L,
    val dateTo: Long = 0L,
    val audioFile: String = "",
    val audioName: String = "",
    val delaySec: Int = 3
) {

    fun matches(number: String, now: Calendar): Boolean {
        if (!enabled || audioFile.isBlank()) return false
        if (!allCalls) {
            val digits = Rule.digitsOnly(number)
            if (digits.isEmpty()) return false
            val hit = callers.any {
                val d = Rule.digitsOnly(it.number)
                d.isNotEmpty() && d.takeLast(10) == digits.takeLast(10)
            }
            if (!hit) return false
        }
        val nowMs = now.timeInMillis
        if (dateFrom > 0L && nowMs < dateFrom) return false
        if (dateTo > 0L && nowMs > dateTo) return false
        if (timeFrom >= 0 && timeTo >= 0) {
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            if (!TimeUtil.inWindow(nowMin, timeFrom, timeTo)) return false
        }
        return true
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("allCalls", allCalls)
        val arr = JSONArray()
        callers.forEach { arr.put(JSONObject().put("number", it.number).put("name", it.name)) }
        put("callers", arr)
        put("timeFrom", timeFrom)
        put("timeTo", timeTo)
        put("dateFrom", dateFrom)
        put("dateTo", dateTo)
        put("audioFile", audioFile)
        put("audioName", audioName)
        put("delaySec", delaySec)
    }

    companion object {
        fun fromJson(o: JSONObject): AnswerRule {
            val list = mutableListOf<Caller>()
            val arr = o.optJSONArray("callers")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    list.add(Caller(c.optString("number"), c.optString("name")))
                }
            }
            return AnswerRule(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name"),
                enabled = o.optBoolean("enabled", true),
                allCalls = o.optBoolean("allCalls", true),
                callers = list,
                timeFrom = o.optInt("timeFrom", -1),
                timeTo = o.optInt("timeTo", -1),
                dateFrom = o.optLong("dateFrom", 0L),
                dateTo = o.optLong("dateTo", 0L),
                audioFile = o.optString("audioFile"),
                audioName = o.optString("audioName"),
                delaySec = o.optInt("delaySec", 3)
            )
        }
    }
}

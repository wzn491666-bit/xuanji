package com.xuanji.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class SessionInsights(
    val workers: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
    val cacheHitRate: Int?,
    val cost: Double,
)

internal fun parseSessionInsights(response: JsonObject): SessionInsights {
    val totals = response["totals"] as? JsonObject ?: JsonObject(emptyMap())
    fun count(key: String) = totals[key]?.jsonPrimitive?.longOrNull ?: 0L
    return SessionInsights(
        workers = totals["workers"]?.jsonPrimitive?.intOrNull ?: 0,
        inputTokens = count("inputTokens"),
        outputTokens = count("outputTokens"),
        cacheReadTokens = count("cacheReadTokens"),
        cacheWriteTokens = count("cacheWriteTokens"),
        cacheHitRate = response["cacheHitRate"]?.jsonPrimitive?.intOrNull,
        cost = totals["cost"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
    )
}

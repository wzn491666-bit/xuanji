package com.xuanji.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionInsightsTest {
    @Test
    fun parsesOfficialSessionTotalsAndCacheHitRate() {
        val response = Json.parseToJsonElement("""{
            "totals":{"workers":2,"inputTokens":120000,"outputTokens":9000,"cacheReadTokens":80000,"cacheWriteTokens":20000,"cost":0.42},
            "cacheHitRate":80
        }""").jsonObject

        val insights = parseSessionInsights(response)

        assertEquals(2, insights.workers)
        assertEquals(120000L, insights.inputTokens)
        assertEquals(80, insights.cacheHitRate)
        assertEquals(20000L, insights.cacheWriteTokens)
    }

    @Test
    fun absentHitRateStaysUnknownInsteadOfZero() {
        val response = Json.parseToJsonElement("""{"totals":{"workers":0,"inputTokens":0,"outputTokens":0}}""").jsonObject
        assertEquals(null, parseSessionInsights(response).cacheHitRate)
    }
}

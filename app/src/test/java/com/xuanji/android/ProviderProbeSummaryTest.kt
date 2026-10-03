package com.xuanji.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderProbeSummaryTest {
    @Test
    fun failedProbeIsNeverReportedAsConnected() {
        val response = Json.parseToJsonElement("""{"ok":false,"error":"auth-failed"}""").jsonObject
        assertTrue(providerProbeSummary(response).startsWith("连接失败"))
    }

    @Test
    fun successfulProbeListsReturnedModels() {
        val response = Json.parseToJsonElement("""{"ok":true,"models":["a","b"]}""").jsonObject
        assertTrue(providerProbeSummary(response).contains("a, b"))
    }
}

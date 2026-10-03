package com.xuanji.android

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderPayloadTest {
    @Test
    fun providerSetupUsesTheOfficialModelObjectSchema() {
        val payload = providerSetupPayload("deepseek", "test-key", "deepseek-chat", "")

        assertEquals("deepseek-chat", payload["model"]!!.jsonObject["id"]!!.jsonPrimitive.content)
    }
}

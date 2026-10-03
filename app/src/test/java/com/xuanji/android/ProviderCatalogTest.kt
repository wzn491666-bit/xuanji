package com.xuanji.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {
    @Test
    fun catalogPreservesCredentialAndModelCapabilities() {
        val payload = Json.parseToJsonElement("""{
            "providers": [
                {"name":"deepseek","label":"DeepSeek","isDefault":true,"keyStatus":{"source":"inline"},"keyless":false,
                 "models":[{"id":"deepseek-chat","supportsVision":false,"effortSupported":true}]},
                {"name":"ollama","label":"Ollama","isDefault":false,"keyStatus":{"source":"none"},"keyless":true,
                 "models":[{"id":"qwen-vl","supportsVision":true,"effortSupported":false}]}
            ]
        }""").jsonObject

        val catalog = parseProviderCatalog(payload)

        assertEquals(listOf("deepseek", "ollama"), catalog.map { it.name })
        assertTrue(catalog.first().hasCredential)
        assertTrue(catalog.last().isAvailable)
        assertFalse(catalog.last().hasCredential)
        assertTrue(catalog.last().models.single().supportsVision)
        assertFalse(catalog.last().models.single().effortSupported)
    }

    @Test
    fun storedProviderCanResumeWithoutCopyingAnApiKey() {
        val selected = Settings("ollama", "", "qwen-vl", useStoredCredential = true)
        assertTrue(selected.isComplete)
        assertEquals(ProviderSyncAction.SELECT_STORED, providerSyncAction(selected))
        assertEquals(ProviderSyncAction.CONFIGURE, providerSyncAction(Settings("deepseek", "new-key", "deepseek-chat")))
        assertFalse(Settings("deepseek", "", "").isComplete)
    }

    @Test
    fun switchingToModelWithoutEffortChannelResetsEffortToAuto() {
        val current = Settings("deepseek", "old-key", "deepseek-chat", reasoningEffort = "max")
        val target = ProviderProfile("ollama", "Ollama", false, false, true,
            listOf(ProviderModel("qwen-vl", supportsVision = true, effortSupported = false)))

        val next = settingsForProviderActivation(current, target, "qwen-vl")

        assertEquals("auto", next.reasoningEffort)
        assertEquals("", next.apiKey)
        assertTrue(next.useStoredCredential)
    }
}

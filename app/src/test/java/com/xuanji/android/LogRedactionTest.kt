package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Test

class LogRedactionTest {
    @Test
    fun knownSecretsNeverReachDisplayedServiceLog() {
        val line = "Authorization: Bearer server-token-123, apiKey=provider-secret-456"
        assertEquals(
            "Authorization: Bearer [redacted], apiKey=[redacted]",
            redactLogLine(line, listOf("server-token-123", "provider-secret-456")),
        )
    }

    @Test
    fun emptyOrShortValuesDoNotEraseOrdinaryLogText() {
        assertEquals("port 3100 ready", redactLogLine("port 3100 ready", listOf("", "3100")))
    }
}

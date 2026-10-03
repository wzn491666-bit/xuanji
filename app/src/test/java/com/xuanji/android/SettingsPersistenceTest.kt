package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SettingsPersistenceTest {
    @Test
    fun storageFailureIsReportedWithItsOriginalCause() {
        val settings = Settings("deepseek", "test-key", "deepseek-chat")
        val failure = IllegalStateException("keystore unavailable")
        var attempts = 0

        val outcome = persistSettingsSafely(settings) {
            attempts++
            throw failure
        }

        assertEquals(1, attempts)
        assertSame(failure, outcome)
    }

    @Test
    fun successfulStorageHasNoFailure() {
        val settings = Settings("deepseek", "test-key", "deepseek-chat")
        var saved: Settings? = null

        val outcome = persistSettingsSafely(settings) { saved = it }

        assertSame(settings, saved)
        assertEquals(null, outcome)
    }
}

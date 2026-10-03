package com.xuanji.android

import java.security.InvalidKeyException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KeyRecoveryTest {
    @Test
    fun invalidatedKeyIsRebuiltAndEncryptionRetriedOnce() {
        var attempts = 0
        var resets = 0

        val result = retryOnceAfterInvalidatedKey(
            operation = {
                attempts++
                if (attempts == 1) throw InvalidKeyException("invalidated")
                "encrypted"
            },
            resetInvalidatedKey = { resets++ },
            isInvalidated = { it is InvalidKeyException },
        )

        assertEquals("encrypted", result)
        assertEquals(2, attempts)
        assertEquals(1, resets)
    }

    @Test
    fun unrelatedFailureDoesNotDeleteKey() {
        var resets = 0

        assertThrows(IllegalStateException::class.java) {
            retryOnceAfterInvalidatedKey(
                operation = { throw IllegalStateException("storage full") },
                resetInvalidatedKey = { resets++ },
                isInvalidated = { it is InvalidKeyException },
            )
        }
        assertEquals(0, resets)
    }

    @Test
    fun secondFailureIsReturnedWithoutAnotherReset() {
        var attempts = 0
        var resets = 0

        assertThrows(InvalidKeyException::class.java) {
            retryOnceAfterInvalidatedKey(
                operation = {
                    attempts++
                    throw InvalidKeyException("still invalid")
                },
                resetInvalidatedKey = { resets++ },
                isInvalidated = { it is InvalidKeyException },
            )
        }
        assertEquals(2, attempts)
        assertEquals(1, resets)
    }
}

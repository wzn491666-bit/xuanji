package com.xuanji.android

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityTargetTest {
    private val target = AccessibilityTarget("com.example.editor", 12)

    @Test
    fun approvalWaitsForTheOriginalWindowToReturn() = runBlocking {
        var reads = 0
        assertTrue(awaitAccessibilityTarget(target, 1_000) {
            if (++reads == 1) AccessibilityTarget("com.xuanji.android", 99) else target
        })
        assertTrue(reads >= 2)
    }

    @Test
    fun anotherWindowInTheSamePackageIsRejected() = runBlocking {
        assertFalse(awaitAccessibilityTarget(target, 20) { target.copy(windowId = 13) })
    }

    @Test
    fun stoppingTheRequestCancelsTargetRestoration() = runBlocking {
        var entered = false
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { awaitAccessibilityTarget(target, 1_000) { entered = true; awaitCancellation() } }
        assertTrue(entered)
        waiting.cancel()
        waiting.join()
        assertTrue(waiting.isCancelled)
    }
}

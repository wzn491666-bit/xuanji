package com.xuanji.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class BlockingOperationTest {
    @Test fun cancellationClosesResourceAndWaitsForInflightOperation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val released = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val cleanups = AtomicInteger()
        val call = launch {
            runOwnedBlocking(cleanup = { cleanups.incrementAndGet(); released.countDown() }) {
                entered.complete(Unit)
                check(released.await(5, TimeUnit.SECONDS))
                finished.set(true)
            }
        }
        withTimeout(2_000) { entered.await() }
        withTimeout(2_000) { call.cancelAndJoin() }
        assertTrue(finished.get())
        assertEquals(1, cleanups.get())
    }

    @Test fun successfulCallAlsoReleasesItsResourceExactlyOnce() = runBlocking {
        var cleanups = 0
        assertEquals("result", runOwnedBlocking({ cleanups++ }) { "result" })
        assertEquals(1, cleanups)
    }
}

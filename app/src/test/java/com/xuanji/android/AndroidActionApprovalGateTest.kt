package com.xuanji.android

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidActionApprovalGateTest {
    @Test
    fun onlyTheMatchingRequestCanBeApproved() = runBlocking {
        val gate = AndroidActionApprovalGate()
        val result = async { gate.request("写文件", "/sdcard/note.txt", 2_000) }
        val pending = gate.pending.filterNotNull().first()

        gate.respond("wrong-id", true)
        assertFalse(result.isCompleted)
        gate.respond(pending.id, true)

        assertTrue(result.await())
        assertNull(gate.pending.value)
    }

    @Test
    fun timeoutAndServiceShutdownDenyTheAction() = runBlocking {
        val gate = AndroidActionApprovalGate()
        assertFalse(gate.request("点按", "(12, 24)", 20))

        val result = async { gate.request("点按", "(12, 24)", 2_000) }
        gate.pending.filterNotNull().first()
        gate.cancelPending()

        assertFalse(result.await())
        assertNull(gate.pending.value)
    }

    @Test
    fun stoppedServiceRejectsPendingAndFutureActions() = runBlocking {
        val gate = AndroidActionApprovalGate()
        val result = async { gate.request("写文件", "/sdcard/note.txt", 2_000) }
        val pending = gate.pending.filterNotNull().first()
        gate.stop()
        gate.respond(pending.id, true)

        assertFalse(result.await())
        assertFalse(gate.request("点按", "(12, 24)", 20))
        assertNull(gate.pending.value)
    }

    @Test
    fun queuedRequestExpiresWithoutReplacingTheVisibleApproval() = runBlocking {
        val gate = AndroidActionApprovalGate()
        val first = async { gate.request("first", "first", 2_000) }
        val pending = gate.pending.filterNotNull().first()
        val queued = async { gate.request("second", "second", 20) }
        delay(60)

        assertTrue(queued.isCompleted)
        assertFalse(queued.await())
        assertTrue(gate.pending.value?.id == pending.id)
        gate.respond(pending.id, true)
        assertTrue(first.await())
    }

    @Test
    fun cancellingRequestClearsTheVisibleApproval() = runBlocking {
        val gate = AndroidActionApprovalGate()
        val request = async { gate.request("first", "first", 2_000) }
        gate.pending.filterNotNull().first()
        request.cancel()
        request.join()
        assertNull(gate.pending.value)
    }

    @Test
    fun restartingServiceDoesNotReviveAnOldQueuedAction() = runBlocking {
        val gate = AndroidActionApprovalGate()
        val first = async { gate.request("first", "first", 2_000) }
        gate.pending.filterNotNull().first()
        val queued = async { gate.request("second", "second", 2_000) }
        yield()
        gate.stop()
        gate.start()

        assertFalse(first.await())
        assertFalse(queued.await())
        assertNull(gate.pending.value)
    }
}

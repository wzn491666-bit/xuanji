package com.xuanji.android

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLifecycleTest {
    @Test
    fun recoveryStatusStartsRunningWithoutAUserEcho() {
        assertTrue(runningAfterSessionEvent(false, SessionEvent.parse(
            """{"type":"status","data":{"status":"running"}}""",
        )))
    }

    @Test
    fun historicalLifecycleEventsCannotOverwriteTheCurrentSnapshot() {
        val historicalDone = SessionEvent.parse("""{"seq":5999,"type":"done"}""")
        assertTrue(runningAfterSessionEvent(true, historicalDone, 6000))
        val historicalUser = SessionEvent.parse("""{"seq":5999,"type":"user"}""")
        assertFalse(runningAfterSessionEvent(false, historicalUser, 6000))
        val currentDone = SessionEvent.parse("""{"seq":6001,"type":"done"}""")
        assertFalse(runningAfterSessionEvent(true, currentDone, 6000))
    }

    @Test
    fun sessionSnapshotUsesTheOfficialStatusField() {
        assertTrue(sessionSnapshotRunning(kotlinx.serialization.json.Json.parseToJsonElement(
            """{"status":"running","lastSeq":6000}""",
        ).jsonObject))
        assertFalse(sessionSnapshotRunning(kotlinx.serialization.json.Json.parseToJsonElement(
            """{"status":"completed","lastSeq":6001}""",
        ).jsonObject))
    }

    @Test
    fun reconnectDelayIsBoundedAndPermanentFailuresRequireRecovery() {
        assertEquals(2_000L, streamReconnectDelayMillis(0))
        assertEquals(4_000L, streamReconnectDelayMillis(1))
        assertEquals(30_000L, streamReconnectDelayMillis(100))
        assertTrue(streamFailureRequiresRecovery(TianshuHttpException(401, "Unauthorized")))
        assertTrue(streamFailureRequiresRecovery(TianshuHttpException(404, "Missing session")))
        assertFalse(streamFailureRequiresRecovery(TianshuHttpException(503, "Starting")))
    }

    @Test
    fun modelIterationAndFinalAnswerDoNotSettleTheRuntime() {
        var running = runningAfterSessionEvent(false, SessionEvent.parse("""{"type":"user","data":{"text":"work"}}"""))
        running = runningAfterSessionEvent(running, SessionEvent.parse("""{"type":"turn_complete","data":{"isFinal":false}}"""))
        assertTrue(running)
        running = runningAfterSessionEvent(running, SessionEvent.parse("""{"type":"turn_complete","data":{"isFinal":true}}"""))
        assertTrue(running)
        running = runningAfterSessionEvent(running, SessionEvent.parse("""{"type":"status","data":{"status":"aborted"}}"""))
        assertFalse(running)
        assertFalse(runningAfterSessionEvent(running, SessionEvent.parse("""{"type":"done"}""")))
    }

    @Test
    fun requestResultsPropagateCancellation() {
        val cancelled = CancellationException("session changed")
        try {
            requestResult<Unit> { throw cancelled }
            throw AssertionError("Cancellation must leave the retry loop")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }

    @Test
    fun requestResultsKeepOperationalErrorsAvailableForUiFeedback() {
        val failure = IllegalStateException("HTTP 503")
        assertSame(failure, requestResult<Unit> { throw failure }.exceptionOrNull())
    }

    @Test
    fun concurrentApprovalsRemainQueuedUntilTheirOwnResolution() {
        val first = Approval("tool-1", "read_file", "{\"path\":\"/outside/one\"}")
        val second = Approval("tool-2", "read_file", "{\"path\":\"/outside/two\"}")
        var queue = pendingApprovalsAfterEvent(emptyList(), SessionEvent.parse(
            """{"type":"approval_required","data":{"requestId":"tool-1","toolName":"read_file","input":{"path":"/outside/one"}}}""",
        ))
        queue = pendingApprovalsAfterEvent(queue, SessionEvent.parse(
            """{"type":"approval_required","data":{"requestId":"tool-2","toolName":"read_file","input":{"path":"/outside/two"}}}""",
        ))
        assertEquals(listOf(first, second), queue)
        queue = pendingApprovalsAfterEvent(queue, SessionEvent.parse(
            """{"type":"approval_resolved","data":{"requestId":"tool-1"}}""",
        ))
        assertEquals(listOf(second), queue)
        assertTrue(pendingApprovalsAfterEvent(queue, SessionEvent.parse("""{"type":"done"}""")).isEmpty())
    }

    @Test
    fun approvalShowsAllArgumentsIncludingTheEndOfALongCommand() {
        val command = "echo " + "x".repeat(600) + "; sensitive-command"
        val input = kotlinx.serialization.json.buildJsonObject {
            put("command", kotlinx.serialization.json.JsonPrimitive(command))
            put("cwd", kotlinx.serialization.json.JsonPrimitive("/outside"))
        }
        val event = SessionEvent(1, "approval_required", kotlinx.serialization.json.buildJsonObject {
            put("requestId", kotlinx.serialization.json.JsonPrimitive("long-command"))
            put("input", input)
        })
        assertEquals(input.toString(), pendingApprovalsAfterEvent(emptyList(), event).single().input)
    }

    @Test
    fun toolProgressAccumulatesUntilTheTerminalResultReplacesIt() {
        var tool = ChatItem.Tool("tool-1", "bash", "build")
        tool = toolAfterResult(tool, SessionEvent.parse(
            """{"type":"tool_result","data":{"id":"tool-1","partial":true,"result":"first "}}""",
        ))
        assertTrue(tool.running)
        tool = toolAfterResult(tool, SessionEvent.parse(
            """{"type":"tool_result","data":{"id":"tool-1","partial":true,"result":"second"}}""",
        ))
        assertEquals("first second", tool.result)
        tool = toolAfterResult(tool, SessionEvent.parse(
            """{"type":"tool_result","data":{"id":"tool-1","result":"final result","isError":false}}""",
        ))
        assertFalse(tool.running)
        assertEquals("final result", tool.result)
    }
}

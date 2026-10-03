package com.xuanji.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitModelTest {
    @Test fun failedDeliveryPreservesReportedGateWithoutClaimingOperationSuccess() {
        val result = CockpitReducer.onEvent(Cockpit(), SessionEvent(1, "tool_result", Json.parseToJsonElement("""
            {"name":"deliver_task","isError":true,"result":"Delivery Gate: GREEN\nCannot commit: evidence firewall"}
        """).jsonObject))
        assertEquals("GREEN", result.deliveryGate)
        assertTrue(result.deliveryFailed)
        assertEquals(false, CockpitReducer.onEvent(result, SessionEvent(2, "user", Json.parseToJsonElement("{}").jsonObject)).deliveryFailed)
    }

    @Test fun unrelatedGateWordsDoNotCreateADeliveryPass() {
        val result = CockpitReducer.onEvent(Cockpit(), SessionEvent(1, "tool_result", Json.parseToJsonElement("""
            {"name":"deliver_task","result":"File: GREEN.md"}
        """).jsonObject))
        assertNull(result.deliveryGate)
    }
    @Test fun missingReasoningUsageIsUnknownRatherThanMeasuredZero() {
        assertNull(TokenUsage().reasoningTokens)
    }
    @Test
    fun authoritativeEmptySnapshotClearsPreviousRunResults() {
        val previous = Cockpit(
            deliveryGate = "GREEN",
            verificationCommands = listOf(VerificationCommand("test previous", "passed")),
            replay = listOf(ReplayEntry("previous tool", "tool")),
            contextUsagePercent = 80,
        )
        val snapshot = Json.parseToJsonElement(
            """{"verification":{"deliveryStatus":"unverified","runs":[]},"trace":{"events":[]},"context":null}""",
        ).jsonObject

        val cockpit = CockpitReducer.mergeSnapshot(previous, snapshot)

        assertEquals(null, cockpit.deliveryGate)
        assertTrue(cockpit.verificationCommands.isEmpty())
        assertTrue(cockpit.replay.isEmpty())
        assertEquals(null, cockpit.contextUsagePercent)
    }

    @Test
    fun partialSnapshotPreservesFieldsItDoesNotProvide() {
        val previous = Cockpit(
            deliveryGate = "GREEN",
            verificationCommands = listOf(VerificationCommand("test previous", "passed")),
            replay = listOf(ReplayEntry("previous tool", "tool")),
            contextUsagePercent = 80,
        )
        assertEquals(previous, CockpitReducer.mergeSnapshot(previous, Json.parseToJsonElement("{}").jsonObject))
    }

    @Test
    fun newRunClearsThePreviousVerificationAndTraceDisplay() {
        val previous = Cockpit(
            deliveryGate = "GREEN",
            verificationCommands = listOf(VerificationCommand("test previous", "passed")),
            replay = listOf(ReplayEntry("previous tool", "tool")),
        )
        val cockpit = CockpitReducer.onEvent(previous, SessionEvent.parse(
            """{"seq":10,"type":"status","data":{"status":"running"}}""",
        ))
        assertEquals(null, cockpit.deliveryGate)
        assertTrue(cockpit.verificationCommands.isEmpty())
        assertTrue(cockpit.replay.isEmpty())
    }

    @Test
    fun idleSnapshotAcceptsTheOfficialNullContext() {
        val snapshot = Json.parseToJsonElement(
            """{"intent":null,"context":null,"verification":{"deliveryStatus":"unverified","runs":[]},"model":{"inputTokens":100,"outputTokens":10,"cacheReadTokens":0},"trace":{"events":[]}}""",
        ).jsonObject

        val cockpit = CockpitReducer.mergeSnapshot(Cockpit(), snapshot)

        assertEquals(100L, cockpit.usage.inputTokens)
        assertEquals(null, cockpit.contextUsagePercent)
    }

    @Test
    fun officialHookResultShowsEachScriptAndItsOutcome() {
        val event = SessionEvent.parse(
            """{"seq":4,"type":"hook_result","data":{"event":"postTool","results":[{"script":"./check.sh","ok":true,"output":"clean"},{"script":"./lint.sh","ok":false,"output":"exit 1"}]}}""",
        )

        val cockpit = CockpitReducer.onEvent(Cockpit(), event)

        assertEquals(listOf("./check.sh", "./lint.sh"), cockpit.hooks.map { it.name })
        assertEquals(listOf("passed", "failed"), cockpit.hooks.map { it.status })
        assertEquals(listOf("clean", "exit 1"), cockpit.hooks.map { it.detail })
    }

    @Test
    fun officialSnapshotDoesNotPresentDoomLoopRiskAsConvergence() {
        val snapshot = Json.parseToJsonElement(
            """{"safety":{"doomLoopLevel":"warn"},"verification":{"deliveryStatus":"unverified"}}""",
        ).jsonObject

        val cockpit = CockpitReducer.mergeSnapshot(Cockpit(), snapshot)

        assertEquals("unknown", cockpit.convergence.status)
    }

    @Test
    fun delegationWithoutWaveIdDoesNotInventAWave() {
        val event = SessionEvent.parse(
            """{"seq":6,"type":"delegation","data":{"workerId":"worker-1","status":"running","objective":"检查构建"}}""",
        )

        val cockpit = CockpitReducer.onEvent(Cockpit(), event)

        assertTrue(cockpit.waves.isEmpty())
        assertEquals("worker-1", cockpit.replay.single().label)
    }

    @Test
    fun officialTurnCompleteReportsUsage() {
        val base = Cockpit()
        val measured = CockpitReducer.onEvent(
            base,
            SessionEvent.parse("""{"seq":2,"type":"turn_complete","data":{"usage":{"input_tokens":1200,"output_tokens":340,"cache_read_input_tokens":800,"reasoning_tokens":90}}}"""),
        )

        assertEquals(1200L, measured.usage.inputTokens)
        assertEquals(340L, measured.usage.outputTokens)
        assertEquals(800L, measured.usage.cacheReadTokens)
        assertEquals(90L, measured.usage.reasoningTokens)
    }

    @Test
    fun officialErrorIsClassifiedWithoutInventingAWave() {
        val failure = CockpitReducer.onEvent(
            Cockpit(),
            SessionEvent.parse("""{"seq":1,"type":"error","data":{"error":"ETIMEDOUT: network request failed"}}"""),
        )
        val unsupported = CockpitReducer.onEvent(
            failure,
            SessionEvent.parse("""{"seq":2,"type":"agent_wave","data":{"wave":2,"active":3,"completed":1,"failed":1}}"""),
        )

        assertEquals("network", unsupported.failures.last().category)
        assertTrue(unsupported.waves.isEmpty())
    }

    @Test
    fun officialCockpitSnapshotMapsVerificationAndModelUsage() {
        val snapshot = Json.parseToJsonElement(
            """{"intent":"修复登录流程","verification":{"deliveryStatus":"verified","runs":[{"tool":"./gradlew test","status":"passed","summary":"41 tests"}]},"model":{"inputTokens":1000,"outputTokens":200,"cacheReadTokens":700},"context":{"estimatedTokens":400,"maxTokens":2000},"safety":{"doomLoopLevel":"none"}}""",
        ).jsonObject

        val cockpit = CockpitReducer.mergeSnapshot(Cockpit(), snapshot)

        assertEquals("修复登录流程", cockpit.goal)
        assertEquals("GREEN", cockpit.deliveryGate)
        assertEquals("./gradlew test", cockpit.verificationCommands.single().command)
        assertEquals(1000L, cockpit.usage.inputTokens)
        assertEquals(200L, cockpit.usage.outputTokens)
        assertEquals(700L, cockpit.usage.cacheReadTokens)
    }

    @Test
    fun contextWindowSnapshotDoesNotOverwriteConvergenceScore() {
        val snapshot = Json.parseToJsonElement(
            """{"context":{"estimatedTokens":1600,"maxTokens":2000},"safety":{"doomLoopLevel":"none"}}""",
        ).jsonObject
        val initial = Cockpit(convergence = ConvergenceState(status = "watching", score = 24))

        val cockpit = CockpitReducer.mergeSnapshot(initial, snapshot)

        assertEquals(24, cockpit.convergence.score)
        assertEquals(80, cockpit.contextUsagePercent)
    }

    @Test
    fun contextBudgetEventDoesNotOverwriteConvergenceScore() {
        val initial = Cockpit(convergence = ConvergenceState(status = "watching", score = 42))

        val cockpit = CockpitReducer.onEvent(
            initial,
            SessionEvent.parse("""{"seq":3,"type":"context_budget","data":{"inputTokens":300,"windowTokens":1000}}"""),
        )

        assertEquals(42, cockpit.convergence.score)
        assertEquals(30, cockpit.contextUsagePercent)
    }
}

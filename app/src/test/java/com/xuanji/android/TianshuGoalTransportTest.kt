package com.xuanji.android

import com.sun.net.httpserver.HttpServer
import com.xuanji.android.ui.chat.NativeSlashRequest
import com.xuanji.android.ui.chat.PaletteAction
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class TianshuGoalTransportTest {
    @Test fun newGoalInitializesAgentAndProducesARealKickoff() = runBlocking {
        withServer(null) { client, calls ->
            val result = executeGoalCommand(client, "s", NativeSlashRequest(PaletteAction.GOAL, "/goal", "目标 --max 10 --budget 2 --criteria '[\"通过测试\"]'"))
            assertEquals(listOf("GET /sessions/s/goal", "GET /sessions/s/rewind-points", "POST /sessions/s/goal"), calls.map { it.first })
            val body = calls.last().second
            assertEquals(10, body["maxIterations"]?.jsonPrimitive?.int)
            assertEquals(120000L, body["wallClockMs"]?.jsonPrimitive?.long)
            assertEquals("通过测试", body["successCriteria"]?.jsonArray?.single()?.jsonPrimitive?.content)
            assertEquals(goalKickoff("目标"), result.kickoff)
            assertEquals("/goal 目标", result.display)
            // The returned driver must enter the official prompt route with its own idempotency key.
            val snapshot = client.sendPrompt("s", result.kickoff!!, "goal-prompt-1")
            assertEquals("running", snapshot["status"]?.jsonPrimitive?.content)
            assertEquals("goal-prompt-1", calls.last().second["requestId"]?.jsonPrimitive?.content)
        }
    }

    @Test fun existingGoalCannotBeSilentlyReplaced() = runBlocking {
        withServer("active") { client, calls ->
            assertNotNull(runCatching { executeGoalCommand(client, "s", NativeSlashRequest(PaletteAction.GOAL, "/goal", "覆盖目标")) }.exceptionOrNull())
            assertEquals(listOf("GET /sessions/s/goal"), calls.map { it.first })
        }
    }

    @Test fun resumeRetainsIdentityAndProducesContinuationPrompt() = runBlocking {
        withServer("paused") { client, calls ->
            val result = executeGoalCommand(client, "s", NativeSlashRequest(PaletteAction.GOAL_RESUME, "/goal-resume"))
            assertEquals("g", result.output.goal?.id)
            assertEquals("[GOAL RESUME] 继续执行目标: 目标", result.kickoff)
            assertEquals("POST /sessions/s/goal/resume", calls.last().first)
            assertFalse(calls.any { it.first == "POST /sessions/s/goal" })
        }
    }

    @Test fun pauseStopsContinuationWithoutIssuingAbortOrAnotherPrompt() = runBlocking {
        withServer("active") { client, calls ->
            val result = executeGoalCommand(client, "s", NativeSlashRequest(PaletteAction.GOAL_PAUSE, "/goal-pause"))
            assertEquals("paused", result.output.goal?.status)
            assertNull(result.kickoff)
            assertEquals(listOf("GET /sessions/s/goal", "POST /sessions/s/goal/pause"), calls.map { it.first })
        }
    }

    @Test fun contradictoryReceiptIsNotReportedAsSuccess() = runBlocking {
        withServer("active", badReceipt = true) { client, _ ->
            assertNotNull(runCatching { client.controlSessionGoal("s", "pause") }.exceptionOrNull())
        }
    }

    private suspend fun withServer(initial: String?, badReceipt: Boolean = false,
        block: suspend (TianshuClient, CopyOnWriteArrayList<Pair<String, JsonObject>>) -> Unit) {
        var status = initial
        val calls = CopyOnWriteArrayList<Pair<String, JsonObject>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val text = exchange.requestBody.bufferedReader().readText()
            val body = if (text.isBlank()) buildJsonObject { } else Json.parseToJsonElement(text).jsonObject
            val path = exchange.requestURI.path
            calls += "${exchange.requestMethod} $path" to body
            val noGoal = exchange.requestMethod == "GET" && path.endsWith("/goal") && status == null
            if (exchange.requestMethod == "POST" && !badReceipt) status = when {
                path.endsWith("/pause") -> "paused"
                path.endsWith("/cancel") -> "complete"
                else -> "active"
            }
            val response = when {
                noGoal -> """{"error":"No active goal for this session"}"""
                path.endsWith("/rewind-points") -> """{"points":[]}"""
                path.endsWith("/prompt") -> """{"id":"s","status":"running","lastEventSeq":1,"receipt":{"requestId":"goal-prompt-1","runId":"run-1","attemptId":"attempt-1","fingerprint":"goal-request","state":"running"}}"""
                else -> """{"goalId":"g","goal":"目标","status":"$status","iteration":2,"maxIterations":10,"wallClockElapsedMs":1000,"wallClockBudgetMs":120000,"successCriteria":["通过测试"]}"""
            }.toByteArray()
            exchange.sendResponseHeaders(if (noGoal) 404 else 200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try { block(TianshuClient(base = "http://127.0.0.1:${server.address.port}", bearerToken = { "test" }), calls) }
        finally { server.stop(0) }
    }
}

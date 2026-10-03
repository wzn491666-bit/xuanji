package com.xuanji.android

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class SessionCommandTransportTest {
    @Test fun sessionActionsUseOfficialRoutesAndBodies() = runBlocking {
        withServer({ path, _ -> when {
            path.endsWith("/compact") -> """{"changed":false}"""
            path.endsWith("/fork") -> """{"session":{"id":"branch","forkedFromId":"s"}}"""
            path.endsWith("/handoff") -> """{"id":"s","status":"running","lastSeq":7}"""
            path.endsWith("/team-checkpoints") -> """{"checkpoints":[{"groupId":"team-1","wave":1,"totalWaves":3,"timestamp":1000}]}"""
            path.endsWith("/team-resume") -> """{"resumed":false,"message":"All tasks completed"}"""
            else -> """{"id":"s","name":"qa","enabled":false}"""
        } }) { client, calls ->
            assertFalse(client.compactSession("s"))
            assertEquals("branch", client.forkSession("s", "新分支"))
            assertEquals("running", client.requestHandoff("s", "保留决策")["status"]?.jsonPrimitive?.content)
            assertEquals("team-1", client.teamCheckpoints("s").single().groupId)
            assertFalse(client.resumeTeam("s", "team-1"))
            client.setSkillEnabled("s", "qa", false)
            assertEquals(listOf("compact", "fork", "handoff", "team-checkpoints", "team-resume", "skills"), calls.map { it.path.substringAfterLast('/') })
            assertEquals("{}", calls[0].body.toString())
            assertEquals("""{"destination":"local","source":"header","title":"新分支"}""", calls[1].body.toString())
            assertEquals("""{"note":"保留决策"}""", calls[2].body.toString())
            assertEquals("GET", calls[3].method)
            assertEquals("""{"groupId":"team-1"}""", calls[4].body.toString())
            assertEquals("""{"name":"qa","enabled":false}""", calls[5].body.toString())
        }
    }

    @Test fun queueAndSteerRetainTheirDistinctRoutesAndReceipts() = runBlocking {
        withServer({ path, _ -> when {
            path.endsWith("/xuanji/queue") -> """{"id":"s","seq":9,"durable":false,"lanes":[{"id":"lane-1","text":"后续检查","status":"queued","ts":1000}]}"""
            path.endsWith("/queue/retract") -> """{"ok":true}"""
            path.endsWith("/steer") -> """{"queued":true}"""
            else -> """{"queued":true,"laneId":"lane-1"}"""
        } }) { client, calls ->
            assertEquals("lane-1", client.queueMessage("s", "后续检查"))
            assertEquals("queued", client.sessionQueue("s").entries.single().status)
            client.steerSession("s", text = "优先看测试")
            client.steerSession("s", laneId = "lane-1")
            client.retractQueuedMessage("s", "lane-1")
            assertEquals("""{"text":"后续检查"}""", calls[0].body.toString())
            assertEquals("GET", calls[1].method)
            assertEquals("""{"text":"优先看测试"}""", calls[2].body.toString())
            assertEquals("""{"laneId":"lane-1"}""", calls[3].body.toString())
            assertEquals("/sessions/s/queue/retract", calls[4].path)
        }
    }

    @Test fun missingOrContradictoryAcknowledgementsAreRejected() = runBlocking {
        withServer({ _, _ -> """{"ok":true,"queued":false,"session":{"id":"branch","forkedFromId":"other"},"id":"other","name":"qa","enabled":true}""" }) { client, _ ->
            assertTrue(runCatching { client.compactSession("s") }.isFailure)
            assertTrue(runCatching { client.forkSession("s") }.isFailure)
            assertTrue(runCatching { client.requestHandoff("s") }.isFailure)
            assertTrue(runCatching { client.resumeTeam("s", "team-1") }.isFailure)
            assertTrue(runCatching { client.queueMessage("s", "followup") }.isFailure)
            assertTrue(runCatching { client.steerSession("s", text = "guide") }.isFailure)
            assertTrue(runCatching { client.setSkillEnabled("s", "qa", false) }.isFailure)
            assertTrue(runCatching { client.sessionQueue("s") }.isFailure)
        }
    }

    @Test fun idleResponseCannotFallBackToPromptOrRetry() = runBlocking {
        withServer({ _, _ -> """{"error":"Session is not running","code":"idle"}""" }, status = 409) { client, calls ->
            val error = runCatching { client.queueMessage("s", "followup") }.exceptionOrNull()
            assertTrue(error is TianshuHttpException)
            assertEquals("idle", (error as TianshuHttpException).code)
            assertEquals(listOf("/sessions/s/queue"), calls.map { it.path })
        }
    }

    private data class RecordedCall(val method: String, val path: String, val body: JsonObject)

    private suspend fun withServer(response: (String, JsonObject) -> String, status: Int = 200,
        block: suspend (TianshuClient, CopyOnWriteArrayList<RecordedCall>) -> Unit) {
        val calls = CopyOnWriteArrayList<RecordedCall>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val text = exchange.requestBody.bufferedReader().readText()
            val body = if (text.isBlank()) buildJsonObject { } else Json.parseToJsonElement(text).jsonObject
            val path = exchange.requestURI.path
            calls += RecordedCall(exchange.requestMethod, path, body)
            val bytes = response(path, body).toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block(TianshuClient("http://127.0.0.1:${server.address.port}", bearerToken = { "test" }), calls) }
        finally { server.stop(0) }
    }
}

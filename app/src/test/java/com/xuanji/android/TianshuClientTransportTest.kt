package com.xuanji.android

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TianshuClientTransportTest {
    @Test
    fun searchToolIsRegisteredWithReadCapability() = runBlocking {
        withServer { server ->
            var payload: JsonObject? = null
            server.createContext("/") { exchange ->
                payload = Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
                val bytes = "{\"ok\":true}".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            client(server).registerAndroidMcp("http://127.0.0.1:9000/mcp", "test-bridge")
            val tools = payload!!["policy"]!!.jsonObject["tools"]!!.jsonObject
            assertEquals("read", tools["android_web_search"]?.jsonObject?.get("capability")?.jsonPrimitive?.contentOrNull)
            listOf("android_browser_tabs", "android_browser_open", "android_browser_snapshot").forEach {
                assertEquals("read", tools[it]?.jsonObject?.get("capability")?.jsonPrimitive?.contentOrNull)
            }
            assertEquals("execute", tools["android_browser_action"]?.jsonObject?.get("capability")?.jsonPrimitive?.contentOrNull)
        }
    }
    @Test
    fun serverCommandsUseOfficialPayloadsAndRequireServerAcknowledgement() = runBlocking {
        withServer { server ->
            val requests = CopyOnWriteArrayList<Pair<String, JsonObject>>()
            server.createContext("/") { exchange ->
                val path = exchange.requestURI.path
                val payload = Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
                requests += path to payload
                val response = when {
                    path.endsWith("/domain") -> "{\"id\":\"session-1\",\"domain\":\"tianshu\"}"
                    path.endsWith("/plan-mode") -> "{\"id\":\"session-1\",\"planMode\":\"planning\"}"
                    path.endsWith("/ask-mode") -> "{\"id\":\"session-1\",\"askMode\":\"asking\"}"
                    path.endsWith("/review-gate") -> "{\"id\":\"session-1\",\"mode\":\"off\"}"
                    else -> "{\"id\":\"session-1\",\"promoted\":false}"
                }.toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            server.start()
            val client = client(server)
            client.setSessionDomain("session-1", "tianshu")
            client.setSessionMode("session-1", true, "planning")
            client.setSessionMode("session-1", false, "asking")
            client.setReviewGate("session-1", "off")
            assertFalse(client.skipZen("session-1"))
            assertEquals(listOf("domain", "plan-mode", "ask-mode", "review-gate", "zen"), requests.map { it.first.substringAfterLast('/') })
            assertEquals(listOf("{\"key\":\"tianshu\"}", "{\"state\":\"planning\"}", "{\"state\":\"asking\"}", "{\"mode\":\"off\"}", "{\"action\":\"skip\"}"), requests.map { it.second.toString() })
            assertNotNull(runCatching { client.setSessionDomain("session-1", "pojun") }.exceptionOrNull())
        }
    }

    @Test
    fun skillLoadErrorsAndDisabledEntriesSurviveTransport() = runBlocking {
        withServer { server ->
            server.createContext("/") { exchange ->
                val bytes = """{"skills":[{"name":"qa","description":"QA","source":"rivet","enabled":false}],"loadErrors":["broken skill frontmatter"]}""".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val result = client(server).skillCatalog("session-1")
            assertFalse(result.skills.single().enabled)
            assertEquals(listOf("broken skill frontmatter"), result.loadErrors)
        }
    }
    @Test
    fun malformedSkillsResponseCannotMasqueradeAsAnEmptyList() = runBlocking {
        withServer { server ->
            server.createContext("/") { exchange ->
                val bytes = "{\"ok\":true}".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            assertNotNull(runCatching { client(server).listSkills("session-1") }.exceptionOrNull())
        }
    }
    @Test
    fun imageSubmissionUsesTheLiveVisionBridgeAndDoesNotSendWhenUnavailable() = runBlocking {
        withServer { server ->
            val requests = CopyOnWriteArrayList<String>()
            var active = false
            server.createContext("/") { exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI.path}"
                val response = if (exchange.requestURI.path.endsWith("vision-bridge")) "{\"active\":$active,\"source\":\"same-provider\",\"detail\":\"test bridge\"}" else """{"id":"session-1","receipt":{"requestId":"r1","runId":"run-1","attemptId":"attempt-1","fingerprint":"image-request","state":"running"}}"""
                val bytes = response.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val file = java.nio.file.Files.createTempFile("xuanji-image", ".png").toFile()
            try {
                file.writeBytes(byteArrayOf(1, 2, 3))
                val image = PromptAttachment("i", "a.png", "image/png", 3, AttachmentKind.IMAGE, file.path)
                val rejected = runCatching { client(server).sendPrompt("session-1", "inspect", "r1", listOf(image)) }.exceptionOrNull()
                assertTrue(rejected is PromptPreparationException)
                assertEquals(listOf("GET /sessions/session-1/vision-bridge"), requests)
                active = true
                client(server).sendPrompt("session-1", "inspect", "r1", listOf(image))
                assertEquals("POST /sessions/session-1/prompt", requests.last())
            } finally { file.delete() }
        }
    }
    @Test
    fun composerSettingsUseSessionEndpointsAndRequireApprovalAcknowledgement() = runBlocking {
        withServer { server ->
            val requests = CopyOnWriteArrayList<String>()
            server.createContext("/") { exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI.path} ${exchange.requestBody.bufferedReader().readText()}"
                val bytes = "{\"id\":\"session-1\",\"approvalMode\":\"auto-safe\"}".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            client(server).setApprovalMode("session-1", ChatApprovalMode.SAFE)
            client(server).switchSessionModel("session-1", "deepseek:key:model")
            assertEquals("POST /sessions/session-1/approval-mode {\"approvalMode\":\"auto-safe\"}", requests[0])
            assertEquals("POST /sessions/session-1/model {\"modelId\":\"deepseek:key:model\"}", requests[1])
            assertTrue(runCatching { client(server).setApprovalMode("session-1", ChatApprovalMode.MANUAL) }.isFailure)
        }
    }
    @Test
    fun effortUpdatesTheExistingSessionAndRejectsMismatchedAcknowledgement() = runBlocking {
        withServer { server ->
            val requests = CopyOnWriteArrayList<String>()
            server.createContext("/") { exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI.path} ${exchange.requestBody.bufferedReader().readText()}"
                val bytes = "{\"id\":\"session-1\",\"effort\":\"high\"}".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            client(server).setReasoningEffort("session-1", "high")
            assertEquals("POST /sessions/session-1/effort {\"effort\":\"high\"}", requests.single())
            assertTrue(runCatching { client(server).setReasoningEffort("session-1", "low") }.isFailure)
        }
    }

    @Test
    fun eventStreamReportsOpenWithoutInventingSessionEvents() = runBlocking {
        withServer { server ->
            val releaseServer = CountDownLatch(1)
            server.createContext("/") { exchange ->
                try {
                    exchange.responseHeaders.add("Content-Type", "text/event-stream")
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.flush()
                    releaseServer.await(5, TimeUnit.SECONDS)
                } finally { exchange.close() }
            }
            server.start()
            val opened = CompletableDeferred<Unit>()
            var eventReceived = false
            val collector = launch {
                client(server).events("session-1", onOpen = { opened.complete(Unit) }).collect { eventReceived = true }
            }
            try {
                withTimeout(5_000) { opened.await() }
                assertFalse(eventReceived)
            } finally {
                releaseServer.countDown()
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun rejectedEventStreamPreservesHttpStatusForRecovery() = runBlocking {
        withServer { server ->
            server.createContext("/") { exchange ->
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            server.start()
            val failure = runCatching { client(server).events("missing").collect {} }.exceptionOrNull()
            assertTrue(failure is TianshuHttpException)
            assertEquals(404, (failure as TianshuHttpException).status)
        }
    }

    @Test
    fun bodylessCommandsSendValidPostRequests() = runBlocking {
        withServer { server ->
            val requests = CopyOnWriteArrayList<Pair<String, String>>()
            server.createContext("/") { exchange ->
                requests += exchange.requestMethod to exchange.requestBody.bufferedReader().readText()
                val bytes = "{\"ok\":true}".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val client = client(server)

            client.approveMcpServer("android")
            client.abort("session-1")

            assertEquals(listOf("POST", "POST"), requests.map { it.first })
            assertEquals(listOf("", ""), requests.map { it.second })
        }
    }

    @Test
    fun cancellationCancelsTheCallWhileReadingTheResponseBody() = runBlocking {
        withServer { server ->
            val headersSent = CountDownLatch(1)
            val releaseBody = CountDownLatch(1)
            val cancelled = CountDownLatch(1)
            server.createContext("/") { exchange ->
                try {
                    exchange.sendResponseHeaders(200, 100)
                    exchange.responseBody.write('{'.code)
                    exchange.responseBody.flush()
                    headersSent.countDown()
                    releaseBody.await(5, TimeUnit.SECONDS)
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val http = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.countDown() }
            }).build()
            val request = launch(start = CoroutineStart.UNDISPATCHED) { client(server, http).listProviders() }
            try {
                assertTrue(headersSent.await(5, TimeUnit.SECONDS))
                request.cancelAndJoin()
                assertTrue("The suspended request must cancel its socket", cancelled.await(2, TimeUnit.SECONDS))
            } finally {
                releaseBody.countDown()
            }
        }
    }

    @Test
    fun fullEventBufferFailsBeforeLaterEventsCanBeAcknowledged() = runBlocking {
        withServer { server ->
            val releaseServer = CountDownLatch(1)
            server.createContext("/") { exchange ->
                try {
                    exchange.responseHeaders.add("Content-Type", "text/event-stream")
                    exchange.sendResponseHeaders(200, 0)
                    for (seq in 1..400) {
                        exchange.responseBody.write("data: {\"seq\":$seq,\"type\":\"text_delta\",\"data\":{\"text\":\"x\"}}\n\n".toByteArray())
                        exchange.responseBody.flush()
                    }
                    releaseServer.await(5, TimeUnit.SECONDS)
                } catch (_: java.io.IOException) {
                    // The client closes the socket when it cannot retain another event.
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val firstEvent = CompletableDeferred<Unit>()
            val resumeCollector = CompletableDeferred<Unit>()
            val received = mutableListOf<Long>()
            var failure: Throwable? = null
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    client(server).events("session-1").collect { event ->
                        received += event.seq
                        if (received.size == 1) {
                            firstEvent.complete(Unit)
                            resumeCollector.await()
                        }
                    }
                } catch (error: Exception) {
                    failure = error
                }
            }
            try {
                withTimeout(5_000) { firstEvent.await() }
                delay(200)
                resumeCollector.complete(Unit)
                withTimeout(3_000) { collector.join() }
                assertNotNull("Overflow must trigger replay instead of silently dropping events", failure)
                assertEquals((1L..received.size.toLong()).toList(), received)
                assertTrue(received.size < 400)
            } finally {
                releaseServer.countDown()
                collector.cancelAndJoin()
            }
        }
    }

    private fun client(server: HttpServer, http: OkHttpClient = OkHttpClient()) =
        TianshuClient("http://127.0.0.1:${server.address.port}", http) { "test-bearer" }

    private suspend fun withServer(block: suspend (HttpServer) -> Unit) {
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { this.executor = executor }
        try {
            block(server)
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}

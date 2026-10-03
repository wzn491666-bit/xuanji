package com.xuanji.android

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class PromptReliabilityTest {
    @Test fun onlyAReceiptForThisSessionAndRequestCanAcknowledgeSubmission() = runBlocking {
        withServer { server, client ->
            var response = snapshot("running")
            server.createContext("/") { it.respond(200, response) }
            server.start()
            assertEquals("running", client.sendPrompt("s", "hello", "r")["receipt"]?.jsonObject?.get("state")?.jsonPrimitive?.content)
            listOf(
                """{"id":"s"}""",
                snapshot("running", session = "different-session"),
                snapshot("running", request = "different-request"),
                snapshot("unknown"),
                """{"id":"s","receipt":{"requestId":"r","state":"running"}}""",
                """{"id":"s","receipt":{"requestId":"r","runId":true,"attemptId":"a","fingerprint":"f","state":"running"}}""",
            ).forEach { invalid ->
                response = invalid
                val error = runCatching { client.sendPrompt("s", "hello", "r") }.exceptionOrNull()
                assertTrue("Invalid receipt must leave delivery unconfirmed: $invalid", error is PromptReceiptException)
                assertFalse(isDefinitivePromptRejection(checkNotNull(error)))
            }
        }
    }

    @Test fun successfulHttpDoesNotHideRejectionOrAnUnsettledPreviousProcess() = runBlocking {
        withServer { server, client ->
            var state = "rejected"
            server.createContext("/") { it.respond(200, snapshot(state)) }
            server.start()
            val rejected = runCatching { client.sendPrompt("s", "hello", "r") }.exceptionOrNull()
            assertTrue(rejected is PromptReceiptException)
            assertTrue(isDefinitivePromptRejection(checkNotNull(rejected), previouslyUncertain = true))
            state = "needs_attention"
            val orphaned = runCatching { client.sendPrompt("s", "hello", "r") }.exceptionOrNull()
            assertTrue(orphaned is PromptReceiptException)
            assertFalse(isDefinitivePromptRejection(checkNotNull(orphaned)))
            assertTrue(orphaned.message.orEmpty().contains("不会自动重跑"))
        }
    }

    @Test fun alreadySettledReceiptsConfirmTransportWithoutInventingSuccessfulExecution() = runBlocking {
        withServer { server, client ->
            var outcome = "completed"
            server.createContext("/") { it.respond(200, snapshot("settled", outcome = outcome)) }
            server.start()
            listOf("completed", "failed", "aborted").forEach { actual ->
                outcome = actual
                val response = client.sendPrompt("s", "hello", "r")
                assertEquals(actual, response["status"]?.jsonPrimitive?.content)
                assertEquals(actual, response["receipt"]?.jsonObject?.get("outcome")?.jsonPrimitive?.content)
            }
        }
    }

    @Test fun conflictResponseIsResolvedFromTheDurableLedgerBeforeAllowingAFreshRetry() = runBlocking {
        withServer { server, client ->
            val calls = CopyOnWriteArrayList<String>()
            server.createContext("/") { exchange ->
                calls += "${exchange.requestMethod} ${exchange.requestURI.path}"
                if (exchange.requestMethod == "POST") exchange.respond(409, """{"error":"busy"}""")
                else exchange.respond(200, receipt("rejected").toString())
            }
            server.start()
            val error = runCatching { client.sendPrompt("s", "hello", "r") }.exceptionOrNull()
            assertTrue(error is PromptReceiptException)
            assertTrue(isDefinitivePromptRejection(checkNotNull(error), previouslyUncertain = true))
            assertEquals(listOf("POST /sessions/s/prompt", "GET /sessions/s/requests/r"), calls)
        }
    }

    @Test fun payloadConflictDoesNotLookLikeAnUnexecutedRequestOrQueryAnUnrelatedReceipt() = runBlocking {
        withServer { server, client ->
            val calls = CopyOnWriteArrayList<String>()
            server.createContext("/") { exchange ->
                calls += exchange.requestMethod
                exchange.respond(409, """{"error":"requestId already belongs to a different request","code":"request_conflict"}""")
            }
            server.start()
            val error = runCatching { client.sendPrompt("s", "changed text", "r") }.exceptionOrNull()
            assertTrue(error is TianshuHttpException)
            assertFalse(isDefinitivePromptRejection(checkNotNull(error)))
            assertEquals(listOf("POST"), calls)
        }
    }

    @Test fun anExistingRunIsAcknowledgedFromItsReceiptWithoutPostingAnotherRun() = runBlocking {
        withServer { server, client ->
            val calls = CopyOnWriteArrayList<String>()
            server.createContext("/") { exchange ->
                calls += "${exchange.requestMethod} ${exchange.requestURI.path}"
                when {
                    exchange.requestMethod == "POST" -> exchange.respond(409, """{"error":"busy"}""")
                    exchange.requestURI.path.contains("/requests/") -> exchange.respond(200, receipt("running").toString())
                    else -> exchange.respond(200, """{"id":"s","status":"running","lastSeq":8}""")
                }
            }
            server.start()
            val response = client.sendPrompt("s", "hello", "r")
            assertEquals(8L, sessionSnapshotSequence(response))
            assertTrue(sessionSnapshotRunning(response))
            val completed = SessionEvent(8, "status", buildJsonObject { put("status", "completed") })
            assertTrue(runningAfterSessionEvent(true, completed, sessionSnapshotSequence(response)))
            assertFalse(runningAfterSessionEvent(true, completed.copy(seq = 9), sessionSnapshotSequence(response)))
            assertEquals("running", response["receipt"]?.jsonObject?.get("state")?.jsonPrimitive?.content)
            assertEquals(listOf("POST /sessions/s/prompt", "GET /sessions/s/requests/r", "GET /sessions/s"), calls)
        }
    }

    @Test fun uncertainRetriesPreserveTheIdAndRejectedRetriesReplaceItWithoutLosingPayload() {
        val attachment = PromptAttachment("i", "a.png", "image/png", 3, AttachmentKind.IMAGE, "/unchanged/image.png")
        val user = ChatItem.User("/goal task", "old", PromptDelivery.UNCERTAIN, "timeout", listOf(attachment), "full goal driver")
        val same = preparePromptRetry(user, "fresh")
        assertEquals("old", same.requestId)
        val fresh = preparePromptRetry(user.copy(delivery = PromptDelivery.REJECTED), "fresh")
        assertEquals("fresh", fresh.requestId)
        listOf(same, fresh).forEach {
            assertEquals(PromptDelivery.SENDING, it.delivery)
            assertNull(it.deliveryError)
            assertEquals(user.text, it.text)
            assertEquals(user.wireText, it.wireText)
            assertEquals(user.attachments, it.attachments)
        }
        assertNotNull(runCatching { preparePromptRetry(user.copy(delivery = PromptDelivery.REJECTED), "old") }.exceptionOrNull())
        assertNotNull(runCatching { preparePromptRetry(user.copy(delivery = PromptDelivery.DELIVERED), "fresh") }.exceptionOrNull())
    }

    @Test fun aFailedRetryPreflightCannotProveTheFirstSubmissionNeverExecuted() {
        listOf(
            PromptPreparationException(IllegalStateException("vision bridge temporarily unavailable")),
            TianshuHttpException(401, """{"error":"Unauthorized"}"""),
            TianshuHttpException(404, """{"error":"Session not found"}"""),
            TianshuHttpException(409, """{"error":"busy"}"""),
        ).forEach {
            assertTrue(isDefinitivePromptRejection(it))
            assertFalse(isDefinitivePromptRejection(it, previouslyUncertain = true))
        }
        assertFalse(isDefinitivePromptRejection(java.io.IOException("connection reset")))
        assertTrue(isDefinitivePromptRejection(PromptReceiptException("rejected", rejected = true), previouslyUncertain = true))
    }

    private fun receipt(state: String, request: String = "r", outcome: String? = null) = buildJsonObject {
        put("requestId", request)
        put("runId", "run-1")
        put("attemptId", "attempt-1")
        put("fingerprint", "original-request-fingerprint")
        put("state", state)
        outcome?.let { put("outcome", it) }
    }

    private fun snapshot(state: String, session: String = "s", request: String = "r", outcome: String? = null) = buildJsonObject {
        put("id", session)
        put("status", outcome ?: "running")
        put("lastSeq", 1)
        put("receipt", receipt(state, request, outcome))
    }.toString()

    private fun HttpExchange.respond(status: Int, response: String) {
        requestBody.close()
        val bytes = response.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private suspend fun withServer(block: suspend (HttpServer, TianshuClient) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val client = TianshuClient("http://127.0.0.1:${server.address.port}", bearerToken = { "test" })
        try { block(server, client) } finally { server.stop(0) }
    }
}

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

class TianshuPlansTest {
    private val plan = """{"slug":"sample","title":"示例计划","status":"submitted","content":"# 示例计划\n\n先核对再执行","options":[{"label":"A","description":"稳妥方案"}]}"""

    @Test fun viewingCancelsAutoApprovalBeforeReadingDocument() = runBlocking {
        withServer { client, calls ->
            val output = executePlanCommand(client, "session", NativeSlashRequest(PaletteAction.PLAN_VIEW, "/plan-view", "sample"))
            assertEquals(listOf("POST /sessions/session/plans/sample/auto-approve/cancel", "GET /sessions/session/plans/sample"), calls.map { it.first })
            assertEquals("# 示例计划\n\n先核对再执行", output.plan?.content)
            assertEquals("A", output.plan?.options?.single()?.label)
        }
    }

    @Test fun approvingUsesUpstreamKickoffWithoutSendingDuplicatePrompt() = runBlocking {
        withServer { client, calls ->
            executePlanCommand(client, "session", NativeSlashRequest(PaletteAction.PLAN_APPROVE, "/plan-approve", "sample A"))
            val approve = calls.single { it.first.endsWith("/approve") }
            assertEquals("A", approve.second["selectedApproach"]?.jsonPrimitive?.content)
            assertFalse(calls.any { it.first.endsWith("/prompt") })
        }
    }

    @Test fun rejectionPreservesFullFeedbackAndRequiresReceipt() = runBlocking {
        withServer { client, calls ->
            executePlanCommand(client, "session", NativeSlashRequest(PaletteAction.PLAN_REJECT, "/plan-reject", "sample 请保留数据\n补充回归测试"))
            assertEquals("请保留数据\n补充回归测试", calls.single { it.first.endsWith("/reject") }.second["comment"]?.jsonPrimitive?.content)
        }
        withServer(receipt = """{"ok":false}""") { client, _ ->
            assertNotNull(runCatching { client.approveSessionPlan("session", "sample") }.exceptionOrNull())
        }
    }

    @Test fun historyPlansCannotBeApprovedAgain() = runBlocking {
        withServer(document = plan.replace("submitted", "approved")) { client, calls ->
            assertNotNull(runCatching { executePlanCommand(client, "session", NativeSlashRequest(PaletteAction.PLAN_APPROVE, "/plan-approve", "sample")) }.exceptionOrNull())
            assertFalse(calls.any { it.first.endsWith("/approve") })
        }
    }

    @Test fun nativeListsKeepDraftSeparateAndEncodeSlugsAsPathSegments() = runBlocking {
        withServer { client, calls ->
            val catalog = client.sessionPlans("session")
            assertEquals(1, catalog.plans.size)
            assertEquals("起草中", catalog.draft?.title)
            client.sessionPlan("session", "中文 计划")
            assertTrue(calls.last().first.contains("%E4%B8%AD%E6%96%87%20%E8%AE%A1%E5%88%92"))
            val before = calls.size
            assertNotNull(runCatching { client.sessionPlan("session", "../sample") }.exceptionOrNull())
            assertNotNull(runCatching { client.updateSessionPlan("session", "sample", " ") }.exceptionOrNull())
            assertEquals(before, calls.size)
        }
    }

    @Test fun unknownStatusOrMissingListFailsExplicitly() {
        assertNotNull(runCatching { parseTianshuPlanCatalog(buildJsonObject { }) }.exceptionOrNull())
        assertNotNull(runCatching { parseTianshuPlan(Json.parseToJsonElement(plan.replace("submitted", "unknown")).jsonObject) }.exceptionOrNull())
        assertNotNull(runCatching { parseTianshuPlan(buildJsonObject { put("slug", "s"); put("status", "submitted") }, true) }.exceptionOrNull())
    }

    private suspend fun withServer(document: String = plan, receipt: String = """{"ok":true}""",
        block: suspend (TianshuClient, CopyOnWriteArrayList<Pair<String, JsonObject>>) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = CopyOnWriteArrayList<Pair<String, JsonObject>>()
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            calls += ("${exchange.requestMethod} ${exchange.requestURI.rawPath}" to if (body.isBlank()) buildJsonObject { } else Json.parseToJsonElement(body).jsonObject)
            val response = when {
                exchange.requestMethod != "GET" -> receipt
                exchange.requestURI.path.endsWith("/plans") -> """{"plans":[$document],"draft":{"title":null,"content":"draft"}}"""
                else -> """{"plan":$document}"""
            }.toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try { block(TianshuClient(base = "http://127.0.0.1:${server.address.port}", bearerToken = { "test" }), calls) }
        finally { server.stop(0) }
    }
}

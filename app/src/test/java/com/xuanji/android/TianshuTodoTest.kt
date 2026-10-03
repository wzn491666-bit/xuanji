package com.xuanji.android

import com.xuanji.android.ui.chat.NativeSlashResult
import com.xuanji.android.ui.chat.NativeSlashRequest
import com.xuanji.android.ui.chat.PaletteAction
import com.xuanji.android.ui.chat.nativeSlashBlocked
import com.xuanji.android.ui.chat.resolveNativeSlash
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class TianshuTodoTest {
    @Test fun todoMutationsReachNativeExecution() {
        listOf("/todo", "/todo list", "/todo add 验证手机读屏", "/todo done user-1", "/todo skip user-1", "/todo move user-1 up",
            "/todo start user-1", "/todo pending user-1", "/todo edit user-1 补充回归测试")
            .forEach { assertTrue(it, resolveNativeSlash(it) is NativeSlashResult.Execute) }
        listOf("/todo add", "/todo done", "/todo move x left", "/todo clear", "/todo list extra",
            "/todo done id extra", "/todo start", "/todo edit id", "/todo move id up extra")
            .forEach { assertTrue(it, resolveNativeSlash(it) is NativeSlashResult.Invalid) }
    }

    @Test fun contentPreservesMultilineWhitespaceAndIdsRemainExact() {
        val text = "/todo edit user-1  先核对\n  再执行  "
        val native = (resolveNativeSlash(text) as NativeSlashResult.Execute).request
        assertEquals(PaletteAction.TODO, native.action)
        val command = parseTodoCommand(native.argument)
        assertEquals("user-1", command.id)
        assertEquals(" 先核对\n  再执行  ", command.content)
        assertEquals(" 完整保留  ", parseTodoCommand("add  完整保留  ").content)
        assertEquals("id", parseTodoCommand("done id  ").id)
        val unusualId = "a \"quoted\"\nID"
        assertEquals(unusualId, parseTodoCommand("done ${todoIdArgument(unusualId)}").id)
        assertEquals(unusualId, parseTodoCommand("edit ${todoIdArgument(unusualId)} updated").id)
        assertEquals(unusualId, parseTodoCommand("move ${todoIdArgument(unusualId)} down").id)
    }

    @Test fun readsStayAvailableDuringRunsButWritesRequireIdleReadySession() {
        val read = NativeSlashRequest(PaletteAction.TODO, "/todo", "list")
        val write = NativeSlashRequest(PaletteAction.TODO, "/todo", "add task")
        assertFalse(nativeSlashBlocked(read, canSwitch = false, sessionReady = true))
        assertTrue(nativeSlashBlocked(write, canSwitch = false, sessionReady = true))
        assertFalse(nativeSlashBlocked(write, canSwitch = true, sessionReady = true))
        assertTrue(nativeSlashBlocked(read, canSwitch = true, sessionReady = false))
        assertTrue(nativeSlashBlocked(read, canSwitch = true, sessionReady = true, controlBusy = true))
    }

    @Test fun typedSnapshotsPreserveIdentityAndActiveFormAndRejectUnconfirmedWrites() {
        val parsed = parseTianshuTodos(Json.parseToJsonElement(snapshot).jsonObject)
        assertEquals("id-a", parsed.items.single().id)
        assertEquals("正在校验", parsed.items.single().activeForm)
        assertEquals("in_progress", parsed.items.single().status)
        listOf(snapshot.replace("in_progress", "unknown"), snapshot.replace(revision, "bad"),
            snapshot.replace("\"id-a\"", "\"\""), snapshot.replace("\"校验\"", "\"\""))
            .forEach { assertNotNull(runCatching { parseTianshuTodos(Json.parseToJsonElement(it).jsonObject) }.exceptionOrNull()) }
        assertNotNull(runCatching { parseTianshuTodos(Json.parseToJsonElement(snapshot).jsonObject, requireDurable = true) }.exceptionOrNull())
        assertNotNull(runCatching { parseTianshuTodos(buildJsonObject { put("revision", revision) }) }.exceptionOrNull())
        val duplicate = Json.parseToJsonElement(snapshot).jsonObject.toMutableMap()
        duplicate["items"] = JsonArray(parsed.items.map { Json.parseToJsonElement("""{"id":"id-a","content":"校验","status":"pending"}""") }.let { it + it })
        assertNotNull(runCatching { parseTianshuTodos(JsonObject(duplicate)) }.exceptionOrNull())
    }

    @Test fun typedSlashMutationReadsRevisionThenPostsWithoutSendingModelPrompt() = runBlocking {
        withServer { client, calls ->
            val output = executeTodoCommand(client, "s", NativeSlashRequest(PaletteAction.TODO, "/todo", "add 新任务\n  第二行"))
            assertEquals(listOf("GET", "POST"), calls.map { it.method })
            assertEquals("/sessions/s/xuanji/todos", calls.last().path)
            assertEquals(revision, calls.last().body["expectedRevision"]?.jsonPrimitive?.content)
            assertEquals("新任务\n  第二行", calls.last().body["content"]?.jsonPrimitive?.content)
            assertEquals("任务已添加", output.text)
            assertEquals("add 新任务\n  第二行", output.todoMutationArgument)
            assertTrue(calls.all { it.auth == "Bearer test" })
        }
    }

    @Test fun panelMutationUsesDisplayedRevisionWithoutReadingAndNeverRetriesStaleWrites() = runBlocking {
        val shown = "b".repeat(64)
        withServer(postStatus = 409) { client, calls ->
            val failure = runCatching { executeTodoCommand(client, "s", NativeSlashRequest(PaletteAction.TODO, "/todo", "done id-a", shown)) }.exceptionOrNull()
            assertTrue(failure is TianshuHttpException && failure.status == 409)
            assertEquals(1, calls.size)
            assertEquals("POST", calls.single().method)
            assertEquals(shown, calls.single().body["expectedRevision"]?.jsonPrimitive?.content)
        }
    }

    @Test fun badPersistenceReceiptIsNotReportedAsSaved() = runBlocking {
        withServer(durable = false) { client, _ ->
            assertNotNull(runCatching { client.updateSessionTodos("s", parseTodoCommand("done id-a"), revision) }.exceptionOrNull())
        }
    }

    @Test fun newerLiveStateSurvivesAnOlderHttpMutationReceiptAndReplay() {
        val original = parseTianshuTodos(Json.parseToJsonElement(snapshot).jsonObject).copy(seq = 5)
        val receipt = original.copy(seq = 7, items = original.items.map { it.copy(status = "completed") })
        val live = original.copy(seq = 8, revision = "b".repeat(64), items = original.items.map { it.copy(content = "模型更新后内容") })
        val whileBusy = newestTianshuTodos(original, live)
        val afterHttp = newestTianshuTodos(whileBusy, receipt)
        assertEquals(live, afterHttp)
        assertEquals(live, newestTianshuTodos(afterHttp, original))
        assertEquals(live, newestTianshuTodos(afterHttp, original.copy(seq = 0)))
        assertEquals(receipt.copy(seq = 9), newestTianshuTodos(afterHttp, receipt.copy(seq = 9)))
    }

    private val revision = "a".repeat(64)
    private val snapshot get() = """{"items":[{"id":"id-a","content":"校验","status":"in_progress","activeForm":"正在校验"}],"revision":"$revision"}"""
    private data class CallRecord(val method: String, val path: String, val body: JsonObject, val auth: String?)
    private suspend fun withServer(postStatus: Int = 200, durable: Boolean = true,
        block: suspend (TianshuClient, CopyOnWriteArrayList<CallRecord>) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = CopyOnWriteArrayList<CallRecord>()
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            calls += CallRecord(exchange.requestMethod, exchange.requestURI.path,
                if (body.isBlank()) buildJsonObject { } else Json.parseToJsonElement(body).jsonObject,
                exchange.requestHeaders.getFirst("Authorization"))
            val response = if (exchange.requestMethod == "GET") snapshot else if (postStatus == 409) """{"error":"待办已更新"}"""
                else snapshot.dropLast(1) + ",\"durable\":$durable,\"notice\":\"saved\"}"
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(if (exchange.requestMethod == "GET") 200 else postStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block(TianshuClient(base = "http://127.0.0.1:${server.address.port}", bearerToken = { "test" }), calls) }
        finally { server.stop(0) }
    }
}

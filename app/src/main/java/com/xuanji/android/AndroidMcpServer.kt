package com.xuanji.android

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class AndroidMcpStatus(
    val state: String = "stopped",
    val endpoint: String? = null,
    val toolCount: Int = 0,
    val error: String? = null,
)

/** Loopback-only Streamable HTTP MCP server for explicitly approved phone capabilities. */
class AndroidMcpServer(context: Context, private val approvalGate: AndroidActionApprovalGate) {
    companion object {
        private const val MAX_HEADER_BYTES = 32 * 1024
        private const val MAX_BODY_BYTES = 1 * 1024 * 1024
        private const val MAX_FILE_BYTES = 1024 * 1024
    }

    private val appContext = context.applicationContext
    private val commandExecutor = CommandExecutor(appContext)
    private val searchSettings = com.xuanji.android.search.SearchSettingsRepository(appContext)
    private val webSearch = com.xuanji.android.search.WebSearchClient()
    private val storageRoot = Environment.getExternalStorageDirectory().canonicalFile
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connections = AndroidMcpConnections()
    private val stopping = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null

    val status = MutableStateFlow(AndroidMcpStatus(toolCount = TianshuClient.ANDROID_MCP_TOOL_CAPABILITIES.size))
    val token: String = UUID.randomUUID().toString()
    val endpoint: String?
        get() = serverSocket?.localPort?.let { "http://127.0.0.1:$it/mcp" }

    private val protocol by lazy { AndroidMcpProtocol(toolDefinitions) { name, arguments -> invokeTool(name, arguments) } }

    fun start() {
        if (serverSocket != null) return
        try {
            stopping.set(false)
            val socket = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
            serverSocket = socket
            status.value = AndroidMcpStatus("running", endpoint, toolDefinitions.size)
            scope.launch {
                while (!stopping.get()) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    connections.execute(client, ::serve)
                }
            }
        } catch (t: Throwable) {
            status.value = AndroidMcpStatus("error", error = t.message ?: "MCP server failed to start")
        }
    }

    fun stop() {
        if (!stopping.compareAndSet(false, true)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        connections.close()
        commandExecutor.close()
        scope.cancel()
        status.value = AndroidMcpStatus("stopped", toolCount = toolDefinitions.size)
    }

    private fun serve(socket: Socket) {
        socket.use { client ->
            val request = runCatching { readRequest(client) }.getOrNull() ?: return
            if (!authorized(request.headers["authorization"])) {
                writeResponse(client.getOutputStream(), 401, "{\"error\":\"Unauthorized\"}")
                return
            }
            if (request.method == "GET") {
                writeResponse(client.getOutputStream(), 405, "{\"error\":\"MCP SSE is not supported\"}", mapOf("Allow" to "POST"))
                return
            }
            if (request.method != "POST" || request.path != "/mcp") {
                writeResponse(client.getOutputStream(), 404, "{\"error\":\"Not found\"}")
                return
            }
            val message = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(request.body).jsonObject }.getOrNull()
            if (message == null) {
                writeResponse(client.getOutputStream(), 400, "{\"error\":\"Invalid JSON\"}")
                return
            }
            val response = runCatching { runBlocking { protocol.handle(message) } }.getOrElse {
                writeResponse(client.getOutputStream(), 500, "{\"error\":\"MCP request failed\"}")
                return
            }
            if (response == null) writeResponse(client.getOutputStream(), 202, "")
            else writeResponse(client.getOutputStream(), 200, response.toString())
        }
    }

    private fun authorized(header: String?): Boolean {
        val expected = "Bearer $token".toByteArray(StandardCharsets.UTF_8)
        val actual = header?.trim()?.toByteArray(StandardCharsets.UTF_8) ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    private data class HttpRequest(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    private fun readRequest(socket: Socket): HttpRequest {
        val input = socket.getInputStream().buffered()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        fun updateReadTimeout() {
            val remaining = deadline - System.nanoTime()
            require(remaining > 0) { "request read timed out" }
            socket.soTimeout = TimeUnit.NANOSECONDS.toMillis(remaining).coerceIn(1, 15_000).toInt()
        }
        val headerBytes = ByteArrayOutputStream()
        var matched = 0
        while (headerBytes.size() < MAX_HEADER_BYTES) {
            updateReadTimeout()
            val next = input.read()
            if (next < 0) throw IllegalArgumentException("request ended before headers")
            headerBytes.write(next)
            matched = when {
                matched == 0 && next == '\r'.code -> 1
                matched == 1 && next == '\n'.code -> 2
                matched == 2 && next == '\r'.code -> 3
                matched == 3 && next == '\n'.code -> 4
                else -> if (next == '\r'.code) 1 else 0
            }
            if (matched == 4) break
        }
        require(matched == 4) { "request headers are too large" }
        val lines = headerBytes.toByteArray().toString(StandardCharsets.ISO_8859_1).trimEnd('\r', '\n').split("\r\n")
        val requestLine = lines.firstOrNull()?.split(' ') ?: throw IllegalArgumentException("missing request line")
        require(requestLine.size >= 2) { "invalid request line" }
        val headers = lines.drop(1).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
        }.toMap()
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        require(contentLength in 0..MAX_BODY_BYTES) { "request body is too large" }
        val bodyBytes = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            updateReadTimeout()
            val read = input.read(bodyBytes, offset, contentLength - offset)
            if (read < 0) throw IllegalArgumentException("request ended before body")
            offset += read
        }
        return HttpRequest(requestLine[0].uppercase(), requestLine[1].substringBefore('?'), headers, bodyBytes.toString(StandardCharsets.UTF_8))
    }

    private fun writeResponse(output: OutputStream, status: Int, body: String, extraHeaders: Map<String, String> = emptyMap()) {
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 202 -> "Accepted"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Internal Server Error" }
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Connection: close\r\n")
            extraHeaders.forEach { (key, value) -> append(key).append(": ").append(value).append("\r\n") }
            append("\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
        output.write(payload)
        output.flush()
    }

    private suspend fun invokeTool(name: String, args: JsonObject): AndroidMcpToolResult = when (name) {
        "android_read_screen" -> readScreen()
        "android_list_files" -> listFiles(args)
        "android_read_file" -> readFile(args)
        "android_write_file" -> writeFile(args)
        "android_shell_id" -> shellId(args)
        "android_permission_status" -> permissionStatus()
        "android_accessibility_action" -> accessibilityAction(args)
        "android_web_search" -> {
            val query = args["query"]?.jsonPrimitive?.contentOrNull ?: error("query is required")
            val config = searchSettings.load()
            val report = webSearch.search(query, config, searchSettings.credentials(config))
            AndroidMcpToolResult(report.toJson().toString(), report.sources.isEmpty())
        }
        "android_browser_tabs", "android_browser_open", "android_browser_snapshot", "android_browser_control", "android_browser_action" -> browserTool(name, args)
        else -> AndroidMcpToolResult("Unknown Android tool: $name", true)
    }

    private suspend fun browserTool(name: String, args: JsonObject): AndroidMcpToolResult {
        val browser = com.xuanji.android.browser.AndroidBrowser.get(appContext)
        val result = when (name) {
            "android_browser_tabs" -> withContext(Dispatchers.Main.immediate) { browser.stateSnapshot() }
            "android_browser_open" -> {
                val url = args["url"]?.jsonPrimitive?.contentOrNull ?: error("url is required")
                val id = withContext(Dispatchers.Main.immediate) {
                    val id = browser.open(url)
                    com.xuanji.android.browser.BrowserActivity.show(appContext)
                    id
                }
                buildJsonObject { put("tabId", id); put("url", com.xuanji.android.browser.browserUrl(url)); put("loading", true) }
            }
            "android_browser_snapshot" -> {
                val id = args["tabId"]?.jsonPrimitive?.contentOrNull ?: browser.state.value.activeId ?: error("请先打开网页")
                browser.snapshot(id, com.xuanji.android.browser.browserSnapshotOptions(args))
            }
            "android_browser_control" -> {
                val request = com.xuanji.android.browser.parseBrowserControl(args)
                val target = withContext(Dispatchers.Main.immediate) { browser.captureControlTarget(request) }
                val detail = "${target.url}\n标签页 ${request.tabId} · ${request.action}${request.url?.let { "\n目标地址：$it" }.orEmpty()}"
                if (!approvalGate.request("控制浏览器", detail)) return AndroidMcpToolResult("用户未允许浏览器操作", true)
                currentCoroutineContext().ensureActive()
                check(!stopping.get()) { "Android 服务已停止，浏览器操作已取消" }
                withContext(Dispatchers.Main.immediate) { browser.control(request, target) }
            }
            else -> {
                val action = com.xuanji.android.browser.validateBrowserAction(args)
                val id = args["tabId"]?.jsonPrimitive?.contentOrNull ?: error("tabId is required")
                val snapshotId = args["snapshotId"]?.jsonPrimitive?.contentOrNull ?: error("snapshotId is required")
                val elementId = args["elementId"]?.jsonPrimitive?.intOrNull
                val detail = when (action) {
                    "type" -> "\n${args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()}"
                    "select" -> "\n选项：${args["value"]?.jsonPrimitive?.contentOrNull.orEmpty()}"
                    "check" -> "\n勾选：${args["checked"]?.jsonPrimitive?.booleanOrNull}"
                    "scroll" -> "\n方向：${args["direction"]?.jsonPrimitive?.contentOrNull} · ${args["amount"]?.jsonPrimitive?.intOrNull ?: "默认"} px"
                    "submit" -> "\n提交目标所属表单，网页可能发送数据"
                    else -> ""
                }
                withContext(Dispatchers.Main.immediate) { browser.assertTarget(id, snapshotId) }
                val target = browser.state.value.active ?: error("标签页已关闭")
                if (!approvalGate.request("操作网页", "${target.title}\n${target.url}\n${elementId?.let { "元素 $it" } ?: "整页"} · $action$detail")) {
                    return AndroidMcpToolResult("用户未允许网页操作", true)
                }
                currentCoroutineContext().ensureActive()
                check(!stopping.get()) { "Android 服务已停止，网页操作已取消" }
                browser.action(id, args)
            }
        }
        return AndroidMcpToolResult(result.toString())
    }

    private suspend fun readScreen(): AndroidMcpToolResult {
        val service = XuanjiAccessibilityService.instance
            ?: return AndroidMcpToolResult("无障碍服务未启用，请先在系统设置中启用璇玑屏幕助手。", true)
        val nodes = withContext(Dispatchers.Main.immediate) { service.readScreen() }
        val json = buildJsonArray {
            nodes.take(300).forEach { node ->
                add(buildJsonObject {
                    put("text", node.text)
                    put("x", node.x)
                    put("y", node.y)
                    put("width", node.width)
                    put("height", node.height)
                    put("clickable", node.clickable)
                    put("editable", node.editable)
                    put("scrollable", node.scrollable)
                })
            }
        }
        return AndroidMcpToolResult(json.toString())
    }

    private fun listFiles(args: JsonObject): AndroidMcpToolResult {
        val path = runCatching {
            resolveSharedStoragePath(args["path"]?.jsonPrimitive?.contentOrNull ?: "/sdcard", storageRoot)
        }.getOrElse { return AndroidMcpToolResult(it.message ?: "path is invalid", true) }
        if (!path.exists()) return AndroidMcpToolResult("路径不存在：${path.path}", true)
        if (!path.isDirectory) return AndroidMcpToolResult("不是目录：${path.path}", true)
        val entries = Files.newDirectoryStream(path.toPath()).use { directory ->
            directory.asSequence().take(200).map { it.toFile() }.sortedBy { it.name.lowercase() }.toList()
        }
        val json = buildJsonArray {
            entries.forEach { entry ->
                add(buildJsonObject {
                    put("name", entry.name)
                    put("path", "/sdcard/${entry.relativeTo(storageRoot).path.replace(File.separatorChar, '/')}")
                    put("type", if (entry.isDirectory) "directory" else "file")
                    put("size", if (entry.isFile) entry.length() else 0L)
                })
            }
        }
        return AndroidMcpToolResult(json.toString())
    }

    private fun readFile(args: JsonObject): AndroidMcpToolResult {
        val path = runCatching {
            resolveSharedStoragePath(args["path"]?.jsonPrimitive?.contentOrNull ?: "", storageRoot)
        }.getOrElse { return AndroidMcpToolResult(it.message ?: "path is invalid", true) }
        if (!path.exists() || !path.isFile) return AndroidMcpToolResult("文件不存在：${path.path}", true)
        if (path.length() > MAX_FILE_BYTES) return AndroidMcpToolResult("文件超过 1 MiB 读取上限。", true)
        val bytes = runCatching { readSharedStorageBytes(path, MAX_FILE_BYTES) }
            .getOrElse { return AndroidMcpToolResult(it.message ?: "文件读取失败", true) }
        return AndroidMcpToolResult(bytes.toString(StandardCharsets.UTF_8))
    }

    private suspend fun writeFile(args: JsonObject): AndroidMcpToolResult {
        if (args["confirm"]?.jsonPrimitive?.booleanOrNull != true) {
            return AndroidMcpToolResult("写文件需要 confirm=true；这是唯一允许修改共享存储的 Android 工具。", true)
        }
        val path = runCatching {
            resolveSharedStoragePath(args["path"]?.jsonPrimitive?.contentOrNull ?: "", storageRoot)
        }.getOrElse { return AndroidMcpToolResult(it.message ?: "path is invalid", true) }
        val content = args["content"]?.jsonPrimitive?.contentOrNull
            ?: return AndroidMcpToolResult("content is required", true)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_FILE_BYTES) return AndroidMcpToolResult("文件超过 1 MiB 写入上限。", true)
        val overwrite = args["overwrite"]?.jsonPrimitive?.booleanOrNull == true
        if (path.exists() && !overwrite) return AndroidMcpToolResult("文件已存在；若确认覆盖请传 overwrite=true。", true)
        if (!approvalGate.request("写入共享文件", "${path.path}\n${if (overwrite) "覆盖已有内容" else "创建新文件"} · ${bytes.size} bytes\n\n$content")) {
            return AndroidMcpToolResult("用户未允许写入文件。", true)
        }
        currentCoroutineContext().ensureActive()
        if (stopping.get()) return AndroidMcpToolResult("Android 服务已停止，文件未写入。", true)
        return runCatching {
            writeSharedStorageText(path, bytes, overwrite)
            AndroidMcpToolResult("写入成功：${path.path}（${bytes.size} bytes）")
        }.getOrElse {
            AndroidMcpToolResult(
                if (it is FileAlreadyExistsException) "文件已存在；若确认覆盖请传 overwrite=true。" else it.message ?: "文件写入失败",
                true,
            )
        }
    }

    private suspend fun accessibilityAction(args: JsonObject): AndroidMcpToolResult {
        if (args["confirm"]?.jsonPrimitive?.booleanOrNull != true) {
            return AndroidMcpToolResult("屏幕操作需要 confirm=true。", true)
        }
        val service = XuanjiAccessibilityService.instance
            ?: return AndroidMcpToolResult("无障碍服务未启用，请先在系统设置中启用璇玑屏幕助手。", true)
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?: return AndroidMcpToolResult("action 必须是 tap、swipe、type、back 或 home。", true)
        if (action !in setOf("tap", "swipe", "type", "back", "home")) {
            return AndroidMcpToolResult("未知 action：$action", true)
        }
        when (action) {
            "tap" -> listOf("x", "y").forEach { args.coordinate(it) }
            "swipe" -> {
                listOf("x1", "y1", "x2", "y2").forEach { args.coordinate(it) }
                require(args.long("duration", 300L) in 1..60_000) { "duration must be between 1 and 60000 ms" }
            }
            "type" -> require(args["text"]?.jsonPrimitive?.contentOrNull?.length?.let { it <= 16_384 } == true) {
                "text is required and must not exceed 16384 characters"
            }
        }
        val target = withContext(Dispatchers.Main.immediate) { service.activeTarget() }
            ?: return AndroidMcpToolResult("没有可操作的前台窗口。", true)
        val detail = when (action) {
            "tap" -> "点击 (${args["x"]}, ${args["y"]})"
            "swipe" -> "从 (${args["x1"]}, ${args["y1"]}) 滑至 (${args["x2"]}, ${args["y2"]})"
            "type" -> args["text"]?.jsonPrimitive?.contentOrNull.orEmpty().let { text ->
                "输入（${text.length} 字符）：\n$text"
            }
            "back" -> "返回上一屏"
            else -> "返回桌面"
        }
        if (!approvalGate.request("屏幕操作", "$detail\n应用：${target.packageName}", targetPackage = target.packageName)) {
            return AndroidMcpToolResult("用户未允许屏幕操作。", true)
        }
        if (!awaitAccessibilityTarget(target) {
                withContext(Dispatchers.Main.immediate) { service.activeTarget() }
            }) {
            return AndroidMcpToolResult("原应用窗口未恢复，已取消屏幕操作。", true)
        }
        val ok = withContext(Dispatchers.Main.immediate) {
            currentCoroutineContext().ensureActive()
            if (stopping.get() || XuanjiAccessibilityService.instance !== service || service.activeTarget() != target) {
                return@withContext false
            }
            when (action) {
                "tap" -> service.tap(args.number("x"), args.number("y"))
                "swipe" -> service.swipe(args.number("x1"), args.number("y1"), args.number("x2"), args.number("y2"), args.long("duration", 300L))
                "type" -> service.typeText(args["text"]?.jsonPrimitive?.contentOrNull ?: "")
                "back" -> service.pressBack()
                "home" -> service.pressHome()
                else -> false
            }
        }
        return AndroidMcpToolResult(if (ok) "操作已分发：$action" else "操作未分发：$action", !ok)
    }

    private fun JsonObject.number(name: String): Float = this[name]?.jsonPrimitive?.floatOrNull
        ?: throw IllegalArgumentException("$name is required")

    private fun JsonObject.coordinate(name: String): Float = number(name).also {
        require(it.isFinite() && it >= 0) { "$name must be a finite nonnegative coordinate" }
    }

    private fun JsonObject.long(name: String, fallback: Long): Long = this[name]?.jsonPrimitive?.longOrNull ?: fallback

    private suspend fun shellId(args: JsonObject): AndroidMcpToolResult {
        val backend = when (args["backend"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
            null, "auto" -> null
            "root" -> ShellBackend.ROOT
            "shizuku" -> ShellBackend.SHIZUKU
            "app" -> ShellBackend.APP
            else -> return AndroidMcpToolResult("backend 必须是 auto、root、shizuku 或 app。", true)
        }
        if (backend == ShellBackend.ROOT || backend == null) commandExecutor.checkRoot()
        val result = runCatching { commandExecutor.execute("id", backend) }
            .getOrElse { return AndroidMcpToolResult(it.message ?: "id 执行失败", true) }
        return AndroidMcpToolResult(
            "${result.backend.name}: 退出码 ${result.exitCode}${if (result.timedOut) "（超时）" else ""}\n${result.output.trim()}",
            result.timedOut || result.exitCode != 0,
        )
    }

    private suspend fun permissionStatus(): AndroidMcpToolResult {
        commandExecutor.checkRoot()
        commandExecutor.refreshShizuku()
        val current = commandExecutor.status.value
        return AndroidMcpToolResult(buildJsonObject {
            put("root", current.root)
            put("shizuku", current.shizuku)
            put("accessibility", if (XuanjiAccessibilityService.isEnabled(appContext)) "enabled" else "disabled")
        }.toString())
    }

    private val toolDefinitions: List<AndroidMcpTool> = listOf(
        AndroidMcpTool(
            "android_read_screen",
            "Read the visible Android accessibility node tree. Requires the Xuanji accessibility service.",
            objectSchema(),
        ),
        AndroidMcpTool(
            "android_list_files",
            "List entries under shared storage (/sdcard). Read-only and capped at 200 entries.",
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { putJsonObject("path") { put("type", "string") } }
            },
        ),
        AndroidMcpTool(
            "android_read_file",
            "Read a UTF-8 text file under shared storage. Files larger than 1 MiB are rejected.",
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { putJsonObject("path") { put("type", "string") } }
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
            },
        ),
        AndroidMcpTool(
            "android_write_file",
            "Write a UTF-8 text file under shared storage. Requires confirm=true and a separate local approval in Xuanji (expires after 45 seconds).",
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string") }
                    putJsonObject("content") { put("type", "string") }
                    putJsonObject("confirm") { put("type", "boolean") }
                    putJsonObject("overwrite") { put("type", "boolean") }
                }
                put("required", buildJsonArray { add(JsonPrimitive("path")); add(JsonPrimitive("content")); add(JsonPrimitive("confirm")) })
            },
        ),
        AndroidMcpTool(
            "android_shell_id",
            "Run the fixed read-only id command through app, Shizuku, root, or automatic privileged selection.",
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("backend") {
                        put("type", "string")
                        put("enum", buildJsonArray { add(JsonPrimitive("auto")); add(JsonPrimitive("root")); add(JsonPrimitive("shizuku")); add(JsonPrimitive("app")) })
                    }
                }
            },
        ),
        AndroidMcpTool(
            "android_permission_status",
            "Return current Root, Shizuku, and accessibility status without changing permissions.",
            objectSchema(),
        ),
        AndroidMcpTool(
            "android_accessibility_action",
            "Perform an explicit accessibility action (tap, swipe, type, back, or home). Requires confirm=true, the Xuanji service, and a separate local approval (expires after 45 seconds).",
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        put("enum", buildJsonArray { listOf("tap", "swipe", "type", "back", "home").forEach { add(JsonPrimitive(it)) } })
                    }
                    putJsonObject("confirm") { put("type", "boolean") }
                    putJsonObject("x") { put("type", "number") }
                    putJsonObject("y") { put("type", "number") }
                    putJsonObject("x1") { put("type", "number") }
                    putJsonObject("y1") { put("type", "number") }
                    putJsonObject("x2") { put("type", "number") }
                    putJsonObject("y2") { put("type", "number") }
                    putJsonObject("duration") { put("type", "integer") }
                    putJsonObject("text") { put("type", "string") }
                }
                put("required", buildJsonArray { add(JsonPrimitive("action")); add(JsonPrimitive("confirm")) })
            },
        ),
    ) + com.xuanji.android.search.androidSearchTools + com.xuanji.android.browser.androidBrowserTools

    private fun objectSchema() = buildJsonObject { put("type", "object") }
}

/** No queued sockets: unauthenticated clients must fit within the active connection cap. */
internal class AndroidMcpConnections(maxConnections: Int = 4) {
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(maxConnections, maxConnections, 0, TimeUnit.MILLISECONDS, SynchronousQueue())
    private var stopped = false

    @Synchronized
    fun execute(socket: Socket, handle: (Socket) -> Unit) {
        if (stopped) {
            socket.close()
            return
        }
        sockets.add(socket)
        try {
            workers.execute {
                try {
                    socket.use { runCatching { handle(it) } }
                } finally {
                    sockets.remove(socket)
                }
            }
        } catch (_: RejectedExecutionException) {
            sockets.remove(socket)
            socket.close()
        }
    }

    @Synchronized
    fun close() {
        stopped = true
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }
}



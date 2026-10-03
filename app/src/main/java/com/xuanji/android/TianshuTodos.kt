package com.xuanji.android

import com.xuanji.android.ui.chat.NativeSlashRequest
import kotlinx.serialization.json.*

data class TianshuTodo(val id: String, val content: String, val status: String, val activeForm: String? = null) {
    val statusLabel: String get() = when (status) {
        "in_progress" -> "进行中"
        "completed" -> "已完成"
        else -> "待办"
    }
}

data class TianshuTodos(val items: List<TianshuTodo>, val revision: String, val notice: String = "", val seq: Long = 0)

internal fun newestTianshuTodos(current: TianshuTodos?, incoming: TianshuTodos): TianshuTodos =
    if (current != null && incoming.seq < current.seq) current else incoming

internal data class TodoCommand(val operation: String, val id: String? = null,
    val content: String? = null, val direction: String? = null) {
    val readOnly: Boolean get() = operation == "list"
    val resultLabel: String get() = when (operation) {
        "add" -> "任务已添加"
        "done" -> "任务已完成"
        "start" -> "任务已开始"
        "pending" -> "已恢复待办"
        "edit" -> "修改已保存"
        "skip" -> "已从清单移除"
        "move" -> "顺序已更新"
        else -> ""
    }
}

internal fun parseTodoCommand(argument: String): TodoCommand {
    if (argument.isBlank()) return TodoCommand("list")
    val match = Regex("^([a-z]+)(?:\\s([\\s\\S]*))?$", RegexOption.IGNORE_CASE).matchEntire(argument.trimStart())
        ?: error("用法：/todo [list|add <内容>|done <ID>|start <ID>|pending <ID>|edit <ID> <内容>|skip <ID>|move <ID> up|down]")
    val operation = match.groupValues[1].lowercase()
    val rest = match.groupValues[2]
    fun content(value: String): String {
        require(value.isNotBlank() && value.length <= 10000) { "任务内容不能为空，最多 10000 字" }
        return value
    }
    fun id(value: String): String {
        val decoded = if (value.startsWith('"')) {
            val primitive = Json.parseToJsonElement(value) as? JsonPrimitive ?: error("任务 ID 格式无效")
            require(primitive.isString) { "任务 ID 格式无效" }
            primitive.content
        } else {
            require(value.none(Char::isWhitespace)) { "请填写完整任务 ID 或唯一前缀" }
            value
        }
        require(decoded.isNotBlank() && decoded.length <= 128) { "请填写完整任务 ID 或唯一前缀" }
        return decoded
    }
    return when (operation) {
        "list" -> { require(rest.isBlank()) { "/todo list 不接收额外参数" }; TodoCommand("list") }
        "add" -> TodoCommand(operation, content = content(rest))
        "done", "start", "pending", "skip" -> TodoCommand(operation, id = id(rest.trim()))
        "edit" -> {
            val edit = Regex("^(\"(?:[^\"\\\\]|\\\\.)*\"|\\S+)\\s([\\s\\S]+)$").matchEntire(rest.trimStart()) ?: error("用法：/todo edit <ID> <内容>")
            TodoCommand(operation, id(edit.groupValues[1]), content(edit.groupValues[2]))
        }
        "move" -> {
            val move = Regex("^(\"(?:[^\"\\\\]|\\\\.)*\"|\\S+)\\s+(up|down)$").matchEntire(rest.trim())
                ?: error("用法：/todo move <ID> up|down")
            TodoCommand(operation, id(move.groupValues[1]), direction = move.groupValues[2])
        }
        else -> error("待办操作无效，请使用 /todo 查看清单")
    }
}

internal fun todoIdArgument(id: String): String = if (id.any(Char::isWhitespace) || id.startsWith('"'))
    JsonPrimitive(id).toString() else id

internal fun parseTianshuTodos(response: JsonObject, requireDurable: Boolean = false): TianshuTodos {
    val entries = response["items"] as? JsonArray ?: error("天枢未返回待办清单")
    val revision = response["revision"]?.jsonPrimitive?.contentOrNull.orEmpty()
    require(Regex("^[a-f0-9]{64}$").matches(revision)) { "待办版本缺失或无效" }
    require(entries.size <= 500) { "待办清单超过 500 项" }
    if (requireDurable) check(response["durable"]?.jsonPrimitive?.booleanOrNull == true) { "天枢未确认待办已保存" }
    val seen = hashSetOf<String>()
    val items = entries.map { element ->
        val item = element as? JsonObject ?: error("待办条目格式无效")
        val id = item["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val content = item["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val status = item["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val activeForm = item["activeForm"]?.jsonPrimitive?.contentOrNull
        require(id.isNotBlank() && id.length <= 128 && seen.add(id)) { "待办 ID 缺失或重复" }
        require(content.isNotBlank() && content.length <= 10000) { "待办内容无效" }
        require(status in setOf("pending", "in_progress", "completed")) { "待办状态不兼容" }
        require(activeForm == null || activeForm.isNotBlank() && activeForm.length <= 10000) { "待办进行时描述无效" }
        TianshuTodo(id, content, status, activeForm)
    }
    val seq = response["seq"]?.jsonPrimitive?.longOrNull ?: 0L
    require(seq in 0..9_007_199_254_740_991L) { "待办事件序号无效" }
    return TianshuTodos(items, revision, response["notice"]?.jsonPrimitive?.contentOrNull.orEmpty(), seq)
}

internal fun todoMutationPayload(command: TodoCommand, expectedRevision: String): JsonObject {
    require(!command.readOnly) { "读取清单不能作为修改提交" }
    require(Regex("^[a-f0-9]{64}$").matches(expectedRevision)) { "请先刷新待办清单" }
    return buildJsonObject {
        put("operation", command.operation)
        put("expectedRevision", expectedRevision)
        command.id?.let { put("id", it) }
        command.content?.let { put("content", it) }
        command.direction?.let { put("direction", it) }
    }
}

internal suspend fun executeTodoCommand(client: TianshuClient, id: String, request: NativeSlashRequest): SessionCommandOutput {
    val command = parseTodoCommand(request.argument)
    if (command.readOnly) return SessionCommandOutput("任务清单", "", todos = client.sessionTodos(id))
    val revision = request.expectedRevision ?: client.sessionTodos(id).revision
    val updated = client.updateSessionTodos(id, command, revision)
    return SessionCommandOutput("任务清单", command.resultLabel, todos = updated, todoMutationArgument = request.argument)
}

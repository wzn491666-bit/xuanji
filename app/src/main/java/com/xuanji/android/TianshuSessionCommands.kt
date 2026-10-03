package com.xuanji.android

import com.xuanji.android.ui.chat.SkillEntry
import kotlinx.serialization.json.*

data class TianshuDomainChoice(val key: String, val name: String, val detail: String, val current: Boolean)
data class SkillCatalog(val skills: List<SkillEntry>, val loadErrors: List<String>)

data class TianshuTeamCheckpoint(val groupId: String, val wave: Int, val totalWaves: Int, val timestamp: Long)

data class TianshuQueueEntry(val id: String, val text: String, val status: String, val timestamp: Long,
    val imageCount: Int = 0, val documentNames: List<String> = emptyList()) {
    val hasAttachments: Boolean get() = imageCount > 0 || documentNames.isNotEmpty()
    val statusLabel: String get() = when (status) {
        "queued" -> "等待下一轮"
        "merged" -> "已进入下一轮"
        "steered" -> "已转为即时引导"
        "retracted" -> "已撤回"
        "interrupted" -> "服务重启，已中断"
        else -> "状态未知"
    }
}

data class TianshuQueueSnapshot(val entries: List<TianshuQueueEntry>, val seq: Long)

internal sealed interface QueueCommand {
    data object ListEntries : QueueCommand
    data class Enqueue(val text: String) : QueueCommand
    data class Retract(val laneId: String) : QueueCommand
}

internal sealed interface SteerCommand {
    data object Show : SteerCommand
    data class Text(val text: String) : SteerCommand
    data class Lane(val laneId: String) : SteerCommand
}

private fun validLaneId(value: String): Boolean = value.isNotBlank() && value.none { it.isWhitespace() || it.isISOControl() }

internal fun parseQueueCommand(argument: String): QueueCommand {
    val text = argument.trim()
    if (text.isBlank() || text == "list") return QueueCommand.ListEntries
    if (text == "retract" || text.startsWith("retract ")) {
        val lane = text.removePrefix("retract").trim()
        require(validLaneId(lane)) { "用法：/queue retract <消息 id>" }
        return QueueCommand.Retract(lane)
    }
    val message = text.removePrefix("-- ").trim()
    require(message.isNotBlank()) { "用法：/queue <跟进内容>" }
    return QueueCommand.Enqueue(message)
}

internal fun parseSteerCommand(argument: String): SteerCommand {
    val text = argument.trim()
    if (text.isBlank()) return SteerCommand.Show
    if (text == "--lane" || text.startsWith("--lane ")) {
        val lane = text.removePrefix("--lane").trim()
        require(validLaneId(lane)) { "用法：/steer --lane <消息 id>" }
        return SteerCommand.Lane(lane)
    }
    return SteerCommand.Text(text)
}

internal data class SkillToggle(val name: String, val enabled: Boolean)

internal fun parseSkillToggle(argument: String): SkillToggle {
    val parts = argument.trim().split(Regex("\\s+"), limit = 2)
    require(parts.size == 2 && parts[0].lowercase() in setOf("on", "off", "enable", "disable") &&
        parts[1].isNotBlank() && parts[1].none { it.isWhitespace() || it.isISOControl() }) { "用法：/skill on|off <技能名>" }
    return SkillToggle(parts[1], parts[0].lowercase() in setOf("on", "enable"))
}
data class SessionCommandOutput(val title: String, val text: String, val domains: List<TianshuDomainChoice> = emptyList(),
    val plans: TianshuPlanCatalog? = null, val plan: TianshuPlan? = null, val editPlan: Boolean = false,
    val goal: TianshuGoal? = null, val showGoal: Boolean = false,
    val todos: TianshuTodos? = null, val todoMutationArgument: String? = null,
    val checkpoints: List<TianshuTeamCheckpoint>? = null, val queue: TianshuQueueSnapshot? = null,
    val handoffPending: Boolean = false)

internal fun parseTeamCheckpoints(response: JsonObject): List<TianshuTeamCheckpoint> {
    val entries = response["checkpoints"] as? JsonArray ?: error("天枢未返回编队检查点")
    return entries.map { element ->
        val entry = element.jsonObject
        val id = entry["groupId"]?.jsonPrimitive?.contentOrNull ?: error("编队检查点缺少标识")
        require(validLaneId(id) && id !in setOf(".", "..") && id.none { it == '/' || it == '\\' }) { "编队检查点标识无效" }
        TianshuTeamCheckpoint(id, entry["wave"]?.jsonPrimitive?.intOrNull ?: error("检查点缺少波次"),
            entry["totalWaves"]?.jsonPrimitive?.intOrNull ?: error("检查点缺少总波次"),
            entry["timestamp"]?.jsonPrimitive?.longOrNull ?: error("检查点缺少时间"))
    }
}

internal fun parseTianshuQueue(response: JsonObject, sessionId: String): TianshuQueueSnapshot {
    check(response["id"]?.jsonPrimitive?.contentOrNull == sessionId) { "队列快照与当前会话不匹配" }
    val entries = response["lanes"] as? JsonArray ?: error("天枢未返回消息队列")
    val seq = response["seq"]?.jsonPrimitive?.longOrNull ?: error("队列快照缺少事件序号")
    return TianshuQueueSnapshot(entries.map { element ->
        val entry = element.jsonObject
        val id = entry["id"]?.jsonPrimitive?.contentOrNull ?: error("队列条目缺少标识")
        val status = entry["status"]?.jsonPrimitive?.contentOrNull
        check(validLaneId(id) && status in setOf("queued", "merged", "steered", "retracted")) { "队列条目状态不兼容" }
        TianshuQueueEntry(id, entry["text"]?.jsonPrimitive?.contentOrNull ?: error("队列条目缺少正文"), checkNotNull(status),
            entry["ts"]?.jsonPrimitive?.longOrNull ?: error("队列条目缺少时间"),
            entry["imageCount"]?.jsonPrimitive?.intOrNull ?: 0,
            (entry["documentNames"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content })
    }.distinctBy { it.id }, seq)
}

internal fun reconcileQueueSnapshot(previous: TianshuQueueSnapshot?, fresh: TianshuQueueSnapshot): TianshuQueueSnapshot {
    if (previous != null && previous.seq > fresh.seq) return previous
    val ids = fresh.entries.map { it.id }.toSet()
    val interrupted = previous?.entries.orEmpty().filter { it.status == "queued" && it.id !in ids }
        .map { it.copy(status = "interrupted") }
    return fresh.copy(entries = (fresh.entries + interrupted).sortedByDescending { it.timestamp }.take(100))
}

internal fun queueAfterEvent(previous: TianshuQueueSnapshot?, event: SessionEvent): TianshuQueueSnapshot? {
    if (event.type !in setOf("queue_pending", "queue_status") || (event.seq > 0 && previous != null && event.seq <= previous.seq)) return previous
    val lane = event.str("laneId")?.takeIf(::validLaneId) ?: return previous
    val entries = previous?.entries.orEmpty()
    val next = if (event.type == "queue_pending") {
        val text = event.str("text") ?: return previous
        entries.filterNot { it.id == lane } + TianshuQueueEntry(lane, text, "queued", event.ts,
            event.data["imageCount"]?.jsonPrimitive?.intOrNull ?: 0,
            (event.data["documentNames"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content })
    } else {
        val status = event.str("status")?.takeIf { it in setOf("merged", "steered", "retracted") } ?: return previous
        entries.map { if (it.id == lane) it.copy(status = status) else it }
    }
    return TianshuQueueSnapshot(next.sortedByDescending { it.timestamp }.take(100), maxOf(previous?.seq ?: 0, event.seq))
}

internal fun parseSkillCatalog(response: JsonObject): SkillCatalog {
    val entries = response["skills"] as? JsonArray ?: error("天枢技能接口缺少 skills 列表，请检查运行时版本")
    val skills = entries.map { element ->
        val entry = element as? JsonObject ?: error("天枢技能条目格式异常")
        val name = entry["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: error("天枢技能缺少名称")
        SkillEntry(name, entry["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            entry["source"]?.jsonPrimitive?.contentOrNull.orEmpty(), entry["enabled"]?.jsonPrimitive?.booleanOrNull ?: true)
    }
    val errors = response["loadErrors"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
    return SkillCatalog(skills, errors)
}

internal fun parseTianshuDomains(response: JsonObject): List<TianshuDomainChoice> {
    val entries = response["entries"] as? JsonArray ?: error("天枢未返回星域列表")
    return entries.map { element ->
        val entry = element.jsonObject
        TianshuDomainChoice(entry["key"]?.jsonPrimitive?.contentOrNull ?: error("星域缺少标识"),
            entry["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            entry["tagline"]?.jsonPrimitive?.contentOrNull ?: entry["motto"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            entry["current"]?.jsonPrimitive?.booleanOrNull == true)
    }
}

package com.xuanji.android

import com.xuanji.android.ui.chat.NativeSlashRequest
import com.xuanji.android.ui.chat.PaletteAction
import kotlinx.serialization.json.*
import kotlin.math.roundToLong

data class TianshuGoalConfig(val goal: String, val maxIterations: Int = 100, val wallClockMs: Long? = null,
    val successCriteria: List<String> = emptyList())
data class TianshuGoal(val id: String, val goal: String, val status: String, val iteration: Int,
    val maxIterations: Int, val elapsedMs: Long, val budgetMs: Long?, val criteria: List<String>,
    val terminalReason: String, val verdict: String) {
    val statusLabel: String get() = when (status) {
        "active" -> "目标进行中"
        "paused" -> "已暂停续跑"
        "blocked" -> "等待解决阻塞"
        "complete" -> if (terminalReason == "cancelled") "已取消" else "已完成"
        else -> status
    }
}

internal val goalActions = setOf(PaletteAction.GOAL, PaletteAction.GOAL_STATUS, PaletteAction.GOAL_PAUSE,
    PaletteAction.GOAL_RESUME, PaletteAction.GOAL_CANCEL, PaletteAction.GOAL_CRITERIA)

internal fun parseGoalConfig(argument: String): TianshuGoalConfig {
    val tokens = mutableListOf<String>()
    val token = StringBuilder()
    var quote: Char? = null
    var index = 0
    while (index < argument.length) {
        val c = argument[index++]
        when {
            c == quote -> quote = null
            quote == null && (c == '\'' || c == '"') -> quote = c
            quote == '"' && c == '\\' && index < argument.length && argument[index] in "\\\"" -> token.append(argument[index++])
            quote == null && c.isWhitespace() -> if (token.isNotEmpty()) { tokens += token.toString(); token.clear() }
            else -> token.append(c)
        }
    }
    require(quote == null) { "目标参数的引号未闭合" }
    if (token.isNotEmpty()) tokens += token.toString()
    var max = 100
    var budget: Long? = null
    var criteria = emptyList<String>()
    val words = mutableListOf<String>()
    val flags = mutableSetOf<String>()
    index = 0
    while (index < tokens.size) {
        val word = tokens[index++]
        if (!word.startsWith("--")) { words += word; continue }
        require(word in setOf("--max", "--budget", "--criteria")) { "未知目标参数：$word" }
        require(flags.add(word)) { "目标参数重复：$word" }
        require(index < tokens.size) { "$word 缺少值" }
        val value = tokens[index++]
        when (word) {
            "--max" -> max = value.toIntOrNull()?.takeIf { it > 0 } ?: error("--max 必须是正整数")
            "--budget" -> {
                val minutes = value.toDoubleOrNull()
                require(minutes != null && minutes.isFinite() && minutes > 0 && minutes <= Long.MAX_VALUE / 60000.0) { "--budget 必须是有效的正数（分钟）" }
                budget = (minutes * 60000).roundToLong()
                require(budget > 0) { "时间预算不能小于 1 毫秒" }
            }
            "--criteria" -> {
                val array = Json.parseToJsonElement(value) as? JsonArray ?: error("验收项必须是 JSON 字符串数组")
                criteria = array.map {
                    val primitive = it as? JsonPrimitive
                    require(primitive?.isString == true && primitive.content.isNotBlank()) { "验收项必须是非空字符串" }
                    primitive.content
                }
            }
        }
    }
    require(words.isNotEmpty()) { "请填写目标" }
    return TianshuGoalConfig(words.joinToString(" "), max, budget, criteria)
}

internal fun goalConfigPayload(config: TianshuGoalConfig) = buildJsonObject {
    require(config.goal.isNotBlank() && config.maxIterations > 0 && (config.wallClockMs == null || config.wallClockMs > 0))
    put("goal", config.goal); put("maxIterations", config.maxIterations)
    config.wallClockMs?.let { put("wallClockMs", it) }
    if (config.successCriteria.isNotEmpty()) put("successCriteria", JsonArray(config.successCriteria.map(::JsonPrimitive)))
}

internal fun goalCommandArgument(config: TianshuGoalConfig): String {
    fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    return buildString {
        append(quote(config.goal)); append(" --max ${config.maxIterations}")
        config.wallClockMs?.let { append(" --budget ${it / 60000.0}") }
        if (config.successCriteria.isNotEmpty()) {
            append(" --criteria "); append(quote(JsonArray(config.successCriteria.map(::JsonPrimitive)).toString()))
        }
    }
}

internal fun parseTianshuGoal(response: JsonObject): TianshuGoal {
    fun text(key: String) = response[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    require(text("goalId").isNotBlank() && text("goal").isNotBlank()) { "天枢未返回有效目标" }
    require(text("status") in setOf("active", "paused", "blocked", "complete")) { "天枢目标状态不兼容" }
    return TianshuGoal(text("goalId"), text("goal"), text("status"),
        response["iteration"]?.jsonPrimitive?.intOrNull ?: error("目标缺少轮次"),
        response["maxIterations"]?.jsonPrimitive?.intOrNull ?: error("目标缺少轮次上限"),
        response["wallClockElapsedMs"]?.jsonPrimitive?.longOrNull ?: error("目标缺少已用时间"),
        response["wallClockBudgetMs"]?.jsonPrimitive?.longOrNull,
        response["successCriteria"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(), text("terminalReason"),
        (response["lastVerdict"] as? JsonObject)?.get("summary")?.jsonPrimitive?.contentOrNull.orEmpty())
}

// Same driver text as upstream src/agent/goal-tracker.ts (Tianshu 3.27.0).
internal fun goalKickoff(goal: String) = "[GOAL MODE] $goal\n\nYou are now in goal-driven mode. Work toward this goal continuously. When fully complete, output \"GOAL ACHIEVED\" on its own line. If you encounter a blocker you cannot resolve, output \"GOAL BLOCKED\" on its own line followed by a brief explanation."

internal fun displayGoalPrompt(text: String): String {
    if (text.startsWith("[GOAL MODE] ")) {
        val goal = text.removePrefix("[GOAL MODE] ").substringBefore("\n\nYou are now in goal-driven mode.")
        if (goalKickoff(goal) == text) return "/goal $goal"
    }
    if (text.startsWith("[GOAL RESUME] 继续执行目标: ")) return "/goal-resume"
    return text
}

internal data class GoalCommandResult(val output: SessionCommandOutput, val kickoff: String? = null, val display: String = "")

internal suspend fun executeGoalCommand(client: TianshuClient, id: String, request: NativeSlashRequest): GoalCommandResult {
    val previous = client.sessionGoal(id)
    if (request.action == PaletteAction.GOAL && request.argument.isNotBlank()) {
        check(previous == null || previous.status == "complete") { "当前目标尚未结束，请先取消后再创建新目标" }
        val config = parseGoalConfig(request.argument)
        client.prepareGoalAgent(id)
        val goal = client.createSessionGoal(id, config)
        return GoalCommandResult(SessionCommandOutput("自主目标", "", goal = goal), goalKickoff(config.goal), "/goal ${config.goal}")
    }
    val goal = when (request.action) {
        PaletteAction.GOAL, PaletteAction.GOAL_STATUS, PaletteAction.GOAL_CRITERIA -> previous
        PaletteAction.GOAL_PAUSE -> {
            check(previous?.status == "active") { "当前没有可暂停的目标" }
            client.controlSessionGoal(id, "pause")
        }
        PaletteAction.GOAL_RESUME -> {
            check(previous?.status in setOf("paused", "blocked")) { "当前没有可恢复的目标" }
            client.controlSessionGoal(id, "resume")
        }
        PaletteAction.GOAL_CANCEL -> {
            check(previous != null && previous.status != "complete") { "当前没有可取消的目标" }
            client.controlSessionGoal(id, "cancel")
        }
        else -> error("不支持的目标命令")
    }
    return GoalCommandResult(SessionCommandOutput("自主目标", "", goal = goal, showGoal = true),
        if (request.action == PaletteAction.GOAL_RESUME) "[GOAL RESUME] 继续执行目标: ${goal!!.goal}" else null,
        if (request.action == PaletteAction.GOAL_RESUME) "/goal-resume" else "")
}

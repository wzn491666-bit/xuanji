package com.xuanji.android.ui.chat

import com.xuanji.android.parseGoalConfig
import com.xuanji.android.parseTodoCommand
import com.xuanji.android.parseQueueCommand
import com.xuanji.android.parseSteerCommand
import com.xuanji.android.parseSkillToggle
import com.xuanji.android.QueueCommand

enum class PaletteAction {
    HELP, NEW_SESSION, HISTORY, MODELS, EFFORT, THINKING, PERMISSIONS,
    COCKPIT, FILES, TERMINAL, SETTINGS, SKILLS, STOP, RECONNECT,
    DOMAIN, PLAN_MODE, ASK_MODE, REVIEW_GATE, FAST,
    PLAN_LIST, PLAN_VIEW, PLAN_EDIT, PLAN_APPROVE, PLAN_REJECT,
    GOAL, GOAL_STATUS, GOAL_PAUSE, GOAL_RESUME, GOAL_CANCEL, GOAL_CRITERIA, TODO,
    COMPACT, FORK, HANDOFF, TEAM_RESUME, QUEUE, STEER, SKILL_MANAGE,
}

data class NativeSlashRequest(val action: PaletteAction, val command: String, val argument: String = "", val expectedRevision: String? = null)

sealed interface NativeSlashResult {
    data class Execute(val request: NativeSlashRequest) : NativeSlashResult
    data class Invalid(val message: String) : NativeSlashResult
}

internal fun nativeSlashBlocked(request: NativeSlashRequest, canSwitch: Boolean, sessionReady: Boolean,
    effortBusy: Boolean = false, controlBusy: Boolean = false, stopping: Boolean = false,
    running: Boolean = !canSwitch): Boolean = when (request.action) {
    PaletteAction.NEW_SESSION -> !canSwitch
    PaletteAction.MODELS -> request.argument.isNotEmpty() && !request.argument.equals("list", true) && (!canSwitch || !sessionReady)
    PaletteAction.HISTORY -> request.argument.isNotEmpty() && !canSwitch
    PaletteAction.EFFORT -> request.argument.isNotEmpty() && (!sessionReady || effortBusy || controlBusy || stopping)
    PaletteAction.PERMISSIONS -> request.command in setOf("/permission", "/permissions") && request.argument.isNotEmpty() &&
        (!sessionReady || effortBusy || controlBusy || stopping)
    PaletteAction.DOMAIN -> !sessionReady || controlBusy ||
        (request.argument.isNotEmpty() && !request.argument.equals("list", true) && !canSwitch)
    PaletteAction.PLAN_MODE, PaletteAction.ASK_MODE -> !sessionReady || controlBusy ||
        (!request.argument.equals("status", true) && !canSwitch)
    PaletteAction.REVIEW_GATE, PaletteAction.FAST -> !sessionReady || effortBusy || controlBusy || stopping
    PaletteAction.PLAN_LIST, PaletteAction.PLAN_VIEW -> !sessionReady || controlBusy || stopping
    PaletteAction.PLAN_EDIT, PaletteAction.PLAN_APPROVE, PaletteAction.PLAN_REJECT -> !sessionReady || !canSwitch
    PaletteAction.GOAL -> !sessionReady || controlBusy || stopping || (request.argument.isNotBlank() && !canSwitch)
    PaletteAction.GOAL_RESUME -> !sessionReady || !canSwitch
    PaletteAction.GOAL_STATUS, PaletteAction.GOAL_CRITERIA, PaletteAction.GOAL_PAUSE, PaletteAction.GOAL_CANCEL -> !sessionReady || controlBusy || stopping || effortBusy
    PaletteAction.TODO -> !sessionReady || controlBusy || stopping ||
        (runCatching { !parseTodoCommand(request.argument).readOnly }.getOrDefault(true) && !canSwitch)
    PaletteAction.COMPACT, PaletteAction.FORK, PaletteAction.HANDOFF -> !sessionReady || !canSwitch
    PaletteAction.TEAM_RESUME -> !sessionReady || controlBusy || stopping ||
        (request.argument.isNotBlank() && !canSwitch)
    PaletteAction.SKILL_MANAGE -> !sessionReady || !canSwitch
    PaletteAction.QUEUE -> !sessionReady || controlBusy || effortBusy || stopping ||
        (runCatching { parseQueueCommand(request.argument) is QueueCommand.Enqueue }.getOrDefault(true) && !running)
    PaletteAction.STEER -> !sessionReady || controlBusy || effortBusy || stopping ||
        (request.argument.isNotBlank() && !running)
    else -> false
}

private val nativeSlashActions = mapOf(
    "/help" to PaletteAction.HELP,
    "/new" to PaletteAction.NEW_SESSION,
    "/sessions" to PaletteAction.HISTORY, "/resume" to PaletteAction.HISTORY,
    "/model" to PaletteAction.MODELS,
    "/effort" to PaletteAction.EFFORT, "/thinking" to PaletteAction.THINKING,
    "/permission" to PaletteAction.PERMISSIONS, "/permissions" to PaletteAction.PERMISSIONS,
    "/mcp" to PaletteAction.PERMISSIONS, "/tools" to PaletteAction.PERMISSIONS, "/doctor" to PaletteAction.PERMISSIONS,
    "/cockpit" to PaletteAction.COCKPIT, "/status" to PaletteAction.COCKPIT,
    "/cache" to PaletteAction.COCKPIT, "/context" to PaletteAction.COCKPIT,
    "/verify" to PaletteAction.COCKPIT, "/evidence" to PaletteAction.COCKPIT,
    "/mission" to PaletteAction.COCKPIT, "/todo" to PaletteAction.TODO,
    "/tasks" to PaletteAction.COCKPIT, "/jobs" to PaletteAction.COCKPIT,
    "/files" to PaletteAction.FILES, "/terminal" to PaletteAction.TERMINAL,
    "/settings" to PaletteAction.SETTINGS, "/setup" to PaletteAction.SETTINGS,
    "/connect" to PaletteAction.SETTINGS, "/config" to PaletteAction.SETTINGS,
    "/vision" to PaletteAction.SETTINGS,
    "/skills" to PaletteAction.SKILLS,
    "/stop" to PaletteAction.STOP, "/reconnect" to PaletteAction.RECONNECT,
    "/domain" to PaletteAction.DOMAIN,
    "/plan-mode" to PaletteAction.PLAN_MODE, "/ask" to PaletteAction.ASK_MODE,
    "/fast" to PaletteAction.FAST,
    "/plan-list" to PaletteAction.PLAN_LIST, "/plan-view" to PaletteAction.PLAN_VIEW,
    "/plan-edit" to PaletteAction.PLAN_EDIT, "/plan-approve" to PaletteAction.PLAN_APPROVE,
    "/plan-reject" to PaletteAction.PLAN_REJECT,
    "/goal" to PaletteAction.GOAL, "/goal-status" to PaletteAction.GOAL_STATUS,
    "/goal-pause" to PaletteAction.GOAL_PAUSE, "/goal-resume" to PaletteAction.GOAL_RESUME,
    "/goal-cancel" to PaletteAction.GOAL_CANCEL, "/cancel-goal" to PaletteAction.GOAL_CANCEL,
    "/goal-criteria" to PaletteAction.GOAL_CRITERIA,
    "/compact" to PaletteAction.COMPACT, "/fork" to PaletteAction.FORK,
    "/handoff" to PaletteAction.HANDOFF, "/team-resume" to PaletteAction.TEAM_RESUME,
    "/queue" to PaletteAction.QUEUE, "/steer" to PaletteAction.STEER,
)

fun nativePaletteAction(command: String): PaletteAction? {
    val resolved = resolveNativeSlash(command) as? NativeSlashResult.Execute
    return resolved?.request?.action
}

fun resolveNativeSlash(text: String): NativeSlashResult? {
    val match = Regex("^(/\\S+)(?:\\s([\\s\\S]*))?$").matchEntire(text.trimStart()) ?: return null
    val command = match.groupValues[1].lowercase()
    val argument = if (command == "/todo") match.groupValues[2].trimStart() else match.groupValues[2].trim()
    if (command == "/review" && argument.lowercase() in setOf("off", "on", "status")) {
        return NativeSlashResult.Execute(NativeSlashRequest(PaletteAction.REVIEW_GATE, command, argument.lowercase()))
    }
    if (command == "/skill" && (argument.isBlank() || argument.lowercase() in setOf("list", "ls"))) {
        return NativeSlashResult.Execute(NativeSlashRequest(PaletteAction.SKILLS, command, argument))
    }
    if (command == "/skill" && argument.split(Regex("\\s+"), limit = 2).first().lowercase() in setOf("on", "off", "enable", "disable")) {
        runCatching { parseSkillToggle(argument) }.onFailure { return NativeSlashResult.Invalid(it.message ?: "技能参数无效") }
        return NativeSlashResult.Execute(NativeSlashRequest(PaletteAction.SKILL_MANAGE, command, argument))
    }
    val action = nativeSlashActions[command] ?: return null
    if (action == PaletteAction.TODO) {
        runCatching { parseTodoCommand(argument) }.onFailure { return NativeSlashResult.Invalid(it.message ?: "待办参数无效") }
        return NativeSlashResult.Execute(NativeSlashRequest(action, command, argument))
    }
    if (action == PaletteAction.QUEUE || action == PaletteAction.STEER) {
        runCatching {
            if (action == PaletteAction.QUEUE) parseQueueCommand(argument) else parseSteerCommand(argument)
        }.onFailure { return NativeSlashResult.Invalid(it.message ?: "消息参数无效") }
        return NativeSlashResult.Execute(NativeSlashRequest(action, command, argument))
    }
    if (action == PaletteAction.GOAL && argument.isNotBlank()) {
        runCatching { parseGoalConfig(argument) }.onFailure { return NativeSlashResult.Invalid(it.message ?: "目标参数无效") }
    }
    if (action == PaletteAction.GOAL_CRITERIA && argument.isNotBlank()) {
        return NativeSlashResult.Invalid("当前天枢 HTTP 接口支持读取判据；修改判据尚无官方接口。可在新目标使用 --criteria '[\"验收项\"]'。")
    }
    val invalid = when (command) {
        "/resume" -> argument.any(Char::isWhitespace)
        "/model" -> argument.any(Char::isWhitespace)
        "/effort" -> argument.isNotEmpty() && argument.lowercase() !in setOf("off", "low", "medium", "high", "max", "auto")
        "/thinking" -> argument.isNotEmpty() && argument.lowercase() !in setOf("collapsed", "preview", "expanded")
        "/permission", "/permissions" -> argument.isNotEmpty() && argument.lowercase() !in setOf("manual", "auto-safe", "dangerously-skip-permissions")
        "/tools", "/mcp" -> argument.isNotEmpty() && argument != "status"
        "/domain" -> argument.any(Char::isWhitespace)
        "/plan-mode", "/ask" -> argument.isNotEmpty() && argument.lowercase() !in setOf("on", "off", "status")
        "/plan-view", "/plan-edit" -> argument.any(Char::isWhitespace)
        "/plan-approve", "/plan-reject" -> false
        "/goal" -> false
        "/fork", "/handoff" -> false
        "/team-resume" -> argument.any(Char::isWhitespace) || argument in setOf(".", "..") ||
            argument.any { it == '/' || it == '\\' || it.isISOControl() }
        else -> argument.isNotEmpty()
    }
    if (invalid) {
        val usage = when (command) {
            "/resume" -> "/resume [会话 id|序号]"
            "/model" -> "/model [list|模型 id]"
            "/effort" -> "/effort [off|low|medium|high|max|auto]"
            "/thinking" -> "/thinking [collapsed|preview|expanded]"
            "/permission", "/permissions" -> "$command [manual|auto-safe|dangerously-skip-permissions]"
            "/tools", "/mcp" -> "$command [status]"
            "/domain" -> "/domain [list|星域名称|auto|off]"
            "/plan-mode", "/ask" -> "$command [on|off|status]"
            "/plan-view", "/plan-edit" -> "$command [slug]"
            "/team-resume" -> "/team-resume [编队 id]"
            else -> command
        }
        return NativeSlashResult.Invalid("用法：$usage")
    }
    return NativeSlashResult.Execute(NativeSlashRequest(action, command, argument))
}

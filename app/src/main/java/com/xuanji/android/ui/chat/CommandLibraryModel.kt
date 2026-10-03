package com.xuanji.android.ui.chat

import java.text.Normalizer
import java.util.Locale

enum class CommandSection(val title: String, val subtitle: String) {
    COMMON("常用", "收藏与最近选择"),
    SESSION("会话", "历史、上下文与交接"),
    MODEL("模型", "模型、星域与思考"),
    COLLABORATION("协作", "计划、目标与团队"),
    REVIEW("审查", "验证、证据与观察"),
    SKILLS("技能", "会话技能与调用"),
    PROJECT("项目", "工作区自定义命令"),
    SYSTEM("系统", "配置、权限与工具"),
    REFERENCE("目录", "全部命令与接入状态"),
}

private val commandTitles = mapOf(
    "/help" to "命令目录", "/new" to "新建对话", "/sessions" to "历史对话", "/resume" to "恢复对话",
    "/compact" to "整理上下文", "/fork" to "创建对话分支", "/handoff" to "生成交接文档",
    "/queue" to "待发消息", "/steer" to "中途补充指令", "/team-resume" to "恢复团队任务",
    "/model" to "切换模型", "/model list" to "可用模型", "/effort" to "思考强度", "/thinking" to "思考显示",
    "/domain" to "选择星域", "/capsule" to "星域胶囊", "/fast" to "结束读专注阶段", "/zen" to "禅模式",
    "/plan" to "制定实现计划", "/write-plan" to "制定实现计划", "/plan-mode" to "只读计划模式",
    "/ask" to "只读问答模式", "/plan-list" to "计划列表", "/plan-view" to "阅读计划", "/plan-edit" to "编辑计划",
    "/plan-approve" to "批准计划", "/plan-reject" to "反馈修改意见", "/plan-close" to "计划收尾",
    "/goal" to "自主目标", "/goal-status" to "目标进度", "/goal-pause" to "暂停目标",
    "/goal-resume" to "恢复目标", "/goal-cancel" to "终止目标", "/goal-criteria" to "验收判据", "/todo" to "任务清单",
    "/team" to "并行团队", "/team max" to "团队强编队", "/galaxy" to "多维并行攻坚", "/scout" to "巡天侦察",
    "/council" to "专家会诊", "/starflow" to "贯通协作流程", "/review" to "对抗审查", "/review max" to "审查编队",
    "/review l1" to "审查纪律提醒", "/review l2" to "对抗验证", "/review l3" to "五席审查",
    "/review on" to "开启审查门", "/review off" to "关闭审查门", "/review status" to "审查门状态",
    "/cockpit" to "工作台", "/status" to "会话状态", "/cache" to "用量与缓存", "/context" to "上下文用量",
    "/verify" to "验证状态", "/evidence" to "任务证据", "/mission" to "任务契约", "/tasks" to "子代理任务", "/jobs" to "后台任务",
    "/skills" to "浏览技能", "/skill" to "调用技能", "/skill list" to "浏览技能", "/skill on" to "启用技能", "/skill off" to "停用技能",
    "/skill install" to "安装技能", "/skill review" to "审阅技能草稿", "/skill approve" to "批准技能草稿", "/skill reject" to "驳回技能草稿",
    "/files" to "工作区文件", "/terminal" to "终端", "/settings" to "设置", "/setup" to "设置",
    "/permissions" to "系统权限", "/permission" to "执行审批", "/mcp" to "Android 工具授权", "/tools" to "工具与授权",
    "/doctor" to "权限检查", "/connect" to "服务商配置", "/config" to "模型配置", "/vision" to "视觉模型配置",
    "/stop" to "停止本轮", "/reconnect" to "重新连接", "/branch" to "分支树", "/remember" to "长期记忆",
)

val MobileCommand.title: String get() = if (custom) name.removePrefix("/") else commandTitles[name]
    ?: description.substringBefore('，').substringBefore('；').take(22).ifBlank { name.removePrefix("/") }

val MobileCommand.librarySection: CommandSection get() = when {
    custom -> CommandSection.PROJECT
    name.startsWith("/skill") -> CommandSection.SKILLS
    name in setOf("/model", "/model list", "/effort", "/thinking", "/domain", "/capsule", "/fast", "/zen") -> CommandSection.MODEL
    name in setOf("/help", "/new", "/sessions", "/resume", "/compact", "/fork", "/branch", "/handoff", "/queue", "/steer", "/memory", "/remember", "/clear", "/btw") -> CommandSection.SESSION
    group == "计划与协作" -> CommandSection.COLLABORATION
    group == "审查与验证" || name in setOf("/cockpit", "/status", "/cache", "/context", "/tasks", "/jobs", "/sensorium", "/prefix-budget") -> CommandSection.REVIEW
    else -> CommandSection.SYSTEM
}

val MobileCommand.searchTerms: String get() = when (name) {
    "/compact" -> "压缩 整理 总结 context"
    "/fork", "/branch" -> "分叉 分支 branch fork"
    "/handoff" -> "交接 移交 handover"
    "/queue" -> "排队 队列 待发 queue"
    "/steer" -> "追加 补充 干预 steer"
    "/team-resume" -> "检查点 波次 断点 恢复 checkpoint"
    "/effort" -> "推理 强度 reasoning"
    "/domain" -> "人格 星域 domain"
    "/skills", "/skill", "/skill list" -> "技能 skills"
    "/cockpit" -> "任务 工作台 驾驶舱 仪表盘 cockpit"
    else -> ""
}

private val equivalentCommands = mapOf(
    "/write-plan" to "/plan", "/resume" to "/sessions", "/model list" to "/model", "/skill list" to "/skills", "/skill" to "/skills",
    "/setup" to "/settings", "/connect" to "/settings", "/config" to "/settings", "/vision" to "/settings",
    "/mcp" to "/permissions", "/tools" to "/permissions", "/doctor" to "/permissions",
    "/status" to "/cockpit", "/cache" to "/cockpit", "/context" to "/cockpit", "/verify" to "/cockpit",
    "/evidence" to "/cockpit", "/mission" to "/cockpit", "/tasks" to "/cockpit", "/jobs" to "/cockpit",
)

/** Collapse navigation aliases only in browsing; exact slash search still reveals the requested entry. */
fun libraryCommands(commands: List<MobileCommand>, searching: Boolean): List<MobileCommand> = if (searching) commands else
    commands.groupBy { if (it.custom) it.name else equivalentCommands[it.name] ?: it.name }.map { (name, entries) ->
        entries.firstOrNull { it.name == name } ?: entries.first()
    }

private fun normalizeSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace('/', ' ').trim().replace(Regex("\\s+"), " ")

internal fun <T> rankCommandMatches(query: String, values: List<T>, fields: (T) -> List<String>): List<T> {
    val needle = normalizeSearch(query)
    if (needle.isEmpty()) return values
    val tokens = needle.split(' ')
    return values.mapIndexedNotNull { index, value ->
        val normalized = fields(value).map(::normalizeSearch)
        val corpus = normalized.joinToString(" ")
        if (!tokens.all { it in corpus }) return@mapIndexedNotNull null
        val score = when {
            normalized.firstOrNull() == needle -> 1000
            normalized.any { it == needle } -> 900
            normalized.firstOrNull()?.startsWith(needle) == true -> 800
            normalized.take(2).any { needle in it } -> 700
            normalized.any { needle in it } -> 600
            else -> 400
        }
        Triple(value, score, index)
    }.sortedWith(compareByDescending<Triple<T, Int, Int>> { it.second }.thenBy { it.third }).map { it.first }
}

val featuredCommandNames = listOf("/plan", "/team", "/review", "/todo", "/domain", "/compact", "/fork", "/handoff", "/team-resume", "/queue", "/steer", "/skills")

fun recentCommandSelection(previous: List<String>, selected: String): List<String> =
    (listOf(selected) + previous.filterNot { it == selected }).take(8)

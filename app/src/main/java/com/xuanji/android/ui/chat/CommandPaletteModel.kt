package com.xuanji.android.ui.chat

data class SkillEntry(
    val name: String,
    val description: String,
    val source: String,
    val enabled: Boolean,
) {
    fun inputText(): String = "/skill $name "
}

data class MobileCommand(
    val name: String,
    val description: String,
    val argsHint: String = "",
    val group: String = "会话与模型",
    val mobileExecutable: Boolean = false,
    val nativeAction: PaletteAction? = null,
    val custom: Boolean = false,
) {
    val category: String get() = group
    fun inputText(): String = "$name "
    fun isSelectable(hasNativeHandler: Boolean): Boolean = mobileExecutable && (nativeAction == null || hasNativeHandler)
    val unavailableReason: String get() = when {
        custom -> description.removePrefix("不可用：")
        group == "桌面专用" -> "此命令依赖天枢终端交互，尚未接入手机端"
        name == "/capsule" -> "天枢 HTTP 接口尚未提供胶囊注入与摘除操作"
        name == "/zen" -> "当前接口支持 /fast 跳过读专注阶段，尚不支持手动开关禅模式"
        name in setOf("/chat", "/mode", "/task") -> "天枢已改为自动识别任务模式，此命令已无需使用"
        else -> "此命令尚未接入 Android 执行入口"
    }
}

data class CommandPaletteResults(val workflows: List<MobileCommand>, val skills: List<SkillEntry>)

object MobileCommandCatalog {
    private val bridgeCommands = setOf(
        "/plan", "/write-plan", "/plan-close", "/team", "/team max", "/scout", "/council", "/galaxy",
        "/review", "/review max", "/review l1", "/review l2", "/review l3", "/skill",
    )
    private data class Meta(val name: String, val description: String, val args: String = "", val group: String)

    // Only prompt routes verified in the bundled sidecar are enabled here.
    private val official = listOf(
        Meta("/help", "显示所有命令及用法说明", group = "会话与模型"), Meta("/yolo", "跳过权限确认并持久化为默认；off 退出", group = "权限与调试"),
        Meta("/yes", "跳过权限确认；输入 off 退出", "off", "权限与调试"), Meta("/btw", "侧问当前会话，不进入历史", "<问题>", "会话与模型"),
        Meta("/queue", "排队一条消息到下一轮", "<text>", "会话与模型"), Meta("/compact", "压缩上下文并保留关键决策", group = "会话与模型"),
        Meta("/connect", "连接模型服务商并配置 API 密钥", group = "会话与模型"), Meta("/login", "通过浏览器完成 OAuth 登录", "[provider]", "会话与模型"),
        Meta("/disconnect", "断开服务商并清除密钥", group = "会话与模型"), Meta("/vision", "配置独立视觉模型", group = "会话与模型"),
        Meta("/config", "设置子代理、审查和识图模型", group = "会话与模型"), Meta("/model", "查看或切换当前会话模型", "list|<model-id>", "会话与模型"), Meta("/model list", "列出所有可用模型", group = "会话与模型"),
        Meta("/chat", "切换到轻量聊天模式", group = "会话与模型"), Meta("/task", "旧版任务模式", group = "桌面专用"), Meta("/tasks", "打开子代理任务面板", group = "桌面专用"),
        Meta("/jobs", "打开后台任务面板", group = "桌面专用"), Meta("/cache", "查看 token 消耗、命中率和缓存", group = "权限与调试"),
        Meta("/mode", "切换提示词模式", group = "会话与模型"), Meta("/verify", "显示所有改动的验证状态", group = "审查与验证"),
        Meta("/verbose", "显示完整工具参数与返回", group = "权限与调试"), Meta("/new", "开新会话", group = "会话与模型"), Meta("/clear", "清屏但保留会话历史", group = "会话与模型"),
        Meta("/sessions", "列出历史会话", group = "会话与模型"), Meta("/resume", "继续历史会话", "[id|序号]", "会话与模型"), Meta("/undo", "预览或撤销文件改动", "[N|preview]", "权限与调试"),
        Meta("/rollback", "预览或确认检查点回滚", "[confirm|cancel]", "权限与调试"), Meta("/evidence", "显示上一轮证据链", group = "审查与验证"), Meta("/context", "显示上下文账本", group = "权限与调试"),
        Meta("/memory", "显示会话记忆", group = "会话与模型"), Meta("/permission", "切换权限模式", group = "权限与调试"), Meta("/mission", "查看当前任务契约", group = "计划与协作"),
        Meta("/goal", "设定跨多轮自主目标", "<目标> --max N", "计划与协作"), Meta("/goal-cancel", "终止自主目标", group = "计划与协作"), Meta("/goal-resume", "恢复暂停或阻塞的目标", group = "计划与协作"),
        Meta("/mcp", "查看 MCP 服务器和工具状态", group = "权限与调试"), Meta("/cockpit", "打开 Cockpit 驾驶舱", group = "审查与验证"), Meta("/scroll", "浏览历史输出", group = "桌面专用"),
        Meta("/theme", "切换配色主题", group = "桌面专用"), Meta("/fork", "把当前会话 fork 成分支", group = "计划与协作"), Meta("/handoff", "写结构化交接文档", "[备注]", "计划与协作"),
        Meta("/remember", "写入项目长期记忆", "<要记住的事>", "计划与协作"), Meta("/vim", "切换 vim 键位绑定", group = "桌面专用"), Meta("/effort", "切换推理强度", "off|low|medium|high|max|auto", "会话与模型"),
        Meta("/domain", "切换星域人格", "list|<name>|auto|off", "会话与模型"), Meta("/capsule", "注入星域方法论胶囊", "[off] <star>", "会话与模型"), Meta("/interview", "通过深度访谈澄清需求", group = "计划与协作"),
        Meta("/team", "团队模式：按文件拆分并行实现", "<任务|plan.md> | max", "计划与协作"), Meta("/scout", "巡天侦察并交付证据清单", "<诊断目标>", "计划与协作"), Meta("/council", "多星域专家对抗会诊", "<目标> [--rounds N]", "计划与协作"),
        Meta("/galaxy", "按维度并行执行并汇总复核", "<任务描述>", "计划与协作"), Meta("/starflow", "贯通评审、团队波次和攻坚", "<任务描述>", "计划与协作"), Meta("/plan", "只读调研并产出 TDD 实现计划", "<任务描述>", "计划与协作"),
        Meta("/write-plan", "/plan 的别名", "<任务描述>", "计划与协作"), Meta("/plan-mode", "切换只读计划编写模式", group = "计划与协作"), Meta("/ask", "切换纯问答模式", group = "计划与协作"),
        Meta("/plan-list", "列出待审批计划", group = "计划与协作"), Meta("/plan-view", "预览计划全文", "[slug]", "计划与协作"), Meta("/plan-approve", "审批计划并开始执行", "<slug> [option]", "计划与协作"),
        Meta("/plan-reject", "驳回计划并附反馈", "<slug> <反馈>", "计划与协作"), Meta("/plan-close", "预览或应用计划收尾", group = "计划与协作"), Meta("/review", "对抗审查当前改动", "max|off|on|status", "审查与验证"),
        Meta("/constellation", "查看项目蓝图与里程碑", group = "桌面专用"), Meta("/leave", "在星图里留下印记", group = "桌面专用"), Meta("/enter", "恢复子代理会话", "<orderId> [prompt]", "计划与协作"),
        Meta("/exit", "保存会话并退出", group = "桌面专用"), Meta("/update", "检查并安装最新版本", group = "桌面专用"), Meta("/doctor", "检查 Node、git、Python 等环境", group = "权限与调试"),
        Meta("/logs", "查看会话与缓存日志", "[open [desktop]]", "权限与调试"), Meta("/init", "初始化 verify、skills、hooks", group = "计划与协作"), Meta("/cd", "切换工作目录", "<path>", "桌面专用"),
        Meta("/status", "显示模型、缓存、token 和账号状态", group = "权限与调试"), Meta("/todo", "管理任务清单", "[list|add|done|skip|move]", "计划与协作"), Meta("/tools", "查看工具分层与挂载状态", "[enable <name>]", "权限与调试"),
        Meta("/trust", "授信当前项目 hooks 和配置", "[status|off]", "权限与调试"), Meta("/grant", "授权工作区外目录", "[path] [read|write]", "权限与调试"), Meta("/skill", "调用已加载技能", "<技能名> <任务>", "已加载技能"), Meta("/skill list", "列出可用技能", group = "已加载技能"), Meta("/skill install", "安装技能", group = "已加载技能"), Meta("/skill review", "审阅技能草稿", group = "已加载技能"), Meta("/skill approve", "批准技能草稿", group = "已加载技能"), Meta("/skill reject", "驳回技能草稿", group = "已加载技能"),
        Meta("/plugin", "管理插件", "[list|install|remove|enable|disable|info]", "桌面专用"), Meta("/settings", "打开设置面板", group = "会话与模型"), Meta("/setup", "打开设置面板", group = "会话与模型"),
        Meta("/rewind", "回退到历史消息点", group = "桌面专用"), Meta("/branch", "查看会话分支树", "[back]", "计划与协作"), Meta("/team-resume", "从波次检查点恢复 team", "[groupId]", "计划与协作"),
        Meta("/goal-status", "查看自主目标轮次和预算", group = "计划与协作"), Meta("/goal-pause", "暂停自主目标", group = "计划与协作"), Meta("/goal-criteria", "查看或设置成功判据", "[set <…>]", "计划与协作"),
        Meta("/plan-template", "管理计划模板", "[list|save <name>|<name>]", "计划与协作"), Meta("/prefix-budget", "查看上下文 token 占比", group = "权限与调试"), Meta("/sensorium", "查看六维遥测快照", group = "权限与调试"),
        Meta("/debug", "查看 prompt、缓存和 MCP 调试信息", "[prompt|cache|context-payload|mcp]", "权限与调试"), Meta("/workflow", "执行或回放 YAML 工作流", "[list|<name>|replay <id>]", "计划与协作"), Meta("/diagram", "生成 Mermaid 图骨架", "[list|<type>]", "计划与协作"),
        Meta("/dream", "把决策蒸馏进项目记忆", group = "计划与协作"), Meta("/index", "重建代码库索引", group = "桌面专用"), Meta("/mirror", "切换依赖下载镜像", "[status|on|off]", "桌面专用"),
        Meta("/python", "检查或初始化 Python 环境", "[status|setup]", "桌面专用"), Meta("/chronicle", "查看项目里程碑编年史", group = "桌面专用"), Meta("/starmap", "查看星图总览", group = "桌面专用"),
        Meta("/pager", "分页浏览历史输出", group = "桌面专用"), Meta("/palette", "打开完整命令面板", group = "桌面专用"), Meta("/glance", "切换概览栏密度", "[compact|full]", "桌面专用"), Meta("/team max", "团队强编队", group = "桌面专用"), Meta("/review max", "审查编队", group = "桌面专用"), Meta("/review off", "关闭自动审查门", group = "桌面专用"), Meta("/zen", "切换禅模式", "[on|off|status]", "会话与模型"),
        Meta("/fast", "退出禅模式", group = "会话与模型"), Meta("/logout", "登出天枢账号", group = "会话与模型"), Meta("/panel", "开关右侧面板", "[on|off]", "桌面专用"), Meta("__surface:chronicle", "打开 Chronicle 浮层", group = "桌面专用"), Meta("__surface:cockpit", "打开 Cockpit 浮层", group = "桌面专用"), Meta("__surface:pager", "打开分页浏览器", group = "桌面专用"), Meta("__surface:starmap", "打开星图总览", group = "桌面专用"),
    )

    private val nativeDescriptions = mapOf(
        "/help" to "打开命令目录", "/new" to "新建会话", "/sessions" to "打开历史会话", "/resume" to "继续历史会话",
        "/model" to "选择或切换当前模型", "/model list" to "查看当前可用模型", "/effort" to "选择或设置思考强度",
        "/permission" to "查看系统授权或设置执行审批", "/connect" to "打开服务商配置", "/config" to "打开模型配置", "/vision" to "打开模型与服务商配置",
        "/settings" to "打开配置", "/setup" to "打开配置",
        "/cockpit" to "打开任务与会话信息", "/status" to "查看会话运行状态", "/cache" to "查看当前会话用量与缓存",
        "/context" to "查看当前会话上下文用量", "/verify" to "查看交付门禁与验证信息", "/evidence" to "查看当前任务证据与交付信息",
        "/mission" to "查看当前任务目标", "/todo" to "管理天枢真实任务清单", "/tasks" to "查看子代理任务", "/jobs" to "查看后台任务",
        "/mcp" to "查看 Android MCP 与系统授权", "/tools" to "查看 Android 工具与系统授权", "/doctor" to "检查 Android 执行权限",
        "/skill list" to "查看已加载技能",
        "/domain" to "选择当前会话的天枢星域", "/plan-mode" to "切换只读计划模式",
        "/ask" to "切换只读问答模式", "/fast" to "跳过禅模式读专注阶段，恢复全量工具",
        "/review off" to "关闭当前会话自动审查门",
        "/plan-list" to "浏览当前工作区计划与草稿", "/plan-view" to "阅读并审阅计划全文",
        "/plan-approve" to "选择方案并批准执行", "/plan-reject" to "向天枢提交修改反馈",
        "/goal-criteria" to "查看天枢目标验收判据",
    )
    private val nativeArgs = mapOf(
        "/skill on" to "<技能名>", "/skill off" to "<技能名>",
        "/fork" to "[分支标题]", "/handoff" to "[交接备注]", "/team-resume" to "[groupId]",
        "/queue" to "[正文|retract laneId]", "/steer" to "[补充内容|--lane laneId]",
        "/permission" to "[manual|auto-safe|dangerously-skip-permissions]", "/todo" to "[list|add|done|start|pending|edit|skip|move]", "/tools" to "[status]", "/mcp" to "[status]",
        "/review" to "[max|l1|l2|l3] [关注点]", "/review max" to "[关注点]", "/team max" to "<任务描述>",
        "/domain" to "[list|星域名称|auto|off]", "/plan-mode" to "[on|off|status]", "/ask" to "[on|off|status]",
        "/plan-view" to "[slug]", "/plan-edit" to "[slug]", "/plan-approve" to "[slug] [option]",
        "/plan-reject" to "[slug] [反馈]",
        "/goal" to "<目标> [--max N] [--budget 分钟] [--criteria JSON]",
    )
    private val extra = listOf(
        Meta("/skills", "浏览并管理会话技能", group = "已加载技能"),
        Meta("/skill on", "启用会话技能", "<技能名>", "已加载技能"),
        Meta("/skill off", "停用会话技能", "<技能名>", "已加载技能"),
        Meta("/steer", "在下一个工具边界补充指令", "<补充内容>", "会话与模型"),
        Meta("/thinking", "选择思考过程显示方式", "[collapsed|preview|expanded]", "会话与模型"),
        Meta("/files", "打开工作区文件", group = "手机操作"), Meta("/terminal", "打开手机终端", group = "手机操作"),
        Meta("/permissions", "打开 Android 系统授权", group = "手机操作"), Meta("/stop", "停止当前任务", group = "手机操作"),
        Meta("/reconnect", "重新连接当前会话", group = "手机操作"),
        Meta("/plan-edit", "编辑待审批计划", "[slug]", "计划与协作"),
        Meta("/review l1", "审查纪律提醒", "[关注点]", "审查与验证"), Meta("/review l2", "对抗验证审查", "[关注点]", "审查与验证"),
        Meta("/review l3", "五席审查编队", "[关注点]", "审查与验证"),
        Meta("/review on", "恢复当前会话自动审查门", group = "审查与验证"),
        Meta("/review status", "查看自动审查门状态", group = "审查与验证"),
    )

    val commands = (official + extra).filter { it.name.startsWith('/') }
        .map { m ->
            val action = if (m.name in setOf("/skill on", "/skill off")) PaletteAction.SKILL_MANAGE else nativePaletteAction(m.name)
            MobileCommand(m.name, nativeDescriptions[m.name] ?: m.description,
                nativeArgs[m.name] ?: if (action != null && m.name !in setOf("/resume", "/model", "/effort", "/thinking")) "" else m.args,
                when {
                    action != null && m.group == "桌面专用" -> "手机操作"
                    m.name == "/team max" -> "计划与协作"
                    m.name == "/review max" -> "审查与验证"
                    else -> m.group
                },
                m.name in bridgeCommands || action != null, action)
        }

    fun filter(query: String, skills: List<SkillEntry>, customCommands: List<MobileCommand> = emptyList()): CommandPaletteResults {
        val customByName = customCommands.associateBy { it.name }
        val allCommands = (commands.map { if (it.mobileExecutable) it else customByName[it.name] ?: it } + customCommands).distinctBy { it.name }
        return CommandPaletteResults(
            rankCommandMatches(query, allCommands) { listOf(it.name, it.title, it.description, it.librarySection.title, it.searchTerms) },
            rankCommandMatches(query, skills.distinctBy { it.name }) { listOf("/skill ${it.name}", it.name, it.description, "技能", it.source) },
        )
    }
}

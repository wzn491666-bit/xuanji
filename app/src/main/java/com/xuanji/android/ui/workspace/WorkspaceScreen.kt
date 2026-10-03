package com.xuanji.android.ui.workspace

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuanji.android.*
import com.xuanji.android.ui.theme.Danger
import com.xuanji.android.ui.theme.Success
import com.xuanji.android.ui.theme.Warning
import com.xuanji.android.ui.theme.Info
import com.xuanji.android.ui.chat.MarkdownParser
import com.xuanji.android.ui.chat.MarkdownBlock
import com.xuanji.android.ui.chat.MarkdownBlockContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

enum class WorkspaceTab(val title: String, val icon: ImageVector) {
    COCKPIT("任务", Icons.Default.SpaceDashboard), FILES("文件", Icons.Default.Folder),
    TERMINAL("终端", Icons.Default.Terminal), SETTINGS("配置", Icons.Default.Tune),
}

@Composable
fun WorkspaceSheet(vm: XuanjiViewModel, initialTab: WorkspaceTab = WorkspaceTab.COCKPIT, onDismiss: () -> Unit) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    WorkspaceDialogShell(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("工作台", style = MaterialTheme.typography.titleLarge)
                Text("天枢", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            com.xuanji.android.ui.chat.ChatIconButton("关闭工作台", Icons.Default.Close, onDismiss)
        }
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WorkspaceTab.entries.forEach { item ->
                Surface(
                    modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                    color = if (tab == item) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        Modifier.heightIn(min = 48.dp).selectable(selected = tab == item, role = Role.Tab, onClick = { tab = item })
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(item.icon, null, Modifier.size(18.dp), tint = if (tab == item) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.title, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                WorkspaceTab.COCKPIT -> CockpitWorkspace(vm)
                WorkspaceTab.FILES -> FilesWorkspace(vm)
                WorkspaceTab.TERMINAL -> TerminalWorkspace(vm)
                WorkspaceTab.SETTINGS -> SettingsWorkspace(vm)
            }
        }
    }
}

@Composable
private fun CockpitWorkspace(vm: XuanjiViewModel) {
    val cockpit by vm.cockpit.collectAsStateWithLifecycle()
    val serviceLog by TianshuService.log.collectAsStateWithLifecycle()
    val mcp by vm.androidMcpStatus.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero") {
            CockpitHero(cockpit)
        }
        item(key = "metrics") { CockpitMetricStrip(cockpit) }
        item(key = "background-jobs") { BackgroundJobsPanel(vm) }
        if (cockpit.todos.isNotEmpty()) item(key = "todos") {
            CockpitSectionCard(
                title = "任务清单",
                subtitle = "${cockpit.todos.count { it.status == "completed" }} 项已完成 · ${cockpit.todos.size} 项总计",
                badge = "${cockpit.todos.count { it.status == "completed" }}/${cockpit.todos.size}",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cockpit.todos.forEach { todo -> TodoRow(todo) }
                }
            }
        }
        if (cockpit.verificationCommands.isNotEmpty()) item(key = "verification") {
            val passed = cockpit.verificationCommands.count { it.status.equals("passed", true) || it.status.equals("pass", true) }
            CockpitSectionCard(
                title = "验证命令",
                subtitle = "命令与执行结果",
                badge = "$passed/${cockpit.verificationCommands.size}",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cockpit.verificationCommands.takeLast(8).asReversed().forEach { check ->
                        VerificationCard(check)
                    }
                }
            }
        }
        if (cockpit.convergence.status != "unknown") item(key = "convergence") {
            val convergence = cockpit.convergence
            CockpitSectionCard(
                title = "收敛检测",
                subtitle = "任务是否正在接近可交付状态",
                badge = convergence.status,
            ) {
                convergence.score?.let { score ->
                    LinearProgressIndicator(
                        progress = { (score.coerceIn(0, 100) / 100f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = cockpitStatusColor(convergence.status),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text("收敛评分 ${score.coerceIn(0, 100)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    convergence.reason.ifBlank { "天枢暂未提供进一步说明。" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cockpit.contextUsagePercent?.let { contextUsage ->
            item(key = "context-usage") {
                val usageColor = when {
                    contextUsage >= 90 -> Danger
                    contextUsage >= 75 -> Warning
                    else -> Info
                }
                CockpitSectionCard(
                    title = "上下文窗口",
                    subtitle = "估算输入占模型上下文窗口",
                    badge = "$contextUsage%",
                ) {
                    LinearProgressIndicator(
                        progress = { contextUsage.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = usageColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        when {
                            contextUsage >= 90 -> "上下文接近上限，任务可能需要压缩历史。"
                            contextUsage >= 75 -> "上下文使用偏高，留意后续任务长度。"
                            else -> "上下文空间充足。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (cockpit.hooks.isNotEmpty()) item(key = "hooks") {
            val passed = cockpit.hooks.count { it.status.equals("passed", true) || it.status.equals("complete", true) }
            CockpitSectionCard(
                title = "Hooks",
                subtitle = "任务钩子执行状态",
                badge = "$passed/${cockpit.hooks.size}",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cockpit.hooks.takeLast(72).asReversed().forEach { hook ->
                        CockpitSignalRow(hook.name, hook.detail, hook.status)
                    }
                }
            }
        }
        if (cockpit.waves.isNotEmpty()) item(key = "waves") {
            CockpitSectionCard(
                title = "多代理波次",
                subtitle = "并行协作与交接",
                badge = "${cockpit.waves.size} 波",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cockpit.waves.takeLast(6).asReversed().forEach { wave ->
                        WaveCard(wave)
                    }
                }
            }
        }
        if (cockpit.usage.totalTokens > 0 || (cockpit.usage.reasoningTokens ?: 0) > 0 || cockpit.usage.cacheReadTokens > 0 || cockpit.insights != null) item(key = "usage") {
            CockpitSectionCard(
                title = "Token 与资源",
                subtitle = "模型消耗统计",
                badge = "${compactNumber(cockpit.usage.totalTokens)} tokens",
            ) {
                UsageSummary(cockpit.usage, cockpit.insights)
            }
        }
        if (cockpit.failures.isNotEmpty()) item(key = "failures") {
            CockpitSectionCard(
                title = "失败分析",
                subtitle = "按来源归类的异常与阻塞",
                badge = "${cockpit.failures.size} 项",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cockpit.failures.takeLast(8).asReversed().forEach { failure ->
                        CockpitSignalRow(failure.category, failure.message, "需关注")
                    }
                }
            }
        }
        if (cockpit.replay.isNotEmpty()) item(key = "replay") {
            CockpitSectionCard(
                title = "任务回放",
                subtitle = "按时间回看执行轨迹",
                badge = "${cockpit.replay.size} 步",
                initiallyExpanded = false,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cockpit.replay.takeLast(24).asReversed().forEachIndexed { index, step ->
                        CockpitSignalRow("${cockpit.replay.size - index}. ${step.label}", step.detail, step.type)
                    }
                }
            }
        }
        if (cockpit.evidence.isNotEmpty()) item(key = "evidence") {
            CockpitSectionCard(
                title = "证据",
                subtitle = "来源与摘要",
                badge = "${cockpit.evidence.size} 项",
                initiallyExpanded = false,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cockpit.evidence.takeLast(20).asReversed().forEach { evidence ->
                        EvidenceCard(evidence)
                    }
                }
            }
        }
        if (cockpit.timeline.isNotEmpty()) item(key = "timeline") {
            CockpitSectionCard(
                title = "事件时间线",
                subtitle = "天枢实时事件",
                badge = "${cockpit.timeline.size} 条",
                initiallyExpanded = false,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cockpit.timeline.takeLast(30).asReversed().forEach { event ->
                        CockpitSignalRow(event.type, event.summary, "记录")
                    }
                }
            }
        }
        item(key = "android-mcp") {
            CockpitSectionCard(
                title = "Android MCP",
                subtitle = "手机能力桥接",
                badge = if (mcp.state == "running") "${mcp.toolCount} 个工具" else mcp.state,
            ) {
                Text(
                    if (mcp.state == "running") "已连接，可供天枢按授权调用。"
                    else mcp.error ?: "等待本地 MCP 服务启动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (serviceLog.isNotEmpty()) item(key = "service-log") {
            CockpitSectionCard(
                title = "天枢最近日志",
                subtitle = "运行时输出",
                badge = "${serviceLog.takeLast(40).size} 行",
                initiallyExpanded = false,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    SelectionContainer {
                        Text(
                            serviceLog.takeLast(40).joinToString("\n"),
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitHero(cockpit: Cockpit) {
    val done = cockpit.todos.count { it.status == "completed" }
    val progress = if (cockpit.todos.isEmpty()) 0f else done.toFloat() / cockpit.todos.size
    val gate = cockpit.deliveryGate
    val gateColor = when (gate) {
        "GREEN" -> Success
        "RED" -> Danger
        "YELLOW" -> Warning
        else -> MaterialTheme.colorScheme.primary
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("COCKPIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("·", color = MaterialTheme.colorScheme.outline)
                Text("实时任务", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (cockpit.deliveryFailed) SignalPill("交付操作失败", Danger)
                else gate?.let { SignalPill(deliveryGateLabel(it), gateColor) }
            }
            Text(
                cockpit.goal.ifBlank { "等待任务开始" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (cockpit.deliveryFailed && gate != null) Text("验证门：$gate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cockpit.todos.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = gateColor,
                    trackColor = MaterialTheme.colorScheme.surface,
                )
                Text(
                    "$done / ${cockpit.todos.size} 项完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (cockpit.goal.isBlank()) {
                Text(
                    "当前没有任务",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun CockpitMetricStrip(cockpit: Cockpit) {
    val verified = cockpit.verificationCommands.count { it.status.equals("passed", true) || it.status.equals("pass", true) }
    val hooksPassed = cockpit.hooks.count { it.status.equals("passed", true) || it.status.equals("complete", true) }
    val metrics = listOf("验证" to "$verified/${cockpit.verificationCommands.size}", "Hooks" to "$hooksPassed/${cockpit.hooks.size}",
        "异常" to cockpit.failures.size.toString(), "Token" to compactNumber(cockpit.usage.totalTokens))
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)) {
        val columns = if (maxWidth < 360.dp || fontScale > 1.3f) 2 else 4
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEachIndexed { index, (label, value) ->
                        if (index > 0) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight())
                        MetricTile(label, value, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier) {
        Column(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
}

@Composable
private fun CockpitSectionCard(
    title: String,
    subtitle: String,
    badge: String? = null,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                badge?.let { SignalPill(it, MaterialTheme.colorScheme.primary) }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun TodoRow(todo: Todo) {
    val completed = todo.status.equals("completed", true)
    val active = todo.status.equals("in_progress", true)
    val color = when {
        completed -> Success
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(
            imageVector = when {
                completed -> Icons.Default.CheckCircle
                active -> Icons.Default.PlayArrow
                else -> Icons.Default.RadioButtonUnchecked
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(todo.content, style = MaterialTheme.typography.bodyMedium)
            if (active) Text("进行中", style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun VerificationCard(command: VerificationCommand) {
    var expanded by rememberSaveable(command.command) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                command.command,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            SignalPill(command.status, cockpitStatusColor(command.status))
        }
        if (command.output.isNotBlank()) {
            Column {
                if (expanded) {
                    Box(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(10.dp)) {
                        SelectionContainer {
                            Text(
                                command.output,
                                modifier = Modifier.fillMaxWidth(),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        Text(
                            command.output,
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (command.output.count { it == '\n' } >= 4) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                    Text(if (expanded) "收起输出" else "查看完整输出")
                }
            }
        } else {
            Text("等待输出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CockpitSignalRow(title: String, detail: String, status: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(cockpitStatusColor(status)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = if (monospace) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        SignalPill(status, cockpitStatusColor(status))
    }
}

@Composable
private fun SignalPill(label: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
        Text(
            label,
            modifier = Modifier.widthIn(max = 116.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WaveCard(wave: AgentWave) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("第 ${wave.wave} 波", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            SignalPill(if (wave.failed == 0) "正常" else "${wave.failed} 个失败", if (wave.failed == 0) Success else Danger)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WaveMetric("活跃", wave.active, Info)
            WaveMetric("完成", wave.completed, Success)
            WaveMetric("失败", wave.failed, if (wave.failed == 0) MaterialTheme.colorScheme.onSurfaceVariant else Danger)
        }
    }
}

@Composable
private fun WaveMetric(label: String, value: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(value.toString(), style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EvidenceCard(evidence: CockpitEvidence) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(evidence.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            SignalPill(evidence.source, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(evidence.value, style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UsageSummary(usage: TokenUsage, insights: SessionInsights?) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("主会话累计", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsageMetric("输入", usage.inputTokens, Modifier.weight(1f))
            UsageMetric("输出", usage.outputTokens, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsageMetric("推理", usage.reasoningTokens, Modifier.weight(1f))
            UsageMetric("缓存读取", usage.cacheReadTokens, Modifier.weight(1f))
        }
        insights?.let { session ->
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("含子代理累计", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("${session.workers} 个代理", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsageMetric("输入", session.inputTokens, Modifier.weight(1f))
                UsageMetric("输出", session.outputTokens, Modifier.weight(1f))
            }
            session.cacheHitRate?.let { hitRate ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("缓存命中", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("$hitRate%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(progress = { hitRate.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
            }
            Text("读取 ${compactNumber(session.cacheReadTokens)} · 写入 ${compactNumber(session.cacheWriteTokens)} tokens",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (session.cost > 0) Text("估算费用 ${String.format(Locale.US, "$%.4f", session.cost)}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UsageMetric(label: String, value: Long?, modifier: Modifier) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.let { compactNumber(it) } ?: "未知", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun compactNumber(value: Long): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}

@Composable
private fun cockpitStatusColor(status: String): Color = when (status.lowercase()) {
    "passed", "pass", "complete", "completed", "success", "stable", "none", "稳定", "正常", "green" -> Success
    "failed", "failure", "error", "失败", "red", "blocked", "需关注", "high", "critical" -> Danger
    "running", "in_progress", "进行中", "active" -> MaterialTheme.colorScheme.primary
    "record", "recorded", "记录", "事件" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> Warning
}

private fun deliveryGateLabel(gate: String): String = when (gate) {
    "GREEN" -> "交付通过"
    "YELLOW" -> "附有提醒"
    "RED" -> "交付阻塞"
    else -> gate
}

@Composable
private fun FilesWorkspace(vm: XuanjiViewModel) {
    val files by vm.workspaceFiles.collectAsStateWithLifecycle()
    val path by vm.workspacePath.collectAsStateWithLifecycle()
    val sessionDirectory by vm.sessionWorkdir.collectAsStateWithLifecycle()
    val preview by vm.filePreview.collectAsStateWithLifecycle()
    var showChanges by remember { mutableStateOf(false) }
    var confirmDirectory by remember { mutableStateOf<String?>(null) }
    val previewPages = remember(preview?.content) {
        preview?.let { splitFilePreviewPages(it.content) }.orEmpty()
    }
    val error by vm.workspaceError.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.listWorkspace() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showChanges, onClick = { showChanges = false }, label = { Text("浏览") })
            FilterChip(selected = showChanges, onClick = { showChanges = true; vm.clearGitDiff() }, label = { Text("改动") })
        }
        if (showChanges) {
            GitChangesWorkspace(vm)
        } else if (preview != null) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::clearFilePreview) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回文件列表") }
                    Column(Modifier.weight(1f)) {
                        Text(preview!!.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(preview!!.path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatBytes(preview!!.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().padding(start = 56.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewBadge(previewFormatLabel(preview!!.format))
                    if (preview!!.truncated) PreviewBadge("已截取安全预览")
                }
                when {
                    preview!!.format == FilePreviewFormat.IMAGE -> FilePreviewImage(preview!!.absolutePath)
                    preview!!.isBinary -> SelectionContainer {
                        Text(preview!!.content, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    preview!!.format == FilePreviewFormat.MARKDOWN -> MarkdownFilePreview(preview!!.content)
                    preview!!.format == FilePreviewFormat.CODE -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(previewPages, key = { index, page -> "${page.startLine}-$index" }) { index, page ->
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (previewPages.size > 1) Text(
                                    previewPageCaption(index, page, previewPages.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                CodeFilePreview(
                                    page.content,
                                    preview!!.path,
                                    page.startLine,
                                    page.continuesPreviousLine,
                                )
                            }
                        }
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(previewPages, key = { index, page -> "${page.startLine}-$index" }) { index, page ->
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (previewPages.size > 1) Text(
                                    previewPageCaption(index, page, previewPages.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SelectionContainer {
                                    Text(page.content, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (path != "/sdcard") {
                    IconButton(onClick = { vm.listWorkspace(parentWorkspacePath(path)) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "上级目录") }
                }
                Text(path, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { vm.listWorkspace(path) }) { Icon(Icons.Default.Refresh, "刷新") }
            }
            if (path != sessionDirectory) {
                TextButton(onClick = { confirmDirectory = path }) { Text("以此目录新建会话") }
            } else {
                Text("当前会话目录", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(files, key = { it.path }) { file ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (file.isDirectory) vm.listWorkspace(file.path) else vm.previewWorkspaceFile(file.path)
                        }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Terminal, null)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (file.isDirectory) "目录" else formatBytes(file.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (file.isDirectory) Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
    confirmDirectory?.let { directory ->
        AlertDialog(
            onDismissRequest = { confirmDirectory = null },
            title = { Text("切换会话目录？") },
            text = { Text("将以 $directory 新建对话。当前对话不会删除，但界面会切换到新会话。") },
            confirmButton = { TextButton(onClick = { vm.startSessionInDirectory(directory); confirmDirectory = null }) { Text("新建对话") } },
            dismissButton = { TextButton(onClick = { confirmDirectory = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun GitChangesWorkspace(vm: XuanjiViewModel) {
    val directory by vm.sessionWorkdir.collectAsStateWithLifecycle()
    val sessionReady by vm.sessionReady.collectAsStateWithLifecycle()
    val tree by vm.gitWorkingTree.collectAsStateWithLifecycle()
    val diff by vm.gitDiff.collectAsStateWithLifecycle()
    val loading by vm.gitLoading.collectAsStateWithLifecycle()
    val error by vm.gitError.collectAsStateWithLifecycle()
    LaunchedEffect(directory, sessionReady) { if (sessionReady) vm.refreshGitChanges() }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (diff != null) IconButton(onClick = vm::clearGitDiff) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回改动列表")
            }
            Column(Modifier.weight(1f)) {
                Text(if (diff == null) "项目改动" else diff!!.path, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(directory, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { if (diff == null) vm.refreshGitChanges() else vm.loadGitDiff(diff!!.path) }) {
                Icon(Icons.Default.Refresh, "刷新改动")
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        when {
            !sessionReady -> Text("等待天枢会话连接", style = MaterialTheme.typography.bodyMedium)
            loading && tree == null -> CircularProgressIndicator(Modifier.padding(16.dp).size(22.dp), strokeWidth = 2.dp)
            diff != null -> {
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                val lines = remember(diff!!.text) { diff!!.text.lineSequence().take(3000).toList() }
                val lineLimitReached = remember(diff!!.text) { diff!!.text.lineSequence().take(3001).count() > 3000 }
                if (diff!!.text.isBlank() && !loading) Text("没有可显示的文本 Diff", style = MaterialTheme.typography.bodyMedium)
                else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (diff!!.truncated || lineLimitReached) item {
                        Text("仅显示前 ${if (lineLimitReached) "3000 行" else "200,000 字符"}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(lines) { line -> GitDiffLine(line) }
                }
            }
            tree?.isRepo == false -> Text("当前会话目录不是 Git 仓库。请在浏览页进入项目目录并新建会话。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            tree?.files.isNullOrEmpty() -> Text("当前项目没有未提交改动", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(tree!!.files, key = { it.path }) { change ->
                    Row(Modifier.fillMaxWidth().clickable { vm.loadGitDiff(change.path) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(change.path, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(gitStatusLabel(change.status), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("+${change.additions}", color = Success, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("-${change.deletions}", color = Danger, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun GitDiffLine(line: String) {
    val kind = gitDiffLineKind(line)
    val background = when (kind) {
        GitDiffLineKind.ADDED -> Success.copy(alpha = 0.12f)
        GitDiffLineKind.REMOVED -> Danger.copy(alpha = 0.12f)
        GitDiffLineKind.HUNK -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    Text(line.ifEmpty { " " },
        modifier = Modifier.fillMaxWidth().background(background).horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = if (kind == GitDiffLineKind.HEADER) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        softWrap = false,
    )
}

private fun gitStatusLabel(status: String): String = when (status) {
    "modified" -> "已修改"
    "added" -> "已添加"
    "deleted" -> "已删除"
    "renamed" -> "已重命名"
    "untracked" -> "未跟踪"
    else -> status
}

@Composable
private fun MarkdownFilePreview(content: String) {
    val blocks by produceState<List<MarkdownBlock>?>(initialValue = null, key1 = content) {
        value = withContext(Dispatchers.Default) { MarkdownParser.parse(content) }
    }
    val parsed = blocks
    if (parsed == null) {
        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (parsed.isEmpty()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
            Text("此文件没有可显示的内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(parsed, key = { index, block -> "${block::class.simpleName}-$index" }) { _, block ->
                SelectionContainer { MarkdownBlockContent(block) }
            }
        }
    }
}

private fun previewPageCaption(index: Int, page: FilePreviewPage, pageCount: Int): String {
    val lastLine = page.startLine + page.lineCount - 1
    val range = if (lastLine == page.startLine) "行 ${page.startLine}" else "行 ${page.startLine}–$lastLine"
    val continuation = if (page.continuesPreviousLine) " · 长行续段" else ""
    return "${index + 1} / $pageCount · $range$continuation"
}

@Composable
private fun PreviewBadge(label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilePreviewImage(path: String) {
    val result by produceState<Result<Bitmap?>?>(initialValue = null, key1 = path) {
        value = runCatching { withContext(Dispatchers.IO) { decodePreviewBitmap(path) } }
    }
    val bitmap = result?.getOrNull()
    if (bitmap == null) {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            if (result == null) CircularProgressIndicator()
            else Text("此图像格式暂不支持预览", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = "文件图片预览",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(8.dp),
        )
    }
}

private fun decodePreviewBitmap(path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

@Composable
private fun CodeFilePreview(content: String, path: String, startLine: Int, continuesPreviousLine: Boolean) {
    val colors = MaterialTheme.colorScheme
    val codeStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val displayContent = content.removeSuffix("\n")
    val highlighted = remember(content, path, colors.primary, colors.tertiary, colors.onSurfaceVariant, colors.secondary) {
        buildAnnotatedString {
            append(displayContent)
            codeSpansForPreview(displayContent, path).forEach { span ->
                val color = when (span.kind) {
                    CodeSpanKind.KEYWORD -> colors.primary
                    CodeSpanKind.STRING -> colors.tertiary
                    CodeSpanKind.COMMENT -> colors.onSurfaceVariant
                    CodeSpanKind.NUMBER -> colors.secondary
                }
                addStyle(
                    SpanStyle(
                        color = color,
                        fontWeight = if (span.kind == CodeSpanKind.KEYWORD) FontWeight.SemiBold else FontWeight.Normal,
                        fontStyle = if (span.kind == CodeSpanKind.COMMENT) FontStyle.Italic else FontStyle.Normal,
                    ),
                    span.start,
                    span.end,
                )
            }
        }
    }
    val lineNumbers = remember(displayContent, startLine, continuesPreviousLine) {
        val count = maxOf(1, displayContent.count { it == '\n' } + 1)
        (0 until count).joinToString("\n") { index ->
            if (index == 0 && continuesPreviousLine) "" else (startLine + index).toString()
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = MaterialTheme.shapes.small) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp, horizontal = 8.dp)) {
            Text(lineNumbers, style = codeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Spacer(Modifier.width(12.dp))
            Text(highlighted, style = codeStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun previewFormatLabel(format: FilePreviewFormat): String = when (format) {
    FilePreviewFormat.MARKDOWN -> "Markdown"
    FilePreviewFormat.CODE -> "源码"
    FilePreviewFormat.TEXT -> "纯文本"
    FilePreviewFormat.IMAGE -> "图像"
}

@Composable
private fun TerminalWorkspace(vm: XuanjiViewModel) {
    val records by vm.terminalRecords.collectAsStateWithLifecycle()
    val busy by vm.terminalBusy.collectAsStateWithLifecycle()
    var command by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf(ShellBackend.APP) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShellBackend.entries.forEach { item ->
                FilterChip(selected = backend == item, onClick = { backend = item }, label = { Text(item.name.lowercase()) })
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::clearTerminal) { Icon(Icons.Default.DeleteSweep, "清空输出") }
        }
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("输入命令") },
            placeholder = { Text("例如：id 或 ls -la /sdcard") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            trailingIcon = {
                TextButton(enabled = command.isNotBlank() && !busy, onClick = { vm.runTerminal(command, backend); command = "" }) { Text(if (busy) "执行中" else "执行") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(records) { _, record ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${record.backend.name.lowercase()}$ ${record.command}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text("退出码 ${record.exitCode}${if (record.timedOut) " · 超时" else ""}", color = if (record.exitCode == 0) Success else Danger, style = MaterialTheme.typography.labelSmall)
                        SelectionContainer { Text(record.output.ifBlank { "（无输出）" }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsWorkspace(vm: XuanjiViewModel) {
    val current by vm.settings.collectAsStateWithLifecycle()
    val thinkingDisplay by vm.thinkingDisplay.collectAsStateWithLifecycle()
    val providers by vm.providerCatalog.collectAsStateWithLifecycle()
    val catalogError by vm.providerCatalogError.collectAsStateWithLifecycle()
    val providerBusy by vm.providerActionBusy.collectAsStateWithLifecycle()
    var provider by remember(current) { mutableStateOf(current.provider) }
    var apiKey by remember(current) { mutableStateOf(current.apiKey) }
    var model by remember(current) { mutableStateOf(current.model) }
    var baseUrl by remember(current) { mutableStateOf(current.baseUrl) }
    var effort by remember(current) { mutableStateOf(current.reasoningEffort) }
    val testResult by vm.providerTest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleteCandidate by remember { mutableStateOf<ProviderProfile?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshProviderCatalog() }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    val draft = Settings(provider, apiKey, model, baseUrl, effort,
        useStoredCredential = provider == current.provider && apiKey.isBlank() && current.useStoredCredential)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "conversation-display") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("对话显示", style = MaterialTheme.typography.titleMedium)
                Text("思考过程", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThinkingDisplay.entries.forEachIndexed { index, display ->
                        SegmentedButton(selected = thinkingDisplay == display, onClick = { vm.setThinkingDisplay(display) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThinkingDisplay.entries.size)) {
                            Text(display.label, maxLines = 2)
                        }
                    }
                }
            }
        }
        item(key = "project-documentation") {
            HorizontalDivider(Modifier.padding(top = 8.dp))
            ListItem(headlineContent = { Text("项目与开源文档") }, supportingContent = { Text("技术架构、第三方来源与许可证") },
                leadingContent = { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable(role = Role.Button) { com.xuanji.android.ProjectDocumentationActivity.open(context) })
        }
        item(key = "provider-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("模型与服务商", style = MaterialTheme.typography.titleMedium)
                        Text("${providers.size} 个已配置服务商", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = vm::refreshProviderCatalog) { Icon(Icons.Default.Refresh, "刷新服务商") }
                }
            }
        }
        catalogError?.let { error ->
            item(key = "provider-error") { Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (providers.isEmpty()) {
            item(key = "provider-empty") {
                Text("天枢运行后可查看已配置的模型", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(providers, key = { "provider:${it.name}" }) { profile ->
                ProviderProfileRow(profile, current, providerBusy, vm::activateProvider, { deleteCandidate = profile })
            }
        }
        item(key = "provider-editor-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { editing = !editing }
                    .semantics { stateDescription = if (editing || providers.isEmpty()) "已展开" else "已收起" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                    Text("添加或更新服务商", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(if (editing || providers.isEmpty()) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (editing || providers.isEmpty()) {
            item(key = "provider-name") {
                OutlinedTextField(provider, { newProvider ->
                    val trimmed = newProvider.trim()
                    if (trimmed != provider) {
                        apiKey = ""
                        model = ""
                        baseUrl = ""
                    }
                    provider = trimmed
                }, label = { Text("Provider") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item(key = "provider-key") {
                OutlinedTextField(apiKey, { apiKey = it.trim() }, label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item(key = "provider-model") {
                OutlinedTextField(model, { model = it.trim() }, label = { Text("模型名") }, supportingText = { Text("留空使用服务商默认模型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item(key = "provider-url") {
                OutlinedTextField(baseUrl, { baseUrl = it.trim() }, label = { Text("自定义 API 地址（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item(key = "provider-effort") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("思考强度", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("auto", "low", "medium", "high", "max").forEach { value ->
                            FilterChip(selected = effort == value, onClick = { effort = value }, label = { Text(value) })
                        }
                    }
                }
            }
            item(key = "provider-actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { vm.testProvider(draft) }, enabled = draft.isComplete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Link, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("测试连接")
                    }
                    Button(onClick = { vm.saveSettings(draft) }, enabled = draft.isComplete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("保存")
                    }
                }
            }
        }
        testResult?.let { result ->
            item(key = "provider-test-result") {
                val resultColor = when {
                    result.startsWith("连接成功") -> Success
                    result.startsWith("连接失败") -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(result, style = MaterialTheme.typography.bodySmall, color = resultColor)
            }
        }
        item(key = "storage-access") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            storageAccessSettingsIntent(context)?.let(context::startActivity)
                        } else {
                            storagePermissionLauncher.launch(legacyStoragePermissions())
                        }
                    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Folder, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "所有文件访问" else "共享存储权限", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("移除 ${candidate.label}？") },
            text = { Text("将从天枢配置中移除该服务商及其密钥。") },
            confirmButton = { TextButton(onClick = { vm.removeProvider(candidate); deleteCandidate = null }) { Text("移除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ProviderProfileRow(
    profile: ProviderProfile,
    current: Settings,
    busy: Boolean,
    onActivate: (ProviderProfile, String) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(profile.name) { mutableStateOf(false) }
    val active = current.provider == profile.name
    Column(
        Modifier.fillMaxWidth()
            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else Color.Transparent,
                RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(profile.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                when {
                    active -> "当前"
                    profile.keyless -> "无需密钥"
                    profile.hasCredential -> "已配置"
                    else -> "缺少密钥"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (profile.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!active) IconButton(onClick = onDelete, enabled = !busy) {
                Icon(Icons.Default.DeleteSweep, "移除 ${profile.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val visibleModels = if (expanded) profile.models else profile.models.take(3)
        if (profile.models.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("暂无模型列表", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (!active && profile.isAvailable) TextButton(onClick = { onActivate(profile, "") }, enabled = !busy) {
                    Text("设为默认")
                }
            }
        }
        visibleModels.forEach { model ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = active && current.model == model.id, enabled = profile.isAvailable && !busy, role = Role.RadioButton) { onActivate(profile, model.id) }
                    .heightIn(min = 48.dp).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (active && current.model == model.id) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (active && current.model == model.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(model.id, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (model.supportsVision) Icon(Icons.Default.Visibility, "支持视觉", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                if (model.effortSupported) Icon(Icons.Default.Psychology, "支持推理档位", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            }
        }
        if (profile.models.size > 3) TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起模型" else "查看全部 ${profile.models.size} 个模型")
        }
    }
}

@Composable
private fun WorkspaceSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KiB"
    else -> "${bytes / (1024 * 1024)} MiB"
}

private fun parentWorkspacePath(path: String): String {
    val normalized = path.trimEnd('/')
    val slash = normalized.lastIndexOf('/')
    return if (slash <= "/sdcard".length) "/sdcard" else normalized.substring(0, slash)
}

package com.xuanji.android.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xuanji.android.SessionCommandOutput
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionCommandSheet(output: SessionCommandOutput, canChangeDomain: Boolean,
    onDomain: (String) -> Unit, busy: Boolean = false, commandError: String? = null, onCommand: (NativeSlashRequest) -> Unit = {},
    onSavePlan: (String, String) -> Unit = { _, _ -> }, running: Boolean = false, promptPending: Boolean = false,
    onDismiss: () -> Unit) {
    val plan = output.plan
    var editing by rememberSaveable(plan?.slug, plan?.content, output.editPlan) { mutableStateOf(output.editPlan) }
    var content by rememberSaveable(plan?.slug, plan?.content) { mutableStateOf(plan?.content.orEmpty()) }
    var approach by rememberSaveable(plan?.slug, plan?.options) { mutableStateOf("") }
    var feedback by rememberSaveable(plan?.slug) { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f).navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (plan != null) ChatIconButton("返回计划列表", Icons.AutoMirrored.Filled.ArrowBack,
                    { onCommand(NativeSlashRequest(PaletteAction.PLAN_LIST, "/plan-list")) }, enabled = !busy)
                Text(output.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (output.plans != null) ChatIconButton("刷新计划", Icons.Default.Refresh,
                    { onCommand(NativeSlashRequest(PaletteAction.PLAN_LIST, "/plan-list")) }, enabled = !busy)
                if (output.showGoal || output.goal != null) ChatIconButton("刷新目标", Icons.Default.Refresh,
                    { onCommand(NativeSlashRequest(PaletteAction.GOAL_STATUS, "/goal-status")) }, enabled = !busy)
                if (output.todos != null) ChatIconButton("刷新任务清单", Icons.Default.Refresh,
                    { onCommand(NativeSlashRequest(PaletteAction.TODO, "/todo")) }, enabled = !busy)
                if (output.checkpoints != null) ChatIconButton("刷新编队检查点", Icons.Default.Refresh,
                    { onCommand(NativeSlashRequest(PaletteAction.TEAM_RESUME, "/team-resume")) }, enabled = !busy)
                if (output.queue != null) ChatIconButton("刷新排队消息", Icons.Default.Refresh,
                    { onCommand(NativeSlashRequest(PaletteAction.QUEUE, "/queue")) }, enabled = !busy)
                ChatIconButton("关闭命令结果", Icons.Default.Close, onDismiss)
            }
            Box(Modifier.fillMaxWidth().height(4.dp)) { if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (output.todos != null) TodoCommandPanel(output.todos, canChangeDomain, busy, output.todoMutationArgument,
                onCommand, Modifier.weight(1f), notice = output.text, error = commandError)
            else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
                commandError?.let { item { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp)) } }
                if (output.showGoal || output.goal != null) item {
                    GoalCommandPanel(output.goal, canChangeDomain, busy, running, promptPending, onCommand)
                }
                if (output.text.isNotBlank()) item { MarkdownView(output.text, Modifier.padding(bottom = 16.dp)) }
                if (output.handoffPending) item {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 20.dp))
                }
                output.checkpoints?.let { checkpoints ->
                    if (checkpoints.isEmpty()) item {
                        Text("没有可恢复的编队", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                    }
                    items(checkpoints, key = { it.groupId }) { checkpoint ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(checkpoint.groupId, style = MaterialTheme.typography.titleSmall)
                                Text("检查点波次 ${checkpoint.wave} / ${checkpoint.totalWaves}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(checkpoint.timestamp)),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ChatIconButton("恢复编队 ${checkpoint.groupId}", Icons.Default.PlayArrow,
                                { onCommand(NativeSlashRequest(PaletteAction.TEAM_RESUME, "/team-resume", checkpoint.groupId)) },
                                enabled = canChangeDomain && !busy && !promptPending)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
                output.queue?.let { queue ->
                    if (queue.entries.isEmpty()) item {
                        Text("没有排队消息", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                    }
                    items(queue.entries, key = { it.id }) { entry ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.statusLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                    color = if (entry.status == "queued") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (entry.status == "queued") {
                                    ChatIconButton("立即引导模型", Icons.Default.NorthEast,
                                        { onCommand(NativeSlashRequest(PaletteAction.STEER, "/steer", "--lane ${entry.id}")) },
                                        enabled = running && !busy && !promptPending && !entry.hasAttachments)
                                    ChatIconButton("撤回排队消息", Icons.Default.Undo,
                                        { onCommand(NativeSlashRequest(PaletteAction.QUEUE, "/queue", "retract ${entry.id}")) },
                                        enabled = (canChangeDomain || running) && !busy && !promptPending)
                                }
                            }
                            Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                            if (entry.hasAttachments) Text(buildList {
                                if (entry.imageCount > 0) add("${entry.imageCount} 张图片")
                                addAll(entry.documentNames)
                            }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
                output.plans?.let { catalog ->
                    catalog.draft?.let { draft -> item {
                        Text("起草中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        Text(draft.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                        if (draft.content.isNotBlank()) MarkdownView(draft.content)
                        HorizontalDivider(Modifier.padding(vertical = 20.dp))
                    } }
                    if (catalog.plans.isEmpty()) item {
                        Text("暂无已提交计划", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                    }
                    items(catalog.plans, key = { it.slug }) { entry ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.Button, onClick = {
                            onCommand(NativeSlashRequest(PaletteAction.PLAN_VIEW, "/plan-view", entry.slug))
                        }).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.statusLabel, style = MaterialTheme.typography.labelMedium,
                                color = if (entry.editable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text(entry.model.ifBlank { entry.slug }, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
                plan?.let { document ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(document.statusLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            if (document.editable && !editing) ChatIconButton("编辑计划", Icons.Default.Edit, { editing = true }, enabled = canChangeDomain && !busy)
                        }
                        if (editing) OutlinedTextField(content, { content = it }, enabled = !busy, minLines = 12, maxLines = 16,
                            textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), label = { Text("计划 Markdown") })
                        else MarkdownView(document.content)
                    }
                    if (document.editable && !editing && document.options.isNotEmpty()) {
                        item { Text("执行方案", Modifier.padding(top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall) }
                        items(document.options, key = { it.label }) { option ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = canChangeDomain && !busy,
                                role = Role.RadioButton, onClick = { approach = option.label }).padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(approach == option.label, onClick = { approach = option.label }, enabled = canChangeDomain && !busy)
                                Column(Modifier.weight(1f)) {
                                    Text(option.label, style = MaterialTheme.typography.titleSmall)
                                    Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (output.domains.isNotEmpty()) item {
                    Text("会话中途切换星域会重建前缀缓存，建议在新会话选择。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                }
                items(output.domains, key = { it.key }) { domain ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = canChangeDomain && !domain.current, role = Role.Button,
                        onClick = { onDomain(domain.key) }).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(domain.name, style = MaterialTheme.typography.titleSmall)
                            Text(domain.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (domain.current) Icon(Icons.Default.Check, "当前星域", tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            }
            if (plan?.editable == true) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (editing) {
                        TextButton(onClick = { editing = false; content = plan.content }, enabled = !busy) { Text("取消编辑") }
                        Button(onClick = { onSavePlan(plan.slug, content) }, enabled = canChangeDomain && !busy && content.isNotBlank(), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("保存修改")
                        }
                    } else {
                        OutlinedButton(onClick = { confirm = "reject" }, enabled = canChangeDomain && !busy) { Text("反馈修改") }
                        Button(onClick = { confirm = "approve" }, enabled = canChangeDomain && !busy &&
                            (plan.options.isEmpty() || approach.isNotBlank()), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("批准并执行")
                        }
                    }
                }
            }
        }
    }
    if (confirm != null && plan != null) AlertDialog(
        onDismissRequest = { if (!busy) confirm = null },
        title = { Text(if (confirm == "approve") "批准并执行" else "反馈修改") },
        text = {
            if (confirm == "approve") Text("${plan.title}\n${approach.ifBlank { "按计划执行" }}")
            else OutlinedTextField(feedback, { feedback = it }, label = { Text("修改反馈") }, minLines = 3, maxLines = 8)
        },
        confirmButton = { TextButton(enabled = canChangeDomain && !busy && (confirm == "approve" || feedback.isNotBlank()), onClick = {
            val approving = confirm == "approve"
            onCommand(NativeSlashRequest(if (approving) PaletteAction.PLAN_APPROVE else PaletteAction.PLAN_REJECT,
                if (approving) "/plan-approve" else "/plan-reject", "${plan.slug} ${if (approving) approach else feedback}".trim()))
            confirm = null
        }) { Text(if (confirm == "approve") "开始执行" else "提交反馈") } },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } },
    )
}

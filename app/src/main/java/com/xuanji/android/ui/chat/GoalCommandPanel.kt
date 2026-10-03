package com.xuanji.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xuanji.android.TianshuGoal
import com.xuanji.android.TianshuGoalConfig
import com.xuanji.android.goalCommandArgument
import com.xuanji.android.parseGoalConfig
import kotlin.math.roundToLong

@Composable
internal fun GoalCommandPanel(goal: TianshuGoal?, canStart: Boolean, busy: Boolean, running: Boolean,
    promptPending: Boolean, onCommand: (NativeSlashRequest) -> Unit) {
    var task by rememberSaveable(goal?.id) { mutableStateOf("") }
    var max by rememberSaveable(goal?.id) { mutableStateOf("100") }
    var budget by rememberSaveable(goal?.id) { mutableStateOf("") }
    var criteria by rememberSaveable(goal?.id) { mutableStateOf("") }
    var formError by rememberSaveable(goal?.id) { mutableStateOf<String?>(null) }
    var cancelling by remember(goal?.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (goal != null) {
            Text(goal.statusLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(when { running -> "天枢正在执行"; promptPending -> "启动回执待确认"; else -> "天枢当前空闲" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(goal.goal, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { (goal.iteration.toFloat() / goal.maxIterations.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${goal.iteration} / ${goal.maxIterations} 轮", style = MaterialTheme.typography.labelMedium)
                Text("${goal.elapsedMs / 1000} 秒" + (goal.budgetMs?.let { " / ${it / 60000} 分钟" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (goal.criteria.isNotEmpty()) {
                Text("验收判据", style = MaterialTheme.typography.titleSmall)
                goal.criteria.forEachIndexed { index, criterion ->
                    Text("${index + 1}. $criterion", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (goal.verdict.isNotBlank()) MarkdownView(goal.verdict)
            if (goal.terminalReason.isNotBlank()) Text(goal.terminalReason, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (goal.status != "complete") {
                Text("暂停与取消目标会停止后续自主轮次；当前回复仍可用聊天页的停止按钮中断。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (goal.status == "active") OutlinedButton(onClick = {
                        onCommand(NativeSlashRequest(PaletteAction.GOAL_PAUSE, "/goal-pause"))
                    }, enabled = !busy) { Icon(Icons.Default.Pause, null, Modifier.size(18.dp)); Text("暂停续跑") }
                    else Button(onClick = {
                        onCommand(NativeSlashRequest(PaletteAction.GOAL_RESUME, "/goal-resume"))
                    }, enabled = canStart && !busy) { Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Text("继续执行") }
                    TextButton(onClick = { cancelling = true }, enabled = !busy) { Text("取消目标") }
                }
            }
        }
        if (goal == null || goal.status == "complete") {
            Text("新目标", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(task, { task = it; formError = null }, enabled = canStart && !busy,
                label = { Text("目标") }, minLines = 3, maxLines = 7, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(max, { max = it; formError = null }, label = { Text("最大轮次") }, singleLine = true,
                    enabled = canStart && !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(budget, { budget = it; formError = null }, label = { Text("分钟预算") }, singleLine = true,
                    enabled = canStart && !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            OutlinedTextField(criteria, { criteria = it; formError = null }, enabled = canStart && !busy,
                label = { Text("验收项（每行一项）") }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
            formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                runCatching {
                    val config = TianshuGoalConfig(task.trim(), max.toIntOrNull() ?: error("最大轮次必须是正整数"),
                        if (budget.isBlank()) null else {
                            val minutes = budget.toDoubleOrNull() ?: error("分钟预算格式无效")
                            require(minutes.isFinite() && minutes > 0 && minutes <= Long.MAX_VALUE / 60000.0) { "分钟预算必须为正数" }
                            (minutes * 60000).roundToLong()
                        }, criteria.lineSequence().map(String::trim).filter(String::isNotBlank).toList())
                    val argument = goalCommandArgument(config)
                    parseGoalConfig(argument)
                    onCommand(NativeSlashRequest(PaletteAction.GOAL, "/goal", argument))
                }.onFailure { formError = it.message ?: "目标参数无效" }
            }, enabled = canStart && !busy && task.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("启动自主目标")
            }
        }
    }
    if (cancelling) AlertDialog(onDismissRequest = { cancelling = false }, title = { Text("取消自主目标") },
        text = { Text(goal?.goal.orEmpty()) }, confirmButton = { TextButton(enabled = !busy, onClick = {
            onCommand(NativeSlashRequest(PaletteAction.GOAL_CANCEL, "/goal-cancel")); cancelling = false
        }) { Text("取消目标") } }, dismissButton = { TextButton(onClick = { cancelling = false }) { Text("保留目标") } })
}

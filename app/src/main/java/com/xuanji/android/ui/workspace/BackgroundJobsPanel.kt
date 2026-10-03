package com.xuanji.android.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuanji.android.BackgroundJob
import com.xuanji.android.XuanjiViewModel
import com.xuanji.android.ui.theme.Danger
import com.xuanji.android.ui.theme.Success
import com.xuanji.android.ui.theme.Warning

@Composable
internal fun BackgroundJobsPanel(vm: XuanjiViewModel) {
    val state by vm.backgroundJobs.collectAsStateWithLifecycle()
    val log by vm.jobLog.collectAsStateWithLifecycle()
    val error by vm.jobError.collectAsStateWithLifecycle()
    val killing by vm.killingJobId.collectAsStateWithLifecycle()
    var selectedJobId by remember { mutableStateOf<String?>(null) }
    var killTarget by remember { mutableStateOf<BackgroundJob?>(null) }
    val running = state.jobs.count { it.status == "running" }

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("后台作业", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                if (running > 0) "$running 项运行中" else "无运行中作业",
                style = MaterialTheme.typography.labelMedium,
                color = if (running > 0) Success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(7.dp))
        if (state.jobs.isEmpty()) {
            Text("暂无后台作业", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.jobs.take(8).forEach { job ->
                JobRow(
                    job = job,
                    killing = killing == job.id,
                    onOpen = { selectedJobId = job.id; vm.loadJobLog(job.id) },
                    onKill = { killTarget = job },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        error?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = Danger, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::dismissJobError, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭作业错误", modifier = Modifier.size(17.dp))
                }
            }
        }
    }

    val selected = state.jobs.firstOrNull { it.id == selectedJobId }
    if (selected != null) {
        Dialog(onDismissRequest = { selectedJobId = null }) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.72f).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("作业日志", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.loadJobLog(selected.id) }) { Icon(Icons.Default.Refresh, "刷新日志") }
                        IconButton(onClick = { selectedJobId = null }) { Icon(Icons.Default.Close, "关闭日志") }
                    }
                    Text(selected.command, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    if (log?.jobId == selected.id && log?.loading == true) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        SelectionContainer {
                            Text(
                                log?.takeIf { it.jobId == selected.id }?.text.orEmpty().ifBlank { "暂无输出" },
                                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }

    killTarget?.let { job ->
        AlertDialog(
            onDismissRequest = { killTarget = null },
            title = { Text("终止后台作业？") },
            text = { Text(job.command, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { vm.killJob(job.id); killTarget = null }) { Text("终止", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { killTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun JobRow(job: BackgroundJob, killing: Boolean, onOpen: () -> Unit, onKill: () -> Unit) {
    val (color, label) = when (job.status) {
        "running" -> Success to "运行中"
        "exited" -> if (job.exitCode == 0) Success to "已完成" else Danger to "退出 ${job.exitCode ?: "?"}"
        else -> Warning to "已终止"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f)) {
            Text(job.command, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(label, job.lastLine).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (job.status == "running") {
            if (killing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
            else IconButton(onClick = onKill, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Stop, contentDescription = "终止 ${job.command}", tint = Danger, modifier = Modifier.size(20.dp))
            }
        }
    }
}

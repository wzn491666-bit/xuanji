package com.xuanji.android.ui.chat

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xuanji.android.TianshuTodos
import com.xuanji.android.todoIdArgument
import kotlinx.coroutines.launch

@Composable
internal fun TodoCommandPanel(snapshot: TianshuTodos, canEdit: Boolean, busy: Boolean,
    acknowledgedArgument: String?, onCommand: (NativeSlashRequest) -> Unit, modifier: Modifier = Modifier,
    notice: String = "", error: String? = null) {
    var draft by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingRevision by rememberSaveable { mutableStateOf("") }
    var editContent by rememberSaveable { mutableStateOf("") }
    var removingId by rememberSaveable { mutableStateOf<String?>(null) }
    var removingRevision by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    var receiptDetails by rememberSaveable { mutableStateOf(false) }
    val enabled = canEdit && !busy
    val scope = rememberCoroutineScope()
    fun submit(argument: String, expectedRevision: String = snapshot.revision) {
        submitted = argument
        onCommand(NativeSlashRequest(PaletteAction.TODO, "/todo", argument, expectedRevision))
    }
    LaunchedEffect(snapshot.revision, acknowledgedArgument) {
        if (acknowledgedArgument != null && submitted == acknowledgedArgument) {
            when {
                acknowledgedArgument == "add $draft" -> draft = ""
                acknowledgedArgument.startsWith("edit ") -> editingId = null
                acknowledgedArgument.startsWith("skip ") -> removingId = null
            }
            submitted = null
        }
    }
    Column(modifier.fillMaxWidth()) {
        val completed = snapshot.items.count { it.status == "completed" }
        val active = snapshot.items.count { it.status == "in_progress" }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${snapshot.items.size - completed} 项待推进", style = MaterialTheme.typography.titleMedium)
                Text("$active 项进行中 · $completed 项已完成", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (snapshot.items.isNotEmpty()) CircularProgressIndicator(progress = {
                completed.toFloat() / snapshot.items.size
            }, Modifier.size(32.dp), strokeWidth = 3.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)) } }
            if (notice.isNotBlank()) item {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (snapshot.items.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("清单留白，下一步由你落笔。", style = MaterialTheme.typography.titleMedium)
                    Text("还没有任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(snapshot.items, key = { _, item -> item.id }) { index, item ->
                var menuOpen by remember(item.id) { mutableStateOf(false) }
                val clipboard = LocalClipboard.current
                val idArgument = todoIdArgument(item.id)
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    Checkbox(item.status == "completed", onCheckedChange = {
                        submit("${if (it) "done" else "pending"} $idArgument")
                    }, enabled = enabled, modifier = Modifier.size(44.dp))
                    Column(Modifier.weight(1f).padding(top = 4.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(item.content, style = MaterialTheme.typography.bodyMedium,
                            color = if (item.status == "completed") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        if (item.status == "in_progress" && !item.activeForm.isNullOrBlank()) Text(item.activeForm,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.statusLabel, style = MaterialTheme.typography.labelSmall,
                                color = if (item.status == "in_progress") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.id.take(12), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                        }
                    }
                    Box {
                        ChatIconButton("任务操作", Icons.Default.MoreVert, { menuOpen = true }, enabled = !busy)
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            if (item.status != "in_progress") DropdownMenuItem(text = { Text("开始任务") }, leadingIcon = {
                                Icon(Icons.Default.PlayArrow, null)
                            }, enabled = enabled, onClick = { menuOpen = false; submit("start $idArgument") })
                            if (item.status != "pending") DropdownMenuItem(text = { Text("恢复待办") }, leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.Undo, null)
                            }, enabled = enabled, onClick = { menuOpen = false; submit("pending $idArgument") })
                            DropdownMenuItem(text = { Text("编辑任务") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                                enabled = enabled, onClick = { menuOpen = false; editingId = item.id;
                                    editingRevision = snapshot.revision; editContent = item.content })
                            DropdownMenuItem(text = { Text("向上移动") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                                enabled = enabled && index > 0, onClick = { menuOpen = false; submit("move $idArgument up") })
                            DropdownMenuItem(text = { Text("向下移动") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                                enabled = enabled && index < snapshot.items.lastIndex, onClick = { menuOpen = false; submit("move $idArgument down") })
                            DropdownMenuItem(text = { Text("复制任务 ID") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                onClick = { menuOpen = false; scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("任务 ID", item.id)))
                                } })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("从清单移除", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error) },
                                enabled = enabled, onClick = { menuOpen = false; removingId = item.id; removingRevision = snapshot.revision })
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            }
            if (snapshot.notice.isNotBlank()) item {
                TextButton(onClick = { receiptDetails = !receiptDetails }) { Text(if (receiptDetails) "收起执行回执" else "查看执行回执") }
                if (receiptDetails) MarkdownView(snapshot.notice, Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!canEdit) Text("天枢执行期间仍可查看清单", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it }, label = { Text("新任务") }, maxLines = 3,
                    enabled = !busy, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyMedium)
                ChatIconButton("添加任务", Icons.Default.Add, { submit("add $draft") },
                    enabled = enabled && draft.isNotBlank() && draft.length <= 10000)
            }
        }
    }
    if (editingId != null) AlertDialog(onDismissRequest = { if (!busy) editingId = null }, title = { Text("编辑任务") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(editContent, { editContent = it }, minLines = 3, maxLines = 8, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium)
            if (editingRevision != snapshot.revision) {
                Text("清单已有更新", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                snapshot.items.firstOrNull { it.id == editingId }?.let { latest ->
                    Text("最新内容：${latest.content}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(enabled = !busy, onClick = { editingRevision = snapshot.revision }) { Text("保留修改，使用最新清单") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } }, confirmButton = { TextButton(enabled = enabled && editContent.isNotBlank() && editContent.length <= 10000 && editingRevision == snapshot.revision,
            onClick = { submit("edit ${todoIdArgument(editingId.orEmpty())} $editContent", editingRevision) }) { Text(if (busy) "保存中" else "保存修改") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { editingId = null }) { Text("取消") } })
    if (removingId != null) AlertDialog(onDismissRequest = { if (!busy) removingId = null }, title = { Text("从清单移除任务") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(snapshot.items.firstOrNull { it.id == removingId }?.content ?: "这条任务已更新，请刷新清单。")
            if (removingRevision != snapshot.revision) {
                Text("清单已有更新，请核对后再次确认。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(enabled = !busy, onClick = { removingRevision = snapshot.revision }) { Text("已核对最新任务") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } }, confirmButton = { TextButton(enabled = enabled && snapshot.items.any { it.id == removingId } && removingRevision == snapshot.revision,
            onClick = { submit("skip ${todoIdArgument(removingId.orEmpty())}", removingRevision) }) { Text("移除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { removingId = null }) { Text("取消") } })
}

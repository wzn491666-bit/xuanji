package com.xuanji.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuanji.android.SessionHistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDrawer(
    sessions: List<SessionHistoryEntry>, selectedId: String?, loading: Boolean, error: String?, enabled: Boolean,
    onSelect: (String) -> Unit, onNew: () -> Unit, onRefresh: () -> Unit, onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val groups = sessions.filter { query.isBlank() || it.title.contains(query, true) || it.model.contains(query, true) }
        .groupBy {
            val day = Instant.ofEpochMilli(it.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            when {
                day == today -> "今天"
                day == today.minusDays(1) -> "昨天"
                day >= today.minusDays(7) -> "最近 7 天"
                else -> "更早"
            }
        }
    ModalDrawerSheet(Modifier.widthIn(max = 320.dp).fillMaxHeight(), drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("璇玑", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ChatIconButton("刷新历史", Icons.Default.Refresh, onRefresh, !loading)
            ChatIconButton("关闭历史", Icons.Default.Close, onClose)
        }
        FilledTonalButton(onClick = onNew, enabled = enabled, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Icon(Icons.Default.EditNote, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("新对话")
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("搜索对话") }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.medium)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
            TextButton(onClick = onRefresh) { Text("重试") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (groups.isEmpty() && !loading && error == null) item {
                Text(if (query.isBlank()) "暂无历史对话" else "没有匹配的对话", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
            groups.forEach { (label, entries) ->
                item(key = "group:$label") {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp))
                }
                items(entries, key = { it.id }) { entry ->
                    val selected = selectedId == entry.id
                    Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(min = 56.dp)
                        .alpha(if (enabled) 1f else 0.5f).clip(MaterialTheme.shapes.medium)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(entry.id) })
                        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (entry.status == "running") Icons.Default.GraphicEq else Icons.Default.ChatBubbleOutline, null,
                            Modifier.size(18.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                            if (entry.model.isNotBlank()) Text(entry.model, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

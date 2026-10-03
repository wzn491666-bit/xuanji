package com.xuanji.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xuanji.android.search.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebSearchSheet(settings: SearchSettings, keys: Map<SearchEngine, Boolean>, busy: Boolean,
    report: WebSearchReport?, error: String?, onUpdate: (SearchSettings) -> Boolean,
    onKey: (SearchEngine, String) -> Boolean, onTest: (String) -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit,
    onOpen: (String) -> Unit) {
    var credentialEngine by remember { mutableStateOf<SearchEngine?>(null) }
    var query by remember { mutableStateOf("Android WebView 官方文档") }
    var instance by remember(settings.searxngUrl) { mutableStateOf(settings.searxngUrl) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("联网搜索", style = MaterialTheme.typography.titleLarge)
                    Text("${settings.engines.size} 个引擎 · ${settings.depth.label}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ChatIconButton("关闭搜索设置", Icons.Default.Close, onDismiss)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(true to "自动", false to "多引擎").forEachIndexed { index, (auto, label) ->
                            SegmentedButton(selected = settings.automatic == auto,
                                onClick = { onUpdate(settings.copy(automatic = auto)) }, enabled = !busy,
                                shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
                        }
                    }
                }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SearchDepth.entries.forEachIndexed { index, depth ->
                            SegmentedButton(selected = settings.depth == depth, enabled = !busy,
                                onClick = { onUpdate(settings.copy(depth = depth)) },
                                shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(depth.label) }
                        }
                    }
                    Text("每源最多 ${settings.depth.perEngine} 条 · 合并后最多 ${settings.depth.outputLimit} 条",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
                item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
                items(SearchEngine.entries, key = { it.wire }) { engine ->
                    Row(Modifier.fillMaxWidth().toggleable(value = engine in settings.engines, role = Role.Checkbox, enabled = !busy,
                        onValueChange = { checked -> onUpdate(settings.copy(engines = if (checked) settings.engines + engine else settings.engines - engine)) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = engine in settings.engines, onCheckedChange = null, enabled = !busy)
                        Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(engine.label, style = MaterialTheme.typography.titleSmall)
                            Text(when {
                                engine == SearchEngine.SEARXNG -> if (settings.searxngUrl.isBlank()) "需配置实例地址" else "自建实例"
                                keys[engine] == true -> "密钥已加密保存"
                                engine.credentialRequired -> "需配置 API Key"
                                engine in setOf(SearchEngine.BING, SearchEngine.DUCKDUCKGO) -> "无需 API Key"
                                else -> "可尝试匿名额度"
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (engine !in setOf(SearchEngine.BING, SearchEngine.DUCKDUCKGO, SearchEngine.SEARXNG)) {
                            ChatIconButton("配置 ${engine.label} 密钥", Icons.Default.Key, { credentialEngine = engine }, enabled = !busy)
                        }
                    }
                }
                if (SearchEngine.SEARXNG in settings.engines) item {
                    OutlinedTextField(value = instance, onValueChange = { instance = it }, enabled = !busy,
                        label = { Text("SearXNG 实例") }, placeholder = { Text("https://search.example.com") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        trailingIcon = { ChatIconButton("保存实例地址", Icons.Default.Check, { onUpdate(settings.copy(searxngUrl = instance)) }, !busy) })
                }
                item {
                    Text("搜索词会发送给所选引擎。匿名接口可能限流；深入检索可能消耗更多额度。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                item {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("测试搜索") },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(), maxLines = 3,
                        trailingIcon = {
                            ChatIconButton(if (busy) "取消搜索测试" else "执行搜索测试", if (busy) Icons.Default.Stop else Icons.Default.Search,
                                { if (busy) onCancel() else onTest(query) }, enabled = busy || query.isNotBlank())
                        })
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                }
                error?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } }
                report?.let { result ->
                    items(result.engines, key = { "probe:${it.engine.wire}" }) { engine ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(if (engine.successful) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, Modifier.size(18.dp),
                                tint = if (engine.successful) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Column(Modifier.weight(1f)) {
                                Text(engine.engine.label, style = MaterialTheme.typography.labelLarge)
                                Text(engine.error ?: if (engine.sources.isEmpty()) "未找到结果" else "${engine.sources.size} 条 · ${engine.elapsedMs} ms",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(result.sources, key = { "source:${it.url}" }) { source ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            TextButton(onClick = { onOpen(source.url) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(source.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, "在内置浏览器打开", Modifier.size(18.dp))
                            }
                            Text(source.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
                            Text(source.engines.joinToString(" · ") { it.label }, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
    credentialEngine?.let { engine ->
        var key by remember(engine) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { credentialEngine = null }, title = { Text("${engine.label} API Key") },
            text = { OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true,
                label = { Text(if (keys[engine] == true) "替换已保存的密钥" else "API Key") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)) },
            confirmButton = { TextButton(enabled = key.isNotBlank(), onClick = { if (onKey(engine, key)) { key = ""; credentialEngine = null } }) { Text("保存") } },
            dismissButton = { Row {
                if (keys[engine] == true) TextButton(onClick = { if (onKey(engine, "")) credentialEngine = null }) { Text("移除密钥") }
                TextButton(onClick = { credentialEngine = null }) { Text("取消") }
            } })
    }
}

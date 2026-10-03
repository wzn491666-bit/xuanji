package com.xuanji.android.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuanji.android.ChatItem
import com.xuanji.android.ThinkingDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PreparedExecutionContent(
    val events: List<ChatItem>,
    val thinking: Map<Int, PreparedThinkingContent>,
    val assistants: Map<Int, PreparedMarkdown>,
)

@Composable
internal fun ExecutionBlock(events: List<ChatItem>, display: ThinkingDisplay,
    onDisclosureChanged: () -> Unit = {}) {
    var manualExpanded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val childOverrides = rememberSaveable(saver = mapSaver(
        save = { choices -> choices.mapKeys { it.key.toString() } },
        restore = { saved -> mutableStateMapOf<Int, Boolean>().apply {
            saved.forEach { (index, expanded) -> put(index.toInt(), expanded as Boolean) }
        } },
    )) { mutableStateMapOf<Int, Boolean>() }
    val mode = thinkingGroupMode(display, manualExpanded)
    val expanded = mode == ThinkingBodyMode.FULL
    val preview = mode == ThinkingBodyMode.PREVIEW
    var interaction by remember { mutableIntStateOf(0) }
    var prepared by remember { mutableStateOf<PreparedExecutionContent?>(null) }
    var readyInteraction by remember { mutableIntStateOf(-1) }
    val latestEvents by rememberUpdatedState(events)
    val requestedInteraction = interaction
    LaunchedEffect(mode, requestedInteraction, display) {
        if (mode != ThinkingBodyMode.FULL) return@LaunchedEffect
        val source = latestEvents.toList()
        val overrides = childOverrides.toMap()
        prepared = withContext(Dispatchers.Default) {
            val thinking = mutableMapOf<Int, PreparedThinkingContent>()
            val assistants = mutableMapOf<Int, PreparedMarkdown>()
            source.forEachIndexed { index, item -> when (item) {
                is ChatItem.Thinking -> thinking[index] = prepareThinkingContent(item.text,
                    thinkingSegmentMode(display, overrides[index]))
                is ChatItem.Assistant -> assistants[index] = PreparedMarkdown(item.text, MarkdownParser.parse(item.text))
                else -> Unit
            } }
            PreparedExecutionContent(source, thinking, assistants)
        }
        readyInteraction = requestedInteraction
    }
    val ready = mode != ThinkingBodyMode.FULL || readyInteraction == interaction && prepared != null
    val latestThinking = events.filterIsInstance<ChatItem.Thinking>().lastOrNull()
    val tools = events.filterIsInstance<ChatItem.Tool>()
    val failures = tools.count { it.isError }
    val working = tools.any { it.running } || latestThinking?.streaming == true || !ready
    val title = when {
        failures > 0 -> "$failures 项工具调用失败"
        tools.any { it.running } -> toolTitle(tools.last { it.running }.name)
        latestThinking?.streaming == true -> "天枢正在思考"
        tools.isNotEmpty() -> "完成 ${tools.size} 项工具调用"
        else -> "思考过程"
    }
    val scheme = MaterialTheme.colorScheme
    val motionEnabled = rememberSystemMotionScale() > 0f && rememberChatForeground()
    val rotation = animateFloatAsState(if (expanded) 180f else 0f,
        if (motionEnabled) tween(160) else snap(), label = "execution disclosure")
    val toggle = {
        onDisclosureChanged()
        val currentlyExpanded = thinkingGroupMode(display, manualExpanded) == ThinkingBodyMode.FULL
        interaction++
        manualExpanded = !currentlyExpanded
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = scheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(0.5.dp, if (failures > 0) scheme.error.copy(alpha = 0.3f) else scheme.outlineVariant.copy(alpha = 0.7f))) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = toggle)
                .semantics { stateDescription = if (expanded) "已展开" else if (preview) "最新四行" else "已收起" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (working) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
                else Icon(if (failures > 0) Icons.Default.ErrorOutline else Icons.Default.AutoAwesome,
                    null, Modifier.size(17.dp), tint = if (failures > 0) scheme.error else scheme.secondary)
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, color = if (failures > 0) scheme.error else scheme.onSurfaceVariant)
                Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp).graphicsLayer { rotationZ = rotation.value }, tint = scheme.onSurfaceVariant)
            }
            ThinkingDisclosureContent(mode, interaction, motionEnabled, ready) { shownMode, revealing, newContent ->
                if (shownMode == ThinkingBodyMode.FULL) {
                    // Reveal exactly the prepared snapshot; new stream events resume after the transition.
                    val shownEvents = if (revealing && newContent && mode == ThinkingBodyMode.FULL && ready) prepared?.events ?: events else events
                    shownEvents.forEachIndexed { index, item -> key(index) {
                        when (item) {
                            is ChatItem.Thinking -> ThinkingSection(item, display, childOverrides[index], onExpandedChange = {
                                onDisclosureChanged()
                                childOverrides[index] = it
                            }, motionEnabled = motionEnabled, initialContent = prepared?.thinking?.get(index))
                            is ChatItem.Tool -> ToolSection(item)
                            is ChatItem.Assistant -> {
                                val body = rememberPreparedMarkdown(item.text, prepared?.assistants?.get(index))
                                val type = MaterialTheme.typography
                                MaterialTheme(typography = type.copy(bodyLarge = type.bodySmall,
                                    titleLarge = type.bodyMedium, titleMedium = type.bodyMedium, titleSmall = type.bodyMedium)) {
                                    SelectionContainer { MarkdownView(body?.blocks.orEmpty(), Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
                                }
                            }
                            is ChatItem.User -> Unit
                        }
                    } }
                    Spacer(Modifier.height(8.dp))
                } else if (shownMode == ThinkingBodyMode.PREVIEW && latestThinking != null) {
                    ThinkingBody(latestThinking, expanded = false, onToggle = toggle)
                }
            }
        }
    }
}

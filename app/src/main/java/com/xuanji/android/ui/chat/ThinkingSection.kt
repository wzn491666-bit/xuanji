package com.xuanji.android.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuanji.android.ChatItem
import com.xuanji.android.ThinkingDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun ThinkingSection(item: ChatItem.Thinking, defaultDisplay: ThinkingDisplay,
    onDisclosureChanged: () -> Unit = {}) {
    var overrideExpanded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val motionEnabled = rememberSystemMotionScale() > 0f && rememberChatForeground()
    ThinkingSection(item, defaultDisplay, overrideExpanded, onExpandedChange = {
        onDisclosureChanged()
        overrideExpanded = it
    }, motionEnabled = motionEnabled)
}

@Composable
internal fun ThinkingSection(item: ChatItem.Thinking, defaultDisplay: ThinkingDisplay,
    overrideExpanded: Boolean?, onExpandedChange: (Boolean) -> Unit, motionEnabled: Boolean,
    initialContent: PreparedThinkingContent? = null) {
    val mode = thinkingSegmentMode(defaultDisplay, overrideExpanded)
    val expanded = mode == ThinkingBodyMode.FULL
    val preview = mode == ThinkingBodyMode.PREVIEW
    var interaction by remember { mutableIntStateOf(0) }
    var prepared by remember { mutableStateOf(initialContent ?: PreparedThinkingContent()) }
    var readyInteraction by remember { mutableIntStateOf(if (prepared.hasBody(mode)) interaction else -1) }
    val latestText by rememberUpdatedState(item.text)
    val requestedInteraction = interaction
    LaunchedEffect(mode, requestedInteraction) {
        if (mode == ThinkingBodyMode.HIDDEN) return@LaunchedEffect
        snapshotFlow { latestText }.collect { source ->
            val previous = prepared
            prepared = withContext(Dispatchers.Default) { prepareThinkingContent(source, mode, previous) }
            readyInteraction = requestedInteraction
        }
    }
    val ready = mode == ThinkingBodyMode.HIDDEN || readyInteraction == interaction && prepared.hasBody(mode)
    val rotation = animateFloatAsState(if (expanded) 180f else 0f,
        if (motionEnabled) tween(160) else snap(), label = "thinking disclosure")
    val scheme = MaterialTheme.colorScheme
    val toggle = { interaction++; onExpandedChange(!expanded) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = toggle)
            .semantics { stateDescription = if (expanded) "已展开" else if (preview) "半折叠" else "已收起" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.streaming || !ready) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
            else Icon(Icons.Default.Psychology, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
            Text(if (item.streaming) "正在思考" else "思考过程", Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            if (item.streaming) Text("实时", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp).graphicsLayer {
                rotationZ = rotation.value
            }, tint = scheme.onSurfaceVariant)
        }
        ThinkingDisclosureContent(mode, interaction, motionEnabled, ready) { shownMode, _, _ ->
            ThinkingBody(item, shownMode == ThinkingBodyMode.FULL, toggle,
                blocks = prepared.full?.blocks, previewText = prepared.previewText)
        }
    }
}

internal data class PreparedThinkingContent(
    val full: PreparedMarkdown? = null,
    val previewSource: String? = null,
    val previewText: String? = null,
) {
    fun hasBody(mode: ThinkingBodyMode) = when (mode) {
        ThinkingBodyMode.FULL -> full != null
        ThinkingBodyMode.PREVIEW -> previewText != null
        ThinkingBodyMode.HIDDEN -> true
    }
}

internal fun prepareThinkingContent(source: String, mode: ThinkingBodyMode,
    previous: PreparedThinkingContent = PreparedThinkingContent()): PreparedThinkingContent {
    if (mode == ThinkingBodyMode.HIDDEN) return previous
    val suffix = source.takeLast(8192)
    val preview = if (previous.previewSource == suffix) previous.previewText
        else MarkdownParser.plainText(suffix).trimEnd()
    val full = if (mode != ThinkingBodyMode.FULL || previous.full?.source == source) previous.full
        else PreparedMarkdown(source, MarkdownParser.parse(source))
    return PreparedThinkingContent(full, suffix, preview)
}

private class ThinkingRevealTarget { var height: Int? = null }

/** Wait for prepared content, then freeze one measured target for this user transition. */
@Composable
internal fun ThinkingDisclosureContent(mode: ThinkingBodyMode, interaction: Int, motionEnabled: Boolean,
    ready: Boolean = true,
    content: @Composable (ThinkingBodyMode, Boolean, Boolean) -> Unit) {
    var renderedMode by remember { mutableStateOf(if (ready) mode else ThinkingBodyMode.HIDDEN) }
    var displayedHeight by remember { mutableIntStateOf(0) }
    var fromHeight by remember { mutableIntStateOf(0) }
    var animating by remember { mutableStateOf(false) }
    var revealingNewContent by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableIntStateOf(interaction) }
    val progress = remember { Animatable(1f) }
    val frozenTarget = remember { ThinkingRevealTarget() }

    LaunchedEffect(mode, interaction, motionEnabled, ready) {
        if (!ready) {
            revealingNewContent = false
            return@LaunchedEffect
        }
        val userChanged = interaction != lastInteraction
        lastInteraction = interaction
        if (!userChanged || !motionEnabled) {
            animating = false
            revealingNewContent = false
            renderedMode = mode
            return@LaunchedEffect
        }
        fromHeight = if (renderedMode == ThinkingBodyMode.HIDDEN) 0 else displayedHeight
        revealingNewContent = mode != ThinkingBodyMode.HIDDEN && renderedMode != mode
        progress.snapTo(0f)
        frozenTarget.height = if (mode == ThinkingBodyMode.HIDDEN) 0 else null
        animating = true
        if (mode != ThinkingBodyMode.HIDDEN) renderedMode = mode
        progress.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
        animating = false
        renderedMode = mode
    }
    if (renderedMode != ThinkingBodyMode.HIDDEN) {
        Box(Modifier.fillMaxWidth().onSizeChanged { displayedHeight = it.height }.clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0))
            val target = frozenTarget.height ?: placeable.height.also { if (animating) frozenTarget.height = it }
            val height = if (animating) (fromHeight + (target - fromHeight) * progress.value).roundToInt() else placeable.height
            layout(placeable.width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                placeable.placeRelative(0, 0)
            }
        }) { Column { content(renderedMode, animating, revealingNewContent) } }
    }
}

@Composable
internal fun ThinkingBody(item: ChatItem.Thinking, expanded: Boolean, onToggle: () -> Unit,
    blocks: List<MarkdownBlock>? = null, previewText: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp).drawBehind {
            drawLine(scheme.outlineVariant, Offset.Zero, Offset(0f, size.height), strokeWidth = 2.dp.toPx())
        }) {
            if (expanded) {
                val typography = MaterialTheme.typography
                val small = typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 21.sp)
                val heading = small.copy(fontWeight = FontWeight.SemiBold)
                MaterialTheme(typography = typography.copy(bodyLarge = small, bodyMedium = small, bodySmall = small,
                    titleLarge = heading, titleMedium = heading, titleSmall = heading)) {
                    SelectionContainer {
                        if (blocks != null) MarkdownView(blocks, Modifier.padding(start = 14.dp))
                        else MarkdownView(item.text, Modifier.padding(start = 14.dp))
                    }
                }
            }
            else {
                val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 21.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false))
                val height = with(LocalDensity.current) { style.lineHeight.toDp() * 4 }
                val displayedPreview = if (previewText != null) previewText else {
                    val suffix = item.text.takeLast(8192)
                    val fallbackPreview by produceState("", suffix) {
                        value = withContext(Dispatchers.Default) { MarkdownParser.plainText(suffix).trimEnd() }
                    }
                    fallbackPreview
                }
                // Bottom-align an unbounded text child inside a four-line viewport.
                // Bounding the suffix keeps streaming layout independent of total history length.
                Box(Modifier.padding(start = 14.dp).fillMaxWidth().heightIn(max = height).clipToBounds()
                    .clickable(role = Role.Button, onClick = onToggle)) {
                    Text(displayedPreview, Modifier.fillMaxWidth()
                        .wrapContentHeight(Alignment.Bottom, unbounded = true), style = style, color = scheme.onSurfaceVariant)
                }
            }
        }
}

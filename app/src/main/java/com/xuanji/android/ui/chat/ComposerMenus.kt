package com.xuanji.android.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupPositionProvider
import com.xuanji.android.ChatApprovalMode
import com.xuanji.android.SessionModelChoice
import com.xuanji.android.ThinkingDisplay

@Composable
internal fun ComposerAddMenu(expanded: Boolean, enabled: Boolean, onDismiss: () -> Unit,
    onImages: () -> Unit, onFiles: () -> Unit, onCommands: () -> Unit) {
    DropdownMenu(expanded, onDismiss, modifier = Modifier.width(232.dp), shape = MaterialTheme.shapes.medium) {
        AddMenuItem("添加图片", Icons.Default.AddPhotoAlternate, enabled) { onDismiss(); onImages() }
        AddMenuItem("添加文件", Icons.Default.AttachFile, enabled) { onDismiss(); onFiles() }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        AddMenuItem("命令与技能", Icons.Default.Terminal, true) { onDismiss(); onCommands() }
    }
}

@Composable
private fun AddMenuItem(label: String, icon: ImageVector, enabled: Boolean, action: () -> Unit) {
    DropdownMenuItem(text = { Text(label, style = MaterialTheme.typography.bodyMedium) }, onClick = action, enabled = enabled,
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) })
}

internal fun approvalIcon(mode: String?) = when (mode) {
    "manual" -> Icons.Outlined.PanTool
    "auto-safe", "auto-accept" -> Icons.Outlined.VerifiedUser
    else -> Icons.Outlined.GppMaybe
}

internal fun approvalLabel(mode: String?) = ChatApprovalMode.entries.firstOrNull { it.wire == mode }?.label
    ?: if (mode == "auto-accept") "自动批准" else "审批模式"

@Composable
internal fun ComposerApprovalMenu(expanded: Boolean, current: String?, busy: Boolean,
    onDismiss: () -> Unit, onSelect: (ChatApprovalMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenu(expanded, onDismiss, modifier = Modifier.widthIn(max = 312.dp), shape = MaterialTheme.shapes.medium) {
        Text(if (busy) "正在应用…" else "天枢如何批准操作？", Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        ChatApprovalMode.entries.forEach { mode ->
            val color = if (mode == ChatApprovalMode.FULL) scheme.tertiary else scheme.onSurface
            DropdownMenuItem(
                text = { Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(mode.label, style = MaterialTheme.typography.bodyMedium, color = color)
                    Text(mode.detail, style = MaterialTheme.typography.bodySmall, color = if (mode == ChatApprovalMode.FULL) color else scheme.onSurfaceVariant)
                } },
                onClick = { onSelect(mode); onDismiss() }, enabled = !busy,
                leadingIcon = { Icon(approvalIcon(mode.wire), null, Modifier.size(19.dp), tint = color) },
                trailingIcon = { if (current == mode.wire) Icon(Icons.Default.Check, "当前模式", Modifier.size(18.dp), tint = scheme.primary) },
                modifier = Modifier.heightIn(min = 62.dp),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = scheme.outlineVariant)
        Text("仅控制天枢审批；Android 系统授权仍需单独授予。", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

internal fun modelDisplayName(value: String): String = when (value) {
    "deepseek-v4-flash" -> "V4 Flash"
    "deepseek-v4-pro" -> "V4 Pro"
    "deepseek-flash" -> "V4.1 Flash"
    else -> value
}

@Composable
internal fun ChatModelPanel(
    models: List<SessionModelChoice>, loading: Boolean, error: String?, enabled: Boolean, switching: Boolean,
    effort: String, levels: List<String>, effortBusy: Boolean, thinkingDisplay: ThinkingDisplay,
    section: ModelPanelSection, onSection: (ModelPanelSection) -> Unit,
    onRefresh: () -> Unit, onDismiss: () -> Unit, onSelect: (SessionModelChoice) -> Unit,
    onEffort: (String) -> Unit, onThinkingDisplay: (ThinkingDisplay) -> Unit,
    onPreview: (Float?, Boolean) -> Unit, expanded: Boolean,
) {
    var query by remember { mutableStateOf("") }
    var pendingSpec by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(expanded, switching) { if (!expanded || !switching) pendingSpec = null }
    val config = LocalConfiguration.current
    val height = (config.screenHeightDp - 148).coerceIn(200, 580).dp
    val width = (config.screenWidthDp - 32).coerceIn(200, 440).dp
    val scheme = MaterialTheme.colorScheme
    val groups = remember(models, query) { models.filter { "${it.alias} ${it.id} ${it.providerLabel} ${it.keyLabel.orEmpty()}".contains(query.trim(), true) }.groupBy { it.providerLabel } }
    val placement = remember { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
            IntOffset(((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
                anchorBounds.bottom.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    } }
    AnimatedChatPopup(expanded, placement, onDismiss, TransformOrigin(0.5f, 0f)) {
        Surface(Modifier.width(width).height(height), shape = RoundedCornerShape(20.dp),
            color = scheme.surface, shadowElevation = 10.dp, border = BorderStroke(0.5.dp, scheme.outlineVariant)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("模型与思考", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                ChatIconButton("刷新模型", Icons.Default.Refresh, onRefresh, !loading)
                ChatIconButton("关闭模型选择", Icons.Default.Close, onDismiss)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                ModelPanelSection.entries.forEachIndexed { index, tab ->
                    SegmentedButton(selected = section == tab, onClick = { onSection(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index, ModelPanelSection.entries.size),
                        icon = {}) { Text(tab.label) }
                }
            }
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (loading || switching || effortBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Crossfade(section, Modifier.weight(1f).fillMaxWidth(), animationSpec = tween(150), label = "model panel section") { tab ->
            if (tab == ModelPanelSection.MODELS) Column(Modifier.fillMaxSize()) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), singleLine = true,
                placeholder = { Text("搜索模型") }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) }, shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodyMedium)
            if (!enabled || switching) Text(if (switching) "正在确认模型切换…" else "当前任务结束后可切换", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            error?.let { Text(it, Modifier.padding(vertical = 8.dp), color = scheme.error, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                groups.forEach { (provider, choices) ->
                    item(key = "provider:$provider") { Text(provider, Modifier.padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = scheme.secondary) }
                    items(choices, key = { it.spec }) { choice ->
                        val selectedColor by animateColorAsState(if (choice.current) scheme.primaryContainer else scheme.surface, tween(180), label = "model selection")
                        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).background(selectedColor, RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled && !choice.current, role = Role.Button) { pendingSpec = choice.spec; onSelect(choice) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(modelDisplayName(choice.alias), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                                Text(listOfNotNull(choice.id.takeIf { it != choice.alias }, choice.keyLabel, choice.description.takeIf(String::isNotBlank)).joinToString(" · "),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (switching && pendingSpec == choice.spec) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else if (choice.current) Icon(Icons.Default.Check, "当前模型", Modifier.size(20.dp), tint = scheme.primary)
                        }
                    }
                }
                if (groups.isEmpty() && !loading && error == null) item { Text("没有匹配的模型", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp)) {
                Text("思考强度", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                ReasoningEffortControl(effort, levels, effortBusy, onEffort, onPreview)
                HorizontalDivider(Modifier.padding(vertical = 24.dp), color = scheme.outlineVariant)
                Text("思考过程", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) { ThinkingDisplay.entries.forEach { display ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = display == thinkingDisplay, role = Role.RadioButton,
                        onClick = { onThinkingDisplay(display) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(display == thinkingDisplay, onClick = null)
                        Text(display.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                } }
            }
            }
        }
        }
    }
}

internal enum class ModelPanelSection(val label: String) { MODELS("模型"), THINKING("思考") }

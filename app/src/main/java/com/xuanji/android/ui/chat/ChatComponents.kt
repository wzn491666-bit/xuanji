package com.xuanji.android.ui.chat

import android.content.ClipData
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuanji.android.*
import com.xuanji.android.R
import com.xuanji.android.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatIconButton(label: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed && enabled) 0.9f else 1f, spring(0.8f, 700f), label = "icon press")
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, enabled = enabled, interactionSource = interactions, modifier = Modifier.size(48.dp)) {
            Icon(icon, label, Modifier.size(20.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
    }
}

@Composable
internal fun ChatHeader(
    state: TianshuService.State, progress: Int, ready: Boolean,
    onWorkspace: () -> Unit, onPermissions: () -> Unit, onNewSession: () -> Unit,
    newSessionEnabled: Boolean, runtimeLabel: String? = null, onHistory: (() -> Unit)? = null,
    modelLabel: String? = null, onModel: (() -> Unit)? = null, effort: String = "auto", approvalPending: Boolean = false,
    onSearch: (() -> Unit)? = null,
    onBrowser: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(approvalPending) { if (approvalPending) showMenu = false }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onHistory?.let { ChatIconButton("历史对话", Icons.Default.Menu, it) }
        Column(Modifier.weight(1f).padding(start = 8.dp).heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
            .clickable(enabled = onModel != null, role = Role.Button) { onModel?.invoke() }
            .semantics { contentDescription = "选择模型：${modelLabel.orEmpty()}" }, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (modelLabel != null) {
                    Text(modelLabel, Modifier.weight(1f, fill = false).padding(end = 4.dp), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeStatus(state, progress, ready, runtimeLabel, Modifier.weight(1f, fill = false))
                if (ready) Text("· ${effortLabel(effort)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        onSearch?.let { ChatIconButton("搜索引擎与深度", Icons.Default.TravelExplore, it, tint = MaterialTheme.colorScheme.secondary) }
        ChatIconButton("新对话", Icons.Default.EditNote, onNewSession, newSessionEnabled)
        Box {
            ChatIconButton("更多", Icons.Default.MoreHoriz, { showMenu = true })
            DropdownMenu(showMenu, { showMenu = false }, shape = MaterialTheme.shapes.medium) {
                onBrowser?.let { open -> DropdownMenuItem({ Text("浏览器") }, { showMenu = false; open() }, leadingIcon = { Icon(Icons.Default.Language, null) }) }
                DropdownMenuItem({ Text("工作台") }, { showMenu = false; onWorkspace() }, leadingIcon = { Icon(Icons.Default.SpaceDashboard, null) })
                DropdownMenuItem({ Text("系统权限") }, { showMenu = false; onPermissions() }, leadingIcon = { Icon(Icons.Default.Tune, null) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatComposer(
    input: String, onInput: (String) -> Unit,
    running: Boolean, sendEnabled: Boolean, onSend: () -> Unit, onStop: () -> Unit,
    onCommands: () -> Unit, focusRequester: FocusRequester,
    onFiles: () -> Unit, onImages: () -> Unit, stopping: Boolean = false,
    nativeCommand: Boolean = false,
    approvalMode: String? = null, controlBusy: Boolean = false,
    onApprovalMode: (ChatApprovalMode) -> Unit = {},
    attachments: List<PromptAttachment> = emptyList(), attachmentsLoading: Boolean = false,
    onRemoveAttachment: (String) -> Unit = {},
    onFocus: (Boolean) -> Unit = {},
    approvalPending: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showApproval by remember { mutableStateOf(false) }
    LaunchedEffect(approvalPending) { if (approvalPending) { showAdd = false; showApproval = false } }
    val sendInteractions = remember { MutableInteractionSource() }
    val sendPressed by sendInteractions.collectIsPressedAsState()
    val stopAction = running && !nativeCommand
    val sendScale = animateFloatAsState(if (sendPressed) 0.92f else 1f, spring(0.76f, 650f), label = "send press")
    val borderColor by animateColorAsState(if (focused) scheme.primary.copy(alpha = 0.35f) else scheme.outlineVariant.copy(alpha = 0.4f), tween(180), label = "composer border")
    val sendBackground by animateColorAsState(if (stopAction) scheme.onSurface else scheme.primary, tween(180), label = "send state color")
    Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            color = scheme.surface,
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 1.dp,
        ) {
            Column {
                if (attachments.isNotEmpty()) AttachmentStrip(attachments, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp), onRemoveAttachment)
                if (attachmentsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.Bottom) {
                    Box {
                        ChatIconButton("添加附件或命令", Icons.Default.Add, { showAdd = true }, tint = scheme.secondary)
                        ComposerAddMenu(showAdd, !attachmentsLoading && !controlBusy, { showAdd = false }, onImages, onFiles, onCommands)
                    }
                    BasicTextField(
                    value = input, onValueChange = onInput,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
                        .padding(horizontal = 4.dp, vertical = 12.dp)
                        .heightIn(min = 24.dp, max = 144.dp)
                        .semantics { contentDescription = "消息输入框" },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.onSurface),
                    decorationBox = { field ->
                        Box {
                            if (input.isEmpty()) Text("问问天枢", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                            field()
                        }
                    },
                )
                    Box {
                        ChatIconButton("审批模式：${approvalLabel(approvalMode)}", approvalIcon(approvalMode),
                            { showApproval = true }, enabled = !controlBusy,
                            tint = if (approvalMode == ChatApprovalMode.FULL.wire) scheme.tertiary else scheme.onSurfaceVariant)
                        ComposerApprovalMenu(showApproval, approvalMode, controlBusy, { showApproval = false }, onApprovalMode)
                    }
                    val actionEnabled = !stopping && (stopAction || sendEnabled)
                    val actionLabel = if (stopping) "正在停止天枢任务" else if (stopAction) "停止天枢任务" else if (nativeCommand) "执行本地命令" else "发送消息"
                        IconButton(
                            onClick = { if (stopAction) onStop() else onSend() }, enabled = !stopping && (stopAction || sendEnabled),
                            modifier = Modifier.size(48.dp).semantics { contentDescription = actionLabel },
                            interactionSource = sendInteractions,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (stopAction) scheme.surface else scheme.onPrimary,
                                disabledContentColor = scheme.onSurfaceVariant,
                            ),
                        ) {
                            Box(Modifier.size(40.dp).graphicsLayer { scaleX = sendScale.value; scaleY = sendScale.value }
                                .background(if (actionEnabled) sendBackground else scheme.surfaceContainerHighest, CircleShape),
                                contentAlignment = Alignment.Center) {
                            AnimatedContent(if (stopping) "stopping" else if (stopAction) "stop" else "send",
                                modifier = Modifier.size(20.dp),
                                transitionSpec = { fadeIn(tween(110)) togetherWith fadeOut(tween(80)) }, label = "send stop") { action ->
                                if (action == "stopping") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(if (action == "stop") Icons.Default.Stop else Icons.Default.ArrowUpward, null, Modifier.size(20.dp))
                            }
                            }
                        }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatEmptyState(modifier: Modifier = Modifier, animated: Boolean = true,
    scene: StellarWelcomeScene = stellarWelcomeScenes.first()) {
    var showDomain by rememberSaveable(scene.id) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
            val compact = maxHeight < 400.dp
            Column(Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                StellarBrand(Modifier.size(if (compact) 156.dp else 216.dp), animated && !showDomain)
                Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
                StellarWelcomeVerse(scene) { showDomain = true }
            }
        }
    }
    if (showDomain) StellarDomainDetails(scene) { showDomain = false }
}

private val stellarVerseFonts = mapOf(
    WelcomeTypeface.WENKAI to FontFamily(Font(R.font.stellar_wenkai, FontWeight.Normal)),
    WelcomeTypeface.XIAOWEI to FontFamily(Font(R.font.stellar_xiaowei, FontWeight.Normal)),
    WelcomeTypeface.MASHAN to FontFamily(Font(R.font.stellar_mashan, FontWeight.Normal)),
    WelcomeTypeface.NOTOSERIF to FontFamily(Font(R.font.stellar_notoserif, FontWeight.Normal)),
    WelcomeTypeface.HUANGYOU to FontFamily(Font(R.font.stellar_huangyou, FontWeight.Normal)),
    WelcomeTypeface.LONGCANG to FontFamily(Font(R.font.stellar_longcang, FontWeight.Normal)),
    WelcomeTypeface.ZHIMANG to FontFamily(Font(R.font.stellar_zhimang, FontWeight.Normal)),
    WelcomeTypeface.LIUJIAN to FontFamily(Font(R.font.stellar_liujian, FontWeight.Normal)),
)

@Composable
private fun StellarWelcomeVerse(scene: StellarWelcomeScene, onDomain: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val ink = if (dark) androidx.compose.ui.graphics.lerp(Color(scene.accent), Color.White, 0.45f) else Color(scene.accent)
    Column(Modifier.widthIn(max = 292.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = "查看星域详情", onClick = onDomain)
            .semantics(mergeDescendants = true) { contentDescription = "${scene.domain}星域详情" }
            .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(Modifier.width(20.dp), color = ink.copy(alpha = 0.35f))
            Text("星笺 · ${scene.domain}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            HorizontalDivider(Modifier.width(20.dp), color = ink.copy(alpha = 0.35f))
        }
        Spacer(Modifier.height(4.dp))
        val offset = scene.composition == WelcomeComposition.OFFSET
        val verseStyle = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = stellarVerseFonts.getValue(scene.typeface), fontWeight = FontWeight.Normal,
            fontSize = if (scene.composition == WelcomeComposition.QUIET) 27.sp else 30.sp,
            lineHeight = 42.sp, letterSpacing = 0.sp,
        )
        Text(scene.firstLine, Modifier.fillMaxWidth().padding(start = if (offset) 20.dp else 0.dp),
            style = verseStyle, color = scheme.onSurface, textAlign = if (offset) TextAlign.Start else TextAlign.Center)
        if (scene.composition == WelcomeComposition.QUIET) {
            HorizontalDivider(Modifier.width(28.dp).padding(vertical = 10.dp), color = ink.copy(alpha = 0.45f))
        }
        Text(scene.secondLine, Modifier.fillMaxWidth().padding(end = if (offset) 20.dp else 0.dp),
            style = verseStyle, color = ink, textAlign = if (offset) TextAlign.End else TextAlign.Center)
    }
}

@Composable
internal fun ChatRestoringState(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text("正在接续对话", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TaskSummaryBar(cockpit: Cockpit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Checklist, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(cockpit.goal.ifBlank { "当前任务" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = if (cockpit.deliveryFailed) "交付操作失败" else when (cockpit.deliveryGate) {
                "GREEN" -> "交付通过"
                "YELLOW" -> "交付需复核"
                "RED" -> "交付已阻止"
                else -> if (cockpit.todos.isNotEmpty()) "${cockpit.todos.count { it.status == "completed" }} / ${cockpit.todos.size} 已完成" else null
            }
            detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (cockpit.deliveryFailed) Danger else when (cockpit.deliveryGate) { "GREEN" -> Success; "YELLOW" -> Warning; "RED" -> Danger; else -> MaterialTheme.colorScheme.onSurfaceVariant }) }
        }
        Icon(Icons.Default.ChevronRight, "查看任务详情", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
internal fun ChatMessage(item: ChatItem, onRetry: (String) -> Unit, onEdit: (String) -> Unit,
    thinkingDisplay: ThinkingDisplay = ThinkingDisplay.PREVIEW, onDisclosureChanged: () -> Unit = {}) {
    when (item) {
        is ChatItem.User -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            if (item.attachments.isNotEmpty()) AttachmentStrip(item.attachments, Modifier.fillMaxWidth().padding(bottom = 8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(modifier = Modifier.widthIn(max = maxWidth * 0.88f), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
                    SelectionContainer { Text(item.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            val status = when (item.delivery) {
                PromptDelivery.SENDING -> "正在发送"
                PromptDelivery.ACCEPTED -> "等待确认"
                PromptDelivery.UNCERTAIN -> "送达尚未确认"
                PromptDelivery.REJECTED -> "发送失败"
                PromptDelivery.DELIVERED -> null
            }
            if (status != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status, style = MaterialTheme.typography.labelSmall, color = if (item.delivery == PromptDelivery.REJECTED) Danger else MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.delivery in setOf(PromptDelivery.UNCERTAIN, PromptDelivery.REJECTED)) {
                    item.requestId?.let { ChatIconButton("重试发送", Icons.Default.Refresh, { onRetry(it) }) }
                    ChatIconButton("编辑消息", Icons.Default.Edit, { onEdit(item.text) })
                }
            }
            if (item.delivery in setOf(PromptDelivery.REJECTED, PromptDelivery.UNCERTAIN)) item.deliveryError?.let { reason ->
                Text(reason, Modifier.fillMaxWidth(0.88f).padding(top = 4.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        is ChatItem.Assistant -> Column(Modifier.fillMaxWidth()) {
            SelectionContainer { MarkdownView(item.text, Modifier.fillMaxWidth()) }
            if (item.text.isNotBlank()) {
                val clipboard = LocalClipboard.current
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                var copied by remember(item.text) { mutableStateOf(false) }
                LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
                ChatIconButton(if (copied) "已复制回复" else "复制回复", if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    { scope.launch {
                        try {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("reply", item.text)))
                            copied = true
                        } catch (_: SecurityException) {
                            android.widget.Toast.makeText(context, "暂时无法访问剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } }, tint = if (copied) Success else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is ChatItem.Thinking -> ThinkingSection(item, thinkingDisplay, onDisclosureChanged = onDisclosureChanged)
        is ChatItem.Tool -> ToolSection(item)
    }
}

@Composable
internal fun ToolSection(tool: ChatItem.Tool) {
    var expanded by remember(tool.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "tool disclosure")
    Column(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { expanded = !expanded }.semantics { stateDescription = if (expanded) "已展开" else "已收起" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    tool.running -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
                    tool.isError -> Icon(Icons.Default.ErrorOutline, null, Modifier.size(18.dp), tint = Danger)
                    else -> Icon(Icons.Default.CheckCircleOutline, null, Modifier.size(18.dp), tint = Success)
                }
                Text(toolTitle(tool.name), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (tool.running) "执行中" else if (tool.isError) "失败" else "完成", style = MaterialTheme.typography.labelSmall, color = if (tool.isError) Danger else MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp).rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tool.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(tool.input, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    tool.result?.let {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    }
                }
            }
    }
}

internal fun toolTitle(name: String): String = when (name) {
    "mcp__android__android_read_screen" -> "读取屏幕"
    "mcp__android__android_accessibility_action" -> "屏幕操作"
    "mcp__android__android_list_files" -> "浏览文件"
    "mcp__android__android_read_file", "read_file" -> "读取文件"
    "mcp__android__android_write_file", "write_file" -> "写入文件"
    "mcp__android__android_permission_status" -> "检查权限"
    "mcp__android__android_shell_id" -> "检查命令权限"
    "bash", "execute_command" -> "运行命令"
    "web_search" -> "搜索网页"
    "web_fetch" -> "读取网页"
    else -> name
}

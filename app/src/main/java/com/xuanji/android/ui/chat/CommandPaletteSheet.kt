package com.xuanji.android.ui.chat

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private data class LibraryItem(val command: MobileCommand? = null, val skill: SkillEntry? = null) {
    val id get() = command?.name ?: "/skill ${requireNotNull(skill).name}"
    val title get() = command?.title ?: requireNotNull(skill).name
    val description get() = command?.description ?: requireNotNull(skill).description
    val section get() = command?.librarySection ?: CommandSection.SKILLS
    val connected get() = command?.mobileExecutable ?: true
}

@Composable
internal fun CommandPaletteSheet(
    skills: List<SkillEntry>, skillsError: String?, onRefresh: () -> Unit,
    onDismiss: () -> Unit, initialTab: Int = 0, customCommands: List<MobileCommand> = emptyList(),
    skillsLoading: Boolean = false, commandsError: String? = null, operationBusy: Boolean = false, operationError: String? = null,
    blockedReason: (NativeSlashRequest) -> String? = { null },
    canToggleSkills: Boolean = false, onSkillToggle: ((String, Boolean) -> Unit)? = null,
    onAction: ((NativeSlashRequest) -> Unit)? = null, onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { LocalCommandPreferences(context.applicationContext) }
    var favorites by remember { mutableStateOf(preferences.favorites()) }
    var recents by remember { mutableStateOf(preferences.recents()) }
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable(initialTab) { mutableStateOf(when (initialTab) {
        1 -> CommandSection.SKILLS
        2 -> CommandSection.REFERENCE
        else -> CommandSection.COMMON
    }) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val all = remember(skills, customCommands) { MobileCommandCatalog.filter("", skills, customCommands) }
    val allItems = remember(all) { all.workflows.map { LibraryItem(command = it) } + all.skills.map { LibraryItem(skill = it) } }
    val byId = remember(allItems) { allItems.associateBy { it.id } }
    val results = remember(query, skills, customCommands) { MobileCommandCatalog.filter(query, skills, customCommands) }
    val searching = query.isNotBlank()
    val entries = remember(results, searching) {
        libraryCommands(results.workflows, searching).map { LibraryItem(command = it) } + results.skills.map { LibraryItem(skill = it) }
    }
    val selected = selectedId?.let(byId::get)
    val listState = rememberLazyListState()
    LaunchedEffect(query, section) { listState.scrollToItem(0) }
    fun record(item: LibraryItem) {
        recents = recentCommandSelection(recents, item.id)
        preferences.saveRecents(recents)
    }
    fun toggleFavorite(item: LibraryItem) {
        favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
        preferences.saveFavorites(favorites)
    }
    fun choose(item: LibraryItem) {
        val command = item.command
        val native = command?.let { resolveNativeSlash(it.name) as? NativeSlashResult.Execute }?.request
        if (command != null && command.isSelectable(onAction != null) && native != null &&
            command.nativeAction in directPaletteActions && blockedReason(native) == null) {
            record(item)
            onAction?.invoke(native)
        } else selectedId = item.id
    }
    Dialog(onDismissRequest = { if (selected != null) selectedId = null else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding()) {
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("命令库", style = MaterialTheme.typography.titleLarge)
                            Text("${libraryCommands(all.workflows, false).count { it.isSelectable(onAction != null) }} 项已接入 · ${skills.size} 个技能",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ChatIconButton("刷新命令与技能", Icons.Default.Refresh, onRefresh)
                        ChatIconButton("关闭命令库", Icons.Default.Close, onDismiss)
                    }
                    if (selected == null) {
                        OutlinedTextField(query, { query = it }, singleLine = true,
                            placeholder = { Text("搜索用途、命令或技能", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                            trailingIcon = { if (searching) ChatIconButton("清除搜索", Icons.Default.Close, { query = "" }) },
                            shape = RoundedCornerShape(8.dp), textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AnimatedContent(selected != null, modifier = Modifier.weight(1f).widthIn(max = 900.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) }, label = "command-detail") { detail ->
                    if (detail && selected != null) {
                        CommandDetail(selected, selected.id in favorites, { toggleFavorite(selected) }, { selectedId = null },
                            blockedReason, canToggleSkills, onSkillToggle, onAction != null, operationBusy, operationError,
                            onSelect = { value -> record(selected); onSelect(value) })
                    } else Row(Modifier.fillMaxSize()) {
                        if (!searching) LazyColumn(Modifier.width(72.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).selectableGroup(),
                            contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(CommandSection.entries, key = { it.name }) { category ->
                                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
                                    .background(if (category == section) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
                                    .selectable(category == section, role = Role.Tab, onClick = { section = category })
                                    .heightIn(min = 62.dp).padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(sectionIcon(category), null, Modifier.size(20.dp), tint = if (category == section) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(category.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                                        color = if (category == section) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                            if (skillsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            listOfNotNull(skillsError, commandsError).distinct().forEach { message -> item {
                                Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            } }
                            fun group(label: String, values: List<LibraryItem>) {
                                if (values.isEmpty()) return
                                item(key = "heading:$label") { LibraryHeading(label) }
                                items(values, key = { "$label:${it.id}" }) { item ->
                                    val native = item.command?.let { resolveNativeSlash(it.name) as? NativeSlashResult.Execute }?.request
                                    val reason = when {
                                        !item.connected -> "未接入"
                                        item.skill?.enabled == false -> "已停用"
                                        native != null && blockedReason(native) != null -> "暂不可执行"
                                        else -> null
                                    }
                                    CommandLibraryRow(item, item.id in favorites, reason, { toggleFavorite(item) }, { choose(item) })
                                }
                            }
                            if (searching) {
                                item { LibraryHeading("搜索结果 · ${entries.size}") }
                                if (entries.isEmpty()) item { LibraryEmpty("没有找到相关命令", "试试“计划”“交接”或技能名称") }
                                group("命令", entries.filter { it.command != null })
                                group("技能", entries.filter { it.skill != null })
                            } else if (section == CommandSection.COMMON) {
                                group("已收藏", favorites.mapNotNull(byId::get))
                                group("最近选择", recents.mapNotNull(byId::get).filter { it.id !in favorites })
                                group("常用操作", featuredCommandNames.mapNotNull(byId::get).filter { it.connected && it.id !in favorites && it.id !in recents })
                                group("会话技能", entries.filter { it.skill?.enabled == true }.take(4))
                                if (skills.isNotEmpty()) item {
                                    TextButton(onClick = { section = CommandSection.SKILLS }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text("查看全部 ${skills.size} 个技能"); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 6.dp).size(16.dp))
                                    }
                                }
                            } else {
                                val shown = entries.filter {
                                    if (section == CommandSection.REFERENCE) true
                                    else it.section == section && (it.connected || section in setOf(CommandSection.SKILLS, CommandSection.PROJECT))
                                }
                                item { LibraryHeading(section.subtitle) }
                                if (section == CommandSection.REFERENCE) {
                                    group("已接入", shown.filter { it.connected })
                                    group("尚未接入", shown.filterNot { it.connected })
                                } else {
                                    group(if (section == CommandSection.SKILLS) "调用与管理" else "命令", shown.filter { it.command != null })
                                    group("已启用", shown.filter { it.skill?.enabled == true })
                                    group("已停用", shown.filter { it.skill?.enabled == false })
                                }
                                if (shown.isEmpty()) item { LibraryEmpty(if (skillsLoading && section == CommandSection.SKILLS) "正在读取技能" else "暂无命令",
                                    if (section == CommandSection.PROJECT) "当前工作区没有可读取的项目命令" else "刷新后查看当前会话的内容") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val directPaletteActions = setOf(PaletteAction.HELP, PaletteAction.HISTORY, PaletteAction.MODELS, PaletteAction.EFFORT,
    PaletteAction.THINKING, PaletteAction.PERMISSIONS, PaletteAction.COCKPIT, PaletteAction.FILES, PaletteAction.TERMINAL,
    PaletteAction.SETTINGS, PaletteAction.SKILLS, PaletteAction.DOMAIN, PaletteAction.PLAN_LIST, PaletteAction.PLAN_VIEW,
    PaletteAction.GOAL_STATUS, PaletteAction.GOAL_CRITERIA, PaletteAction.TODO, PaletteAction.TEAM_RESUME)

@Composable
private fun CommandLibraryRow(item: LibraryItem, favorite: Boolean, reason: String?, onFavorite: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(role = Role.Button, onClick = onClick).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(sectionIcon(item.section), null, Modifier.size(20.dp), tint = sectionTint(item.section))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.id, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(reason ?: item.description.ifBlank { "会话技能" }, style = MaterialTheme.typography.bodySmall,
                color = if (reason != null && reason != "未接入") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ChatIconButton(if (favorite) "取消收藏 ${item.title}" else "收藏 ${item.title}", if (favorite) Icons.Default.Star else Icons.Default.StarBorder, onFavorite,
            tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(start = 44.dp, end = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun CommandDetail(item: LibraryItem, favorite: Boolean, onFavorite: () -> Unit, onBack: () -> Unit,
    blockedReason: (NativeSlashRequest) -> String?, canToggleSkills: Boolean, onSkillToggle: ((String, Boolean) -> Unit)?,
    hasNativeHandler: Boolean, operationBusy: Boolean, operationError: String?, onSelect: (String) -> Unit) {
    var argument by rememberSaveable(item.id) { mutableStateOf("") }
    val command = item.command
    val skill = item.skill
    val text = item.id + if (argument.isBlank()) "" else " ${argument.trim()}"
    val resolution = resolveNativeSlash(text)
    val blocked = (resolution as? NativeSlashResult.Execute)?.request?.let(blockedReason)
    val available = command?.isSelectable(hasNativeHandler) ?: (skill?.enabled == true)
    val inputNeeded = skill != null || command?.argsHint?.isNotBlank() == true || command?.custom == true
    val required = skill != null || command?.argsHint?.startsWith('<') == true
    val invalid = (resolution as? NativeSlashResult.Invalid)?.message
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatIconButton("返回命令列表", Icons.AutoMirrored.Filled.ArrowBack, onBack)
            Text(item.section.title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChatIconButton(if (favorite) "取消收藏" else "收藏命令", if (favorite) Icons.Default.Star else Icons.Default.StarBorder, onFavorite)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
            item {
                Icon(sectionIcon(item.section), null, Modifier.size(30.dp), tint = sectionTint(item.section))
                Text(item.title, Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge)
                Text(item.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Text(item.description, Modifier.padding(top = 16.dp, bottom = 24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                command?.let {
                    val note = if (!available) it.unavailableReason else commandOperationNote(it.name)
                    if (note != null) Text(note, Modifier.padding(bottom = 20.dp), style = MaterialTheme.typography.bodySmall,
                        color = if (!available) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (skill != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (skill.enabled) "已启用" else "已停用", style = MaterialTheme.typography.titleSmall)
                            Text(skill.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(skill.enabled, onCheckedChange = { onSkillToggle?.invoke(skill.name, it) }, enabled = canToggleSkills && onSkillToggle != null,
                            modifier = Modifier.semantics { contentDescription = "启用技能 ${skill.name}" })
                    }
                    if (operationBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
                    if (operationError != null) Text(operationError, Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (inputNeeded && available) OutlinedTextField(argument, { argument = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                    label = { Text(if (skill != null) "交给技能的任务" else "命令参数") },
                    placeholder = { Text(command?.argsHint?.ifBlank { "可选参数" } ?: "描述要完成的任务", style = MaterialTheme.typography.bodySmall) },
                    shape = RoundedCornerShape(8.dp), textStyle = MaterialTheme.typography.bodyMedium)
                if (invalid != null && argument.isNotBlank()) Text(invalid, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (blocked != null) Text("$blocked；可以先放入对话框，条件满足后再发送。", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Button(onClick = { onSelect("$text ") }, enabled = available && invalid == null && (!required || argument.isNotBlank()), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.SubdirectoryArrowLeft, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(if (available) "放入对话框" else if (skill != null) "请先启用技能" else "尚未接入")
            }
        }
    }
}

private class LocalCommandPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("command-library", Context.MODE_PRIVATE)
    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
    fun recents(): List<String> = runCatching { Json.decodeFromString<List<String>>(prefs.getString("recents", "[]") ?: "[]") }.getOrDefault(emptyList()).distinct().take(8)
    fun saveFavorites(values: Set<String>) { prefs.edit().putStringSet("favorites", values.toSet()).apply() }
    fun saveRecents(values: List<String>) { prefs.edit().putString("recents", Json.encodeToString(values)).apply() }
}

@Composable private fun LibraryHeading(title: String) {
    Text(title, Modifier.padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 6.dp).semantics { heading() },
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun LibraryEmpty(title: String, description: String) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun sectionIcon(section: CommandSection): ImageVector = when (section) {
    CommandSection.COMMON -> Icons.Default.StarBorder
    CommandSection.SESSION -> Icons.Default.Forum
    CommandSection.MODEL -> Icons.Default.AutoAwesome
    CommandSection.COLLABORATION -> Icons.Default.AccountTree
    CommandSection.REVIEW -> Icons.AutoMirrored.Filled.FactCheck
    CommandSection.SKILLS -> Icons.Default.Extension
    CommandSection.PROJECT -> Icons.Default.FolderOpen
    CommandSection.SYSTEM -> Icons.Default.Tune
    CommandSection.REFERENCE -> Icons.AutoMirrored.Filled.MenuBook
}

@Composable private fun sectionTint(section: CommandSection): Color = when (section) {
    CommandSection.COLLABORATION, CommandSection.SKILLS -> MaterialTheme.colorScheme.secondary
    CommandSection.COMMON, CommandSection.REVIEW -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

private fun commandOperationNote(name: String): String? = when (name) {
    "/compact" -> "整理可安全回收的上下文。若没有回收空间，天枢会如实返回未变化。"
    "/fork" -> "从最近的用户消息创建本地分支，保留源会话；需等待当前任务结束。"
    "/handoff" -> "让天枢生成 .rivet/HANDOFF.md。受理请求后仍需等待本轮完成；新会话默认不会自动注入。"
    "/queue" -> "不填参数查看待发消息；填写正文后排入下一轮。停止当前轮次不会自动撤回队列。"
    "/steer" -> "仅在任务运行中可用。天枢将在下一个工具边界读取补充内容，已接收不等于已应用。"
    "/team-resume" -> "不填参数浏览真实检查点；填写 groupId 只恢复尚未完成的任务。"
    else -> null
}

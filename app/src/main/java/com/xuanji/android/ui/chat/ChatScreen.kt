package com.xuanji.android.ui.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuanji.android.*
import com.xuanji.android.ui.theme.*
import com.xuanji.android.ui.workspace.WorkspaceSheet
import com.xuanji.android.ui.workspace.WorkspaceTab
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: XuanjiViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val cockpit by vm.cockpit.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val stopping by vm.stopping.collectAsStateWithLifecycle()
    val effortBusy by vm.effortBusy.collectAsStateWithLifecycle()
    val activeEffort by vm.activeEffort.collectAsStateWithLifecycle()
    val thinkingDisplay by vm.thinkingDisplay.collectAsStateWithLifecycle()
    val welcomeScene by vm.welcomeScene.collectAsStateWithLifecycle()
    val activeModel by vm.activeModel.collectAsStateWithLifecycle()
    val controlBusy by vm.controlBusy.collectAsStateWithLifecycle()
    val activeApprovalMode by vm.activeApprovalMode.collectAsStateWithLifecycle()
    val modelChoices by vm.sessionModels.collectAsStateWithLifecycle()
    val modelsLoading by vm.modelsLoading.collectAsStateWithLifecycle()
    val modelsError by vm.modelsError.collectAsStateWithLifecycle()
    val attachments by vm.draftAttachments.collectAsStateWithLifecycle()
    val attachmentsLoading by vm.attachmentsLoading.collectAsStateWithLifecycle()
    val activeSessionId by vm.activeSessionId.collectAsStateWithLifecycle()
    val catalog by vm.providerCatalog.collectAsStateWithLifecycle()
    val history by vm.sessionHistory.collectAsStateWithLifecycle()
    val historyLoading by vm.historyLoading.collectAsStateWithLifecycle()
    val historyError by vm.historyError.collectAsStateWithLifecycle()
    val state by vm.serviceState.collectAsStateWithLifecycle()
    val progress by vm.installProgress.collectAsStateWithLifecycle()
    val approval by vm.approval.collectAsStateWithLifecycle()
    val approvalBusy by vm.approvalAnswerBusy.collectAsStateWithLifecycle()
    val androidApproval by TianshuService.androidActionApproval.pending.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val commandStatus by vm.commandStatus.collectAsStateWithLifecycle()
    val commandChecks by vm.commandChecks.collectAsStateWithLifecycle()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val skillsError by vm.skillsError.collectAsStateWithLifecycle()
    val skillsLoading by vm.skillsLoading.collectAsStateWithLifecycle()
    val commandsError by vm.commandsError.collectAsStateWithLifecycle()
    val sessionCommandOutput by vm.sessionCommandOutput.collectAsStateWithLifecycle()
    val webSearchSettings by vm.webSearchSettings.collectAsStateWithLifecycle()
    val searchKeys by vm.searchKeyConfigured.collectAsStateWithLifecycle()
    val searchProbe by vm.searchProbe.collectAsStateWithLifecycle()
    val searchProbeBusy by vm.searchProbeBusy.collectAsStateWithLifecycle()
    val searchError by vm.searchError.collectAsStateWithLifecycle()
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val customCommands by vm.customCommands.collectAsStateWithLifecycle()
    val accessibilityEnabled by vm.accessibilityEnabled.collectAsStateWithLifecycle()
    val permissionState by vm.systemPermissionState.collectAsStateWithLifecycle()
    val sessionReady by vm.sessionReady.collectAsStateWithLifecycle()
    val connectionStatus by vm.connectionStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val inputFocus = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var showPermissions by rememberSaveable { mutableStateOf(false) }
    var showWorkspace by rememberSaveable { mutableStateOf(false) }
    var workspaceTab by rememberSaveable { mutableStateOf(WorkspaceTab.COCKPIT) }
    var showCommands by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var modelSection by rememberSaveable { mutableStateOf(ModelPanelSection.MODELS) }
    var commandInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var composerFocused by remember { mutableStateOf(false) }
    var confirmFullAccess by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), vm::addAttachments)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), vm::addAttachments)
    var input by rememberSaveable { mutableStateOf("") }
    val drafts = rememberSaveable(saver = mapSaver(
        save = { it.toMap() }, restore = { saved -> saved.mapValues { it.value as String }.toMutableMap() },
    )) { mutableMapOf<String, String>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var followTail by remember { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }
    val availability = chatSendAvailability(state, sessionReady, running, if (attachments.isEmpty()) input else input.ifBlank { "附件" }, items.hasUnconfirmedPrompt())
    val nativeResolution = remember(input) { resolveNativeSlash(input) }
    val rows = remember(items) { chatPresentation(items) }
    val fillInput: (String) -> Unit = {
        input = it
        inputFocus.requestFocus()
        keyboard?.show()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshAccessibility()
                vm.refreshSystemPermissions()
                vm.refreshShizuku()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) followTail = true }
    LaunchedEffect(Unit) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            val atBottom = last != null && last.index == layout.totalItemsCount - 1 &&
                last.offset + last.size <= layout.viewportEndOffset + 24
            listState.isScrollInProgress to atBottom
        }.collect { (scrolling, atBottom) -> if (scrolling && !autoScrolling) followTail = atBottom }
    }
    // Observe measured height too: asynchronous Markdown layout can finish after a text delta.
    LaunchedEffect(followTail, rows.size) {
        if (!followTail || rows.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            Triple(last?.index, last?.let { it.offset + it.size }, layout.viewportEndOffset)
        }.collect { (index, end, viewportEnd) ->
            autoScrolling = true
            try {
                if (index == null || index < rows.lastIndex) listState.scrollToItem(rows.lastIndex)
                else if (end != null && end > viewportEnd) listState.scrollBy((end - viewportEnd).toFloat())
            } finally { autoScrolling = false }
        }
    }
    LaunchedEffect(showCommands) { if (showCommands) vm.refreshSkills() }
    LaunchedEffect(androidApproval?.id, approval?.requestId) {
        if (androidApproval != null || approval != null) {
            showModels = false
            showCommands = false
            showWorkspace = false
            showPermissions = false
            confirmFullAccess = false
            keyboard?.hide()
        }
    }
    LaunchedEffect(drawer.isOpen) { if (drawer.isOpen) vm.refreshSessionHistory() }
    val canSwitch = !running && !stopping && !effortBusy && !controlBusy && !attachmentsLoading && !items.hasUnconfirmedPrompt()
    val startNew = {
        if (canSwitch) {
            if (resolveNativeSlash(input) == null) activeSessionId?.let { drafts[it] = input }
            vm.newSession()
            input = ""
        }
    }
    val modelProfile = activeProviderModel(catalog, activeModel)
    val levels = reasoningLevels(modelProfile?.first?.name.orEmpty(), modelProfile?.second?.effortSupported == true)
    val modelLabel = modelDisplayName(modelChoices.firstOrNull { it.current }?.alias ?: modelProfile?.second?.id ?: activeModel.substringAfterLast(':'))
    val effortPreview = remember(activeSessionId, activeModel) { mutableFloatStateOf(Float.NaN) }
    val effortInteracting = remember(activeSessionId, activeModel) { mutableStateOf(false) }
    val sendLightTransfer = remember(activeSessionId) { mutableFloatStateOf(0f) }
    val chatVisible = !showSearch && !showWorkspace && !showCommands && !showPermissions && sessionCommandOutput == null && androidApproval == null && approval == null && !confirmFullAccess
    LaunchedEffect(sessionCommandOutput) {
        if (sessionCommandOutput != null) { keyboard?.hide(); focusManager.clearFocus() }
    }
    val openModels: (ModelPanelSection) -> Unit = { section ->
        keyboard?.hide()
        modelSection = section
        vm.refreshSessionModels()
        showModels = true
    }
    val openWorkspace: (WorkspaceTab) -> Unit = { tab -> keyboard?.hide(); workspaceTab = tab; showWorkspace = true }
    val dispatchNative: (NativeSlashRequest) -> Boolean = { request ->
        val argument = request.argument
        val blocked = nativeSlashBlocked(request, canSwitch, sessionReady, effortBusy, controlBusy, stopping, running)
        if (blocked) {
            vm.error.value = "当前操作尚未结束，请稍后重试"
            false
        } else {
            var accepted = true
            when (request.action) {
                PaletteAction.HELP, PaletteAction.SKILLS -> {
                    commandInitialTab = if (request.action == PaletteAction.HELP) 2 else 1
                    showCommands = true
                }
                PaletteAction.NEW_SESSION -> startNew()
                PaletteAction.HISTORY -> {
                    vm.refreshSessionHistory()
                    if (argument.isBlank()) { keyboard?.hide(); scope.launch { drawer.open() } }
                    else {
                        val target = history.firstOrNull { it.id == argument }
                            ?: argument.toIntOrNull()?.let { history.getOrNull(it - 1) }
                        if (target == null) { vm.error.value = "未找到会话，请在历史记录中选择"; accepted = false }
                        else if (vm.openSession(target.id)) {
                            if (resolveNativeSlash(input) == null) activeSessionId?.let { drafts[it] = input }
                            input = drafts[target.id].orEmpty()
                            followTail = true
                        } else accepted = false
                    }
                }
                PaletteAction.MODELS -> {
                    if (argument.isBlank() || argument.equals("list", true)) openModels(ModelPanelSection.MODELS)
                    else {
                        val matches = modelChoices.filter { it.spec == argument || it.id == argument || it.alias == argument }
                        val choice = matches.singleOrNull() ?: matches.firstOrNull { it.spec == argument }
                        if (choice == null) { openModels(ModelPanelSection.MODELS); vm.error.value = "模型不存在或名称不唯一，请在面板中选择"; accepted = false }
                        else vm.setSessionModel(choice)
                    }
                }
                PaletteAction.EFFORT -> {
                    if (argument.isBlank()) openModels(ModelPanelSection.THINKING)
                    else if (argument.lowercase() == "auto" || argument.lowercase() in levels) vm.setReasoningEffort(argument.lowercase())
                    else { vm.error.value = "当前模型不支持此思考强度"; accepted = false }
                }
                PaletteAction.THINKING -> {
                    if (argument.isBlank()) openModels(ModelPanelSection.THINKING)
                    else ThinkingDisplay.entries.firstOrNull { it.wire == argument.lowercase() }?.let(vm::setThinkingDisplay)
                }
                PaletteAction.PERMISSIONS -> {
                    if (argument.isBlank() || request.command !in setOf("/permission", "/permissions")) {
                        vm.refreshShizuku(); vm.refreshSystemPermissions(); showPermissions = true
                    }
                    else ChatApprovalMode.entries.firstOrNull { it.wire == argument.lowercase() }?.let { mode ->
                        if (mode == ChatApprovalMode.FULL) confirmFullAccess = true else vm.setChatApprovalMode(mode)
                    }
                }
                PaletteAction.COCKPIT -> openWorkspace(WorkspaceTab.COCKPIT)
                PaletteAction.FILES -> openWorkspace(WorkspaceTab.FILES)
                PaletteAction.TERMINAL -> openWorkspace(WorkspaceTab.TERMINAL)
                PaletteAction.SETTINGS -> openWorkspace(WorkspaceTab.SETTINGS)
                PaletteAction.STOP -> if (running) vm.stop() else { vm.error.value = "当前没有正在执行的任务"; accepted = false }
                PaletteAction.RECONNECT -> vm.retryConnection()
                PaletteAction.DOMAIN, PaletteAction.PLAN_MODE, PaletteAction.ASK_MODE, PaletteAction.REVIEW_GATE, PaletteAction.FAST,
                PaletteAction.PLAN_LIST, PaletteAction.PLAN_VIEW, PaletteAction.PLAN_EDIT, PaletteAction.PLAN_APPROVE, PaletteAction.PLAN_REJECT,
                PaletteAction.GOAL, PaletteAction.GOAL_STATUS, PaletteAction.GOAL_PAUSE, PaletteAction.GOAL_RESUME,
                PaletteAction.GOAL_CANCEL, PaletteAction.GOAL_CRITERIA, PaletteAction.TODO,
                PaletteAction.COMPACT, PaletteAction.FORK, PaletteAction.HANDOFF, PaletteAction.TEAM_RESUME,
                PaletteAction.QUEUE, PaletteAction.STEER, PaletteAction.SKILL_MANAGE ->
                    accepted = vm.executeSessionCommand(request)
            }
            accepted
        }
    }
    val submit = {
        val submitted = input
        when (val native = nativeResolution) {
            is NativeSlashResult.Invalid -> vm.error.value = native.message
            is NativeSlashResult.Execute -> if (attachments.isNotEmpty()) vm.error.value = "本地命令不接收附件，请先移除附件或发送普通消息"
                else if (dispatchNative(native.request) && input == submitted) input = ""
            null -> if (availability.enabled && vm.send(input)) {
                input = ""
                followTail = true
                keyboard?.hide()
                focusManager.clearFocus()
            }
        }
    }

    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = true, drawerContent = {
        HistoryDrawer(history, activeSessionId, historyLoading, historyError, canSwitch,
            onSelect = { id ->
                val previousId = activeSessionId
                if (vm.openSession(id)) {
                    keyboard?.hide()
                    if (previousId != id) {
                        previousId?.let { drafts[it] = input }
                        input = drafts[id].orEmpty()
                        followTail = true
                    }
                    scope.launch { drawer.close() }
                }
            },
            onNew = { keyboard?.hide(); startNew(); scope.launch { drawer.close() } },
            onRefresh = vm::refreshSessionHistory, onClose = { scope.launch { drawer.close() } })
    }) {
    Box(Modifier.fillMaxSize()) {
    ChatEffortBackdrop(activeEffort, effortPreview, effortInteracting, sendLightTransfer, chatVisible)
    ChatFocusStars(composerFocused && chatVisible, sendLightTransfer)
    key(activeSessionId) {
        val latestUserIndex = items.indexOfLast { it is ChatItem.User }
        val latestUser = items.getOrNull(latestUserIndex) as? ChatItem.User
        ChatSendGlow(latestUser?.requestId ?: latestUserIndex.toString(),
            sessionReady && state == TianshuService.State.RUNNING && awaitingAssistantReply(items, running, stopping),
            activeEffort, sendLightTransfer, chatVisible)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            Box {
            ChatHeader(state, progress, sessionReady,
                onWorkspace = { workspaceTab = WorkspaceTab.COCKPIT; showWorkspace = true },
                onPermissions = { vm.refreshShizuku(); vm.refreshSystemPermissions(); showPermissions = true },
                onNewSession = startNew,
                newSessionEnabled = canSwitch,
                onSearch = { keyboard?.hide(); showSearch = true },
                onBrowser = { keyboard?.hide(); com.xuanji.android.browser.BrowserActivity.show(context) },
                onHistory = { keyboard?.hide(); scope.launch { drawer.open() } },
                modelLabel = modelLabel, onModel = { openModels(ModelPanelSection.MODELS) }, effort = activeEffort,
                approvalPending = androidApproval != null || approval != null,
            )
            ChatModelPanel(modelChoices, modelsLoading, modelsError, canSwitch && sessionReady, controlBusy,
                activeEffort, levels, effortBusy || controlBusy || stopping || !sessionReady, thinkingDisplay, modelSection, { modelSection = it },
                vm::refreshSessionModels, { showModels = false }, vm::setSessionModel,
                vm::setReasoningEffort, vm::setThinkingDisplay,
                onPreview = { value, active -> effortPreview.floatValue = value ?: Float.NaN; effortInteracting.value = active }, expanded = showModels)
            }
        },
        bottomBar = {
            ChatComposer(
                input = input, onInput = { input = it },
                running = running, sendEnabled = !stopping && !attachmentsLoading &&
                    (nativeResolution != null || availability.enabled && !effortBusy && !controlBusy),
                nativeCommand = nativeResolution != null,
                onSend = submit, onStop = vm::stop, onCommands = { keyboard?.hide(); commandInitialTab = 0; showCommands = true },
                focusRequester = inputFocus,
                onFiles = { keyboard?.hide(); filePicker.launch(arrayOf("*/*")) },
                onImages = { keyboard?.hide(); imagePicker.launch(arrayOf("image/png", "image/jpeg", "image/webp", "image/gif")) },
                stopping = stopping,
                approvalMode = activeApprovalMode, controlBusy = controlBusy || effortBusy || !sessionReady,
                onApprovalMode = { mode -> if (mode == ChatApprovalMode.FULL) confirmFullAccess = true else vm.setChatApprovalMode(mode) },
                attachments = attachments, attachmentsLoading = attachmentsLoading, onRemoveAttachment = vm::removeAttachment,
                onFocus = { composerFocused = it },
                approvalPending = androidApproval != null || approval != null,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                connectionStatus?.let { message ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChatIconButton("重新连接", Icons.Default.Refresh, vm::retryConnection, enabled = state == TianshuService.State.RUNNING)
                    }
                }
                if (cockpit.goal.isNotBlank() || cockpit.todos.isNotEmpty() || cockpit.deliveryGate != null || cockpit.deliveryFailed) {
                    TaskSummaryBar(cockpit, onOpen = { workspaceTab = WorkspaceTab.COCKPIT; showWorkspace = true })
                }
                if (items.isEmpty()) {
                    if (!sessionReady) ChatRestoringState(Modifier.weight(1f)) else ChatEmptyState(Modifier.weight(1f), chatVisible, welcomeScene)
                } else Box(Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.any { it.pressed }) followTail = false
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(rows, key = { it.key }) { row -> key(activeSessionId) {
                            when (row) {
                                is ChatRow.Message -> ChatMessage(row.item, onRetry = vm::retryPrompt, onEdit = fillInput,
                                    thinkingDisplay = thinkingDisplay, onDisclosureChanged = { followTail = false })
                                is ChatRow.Execution -> ExecutionBlock(row.events, thinkingDisplay, onDisclosureChanged = { followTail = false })
                            }
                        }
                        }
                    }
                    if (!followTail) SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.scrollToItem(rows.lastIndex)
                                followTail = true
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) { Icon(Icons.Default.ArrowDownward, "回到最新消息") }
                }
            }
        }
    }
    }
    }

    if (showCommands) CommandPaletteSheet(skills, skillsError, vm::refreshSkills, { showCommands = false },
        initialTab = commandInitialTab, customCommands = customCommands, skillsLoading = skillsLoading, commandsError = commandsError,
        operationBusy = controlBusy, operationError = error,
        blockedReason = { request ->
            when {
                !sessionReady && request.action !in setOf(PaletteAction.HELP, PaletteAction.HISTORY, PaletteAction.SETTINGS, PaletteAction.PERMISSIONS) -> "等待天枢连接"
                request.action == PaletteAction.STOP && !running -> "当前没有运行中的任务"
                nativeSlashBlocked(request, canSwitch, sessionReady, effortBusy, controlBusy, stopping, running) -> "当前会话状态暂不支持执行"
                else -> null
            }
        }, canToggleSkills = sessionReady && canSwitch, onSkillToggle = { name, enabled ->
            vm.setSkillEnabled(name, enabled)
        }, onAction = { request ->
            showCommands = false
            dispatchNative(request)
        }) {
        showCommands = false
        fillInput(it)
    }
    sessionCommandOutput?.let { output ->
        SessionCommandSheet(output, canSwitch, onDomain = { key ->
            vm.executeSessionCommand(NativeSlashRequest(PaletteAction.DOMAIN, "/domain", key))
        }, busy = controlBusy, commandError = error, onCommand = { vm.executeSessionCommand(it) }, onSavePlan = vm::saveSessionPlan,
            running = running, promptPending = items.hasUnconfirmedPrompt(),
            onDismiss = vm::dismissSessionCommand)
    }
    if (showWorkspace) WorkspaceSheet(vm, initialTab = workspaceTab) { showWorkspace = false }
    if (showSearch) WebSearchSheet(webSearchSettings, searchKeys, searchProbeBusy, searchProbe, searchError,
        vm::updateWebSearch, vm::saveSearchKey, vm::testWebSearch, vm::cancelSearchProbe, { showSearch = false },
        onOpen = { url -> runCatching { com.xuanji.android.browser.BrowserActivity.open(context, url) }.onFailure { vm.error.value = it.message } })
    androidApproval?.let { request ->
        AlertDialog(
            onDismissRequest = { TianshuService.androidActionApproval.respond(request.id, false) },
            icon = { Icon(Icons.Default.Security, null) },
            title = { Text("允许天枢${request.title}？") },
            text = { ApprovalDetails(request.detail, request.targetPackage) },
            confirmButton = { TextButton(onClick = {
                if (TianshuService.androidActionApproval.respond(request.id, true) && request.targetPackage != null && request.targetPackage != context.packageName) {
                    context.findActivity()?.moveTaskToBack(true)
                }
            }) { Text("仅本次允许") } },
            dismissButton = { TextButton({ TianshuService.androidActionApproval.respond(request.id, false) }) { Text("拒绝") } },
        )
    } ?: approval?.let { request ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.Security, null) },
            title = { Text("执行审批") },
            text = { ApprovalDetails(request.input, request.toolName) },
            confirmButton = { TextButton({ vm.answer(true) }, enabled = !approvalBusy) { Text(if (approvalBusy) "正在提交…" else "允许") } },
            dismissButton = { TextButton({ vm.answer(false) }, enabled = !approvalBusy) { Text("拒绝") } },
        )
    }
    if (confirmFullAccess) AlertDialog(onDismissRequest = { confirmFullAccess = false },
        icon = { Icon(Icons.Default.GppMaybe, null, tint = MaterialTheme.colorScheme.tertiary) }, title = { Text("启用完全访问权限？") },
        text = { Text("天枢将无需执行审批即可运行工具，包括修改文件与执行命令。Android 系统授权及屏幕操作确认仍然有效。") },
        confirmButton = { TextButton({ confirmFullAccess = false; vm.setChatApprovalMode(ChatApprovalMode.FULL) }) { Text("启用") } },
        dismissButton = { TextButton({ confirmFullAccess = false }) { Text("取消") } })
    if (showPermissions) SystemPermissionsSheet(vm, commandStatus, commandChecks.forBackend(ShellBackend.ROOT), commandChecks.forBackend(ShellBackend.SHIZUKU), accessibilityEnabled, permissionState) { showPermissions = false }
    error?.let { message ->
        AlertDialog(onDismissRequest = vm::dismissError, title = { Text("操作未完成") }, text = { Text(message) }, confirmButton = { TextButton(vm::dismissError) { Text("知道了") } })
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun ApprovalDetails(detail: String, label: String?) {
    val chunks = remember(detail) { approvalTextChunks(detail) }
    val fields = remember(detail) {
        val data = runCatching { Json.parseToJsonElement(detail) as? JsonObject }.getOrNull()
        val names = mapOf("command" to "命令", "path" to "路径", "file_path" to "路径", "action" to "操作",
            "url" to "地址", "package" to "应用", "targetPackage" to "目标应用", "cwd" to "工作目录")
        data?.mapNotNull { (key, value) -> names[key]?.let { name ->
            (value as? JsonPrimitive)?.contentOrNull?.let { name to it }
        } }.orEmpty()
    }
    var fullParameters by remember(detail) { mutableStateOf(false) }
    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { item { Text(toolTitle(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) } }
        items(fields) { (name, value) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
        }
        if (fields.isNotEmpty()) item {
            TextButton(onClick = { fullParameters = !fullParameters }) {
                Icon(if (fullParameters) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("完整参数")
            }
        }
        if (fields.isEmpty() || fullParameters) items(chunks) { chunk ->
            SelectionContainer { Text(chunk, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
        }
    }
}

internal fun approvalTextChunks(text: String): List<String> = buildList {
    var start = 0
    while (start < text.length) {
        var end = (start + 4000).coerceAtMost(text.length)
        if (end < text.length && text[end - 1].isHighSurrogate()) end--
        add(text.substring(start, end))
        start = end
    }
}

@Composable
internal fun RuntimeTitle(state: TianshuService.State, progress: Int, ready: Boolean, labelOverride: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("璇玑", style = MaterialTheme.typography.titleMedium)
        RuntimeStatus(state, progress, ready, labelOverride)
    }
}

@Composable
internal fun RuntimeStatus(state: TianshuService.State, progress: Int, ready: Boolean, labelOverride: String? = null,
    modifier: Modifier = Modifier) {
    val (color, label) = when (state) {
        TianshuService.State.RUNNING -> if (ready) Success to "天枢运行中" else Warning to "会话准备中"
        TianshuService.State.INSTALLING -> Warning to "安装环境 · $progress 项"
        TianshuService.State.STARTING -> Warning to "正在启动"
        TianshuService.State.CRASHED -> Danger to "异常重启中"
        TianshuService.State.STOPPED -> Danger to "天枢未启动"
    }
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (labelOverride == null) color else MaterialTheme.colorScheme.onSurfaceVariant))
            Text(labelOverride ?: label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
}

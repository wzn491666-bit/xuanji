package com.xuanji.android

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import com.xuanji.android.ui.chat.SkillEntry
import com.xuanji.android.ui.chat.MobileCommand
import com.xuanji.android.ui.chat.discoverProjectCommands
import com.xuanji.android.ui.chat.nextStellarWelcome
import com.xuanji.android.ui.chat.storedStellarWelcome
import com.xuanji.android.ui.chat.NativeSlashRequest
import com.xuanji.android.ui.chat.PaletteAction
import com.xuanji.android.ui.chat.nativeSlashBlocked
import java.util.UUID

/** 对话流里的一条内容 */
enum class PromptDelivery { SENDING, ACCEPTED, UNCERTAIN, REJECTED, DELIVERED }

sealed interface ChatItem {
    data class User(
        val text: String,
        val requestId: String? = null,
        val delivery: PromptDelivery = PromptDelivery.DELIVERED,
        val deliveryError: String? = null,
        val attachments: List<PromptAttachment> = emptyList(),
        val wireText: String? = null,
    ) : ChatItem
    data class Assistant(val text: String) : ChatItem
    data class Thinking(val text: String, val streaming: Boolean) : ChatItem
    data class Tool(
        val id: String,
        val name: String,
        val input: String,
        val result: String? = null,
        val isError: Boolean = false,
        val streaming: Boolean = false,
    ) : ChatItem { val running get() = result == null || streaming }
}

internal fun updatePromptDelivery(
    items: List<ChatItem>, requestId: String, delivery: PromptDelivery, error: String? = null,
): List<ChatItem> = items.map { item ->
    if (item is ChatItem.User && item.requestId == requestId && item.delivery != PromptDelivery.DELIVERED) {
        item.copy(delivery = delivery, deliveryError = error)
    } else item
}

internal fun preparePromptRetry(item: ChatItem.User, freshRequestId: String): ChatItem.User {
    require(item.requestId != null && item.delivery in setOf(PromptDelivery.UNCERTAIN, PromptDelivery.REJECTED))
    val retryId = if (item.delivery == PromptDelivery.REJECTED) {
        require(freshRequestId.isNotBlank() && freshRequestId != item.requestId)
        freshRequestId
    } else item.requestId
    return item.copy(requestId = retryId, delivery = PromptDelivery.SENDING, deliveryError = null)
}

internal fun List<ChatItem>.hasUnconfirmedPrompt() = any { item ->
    item is ChatItem.User && item.delivery in setOf(PromptDelivery.SENDING, PromptDelivery.UNCERTAIN)
}

data class Todo(val content: String, val status: String, val id: String = "", val activeForm: String? = null)
data class Approval(val requestId: String, val toolName: String, val input: String)
data class CockpitEvidence(val label: String, val value: String, val source: String)
data class CockpitTimelineEntry(val type: String, val summary: String)

data class TerminalRecord(
    val command: String,
    val backend: ShellBackend,
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean,
)

data class JobLogView(val jobId: String, val text: String = "", val loading: Boolean = false)

/** Cockpit 卡片显示的内容，全部来自天枢事件流 */
data class Cockpit(
    val goal: String = "",
    val todos: List<Todo> = emptyList(),
    val deliveryGate: String? = null, // GREEN / YELLOW / RED
    val deliveryFailed: Boolean = false,
    val evidence: List<CockpitEvidence> = emptyList(),
    val timeline: List<CockpitTimelineEntry> = emptyList(),
    val verificationCommands: List<VerificationCommand> = emptyList(),
    val convergence: ConvergenceState = ConvergenceState(),
    val contextUsagePercent: Int? = null,
    val hooks: List<HookStatus> = emptyList(),
    val replay: List<ReplayEntry> = emptyList(),
    val failures: List<FailureInfo> = emptyList(),
    val usage: TokenUsage = TokenUsage(),
    val insights: SessionInsights? = null,
    val waves: List<AgentWave> = emptyList(),
)

data class Settings(
    val provider: String,
    val apiKey: String,
    val model: String,
    val baseUrl: String = "",
    val reasoningEffort: String = "auto",
    val useStoredCredential: Boolean = false,
) {
    val isComplete get() = provider.isNotBlank() && (apiKey.isNotBlank() || useStoredCredential)

    fun save(ctx: Context) {
        SecureSettings.saveApiKey(ctx, apiKey) {
            putString("provider", provider)
            putString("model", model)
            putString("baseUrl", baseUrl)
            putString("reasoningEffort", reasoningEffort)
            putBoolean("useStoredCredential", useStoredCredential)
        }
    }

    companion object {
        fun load(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).let {
            Settings(
                provider = it.getString("provider", "") ?: "",
                apiKey = SecureSettings.loadApiKey(ctx),
                model = it.getString("model", "") ?: "",
                baseUrl = it.getString("baseUrl", "") ?: "",
                reasoningEffort = it.getString("reasoningEffort", "auto") ?: "auto",
                useStoredCredential = it.getBoolean("useStoredCredential", false),
            )
        }
    }
}

internal fun persistSettingsSafely(settings: Settings, persist: (Settings) -> Unit): Exception? =
    try {
        persist(settings)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

class XuanjiViewModel(app: Application) : AndroidViewModel(app) {

    private val client = TianshuClient()
    private val commandExecutor = CommandExecutor(app)
    private val workspaceRepository = WorkspaceRepository()
    private val promptLedger = UserPromptLedger()
    private val sessionPreferences = app.getSharedPreferences("active_session", Context.MODE_PRIVATE)
    private val readyMutex = Mutex()
    val commandStatus = commandExecutor.status
    val commandChecks = MutableStateFlow(CommandCheckOutput())

    val serviceState = TianshuService.state
    val installProgress = TianshuService.installProgress
    val androidMcpStatus = TianshuService.androidMcpStatus
    val androidMcpError = MutableStateFlow<String?>(null)

    val settings = MutableStateFlow(Settings.load(app))
    private val uiPreferences = app.getSharedPreferences("chat_ui", Context.MODE_PRIVATE)
    internal val welcomeScene = MutableStateFlow(nextStellarWelcome(uiPreferences.getString("welcomeScene", null)))
    val thinkingDisplay = MutableStateFlow(ThinkingDisplay.fromStored(uiPreferences.getString("thinkingDisplay", null)))
    val savingSettings = MutableStateFlow(false)
    val items = MutableStateFlow(listOf<ChatItem>())
    val cockpit = MutableStateFlow(Cockpit())
    val approval = MutableStateFlow<Approval?>(null)
    val approvalAnswerBusy = MutableStateFlow(false)
    val running = MutableStateFlow(false)
    val stopping = MutableStateFlow(false)
    val effortBusy = MutableStateFlow(false)
    val controlBusy = MutableStateFlow(false)
    val activeApprovalMode = MutableStateFlow<String?>(null)
    val sessionModels = MutableStateFlow<List<SessionModelChoice>>(emptyList())
    val modelsLoading = MutableStateFlow(false)
    val modelsError = MutableStateFlow<String?>(null)
    val draftAttachments = MutableStateFlow<List<PromptAttachment>>(emptyList())
    val attachmentsLoading = MutableStateFlow(false)
    private val attachmentDrafts = mutableMapOf<String, List<PromptAttachment>>()
    val activeModel = MutableStateFlow(settings.value.model)
    val activeEffort = MutableStateFlow(settings.value.reasoningEffort)
    val activeSessionId = MutableStateFlow(sessionPreferences.getString("id", null))
    val sessionHistory = MutableStateFlow<List<SessionHistoryEntry>>(emptyList())
    val historyLoading = MutableStateFlow(false)
    val historyError = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val skills = MutableStateFlow<List<SkillEntry>>(emptyList())
    val customCommands = MutableStateFlow<List<MobileCommand>>(emptyList())
    val skillsError = MutableStateFlow<String?>(null)
    val skillsLoading = MutableStateFlow(false)
    val commandsError = MutableStateFlow<String?>(null)
    val sessionCommandOutput = MutableStateFlow<SessionCommandOutput?>(null)
    private var skillsRequest = 0
    private val searchRepository = com.xuanji.android.search.SearchSettingsRepository(app)
    private val searchClient = com.xuanji.android.search.WebSearchClient()
    val webSearchSettings = MutableStateFlow(searchRepository.load())
    val searchKeyConfigured = MutableStateFlow(com.xuanji.android.search.SearchEngine.entries.associateWith(searchRepository::hasKey))
    val searchProbe = MutableStateFlow<com.xuanji.android.search.WebSearchReport?>(null)
    val searchProbeBusy = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)

    fun updateWebSearch(settings: com.xuanji.android.search.SearchSettings): Boolean = runCatching {
        check(!searchProbeBusy.value) { "请先停止搜索测试再修改配置" }
        searchRepository.save(settings)
        webSearchSettings.value = settings
        searchError.value = null
    }.onFailure { searchError.value = it.message }.isSuccess

    fun saveSearchKey(engine: com.xuanji.android.search.SearchEngine, key: String): Boolean = runCatching {
        check(!searchProbeBusy.value) { "请先停止搜索测试再修改密钥" }
        searchRepository.saveKey(engine, key)
        searchKeyConfigured.value = com.xuanji.android.search.SearchEngine.entries.associateWith(searchRepository::hasKey)
        searchError.value = null
    }.onFailure { searchError.value = it.message ?: "搜索密钥保存失败" }.isSuccess

    private var searchProbeJob: Job? = null
    fun testWebSearch(query: String) {
        if (searchProbeBusy.value) return
        searchProbeBusy.value = true
        searchError.value = null
        searchProbe.value = null
        val config = webSearchSettings.value
        searchProbeJob = viewModelScope.launch {
            try {
                requestResult {
                    val credentials = withContext(Dispatchers.IO) { searchRepository.credentials(config) }
                    searchClient.search(query, config, credentials)
                }.onSuccess { searchProbe.value = it }
                    .onFailure { searchError.value = it.message ?: "搜索失败" }
            } finally { searchProbeBusy.value = false }
        }
    }
    fun cancelSearchProbe() { searchProbeJob?.cancel() }
    val accessibilityEnabled = MutableStateFlow(XuanjiAccessibilityService.isEnabled(app))
    val systemPermissionState = MutableStateFlow(readRuntimePermissionState(app))
    val sessionReady = MutableStateFlow(false)
    val connectionStatus = MutableStateFlow<String?>(null)
    val workspacePath = MutableStateFlow("/sdcard")
    val sessionWorkdir = MutableStateFlow(sessionPreferences.getString("cwd", "/sdcard") ?: "/sdcard")
    val workspaceFiles = MutableStateFlow<List<WorkspaceFile>>(emptyList())
    val filePreview = MutableStateFlow<FilePreview?>(null)
    val gitWorkingTree = MutableStateFlow<GitWorkingTree?>(null)
    val gitDiff = MutableStateFlow<GitDiff?>(null)
    val gitLoading = MutableStateFlow(false)
    val gitError = MutableStateFlow<String?>(null)
    val workspaceError = MutableStateFlow<String?>(null)
    val terminalRecords = MutableStateFlow<List<TerminalRecord>>(emptyList())
    val terminalBusy = MutableStateFlow(false)
    val providerTest = MutableStateFlow<String?>(null)
    val providerCatalog = MutableStateFlow<List<ProviderProfile>>(emptyList())
    val providerCatalogError = MutableStateFlow<String?>(null)
    val providerActionBusy = MutableStateFlow(false)
    val backgroundJobs = MutableStateFlow(BackgroundJobsState())
    val jobLog = MutableStateFlow<JobLogView?>(null)
    val jobError = MutableStateFlow<String?>(null)
    val killingJobId = MutableStateFlow<String?>(null)

    private var sessionId: String?
        get() = activeSessionId.value
        set(value) { activeSessionId.value = value }
    private var sessionGeneration = 0L
    private var sessionCommandRevision = 0L
    private var todoRefreshRevision = 0L
    private var latestTodoSnapshot: TianshuTodos? = null
    private var latestQueueSnapshot: TianshuQueueSnapshot? = null
    private var queueRefreshRevision = 0L
    private var lastHandoffArchived: SessionEvent? = null
    private var nativeCommandJob: Job? = null
    private var lastSeq = 0L
    private var runningSnapshotSeq = 0L
    private var streamJob: Job? = null
    private var providerSynced = false
    private var configuredAndroidMcpEndpoint: String? = null
    private var connectedServerToken = ""
    private var pendingApprovals = emptyList<Approval>()
    private var approvalAnswerRequestId: String? = null
    private var gitRequest = 0L
    private var workspaceRequest = 0L
    private var previewRequest = 0L
    private var providerTestRequest = 0L
    private var providerCatalogRequest = 0L
    private var modelListRequest = 0L

    internal fun clearPendingApprovals() {
        pendingApprovals = emptyList()
        approvalAnswerRequestId = null
        approvalAnswerBusy.value = false
        approval.value = null
    }

    private fun currentSession(id: String, generation: Long, serverToken: String) =
        sessionId == id && sessionGeneration == generation && TianshuService.token == serverToken

    init {
        uiPreferences.edit().putString("welcomeScene", welcomeScene.value.id).apply()
        TianshuService.addSensitiveValue(settings.value.apiKey)
        if (settings.value.isComplete) TianshuService.start(app)
        // 天枢每次（重新）启动后：同步 API Key、建会话、订阅事件流
        viewModelScope.launch {
            serviceState.filter { it == TianshuService.State.RUNNING }.collect { onTianshuReady() }
        }
    }

    fun saveSettings(s: Settings) {
        if (!s.isComplete || savingSettings.value) return
        savingSettings.value = true
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val persistenceError = withContext(Dispatchers.IO) {
                    persistSettingsSafely(s) { it.save(app) }
                }
                if (persistenceError != null) {
                    Log.e("XuanjiSettings", "配置无法安全保存", persistenceError)
                    error.value = "配置无法安全保存（${persistenceError.javaClass.simpleName}）。请重试；凭据保存成功后才能启动天枢。"
                    return@launch
                }
                val previous = settings.value
                TianshuService.addSensitiveValue(s.apiKey)
                settings.value = s
                providerSynced = false
                if (previous != s) {
                    nativeCommandJob?.cancel()
                    sessionGeneration++
                    effortBusy.value = false
                    stopping.value = false
                    sessionId = null
                    sessionPreferences.edit().remove("id").apply()
                    lastSeq = 0
                    sessionReady.value = false
                    streamJob?.cancel()
                    running.value = false
                    clearPendingApprovals()
                    items.value = emptyList()
                    cockpit.value = Cockpit()
                    backgroundJobs.value = BackgroundJobsState()
                    jobLog.value = null
                    jobError.value = null
                    killingJobId.value = null
                    gitWorkingTree.value = null
                    gitDiff.value = null
                    gitLoading.value = false
                    gitError.value = null
                    providerTest.value = null
                    promptLedger.clear()
                }
                val startError = runCatching { TianshuService.start(app) }.exceptionOrNull()
                if (startError != null) Log.e("XuanjiSettings", "天枢服务启动请求失败", startError)
                error.value = startError?.let { "天枢服务启动失败：${it.message ?: it.javaClass.simpleName}" }
                if (startError == null && serviceState.value == TianshuService.State.RUNNING) {
                    viewModelScope.launch { onTianshuReady() }
                }
            } finally {
                savingSettings.value = false
            }
        }
    }

    private suspend fun onTianshuReady() = readyMutex.withLock {
        val generation = sessionGeneration
        sessionReady.value = false
        connectionStatus.value = "正在恢复会话…"
        try {
            val serverToken = TianshuService.token
            fun current() = generation == sessionGeneration &&
                serviceState.value == TianshuService.State.RUNNING && serverToken == TianshuService.token
            if (serverToken != connectedServerToken) {
                nativeCommandJob?.cancel()
                providerSynced = false
                configuredAndroidMcpEndpoint = null
                connectedServerToken = serverToken
                latestQueueSnapshot = null
            }
            val s = settings.value
            if (!providerSynced) {
                when (providerSyncAction(s)) {
                    ProviderSyncAction.CONFIGURE -> client.setupProvider(s.provider, s.apiKey, s.model, s.baseUrl)
                    ProviderSyncAction.SELECT_STORED -> client.selectDefaultProvider(s.provider)
                }
                if (!current()) return@withLock
                providerSynced = true
            }
            refreshProviderCatalog()
            if (!current()) return@withLock
            val previousId = sessionId
            val previousSnapshot = previousId?.let { client.sessionSnapshot(it) }
            if (!current()) return@withLock
            if (previousId != null && previousSnapshot == null) {
                sessionId = null
                sessionPreferences.edit().remove("id").apply()
                lastSeq = 0
                streamJob?.cancel()
                items.value = emptyList()
                running.value = false
                clearPendingApprovals()
                cockpit.value = Cockpit()
                backgroundJobs.value = BackgroundJobsState()
                jobLog.value = null
                jobError.value = null
                killingJobId.value = null
                gitWorkingTree.value = null
                gitDiff.value = null
                gitLoading.value = false
                gitError.value = null
                promptLedger.clear()
                error.value = "此前会话未能恢复，已创建新对话"
            }
            val id = sessionId ?: client.createSession(s.model, cwd = sessionWorkdir.value, reasoningEffort = s.reasoningEffort).also { created ->
                if (!current()) return@withLock
                sessionId = created
                sessionPreferences.edit().putString("id", created).apply()
                lastSeq = 0
            }
            if (!current()) return@withLock
            val snapshot = previousSnapshot ?: client.sessionSnapshot(id)
                ?: throw IllegalStateException("会话已失效，请重新连接")
            if (!current()) return@withLock
            val sceneKey = "welcomeSession:$id"
            welcomeScene.value = storedStellarWelcome(uiPreferences.getString(sceneKey, null), welcomeScene.value)
            if (!uiPreferences.contains(sceneKey)) uiPreferences.edit().putString(sceneKey, welcomeScene.value.id).apply()
            running.value = sessionSnapshotRunning(snapshot)
            runningSnapshotSeq = sessionSnapshotSequence(snapshot)
            activeModel.value = snapshot["model"]?.jsonPrimitive?.contentOrNull ?: s.model
            activeEffort.value = snapshot["reasoningEffort"]?.jsonPrimitive?.contentOrNull ?: "auto"
            activeApprovalMode.value = snapshot["approvalMode"]?.jsonPrimitive?.contentOrNull
            stopping.value = false
            effortBusy.value = false
            controlBusy.value = nativeCommandJob?.isActive == true
            if (lastSeq == 0L && runningSnapshotSeq > 0) {
                connectionStatus.value = "正在恢复历史记录…"
                val history = client.historyEvents(id, runningSnapshotSeq)
                if (!current()) return@withLock
                history.forEach(::onEvent)
                if (!running.value) items.update(ChatTimelineReducer::finishThinking)
            }
            subscribe(id)
            refreshSessionHistory()
            refreshSessionModels()
            // MCP 管理器和 Android bridge 都在服务启动期异步初始化；注册失败不应阻塞聊天会话。
            requestResult { configureAndroidMcp(generation, serverToken) }
                .onFailure { if (current()) androidMcpError.value = it.message ?: "Android MCP 注册失败" }
            if (!current()) return@withLock
            refreshSkills()
            refreshSessionQueue()
            refreshCockpitSnapshot(id)
            refreshInsights(id)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (generation == sessionGeneration) {
                error.value = t.message ?: "会话恢复失败"
                connectionStatus.value = "会话连接失败，请重新连接"
            }
        }
    }

    private suspend fun configureAndroidMcp(generation: Long, serverToken: String) {
        val bridgeToken = TianshuService.androidMcpToken
        fun current() = generation == sessionGeneration && serverToken == TianshuService.token
        var lastError: Throwable? = null
        for (attempt in 0 until 20) {
            if (!current()) return
            val endpoint = androidMcpStatus.value.endpoint
            if (bridgeToken.isBlank() || endpoint.isNullOrBlank()) {
                delay(500)
                continue
            }
            if (configuredAndroidMcpEndpoint == endpoint) return
            try {
                client.registerAndroidMcp(endpoint, bridgeToken)
                if (!current()) return
                client.approveMcpServer("android")
                if (!current()) return
                client.mcpStatus()
                if (!current()) return
                configuredAndroidMcpEndpoint = endpoint
                androidMcpError.value = null
                return
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                lastError = t
                delay(500)
            }
        }
        throw lastError ?: IllegalStateException("天枢 MCP 管理器尚未就绪")
    }
    private fun subscribe(id: String) {
        val generation = sessionGeneration
        val serverToken = TianshuService.token
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val subscriptionJob = currentCoroutineContext()[Job]
            var failures = 0
            var streamAttempt = 0L
            while (currentCoroutineContext().isActive && currentSession(id, generation, serverToken)) {
                sessionReady.value = false
                connectionStatus.value = if (failures == 0) "正在连接事件流…" else "正在重新连接事件流…"
                val attempt = ++streamAttempt
                val result = requestResult {
                    val snapshot = client.sessionSnapshot(id)
                        ?: throw TianshuHttpException(404, "Session not found")
                    if (!currentSession(id, generation, serverToken)) return@requestResult
                    running.value = sessionSnapshotRunning(snapshot)
                    runningSnapshotSeq = sessionSnapshotSequence(snapshot)
                    client.events(id, lastSeq, onOpen = {
                        viewModelScope.launch {
                            if (subscriptionJob?.isActive == true && currentSession(id, generation, serverToken) && streamAttempt == attempt) {
                                sessionReady.value = true
                                connectionStatus.value = null
                            }
                        }
                    }).collect {
                        if (currentSession(id, generation, serverToken)) {
                            if (it.seq > lastSeq) failures = 0
                            onEvent(it)
                        }
                    }
                }
                streamAttempt++
                if (!currentSession(id, generation, serverToken)) break
                sessionReady.value = false
                val failure = result.exceptionOrNull()
                if (streamFailureRequiresRecovery(failure)) {
                    connectionStatus.value = if ((failure as TianshuHttpException).status == 404)
                        "会话已失效，请重新连接" else "事件流鉴权失败，请重新连接"
                    error.value = connectionStatus.value
                    break
                }
                val waitMillis = streamReconnectDelayMillis(failures++)
                connectionStatus.value = "事件流已断开，${waitMillis / 1_000} 秒后重连"
                delay(waitMillis)
            }
        }
    }

    fun retryConnection() {
        if (serviceState.value != TianshuService.State.RUNNING) return
        error.value = null
        viewModelScope.launch { onTianshuReady() }
    }

    private fun refreshCockpitSnapshot(id: String) {
        val generation = sessionGeneration
        val serverToken = TianshuService.token
        val requestedSeq = lastSeq
        viewModelScope.launch {
            requestResult { client.cockpitSnapshot(id) }
                .onSuccess { snapshot ->
                    if (currentSession(id, generation, serverToken) && requestedSeq == lastSeq) {
                        cockpit.update { CockpitReducer.mergeSnapshot(it, snapshot) }
                    }
                }
        }
    }

    private fun refreshInsights(id: String) {
        val generation = sessionGeneration
        val serverToken = TianshuService.token
        viewModelScope.launch {
            requestResult { parseSessionInsights(client.sessionInsights(id)) }
                .onSuccess { snapshot -> if (currentSession(id, generation, serverToken)) cockpit.update { it.copy(insights = snapshot) } }
        }
    }

    fun send(text: String): Boolean {
        val attachments = draftAttachments.value.toList()
        runCatching { validateAttachmentBundle(attachments) }.onFailure { error.value = it.message; return false }
        val availability = chatSendAvailability(
            serviceState.value,
            sessionReady.value,
            running.value,
            if (attachments.isEmpty()) text else text.ifBlank { "请查看我发送的附件。" },
            items.value.hasUnconfirmedPrompt(),
        )
        if (!availability.enabled || stopping.value || effortBusy.value || controlBusy.value || attachmentsLoading.value) {
            availability.message?.let { error.value = it }
            return false
        }
        val id = sessionId ?: run {
            error.value = "会话尚未准备好，请稍候"
            return false
        }
        val localText = text.trim().ifBlank { "请查看我发送的附件。" }
        val requestId = UUID.randomUUID().toString()
        items.update { it + ChatItem.User(localText, requestId, PromptDelivery.SENDING, attachments = attachments) }
        promptLedger.record(requestId, localText, System.currentTimeMillis())
        submitPrompt(id, localText, requestId, attachments)
        draftAttachments.value = emptyList()
        return true
    }

    fun retryPrompt(requestId: String) {
        val id = sessionId ?: return
        if (!sessionReady.value || serviceState.value != TianshuService.State.RUNNING || running.value || controlBusy.value || effortBusy.value || stopping.value) return
        val item = items.value.filterIsInstance<ChatItem.User>().firstOrNull { it.requestId == requestId } ?: return
        if (item.delivery !in setOf(PromptDelivery.UNCERTAIN, PromptDelivery.REJECTED)) return
        if (items.value.any { it is ChatItem.User && it.requestId != requestId && it.delivery in setOf(PromptDelivery.SENDING, PromptDelivery.UNCERTAIN) }) return
        val retry = preparePromptRetry(item, UUID.randomUUID().toString())
        val retryId = checkNotNull(retry.requestId)
        if (item.delivery == PromptDelivery.REJECTED) {
            promptLedger.remove(requestId)
            promptLedger.record(retryId, retry.wireText ?: retry.text, System.currentTimeMillis())
        }
        items.update { entries -> entries.map { if (it == item) retry else it } }
        submitPrompt(id, retry.wireText ?: retry.text, retryId, retry.attachments, reportError = retry.wireText != null,
            previouslyUncertain = item.delivery == PromptDelivery.UNCERTAIN)
    }

    private fun submitPrompt(id: String, text: String, requestId: String, attachments: List<PromptAttachment> = emptyList(), reportError: Boolean = false, previouslyUncertain: Boolean = false) {
        val generation = sessionGeneration
        val serverToken = TianshuService.token
        viewModelScope.launch {
            try {
                val snapshot = client.sendPrompt(id, text, requestId, attachments)
                if (currentSession(id, generation, serverToken)) {
                    // The prompt receipt contains the authoritative session record. A newer
                    // streamed event may already have completed the turn before HTTP returns.
                    if (sessionSnapshotSequence(snapshot) >= lastSeq) {
                        running.value = sessionSnapshotRunning(snapshot)
                        runningSnapshotSeq = maxOf(runningSnapshotSeq, sessionSnapshotSequence(snapshot))
                    }
                    if (generation == sessionGeneration && sessionId == id) items.update { updatePromptDelivery(it, requestId, PromptDelivery.ACCEPTED) }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (!currentSession(id, generation, serverToken)) return@launch
                val rejected = isDefinitivePromptRejection(t, previouslyUncertain)
                if (rejected) promptLedger.remove(requestId)
                items.update {
                    updatePromptDelivery(
                        it, requestId,
                        if (rejected) PromptDelivery.REJECTED else PromptDelivery.UNCERTAIN,
                        t.message ?: "请求未完成",
                    )
                }
                if (reportError) error.value = if (rejected) "目标启动请求被拒绝：${t.message}" else "目标启动回执未确认：${t.message}"
            }
        }
    }

    fun refreshSkills() {
        val id = sessionId ?: return
        val generation = sessionGeneration
        val serverToken = TianshuService.token
        val request = ++skillsRequest
        skillsLoading.value = true
        viewModelScope.launch {
            try {
                requestResult { client.skillCatalog(id) }
                    .onSuccess {
                        if (request == skillsRequest && currentSession(id, generation, serverToken)) {
                            skills.value = it.skills
                            skillsError.value = it.loadErrors.takeIf(List<String>::isNotEmpty)?.joinToString("\n")
                        }
                    }
                    .onFailure {
                        if (request == skillsRequest && currentSession(id, generation, serverToken)) skillsError.value = it.message ?: "技能列表读取失败"
                    }
                requestResult {
                    val snapshot = client.sessionSnapshot(id) ?: error("会话已失效")
                    val cwd = snapshot["cwd"]?.jsonPrimitive?.contentOrNull ?: error("会话工作目录缺失")
                    withContext(Dispatchers.IO) {
                        discoverProjectCommands(cwd, Environment.getExternalStorageDirectory(), Runtime(getApplication<Application>()).rootfs)
                    }
                }.onSuccess {
                    if (request == skillsRequest && currentSession(id, generation, serverToken)) {
                        customCommands.value = it
                        commandsError.value = null
                    }
                }.onFailure {
                    if (request == skillsRequest && currentSession(id, generation, serverToken)) {
                        customCommands.value = emptyList()
                        commandsError.value = it.message ?: "项目命令读取失败"
                    }
                }
            } finally {
                if (request == skillsRequest && currentSession(id, generation, serverToken)) skillsLoading.value = false
            }
        }
    }

    fun setSkillEnabled(name: String, enabled: Boolean): Boolean = executeSessionCommand(
        NativeSlashRequest(PaletteAction.SKILL_MANAGE, "/skill", "${if (enabled) "on" else "off"} $name"), showOutput = false)

    fun executeSessionCommand(request: NativeSlashRequest, showOutput: Boolean = true): Boolean {
        val id = sessionId ?: run { error.value = "天枢会话尚未连接"; return false }
        val canSwitch = !running.value && !stopping.value && !controlBusy.value && !effortBusy.value &&
            !attachmentsLoading.value && !items.value.hasUnconfirmedPrompt()
        if (nativeSlashBlocked(request, canSwitch, sessionReady.value, effortBusy.value, controlBusy.value, stopping.value, running.value)) {
            error.value = "当前会话尚未就绪或操作未结束，请稍后重试"
            return false
        }
        if (request.action in setOf(PaletteAction.QUEUE, PaletteAction.STEER) && request.argument.isNotBlank() &&
            (attachmentsLoading.value || items.value.hasUnconfirmedPrompt())) {
            error.value = "当前发送尚未确认，请先核对消息回执"
            return false
        }
        val generation = sessionGeneration
        val token = TianshuService.token
        val commandRevision = ++sessionCommandRevision
        val commandStartSeq = lastSeq
        todoRefreshRevision++
        controlBusy.value = true
        error.value = null
        if (showOutput && request.action !in planActions && request.action !in goalActions && request.action != PaletteAction.TODO) sessionCommandOutput.value = null
        nativeCommandJob = viewModelScope.launch {
            var goalCommand: GoalCommandResult? = null
            var forkedSessionId: String? = null
            var handoffSnapshot: JsonObject? = null
            var teamStarted = false
            var skillToggle: SkillToggle? = null
            try {
                requestResult {
                    check(currentSession(id, generation, token)) { "当前会话已切换，命令未执行" }
                    when (request.action) {
                        PaletteAction.COMPACT -> SessionCommandOutput("上下文压缩",
                            if (client.compactSession(id)) "已压缩上下文，保留关键决策。" else "当前上下文无需压缩。")
                        PaletteAction.FORK -> {
                            forkedSessionId = client.forkSession(id, request.argument)
                            SessionCommandOutput("分支已创建", "已复制当前对话到新的独立会话。")
                        }
                        PaletteAction.HANDOFF -> {
                            handoffSnapshot = client.requestHandoff(id, request.argument)
                            SessionCommandOutput("项目交接", "已受理，天枢正在生成交接文档。", handoffPending = true)
                        }
                        PaletteAction.TEAM_RESUME -> {
                            if (request.argument.isBlank()) SessionCommandOutput("恢复编队", "",
                                checkpoints = client.teamCheckpoints(id))
                            else {
                                teamStarted = client.resumeTeam(id, request.argument)
                                SessionCommandOutput("恢复编队", if (teamStarted) "编队已恢复，正在继续未完成的任务。" else "该编队全部任务已完成，无需恢复。")
                            }
                        }
                        PaletteAction.SKILL_MANAGE -> {
                            val toggle = parseSkillToggle(request.argument)
                            val catalog = client.skillCatalog(id)
                            check(catalog.skills.any { it.name == toggle.name }) { "未找到技能 ${toggle.name}，请刷新技能列表" }
                            check(currentSession(id, generation, token)) { "当前会话已切换，技能设置未执行" }
                            client.setSkillEnabled(id, toggle.name, toggle.enabled)
                            skillToggle = toggle
                            SessionCommandOutput("技能设置", "${toggle.name} 已${if (toggle.enabled) "启用" else "停用"}。")
                        }
                        PaletteAction.QUEUE -> when (val command = parseQueueCommand(request.argument)) {
                            QueueCommand.ListEntries -> SessionCommandOutput("排队消息", "", queue = client.sessionQueue(id))
                            is QueueCommand.Enqueue -> {
                                val laneId = client.queueMessage(id, command.text)
                                if (currentSession(id, generation, token)) {
                                    val previous = latestQueueSnapshot ?: TianshuQueueSnapshot(emptyList(), 0)
                                    if (previous.entries.none { it.id == laneId }) latestQueueSnapshot = previous.copy(
                                        entries = listOf(TianshuQueueEntry(laneId, command.text, "queued", System.currentTimeMillis())) + previous.entries)
                                }
                                SessionCommandOutput("排队消息", "已加入下一轮。停止当前任务不会撤回这条消息。", queue = latestQueueSnapshot)
                            }
                            is QueueCommand.Retract -> {
                                client.retractQueuedMessage(id, command.laneId)
                                if (currentSession(id, generation, token)) latestQueueSnapshot = latestQueueSnapshot?.let { snapshot ->
                                    snapshot.copy(entries = snapshot.entries.map { if (it.id == command.laneId) it.copy(status = "retracted") else it }) }
                                SessionCommandOutput("排队消息", "消息已撤回。", queue = latestQueueSnapshot ?: TianshuQueueSnapshot(emptyList(), 0))
                            }
                        }
                        PaletteAction.STEER -> when (val command = parseSteerCommand(request.argument)) {
                            SteerCommand.Show -> SessionCommandOutput("即时引导", "", queue = client.sessionQueue(id))
                            is SteerCommand.Text -> {
                                client.steerSession(id, text = command.text)
                                SessionCommandOutput("即时引导", "已受理，将在下一个工具边界送达模型。")
                            }
                            is SteerCommand.Lane -> {
                                client.steerSession(id, laneId = command.laneId)
                                if (currentSession(id, generation, token)) latestQueueSnapshot = latestQueueSnapshot?.let { snapshot ->
                                    snapshot.copy(entries = snapshot.entries.map { if (it.id == command.laneId) it.copy(status = "steered") else it }) }
                                SessionCommandOutput("即时引导", "已转为本轮即时引导。", queue = latestQueueSnapshot ?: TianshuQueueSnapshot(emptyList(), 0))
                            }
                        }
                        PaletteAction.DOMAIN -> {
                            val choices = client.sessionDomains(id)
                            if (request.argument.isBlank() || request.argument.equals("list", true)) {
                                SessionCommandOutput("星域", "", choices)
                            } else {
                                val key = choices.singleOrNull { it.key.equals(request.argument, true) || it.name == request.argument }?.key
                                    ?: request.argument.takeIf { it == "off" } ?: error("未找到该星域，请用 /domain 查看列表")
                                client.setSessionDomain(id, key)
                                val updated = client.sessionDomains(id)
                                check(key == "off" || updated.any { it.key == key && it.current }) { "星域切换核对失败" }
                                SessionCommandOutput("星域已切换", "当前星域：${updated.firstOrNull { it.current }?.name ?: key}", updated)
                            }
                        }
                        PaletteAction.PLAN_MODE, PaletteAction.ASK_MODE -> {
                            val planning = request.action == PaletteAction.PLAN_MODE
                            val field = if (planning) "planMode" else "askMode"
                            val active = if (planning) "planning" else "asking"
                            val snapshot = client.sessionSnapshot(id) ?: error("会话已失效")
                            val previous = snapshot[field]?.jsonPrimitive?.contentOrNull ?: "off"
                            val state = when (request.argument.lowercase()) {
                                "status" -> previous
                                "on" -> active
                                "off" -> "off"
                                else -> if (previous == active) "off" else active
                            }
                            if (request.argument.lowercase() != "status") client.setSessionMode(id, planning, state)
                            val checked = client.sessionSnapshot(id) ?: error("会话已失效")
                            check((checked[field]?.jsonPrimitive?.contentOrNull ?: "off") == state) { "模式切换核对失败" }
                            SessionCommandOutput(if (planning) "计划模式" else "问答模式", if (state == active) "已开启 · 只读" else "已关闭")
                        }
                        PaletteAction.REVIEW_GATE -> {
                            val desired = if (request.argument == "off") "off" else "auto"
                            if (request.argument != "status") client.setReviewGate(id, desired)
                            val mode = client.reviewGate(id)
                            check(request.argument == "status" || mode == desired) { "审查门核对失败" }
                            SessionCommandOutput("自动审查", if (mode == "off") "已关闭。手动 /review 仍可使用。" else "已开启")
                        }
                        PaletteAction.FAST -> SessionCommandOutput("禅模式", if (client.skipZen(id)) "已恢复全量工具" else "当前不在读专注阶段，无需跳过")
                        PaletteAction.PLAN_LIST, PaletteAction.PLAN_VIEW, PaletteAction.PLAN_EDIT,
                        PaletteAction.PLAN_APPROVE, PaletteAction.PLAN_REJECT -> executePlanCommand(client, id, request)
                        PaletteAction.GOAL, PaletteAction.GOAL_STATUS, PaletteAction.GOAL_PAUSE, PaletteAction.GOAL_RESUME,
                        PaletteAction.GOAL_CANCEL, PaletteAction.GOAL_CRITERIA -> executeGoalCommand(client, id, request).also { goalCommand = it }.output
                        PaletteAction.TODO -> executeTodoCommand(client, id, request)
                        else -> error("此操作没有会话执行接口")
                    }
                }.onSuccess {
                    if (currentSession(id, generation, token)) {
                        var output = it.todos?.let { snapshot -> it.copy(todos = applyTodoSnapshot(snapshot)) } ?: it
                        output.queue?.let { snapshot ->
                            latestQueueSnapshot = reconcileQueueSnapshot(latestQueueSnapshot, snapshot)
                            output = output.copy(queue = latestQueueSnapshot)
                        }
                        handoffSnapshot?.let { snapshot ->
                            if (sessionSnapshotSequence(snapshot) >= lastSeq) {
                                running.value = sessionSnapshotRunning(snapshot)
                                runningSnapshotSeq = maxOf(runningSnapshotSeq, sessionSnapshotSequence(snapshot))
                            }
                            lastHandoffArchived?.takeIf { it.seq > commandStartSeq }?.let { archived ->
                                output = output.copy(text = "交接文档已归档。\n${archived.str("dest").orEmpty()}", handoffPending = false)
                            }
                            if (output.handoffPending && !running.value) output = output.copy(
                                text = "交接任务已结束，但尚未收到归档完成回执，请核对项目文档。", handoffPending = false)
                        }
                        if (teamStarted && lastSeq <= commandStartSeq) running.value = true
                        skillToggle?.let { toggle ->
                            skills.update { entries -> entries.map { skill -> if (skill.name == toggle.name) skill.copy(enabled = toggle.enabled) else skill } }
                            refreshSkills()
                        }
                        forkedSessionId?.let { forked ->
                            refreshSessionHistory()
                            if (!running.value && !stopping.value && !items.value.hasUnconfirmedPrompt()) {
                                resetSession(forked)
                                return@onSuccess
                            }
                            output = output.copy(text = "分支已创建：$forked\n源会话正在执行，可从历史记录打开分支。")
                        }
                        if (showOutput && commandRevision == sessionCommandRevision) sessionCommandOutput.value = output
                        if (request.action in setOf(PaletteAction.QUEUE, PaletteAction.STEER)) refreshSessionQueue()
                        goalCommand?.let { result -> result.kickoff?.let { prompt ->
                            val requestId = UUID.randomUUID().toString()
                            items.update { entries -> entries + ChatItem.User(result.display, requestId, PromptDelivery.SENDING, wireText = prompt) }
                            promptLedger.record(requestId, prompt, System.currentTimeMillis())
                            submitPrompt(id, prompt, requestId, reportError = true)
                        } }
                    }
                }
                    .onFailure { if (currentSession(id, generation, token)) {
                        val mutation = request.action in setOf(PaletteAction.COMPACT, PaletteAction.FORK, PaletteAction.HANDOFF, PaletteAction.SKILL_MANAGE) ||
                            (request.action in setOf(PaletteAction.TEAM_RESUME, PaletteAction.QUEUE, PaletteAction.STEER) && request.argument.isNotBlank())
                        error.value = if (mutation && it is java.io.IOException)
                            "未收到命令结果，操作可能已经受理。请先核对会话状态，避免重复执行。\n${it.message.orEmpty()}"
                        else it.message ?: "命令执行失败"
                    } }
            } finally { if (currentSession(id, generation, token)) controlBusy.value = false }
        }
        return true
    }

    private fun refreshSessionQueue() {
        val id = sessionId ?: return
        val generation = sessionGeneration
        val token = TianshuService.token
        val revision = ++queueRefreshRevision
        viewModelScope.launch {
            requestResult { client.sessionQueue(id) }.onSuccess { snapshot ->
                if (currentSession(id, generation, token) && revision == queueRefreshRevision) {
                    latestQueueSnapshot = reconcileQueueSnapshot(latestQueueSnapshot, snapshot)
                    sessionCommandOutput.update { output -> if (output?.queue != null) output.copy(queue = latestQueueSnapshot) else output }
                }
            }.onFailure {
                if (currentSession(id, generation, token) && revision == queueRefreshRevision && sessionCommandOutput.value?.queue != null)
                    error.value = "队列状态同步失败：${it.message}。请核对结果后再操作。"
            }
        }
    }

    fun saveSessionPlan(slug: String, content: String) {
        val id = sessionId ?: return
        if (nativeSlashBlocked(NativeSlashRequest(PaletteAction.PLAN_EDIT, "/plan-edit", slug),
                !running.value && !stopping.value && !controlBusy.value && !effortBusy.value &&
                    !attachmentsLoading.value && !items.value.hasUnconfirmedPrompt(), sessionReady.value)) return
        val generation = sessionGeneration
        val token = TianshuService.token
        val commandRevision = ++sessionCommandRevision
        controlBusy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                requestResult {
                    client.updateSessionPlan(id, slug, content)
                    val plan = client.sessionPlan(id, slug)
                    SessionCommandOutput(plan.title, "修改已保存", plan = plan)
                }.onSuccess { if (currentSession(id, generation, token) && commandRevision == sessionCommandRevision) sessionCommandOutput.value = it }
                    .onFailure { if (currentSession(id, generation, token)) error.value = it.message ?: "计划保存失败" }
            } finally { if (currentSession(id, generation, token)) controlBusy.value = false }
        }
    }

    fun dismissSessionCommand() {
        sessionCommandRevision++
        todoRefreshRevision++
        sessionCommandOutput.value = null
    }

    private fun refreshOpenTodoPanel() {
        if (sessionCommandOutput.value?.todos == null || controlBusy.value) return
        val id = sessionId ?: return
        val generation = sessionGeneration
        val token = TianshuService.token
        val commandRevision = sessionCommandRevision
        val revision = ++todoRefreshRevision
        viewModelScope.launch {
            requestResult { client.sessionTodos(id) }.onSuccess { snapshot ->
                if (currentSession(id, generation, token) && revision == todoRefreshRevision &&
                    commandRevision == sessionCommandRevision && !controlBusy.value) {
                    val newest = applyTodoSnapshot(snapshot)
                    sessionCommandOutput.update { output -> if (output?.todos != null)
                        output.copy(todos = newest, todoMutationArgument = null, text = "") else output }
                }
            }.onFailure {
                if (currentSession(id, generation, token) && revision == todoRefreshRevision &&
                    commandRevision == sessionCommandRevision && !controlBusy.value) error.value = it.message ?: "待办同步失败"
            }
        }
    }

    private fun applyTodoSnapshot(snapshot: TianshuTodos): TianshuTodos {
        val newest = newestTianshuTodos(latestTodoSnapshot, snapshot)
        latestTodoSnapshot = newest
        cockpit.update { c -> c.copy(todos = newest.items.map { Todo(it.content, it.status, it.id, it.activeForm) }) }
        return newest
    }

    fun stop() {
        val id = sessionId ?: return
        if (stopping.value || !running.value) return
        val generation = sessionGeneration
        val serverToken = TianshuService.token
        stopping.value = true
        viewModelScope.launch {
            try {
                requestResult {
                    val result = client.abort(id)
                    check(result["aborted"]?.jsonPrimitive?.booleanOrNull == true) { "天枢未确认停止任务" }
                    val snapshot = client.sessionSnapshot(id) ?: error("会话已失效")
                    if (currentSession(id, generation, serverToken)) {
                        running.value = sessionSnapshotRunning(snapshot)
                        runningSnapshotSeq = maxOf(runningSnapshotSeq, sessionSnapshotSequence(snapshot))
                        items.update(ChatTimelineReducer::finishThinking)
                        clearPendingApprovals()
                        refreshSessionHistory()
                    }
                }.onFailure { if (currentSession(id, generation, serverToken)) error.value = it.message ?: "停止任务失败" }
            } finally {
                if (currentSession(id, generation, serverToken)) stopping.value = false
            }
        }
    }

    fun setThinkingDisplay(display: ThinkingDisplay) {
        uiPreferences.edit().putString("thinkingDisplay", display.wire).apply()
        thinkingDisplay.value = display
    }

    fun setReasoningEffort(effort: String) {
        val id = sessionId ?: return
        val model = activeProviderModel(providerCatalog.value, activeModel.value)
        val supported = model?.second?.effortSupported == true
        if (!sessionReady.value || effortBusy.value || controlBusy.value || (effort != "auto" && effort !in reasoningLevels(model?.first?.name.orEmpty(), supported))) return
        val generation = sessionGeneration
        val token = TianshuService.token
        effortBusy.value = true
        viewModelScope.launch {
            try {
                requestResult {
                    client.setReasoningEffort(id, effort)
                    val snapshot = client.sessionSnapshot(id) ?: error("会话已失效")
                    check(snapshot["reasoningEffort"]?.jsonPrimitive?.contentOrNull == effort) { "思考强度核对失败，请重试" }
                    if (currentSession(id, generation, token)) {
                        activeEffort.value = effort
                        val next = settings.value.copy(reasoningEffort = effort)
                        withContext(Dispatchers.IO) { next.save(getApplication<Application>()) }
                        if (currentSession(id, generation, token)) settings.value = next
                    }
                }.onFailure { if (currentSession(id, generation, token)) error.value = it.message ?: "思考强度更新失败" }
            } finally {
                if (currentSession(id, generation, token)) effortBusy.value = false
            }
        }
    }

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty() || attachmentsLoading.value) return
        val id = sessionId ?: return
        val generation = sessionGeneration
        attachmentsLoading.value = true
        viewModelScope.launch {
            try {
                for (uri in uris.take(8)) {
                    val result = requestResult { withContext(Dispatchers.IO) { importChatAttachment(getApplication(), uri) } }
                    if (id != sessionId || generation != sessionGeneration) return@launch
                    result.onSuccess { attachment ->
                        val count = draftAttachments.value.count { (it.kind == AttachmentKind.IMAGE) == (attachment.kind == AttachmentKind.IMAGE) }
                        if (count < 4) draftAttachments.update { it + attachment }
                        else error.value = if (attachment.kind == AttachmentKind.IMAGE) "最多添加 4 张图片" else "最多添加 4 个文件"
                    }.onFailure { error.value = it.message ?: "附件读取失败" }
                }
                if (uris.size > 8) error.value = "每次最多添加 8 个附件"
            } finally { attachmentsLoading.value = false }
        }
    }

    fun removeAttachment(id: String) { draftAttachments.update { list -> list.filterNot { it.id == id } } }

    fun refreshSessionModels() {
        val id = sessionId ?: return
        if (modelsLoading.value || controlBusy.value) return
        val generation = sessionGeneration
        val token = TianshuService.token
        val request = ++modelListRequest
        modelsLoading.value = true
        viewModelScope.launch {
            try {
                requestResult { client.sessionModels(id) }
                    .onSuccess { if (request == modelListRequest && currentSession(id, generation, token)) { sessionModels.value = it; modelsError.value = null } }
                    .onFailure { if (request == modelListRequest && currentSession(id, generation, token)) modelsError.value = it.message ?: "模型列表读取失败" }
            } finally { if (request == modelListRequest) modelsLoading.value = false }
        }
    }

    fun setSessionModel(choice: SessionModelChoice) {
        val id = sessionId ?: return
        if (!sessionReady.value || running.value || controlBusy.value || effortBusy.value || stopping.value || items.value.hasUnconfirmedPrompt()) return
        if (sessionModels.value.none { it.spec == choice.spec } || choice.current) return
        val generation = sessionGeneration
        val token = TianshuService.token
        controlBusy.value = true
        modelListRequest++
        modelsLoading.value = false
        viewModelScope.launch {
            try {
                requestResult {
                    val result = client.switchSessionModel(id, choice.spec)
                    check(result["id"]?.jsonPrimitive?.contentOrNull == id) { "模型切换未得到确认" }
                    val snapshot = client.sessionSnapshot(id) ?: error("会话已失效")
                    val choices = client.sessionModels(id)
                    check(choices.any { it.spec == choice.spec && it.current }) { "模型切换核对失败，请重试" }
                    if (currentSession(id, generation, token)) {
                        activeModel.value = snapshot["model"]?.jsonPrimitive?.contentOrNull ?: choice.spec
                        activeEffort.value = snapshot["reasoningEffort"]?.jsonPrimitive?.contentOrNull ?: "auto"
                        sessionModels.value = choices
                        refreshSessionHistory()
                    }
                }.onFailure { if (currentSession(id, generation, token)) error.value = it.message ?: "模型切换失败" }
            } finally { if (currentSession(id, generation, token)) controlBusy.value = false }
        }
    }

    fun setChatApprovalMode(mode: ChatApprovalMode) {
        val id = sessionId ?: return
        if (!sessionReady.value || controlBusy.value || effortBusy.value || stopping.value) return
        val generation = sessionGeneration
        val token = TianshuService.token
        controlBusy.value = true
        viewModelScope.launch {
            try {
                requestResult {
                    client.setApprovalMode(id, mode)
                    val snapshot = client.sessionSnapshot(id) ?: error("会话已失效")
                    check(snapshot["approvalMode"]?.jsonPrimitive?.contentOrNull == mode.wire) { "审批模式核对失败，请重试" }
                    if (currentSession(id, generation, token)) activeApprovalMode.value = mode.wire
                }.onFailure { if (currentSession(id, generation, token)) error.value = it.message ?: "审批模式更新失败" }
            } finally { if (currentSession(id, generation, token)) controlBusy.value = false }
        }
    }

    fun refreshSessionHistory() {
        if (serviceState.value != TianshuService.State.RUNNING || historyLoading.value) return
        historyLoading.value = true
        val token = TianshuService.token
        viewModelScope.launch {
            try {
                requestResult { client.listSessions() }
                    .onSuccess { if (token == TianshuService.token) { sessionHistory.value = it; historyError.value = null } }
                    .onFailure { if (token == TianshuService.token) historyError.value = it.message ?: "历史记录读取失败" }
            } finally { historyLoading.value = false }
        }
    }

    fun openSession(id: String): Boolean {
        if (id == sessionId) return true
        if (running.value || stopping.value || effortBusy.value || controlBusy.value || attachmentsLoading.value || items.value.hasUnconfirmedPrompt()) return false
        resetSession(id)
        return true
    }

    fun answer(approve: Boolean) {
        val id = sessionId ?: return
        val a = approval.value ?: return
        if (approvalAnswerBusy.value) return
        val generation = sessionGeneration
        val serverToken = TianshuService.token
        approvalAnswerRequestId = a.requestId
        approvalAnswerBusy.value = true
        viewModelScope.launch {
            try {
                requestResult { client.answerApproval(id, a.requestId, approve) }
                    .onSuccess {
                        if (currentSession(id, generation, serverToken)) {
                            pendingApprovals = pendingApprovals.filterNot { it.requestId == a.requestId }
                            approval.value = pendingApprovals.firstOrNull()
                        }
                    }
                    .onFailure { if (currentSession(id, generation, serverToken)) error.value = it.message ?: "审批提交失败，请重试" }
            } finally {
                if (currentSession(id, generation, serverToken) && approvalAnswerRequestId == a.requestId) {
                    approvalAnswerRequestId = null
                    approvalAnswerBusy.value = false
                }
            }
        }
    }

    fun newSession() {
        if (running.value || stopping.value || effortBusy.value || controlBusy.value || attachmentsLoading.value || items.value.hasUnconfirmedPrompt()) return
        welcomeScene.value = nextStellarWelcome(welcomeScene.value.id)
        uiPreferences.edit().putString("welcomeScene", welcomeScene.value.id).apply()
        resetSession(null)
    }

    private fun resetSession(id: String?) {
        nativeCommandJob?.cancel()
        sessionId?.let { attachmentDrafts[it] = draftAttachments.value }
        draftAttachments.value = id?.let { attachmentDrafts[it] }.orEmpty()
        sessionGeneration++
        sessionId = id
        sessionPreferences.edit().apply { if (id == null) remove("id") else putString("id", id) }.apply()
        lastSeq = 0
        sessionReady.value = false
        streamJob?.cancel()
        running.value = false
        stopping.value = false
        effortBusy.value = false
        controlBusy.value = false
        modelListRequest++
        modelsLoading.value = false
        activeApprovalMode.value = null
        sessionModels.value = emptyList()
        modelsError.value = null
        skills.value = emptyList()
        customCommands.value = emptyList()
        skillsError.value = null
        skillsRequest++
        skillsLoading.value = false
        commandsError.value = null
        sessionCommandOutput.value = null
        latestTodoSnapshot = null
        latestQueueSnapshot = null
        queueRefreshRevision++
        lastHandoffArchived = null
        activeModel.value = settings.value.model
        activeEffort.value = settings.value.reasoningEffort
        clearPendingApprovals()
        items.value = emptyList()
        cockpit.value = Cockpit()
        backgroundJobs.value = BackgroundJobsState()
        jobLog.value = null
        jobError.value = null
        killingJobId.value = null
        gitWorkingTree.value = null
        gitDiff.value = null
        gitLoading.value = false
        gitError.value = null
        promptLedger.clear()
        if (serviceState.value == TianshuService.State.RUNNING) viewModelScope.launch { onTianshuReady() }
    }

    fun dismissError() { error.value = null }

    fun loadJobLog(jobId: String) {
        val id = sessionId ?: return
        val generation = sessionGeneration
        val serverToken = TianshuService.token
        jobLog.value = JobLogView(jobId, loading = true)
        viewModelScope.launch {
            requestResult { client.jobLogs(id, jobId) }
                .onSuccess { if (currentSession(id, generation, serverToken) && jobLog.value?.jobId == jobId) jobLog.value = JobLogView(jobId, it) }
                .onFailure { if (currentSession(id, generation, serverToken) && jobLog.value?.jobId == jobId) jobLog.value = JobLogView(jobId, it.message ?: "日志读取失败") }
        }
    }

    fun killJob(jobId: String) {
        val id = sessionId ?: return
        val generation = sessionGeneration
        val serverToken = TianshuService.token
        if (backgroundJobs.value.jobs.none { it.id == jobId && it.status == "running" } || killingJobId.value != null) return
        killingJobId.value = jobId
        jobError.value = null
        viewModelScope.launch {
            try {
                requestResult { client.killJob(id, jobId) }
                    .onFailure { if (currentSession(id, generation, serverToken)) jobError.value = it.message ?: "终止作业失败" }
            } finally {
                if (currentSession(id, generation, serverToken) && killingJobId.value == jobId) killingJobId.value = null
            }
        }
    }

    fun dismissJobError() { jobError.value = null }

    fun checkRoot() = viewModelScope.launch {
        commandExecutor.checkRoot()
    }

    fun requestShizuku() = commandExecutor.requestShizuku()

    fun refreshShizuku() = commandExecutor.refreshShizuku()

    fun refreshAccessibility() {
        accessibilityEnabled.value = XuanjiAccessibilityService.isEnabled(getApplication())
    }

    fun refreshSystemPermissions() {
        systemPermissionState.value = readRuntimePermissionState(getApplication())
    }

    fun listWorkspace(path: String = workspacePath.value) = viewModelScope.launch {
        val request = ++workspaceRequest
        previewRequest++
        workspaceError.value = null
        requestResult { withContext(Dispatchers.IO) { workspaceRepository.list(path) } }
            .onSuccess {
                if (request != workspaceRequest) return@onSuccess
                workspacePath.value = path
                workspaceFiles.value = it
                filePreview.value = null
            }
            .onFailure { if (request == workspaceRequest) workspaceError.value = it.message ?: "目录读取失败" }
    }

    fun previewWorkspaceFile(path: String) = viewModelScope.launch {
        val request = ++previewRequest
        workspaceError.value = null
        requestResult { withContext(Dispatchers.IO) { workspaceRepository.read(path) } }
            .onSuccess { if (request == previewRequest) filePreview.value = it }
            .onFailure { if (request == previewRequest) workspaceError.value = it.message ?: "文件读取失败" }
    }

    fun clearFilePreview() { previewRequest++; filePreview.value = null }

    fun startSessionInDirectory(path: String) {
        if (running.value || items.value.hasUnconfirmedPrompt()) {
            error.value = "请先等待当前任务结束，再切换会话目录"
            return
        }
        val directory = runCatching {
            resolveSharedStoragePath(path, Environment.getExternalStorageDirectory().canonicalFile)
        }.getOrElse { error.value = it.message ?: "目录无效"; return }
        if (!directory.isDirectory) { error.value = "目录不存在：$path"; return }
        sessionWorkdir.value = path
        sessionPreferences.edit().putString("cwd", path).apply()
        newSession()
    }

    fun refreshGitChanges() {
        val id = sessionId ?: return
        val generation = sessionGeneration
        val request = ++gitRequest
        gitLoading.value = true
        gitError.value = null
        viewModelScope.launch {
            requestResult { parseGitWorkingTree(client.sessionWorkingTree(id)) }
                .onSuccess { if (generation == sessionGeneration && sessionId == id && request == gitRequest) gitWorkingTree.value = it }
                .onFailure { if (generation == sessionGeneration && sessionId == id && request == gitRequest) gitError.value = it.message ?: "读取改动失败" }
            if (generation == sessionGeneration && sessionId == id && request == gitRequest) gitLoading.value = false
        }
    }

    fun loadGitDiff(path: String) {
        val id = sessionId ?: return
        val generation = sessionGeneration
        val request = ++gitRequest
        gitDiff.value = GitDiff(path, "")
        gitLoading.value = true
        gitError.value = null
        viewModelScope.launch {
            requestResult { client.sessionFileDiff(id, path) }
                .onSuccess { if (generation == sessionGeneration && sessionId == id && request == gitRequest) gitDiff.value = GitDiff(path, it.take(200_000), it.length > 200_000) }
                .onFailure { if (generation == sessionGeneration && sessionId == id && request == gitRequest) gitError.value = it.message ?: "读取 Diff 失败" }
            if (generation == sessionGeneration && sessionId == id && request == gitRequest) gitLoading.value = false
        }
    }

    fun clearGitDiff() { gitRequest++; gitDiff.value = null; gitError.value = null; gitLoading.value = false }

    fun runTerminal(command: String, backend: ShellBackend) {
        if (command.isBlank() || terminalBusy.value) return
        terminalBusy.value = true
        viewModelScope.launch {
            if (backend == ShellBackend.ROOT) commandExecutor.checkRoot()
            if (backend == ShellBackend.SHIZUKU) commandExecutor.refreshShizuku()
            val result = requestResult { commandExecutor.execute(command.trim(), backend) }
            result.onSuccess { shell ->
                terminalRecords.update { records ->
                    (records + TerminalRecord(command.trim(), backend, shell.exitCode, shell.output, shell.timedOut)).takeLast(50)
                }
            }.onFailure {
                val message = it.message ?: "执行失败"
                terminalRecords.update { records ->
                    (records + TerminalRecord(command.trim(), backend, -1, message, false)).takeLast(50)
                }
            }
            terminalBusy.value = false
        }
    }

    fun clearTerminal() { terminalRecords.value = emptyList() }

    fun testProvider(candidate: Settings) = viewModelScope.launch {
        val request = ++providerTestRequest
        val generation = sessionGeneration
        providerTest.value = "正在测试模型连接…"
        requestResult { client.testProvider(candidate.provider, candidate.apiKey, candidate.model, candidate.baseUrl) }
            .onSuccess { if (request == providerTestRequest && generation == sessionGeneration) providerTest.value = providerProbeSummary(it) }
            .onFailure { if (request == providerTestRequest && generation == sessionGeneration) providerTest.value = "连接失败：${it.message ?: "未知错误"}" }
    }

    fun refreshProviderCatalog() {
        if (serviceState.value != TianshuService.State.RUNNING) return
        val request = ++providerCatalogRequest
        val serverToken = TianshuService.token
        viewModelScope.launch {
            requestResult { parseProviderCatalog(client.listProviders()) }
                .onSuccess { if (request == providerCatalogRequest && serverToken == TianshuService.token) { providerCatalog.value = it; providerCatalogError.value = null } }
                .onFailure { if (request == providerCatalogRequest && serverToken == TianshuService.token) providerCatalogError.value = it.message ?: "服务商列表读取失败" }
        }
    }

    fun activateProvider(profile: ProviderProfile, modelId: String) {
        if (!profile.isAvailable || providerActionBusy.value) return
        providerActionBusy.value = true
        viewModelScope.launch {
            requestResult { client.selectDefaultProvider(profile.name) }
                .onSuccess {
                    saveSettings(settingsForProviderActivation(settings.value, profile, modelId))
                    refreshProviderCatalog()
                }
                .onFailure { providerCatalogError.value = it.message ?: "切换服务商失败" }
            providerActionBusy.value = false
        }
    }

    fun removeProvider(profile: ProviderProfile) {
        if (profile.name == settings.value.provider || providerActionBusy.value) return
        providerActionBusy.value = true
        viewModelScope.launch {
            requestResult { client.deleteProvider(profile.name) }
                .onSuccess { refreshProviderCatalog() }
                .onFailure { providerCatalogError.value = it.message ?: "移除服务商失败" }
            providerActionBusy.value = false
        }
    }

    fun checkCommand(backend: ShellBackend) = viewModelScope.launch {
        commandChecks.update { it.withResult(backend, "正在执行 id…") }
        val result = requestResult { commandExecutor.execute("id", backend) }
            .fold(
                { "${it.backend}: 退出码 ${it.exitCode}${if (it.timedOut) "（超时）" else ""}\n${it.output.trim()}" },
                { "检查失败：${it.message}" },
            )
        commandChecks.update { it.withResult(backend, result) }
    }

    override fun onCleared() {
        commandExecutor.close()
        super.onCleared()
    }

    // ===== 把天枢事件折叠成界面状态 =====
    private fun onEvent(e: SessionEvent) {
        if (e.seq > 0) {
            if (e.seq <= lastSeq) return // 重连回放时跳过已处理过的事件
            lastSeq = e.seq
        }
        // Older replay events must not replace the authoritative state read before connecting.
        running.value = runningAfterSessionEvent(running.value, e, runningSnapshotSeq)
        pendingApprovals = pendingApprovalsAfterEvent(pendingApprovals, e)
        approval.value = pendingApprovals.firstOrNull()
        if (e.type == "job" || e.type == "job_snapshot") {
            backgroundJobs.update { BackgroundJobsReducer.onEvent(it, e) }
        }
        val localRequestId = if (e.type == "user") {
            promptLedger.consumeServerEvent(e.str("promptText") ?: e.str("text") ?: e.str("prompt") ?: "", e.ts)
        } else null
        val serverUserEchoConsumed = localRequestId != null
        if (localRequestId != null) items.update { updatePromptDelivery(it, localRequestId, PromptDelivery.DELIVERED) }
        cockpit.update { current ->
            CockpitReducer.onEvent(current, e)
        }
        cockpit.update { current ->
            val summary = eventSummary(e, serverUserEchoConsumed)
            if (summary.isBlank()) current
            else current.copy(timeline = (current.timeline + CockpitTimelineEntry(e.type, summary)).takeLast(60))
        }
        items.update { ChatTimelineReducer.onEventBoundary(it, e.type) }
        when (e.type) {
            "skills_changed" -> if (e.seq == 0L || e.seq > runningSnapshotSeq) refreshSkills()
            "queue_pending", "queue_status" -> {
                latestQueueSnapshot = queueAfterEvent(latestQueueSnapshot, e)
                sessionCommandOutput.update { output -> if (output?.queue != null) output.copy(queue = latestQueueSnapshot) else output }
            }
            "steer_delivered" -> if (e.seq == 0L || e.seq > runningSnapshotSeq) sessionCommandOutput.update { output ->
                if (output?.title == "即时引导") output.copy(text = "模型已收到即时引导。") else output }
            "handoff_archived" -> {
                lastHandoffArchived = e
                sessionCommandOutput.update { output -> if (output?.handoffPending == true)
                    output.copy(text = "交接文档已归档。\n${e.str("dest").orEmpty()}", handoffPending = false) else output }
            }
            "user" -> {
                val serverText = e.str("promptText") ?: e.str("text") ?: e.str("prompt") ?: ""
                if (!serverUserEchoConsumed) {
                    val (text, attachments) = restoreAttachmentMessage(serverText, e.data)
                    items.value += ChatItem.User(displayGoalPrompt(text), attachments = attachments)
                }
                cockpit.update { it.copy(deliveryGate = null) }
            }
            "text_delta" -> {
                val delta = e.str("text") ?: return
                items.update { list ->
                    val last = list.lastOrNull()
                    if (last is ChatItem.Assistant) list.dropLast(1) + last.copy(text = last.text + delta)
                    else list + ChatItem.Assistant(delta)
                }
            }
            "thinking_delta" -> {
                val delta = e.str("text") ?: return
                items.update { ChatTimelineReducer.appendThinking(it, delta) }
            }
            "tool_use" -> items.value += ChatItem.Tool(
                id = e.str("id") ?: "",
                name = e.str("name") ?: "",
                input = e.data["input"]?.let(::summarize) ?: "",
            )
            "tool_result" -> {
                val id = e.str("id")
                val result = e.str("uiContent") ?: e.str("result") ?: ""
                items.update { list ->
                    list.map { if (it is ChatItem.Tool && it.id == id) toolAfterResult(it, e) else it }
                }
            }
            "todo_state" -> {
                cockpit.update { c -> c.copy(todos = e.data["items"]?.jsonArray?.map {
                    val o = it.jsonObject
                    Todo(o["content"]?.jsonPrimitive?.content ?: "", o["status"]?.jsonPrimitive?.content ?: "pending",
                        o["id"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["activeForm"]?.jsonPrimitive?.contentOrNull)
                } ?: emptyList()) }
                if (e.data["revision"] != null && e.data["authoritative"]?.jsonPrimitive?.booleanOrNull == true) {
                    todoRefreshRevision++
                    runCatching { parseTianshuTodos(e.data) }.onSuccess { snapshot ->
                        val newest = applyTodoSnapshot(snapshot.copy(seq = e.seq))
                        sessionCommandOutput.update { output -> if (output?.todos != null)
                            output.copy(todos = newest, todoMutationArgument = null, text = "") else output }
                    }
                } else refreshOpenTodoPanel()
            }
            "goal_state" -> {
                cockpit.update { it.copy(goal = e.str("goal") ?: it.goal) }
                if (!e.str("goalId").isNullOrBlank()) runCatching { parseTianshuGoal(e.data) }.onSuccess { goal ->
                    sessionCommandOutput.update { output -> if (output?.showGoal == true || output?.goal != null) output.copy(goal = goal) else output }
                }
            }
            "error" -> if (e.seq == 0L || e.seq > runningSnapshotSeq) error.value = e.str("error")
            "done" -> {
                if (e.seq > 0 && e.seq <= runningSnapshotSeq) return
                sessionCommandOutput.update { output -> if (output?.handoffPending == true)
                    output.copy(text = "交接任务已结束，但尚未收到归档完成回执，请核对项目文档。", handoffPending = false) else output }
                refreshSessionHistory()
                sessionId?.let(::refreshCockpitSnapshot)
                sessionId?.let(::refreshInsights)
                refreshOpenTodoPanel()
                if (skills.value.isEmpty() && !skillsLoading.value) refreshSkills()
            }
        }
    }

    private fun eventSummary(e: SessionEvent, localUserEcho: Boolean = false): String = when (e.type) {
        "user" -> if (localUserEcho) "用户输入已显示" else (e.str("promptText") ?: e.str("text") ?: e.str("prompt")).orEmpty().take(180)
        "text_delta" -> "回答流式更新"
        "thinking_delta" -> "思考流式更新"
        "tool_use" -> "调用 ${e.str("name") ?: "工具"}"
        "tool_result" -> "${e.str("name") ?: "工具"} 返回结果"
        "todo_state" -> "待办列表更新"
        "goal_state" -> "目标更新"
        "queue_pending" -> "跟进消息已排队"
        "queue_status" -> "排队消息状态更新：${e.str("status").orEmpty()}"
        "steer_queued" -> "即时引导已受理"
        "steer_delivered" -> "模型已收到即时引导"
        "handoff_archived" -> "交接文档已归档"
        "approval_required" -> "等待审批：${e.str("toolName") ?: "工具"}"
        "approval_resolved" -> "审批已处理"
        "hook_result" -> "Hook 状态更新"
        "error" -> "错误：${e.str("error").orEmpty()}"
        "done", "turn_complete" -> "本轮完成"
        else -> ""
    }

    /** 把工具参数压成一行，方便在卡片里显示 */
    private fun summarize(el: JsonElement): String = when (el) {
        is JsonObject -> (el["command"] ?: el["file_path"] ?: el["path"] ?: el["pattern"] ?: el["url"])
            ?.jsonPrimitive?.contentOrNull ?: el.toString()
        else -> el.toString()
    }.take(300)
}


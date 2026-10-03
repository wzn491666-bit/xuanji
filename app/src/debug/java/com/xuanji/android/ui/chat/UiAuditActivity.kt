package com.xuanji.android.ui.chat

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.xuanji.android.ChatItem
import com.xuanji.android.TianshuService
import com.xuanji.android.SessionModelChoice
import com.xuanji.android.ThinkingDisplay
import com.xuanji.android.XuanjiViewModel
import com.xuanji.android.Settings
import com.xuanji.android.ui.theme.XuanjiTheme
import com.xuanji.android.ui.workspace.WorkspaceSheet
import com.xuanji.android.ui.workspace.WorkspaceTab
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch

/** Offline visual checks. This activity is excluded from release builds. */
class UiAuditActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialDark = intent.getBooleanExtra("dark", false)
        val initialEmpty = intent.getBooleanExtra("empty", false)
        val welcomeIndex = intent.getIntExtra("welcome", 0).coerceIn(stellarWelcomeScenes.indices)
        // Inspect real disconnected workspace components only on a fresh emulator.
        val workspaceVm = if (intent.getBooleanExtra("workspace", false) &&
            Build.HARDWARE in setOf("ranchu", "goldfish") && !Settings.load(application).isComplete) {
            ViewModelProvider(this)[XuanjiViewModel::class.java]
        } else null
        val workspaceTab = WorkspaceTab.entries.firstOrNull { it.name == intent.getStringExtra("workspaceTab") } ?: WorkspaceTab.COCKPIT
        setContent {
            var dark by remember { mutableStateOf(initialDark) }
            var empty by remember { mutableStateOf(initialEmpty) }
            var commands by remember { mutableStateOf(intent.getBooleanExtra("commands", false)) }
            var models by remember { mutableStateOf(intent.getBooleanExtra("models", false)) }
            var workspace by remember { mutableStateOf(workspaceVm != null) }
            var selectedModel by remember { mutableStateOf("deepseek-flash") }
            var effort by remember { mutableStateOf(intent.getStringExtra("effort") ?: "high") }
            var thinking by remember { mutableStateOf(ThinkingDisplay.entries.firstOrNull { it.wire == intent.getStringExtra("thinking") } ?: ThinkingDisplay.PREVIEW) }
            var section by remember { mutableStateOf(if (intent.getBooleanExtra("effortPanel", false)) ModelPanelSection.THINKING else ModelPanelSection.MODELS) }
            var input by remember { mutableStateOf(intent.getStringExtra("input").orEmpty()) }
            var running by remember { mutableStateOf(intent.getBooleanExtra("running", false)) }
            val stopping = intent.getBooleanExtra("stopping", false)
            var focused by remember { mutableStateOf(false) }
            val preview = remember { mutableFloatStateOf(Float.NaN) }
            val interacting = remember { mutableStateOf(false) }
            val transfer = remember { mutableFloatStateOf(0f) }
            val focus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            val scope = rememberCoroutineScope()
            XuanjiTheme(dark) {
                val drawer = rememberDrawerState(DrawerValue.Closed)
                ModalNavigationDrawer(drawerState = drawer, drawerContent = {
                    HistoryDrawer(emptyList(), null, false, null, true, {},
                        { empty = true; scope.launch { drawer.close() } }, {}, { scope.launch { drawer.close() } })
                }) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ChatEffortBackdrop(effort, preview, interacting, transfer)
                ChatFocusStars(focused, transfer)
                ChatSendGlow("offline-sample", running && !stopping, effort, transfer)
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        Box {
                        ChatHeader(TianshuService.State.STOPPED, 0, false,
                            onWorkspace = { dark = !dark }, onPermissions = { finish() },
                            onNewSession = { empty = !empty }, newSessionEnabled = true,
                            onHistory = { keyboard?.hide(); scope.launch { drawer.open() } },
                            runtimeLabel = "离线界面样例",
                            modelLabel = modelDisplayName(selectedModel), onModel = { models = true }, effort = effort,
                        )
                        val choices = listOf("deepseek-v4-flash", "deepseek-v4-pro", "deepseek-flash").map {
                            SessionModelChoice(it, it, "deepseek", "DeepSeek", null, null, selectedModel == it, "离线排版样例")
                        }
                        ChatModelPanel(choices, false, null, true, false, effort, listOf("off", "low", "high", "max"),
                            false, thinking, section, { section = it }, {}, { models = false }, { selectedModel = it.id },
                            { effort = it }, { thinking = it }, { value, active -> preview.floatValue = value ?: Float.NaN; interacting.value = active }, models)
                        }
                    },
                    bottomBar = {
                        ChatComposer(input, { input = it }, running, input.isNotBlank(),
                            { input = ""; running = true; keyboard?.hide() }, { running = false }, { commands = true }, focus, {
                                android.widget.Toast.makeText(this@UiAuditActivity, "离线样例不读取用户文件", android.widget.Toast.LENGTH_SHORT).show()
                            }, {
                                android.widget.Toast.makeText(this@UiAuditActivity, "离线样例不读取图片", android.widget.Toast.LENGTH_SHORT).show()
                            }, stopping = stopping, onFocus = { focused = it })
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                        if (empty) ChatEmptyState(Modifier.fillMaxSize(), scene = stellarWelcomeScenes[welcomeIndex])
                        else LazyColumn(
                            Modifier.widthIn(max = 760.dp).fillMaxSize(),
                            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(chatPresentation(auditMessages), key = { it.key }) { row ->
                                when (row) {
                                    is ChatRow.Message -> ChatMessage(row.item, {}, { text -> input = text }, thinking)
                                    is ChatRow.Execution -> ExecutionBlock(row.events, thinking)
                                }
                            }
                        }
                    }
                }
                }
                }
                if (commands) CommandPaletteSheet(emptyList(), null, {}, { commands = false }) {
                    input = it
                    commands = false
                }
                if (workspace && workspaceVm != null) WorkspaceSheet(workspaceVm, workspaceTab) { workspace = false }
            }
        }
    }
}

private val auditMessages = listOf(
    ChatItem.User("请审查权限链路，并列出验证结果。"),
    ChatItem.Thinking("| 项目 | 状态 |\n| --- | --- |\n| 思考表格 | 可展开 |\n\n" + (1..24).joinToString("\n\n") {
        "第 $it 项：核对授权状态、命令返回值与会话恢复结果，保留完整验证依据。"
    }, false),
    ChatItem.Assistant("""
        ## 权限链路审查

        **Root 与 Shizuku** 已完成基本连通性验证。运行 `id` 可检查当前身份，结果应以实际输出为准。

        | 能力 | 结果 | 下一步 |
        | :--- | :---: | ---: |
        | Root 命令 | 通过 | 核对 uid |
        | Shizuku 服务 | 已授权 | 重启后复测 |
        | 会话恢复与后台连接 | 需验证 | 保留事件序列 |

        ### 回归步骤

        3. 打开权限面板，确认状态。
        4. 执行只读命令：
           - 核对退出码和完整输出。
           - 检查 **失败时的 _错误反馈_**。

        > 所有结论都应对应可复现的证据。

        > | 引用表格 | 状态 |
        > | --- | --- |
        > | 嵌套布局 | 可读 |

        ~~~sh
        id
        printf '%s\n' 'permission check'
        ~~~

        参考 [Shizuku 文档](https://shizuku.rikka.app/)。
    """.trimIndent()),
    ChatItem.Assistant("已到达回复末尾。"),
)

package com.xuanji.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuanji.android.ui.chat.MarkdownBlock
import com.xuanji.android.ui.chat.MarkdownBlockContent
import com.xuanji.android.ui.chat.MarkdownParser
import com.xuanji.android.ui.theme.XuanjiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Offline documentation is available before model credentials or the runtime are configured. */
class ProjectDocumentationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { XuanjiTheme { DocumentationScreen(::finish) } }
    }

    companion object {
        fun open(context: Context) = context.startActivity(Intent(context, ProjectDocumentationActivity::class.java))
    }
}

private data class DocumentEntry(val path: String, val title: String, val subtitle: String = "")
private data class DocumentContent(val blocks: List<MarkdownBlock> = emptyList(), val plainBlocks: List<String> = emptyList(),
    val error: String? = null, val loading: Boolean = true)

private val projectDocuments = listOf(
    DocumentEntry("documentation/technical-architecture.md", "技术架构", "Android、天枢运行时与执行链路"),
    DocumentEntry("documentation/privacy.md", "隐私说明", "本机存储、外部服务与用户控制"),
    DocumentEntry("documentation/third-party-notices.md", "第三方来源与许可", "代码、运行环境、字体与素材"),
    DocumentEntry("documentation/open-source/INVENTORY.md", "完整组件索引", "名称、版本、来源与许可证据"),
    DocumentEntry("documentation/open-source/RELEASE_CHECKLIST.md", "开源发布核对", "已核实证据与待补事项"),
    DocumentEntry("documentation/LICENSE", "Apache License 2.0", "璇玑原创代码的许可证原文"),
    DocumentEntry("documentation/NOTICE", "NOTICE", "项目归属与第三方声明索引"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentationScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var path by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("项目文档") }
    var licenses by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    val sourcePath = path
    val content by produceState(DocumentContent(), sourcePath, retry) {
        value = DocumentContent()
        if (sourcePath != null) value = try {
            val text = withContext(Dispatchers.IO) { context.assets.open(sourcePath).bufferedReader().use { it.readText() } }
            if (sourcePath.endsWith(".md")) DocumentContent(
                blocks = withContext(Dispatchers.Default) { MarkdownParser.parse(text) }, loading = false)
            else DocumentContent(plainBlocks = withContext(Dispatchers.Default) {
                text.lineSequence().chunked(32).map { it.joinToString("\n") }.toList()
            }, loading = false)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { DocumentContent(error = "文档暂时无法读取：${error.message}", loading = false) }
    }
    val licenseEntries by produceState<List<DocumentEntry>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            fun collect(directory: String): List<DocumentEntry> = context.assets.list(directory).orEmpty().flatMap { name ->
                val file = "$directory/$name"
                if (context.assets.list(file).orEmpty().isNotEmpty()) collect(file)
                else listOf(DocumentEntry(file,
                    if (directory == "licenses") name.substringBeforeLast('.') else "${directory.substringAfterLast('/')} / $name",
                    directory.removePrefix("documentation/open-source/license-texts/")))
            }
            (collect("licenses") + collect("documentation/open-source/license-texts")).sortedBy { "${it.subtitle}/${it.title}".lowercase() }
        }
    }
    fun back() {
        when {
            path != null -> { path = null; title = if (licenses) "许可证原文" else "项目文档" }
            licenses -> { licenses = false; query = ""; title = "项目文档" }
            else -> onFinish()
        }
    }
    BackHandler(onBack = ::back)
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
            IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }) }) { padding ->
        if (sourcePath != null) {
            key(sourcePath) {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (content.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        content.error?.let { message -> item {
                            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { retry++ }) { Text("重新读取") }
                        } }
                        items(content.blocks) { block -> MarkdownBlockContent(block) }
                        items(content.plainBlocks) {
                            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (licenses) OutlinedTextField(query, { query = it }, label = { Text("查找许可证") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                    val entries = if (licenses) licenseEntries.filter {
                        query.isBlank() || "${it.title} ${it.subtitle}".contains(query, ignoreCase = true)
                    } else projectDocuments
                    items(entries, key = { it.path }) { document ->
                        ListItem(headlineContent = { Text(document.title) }, supportingContent = {
                            if (document.subtitle.isNotBlank()) Text(document.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }, leadingContent = { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable(role = Role.Button) { path = document.path; title = document.title })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (!licenses) item {
                        ListItem(headlineContent = { Text("许可证原文") }, supportingContent = { Text("保留原作者版权声明") },
                            leadingContent = { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.secondary) },
                            modifier = Modifier.clickable(role = Role.Button) { licenses = true; title = "许可证原文" })
                    }
                    if (licenses && entries.isEmpty()) item {
                        Text("没有匹配的许可证", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

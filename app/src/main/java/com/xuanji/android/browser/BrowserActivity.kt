package com.xuanji.android.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuanji.android.TianshuService
import com.xuanji.android.ui.chat.ChatIconButton
import com.xuanji.android.ui.chat.ApprovalDetails
import com.xuanji.android.ui.theme.XuanjiTheme

internal class BrowserActivity : ComponentActivity() {
    companion object {
        fun show(context: Context) {
            context.startActivity(Intent(context, BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        fun open(context: Context, url: String) {
            AndroidBrowser.get(context).open(url)
            show(context)
        }
    }
    private val browser by lazy { AndroidBrowser.get(this) }
    private var pendingFile: ValueCallback<Array<Uri>>? = null
    private var multipleFiles = false
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingFile?.onReceiveValue(uris.take(if (multipleFiles) uris.size else 1).toTypedArray().takeIf { it.isNotEmpty() })
        pendingFile = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { XuanjiTheme { BrowserScreen(browser, ::finish) } }
    }
    override fun onResume() {
        super.onResume()
        browser.attach(this)
        browser.fileChooser = { callback, params ->
            pendingFile?.onReceiveValue(null)
            pendingFile = callback
            multipleFiles = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
            val types = params.acceptTypes.flatMap { it.split(',') }.map { it.trim() }.filter { it.contains('/') }.ifEmpty { listOf("*/*") }
            runCatching { filePicker.launch(types.toTypedArray()) }.onFailure { callback.onReceiveValue(null); pendingFile = null }
        }
    }
    override fun onPause() { browser.detach(this); super.onPause() }
    override fun onDestroy() { pendingFile?.onReceiveValue(null); pendingFile = null; browser.detach(this); super.onDestroy() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(browser: AndroidBrowser, onClose: () -> Unit) {
    val state by browser.state.collectAsStateWithLifecycle()
    val approval by TianshuService.androidActionApproval.pending.collectAsStateWithLifecycle()
    val tab = state.active
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var address by remember(tab?.url) { mutableStateOf(tab?.url.orEmpty()) }
    var tabsVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val navigate = {
        runCatching {
            if (tab == null) browser.open(address) else browser.navigate(tab.id, address)
            keyboard?.hide()
        }.onFailure { error = it.message }
        Unit
    }
    BackHandler {
        if (tab?.back == true) browser.back(tab.id) else onClose()
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatIconButton("返回对话", Icons.Default.Close, onClose)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(tab?.title ?: "浏览器", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${state.tabs.size} 个标签页", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ChatIconButton("标签页", Icons.Default.Tab, { tabsVisible = true })
        }
        OutlinedTextField(address, { address = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("输入网页地址") }, singleLine = true, shape = RoundedCornerShape(8.dp),
            leadingIcon = { Icon(if (tab?.url?.startsWith("https://") == true) Icons.Default.Lock else Icons.Default.Language, null, Modifier.size(18.dp)) },
            trailingIcon = { ChatIconButton("打开地址", Icons.AutoMirrored.Filled.ArrowForward, navigate, address.isNotBlank()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { navigate() }))
        Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.Center) {
            if (tab?.loading == true) LinearProgressIndicator(progress = { tab.progress / 100f }, Modifier.fillMaxWidth().height(2.dp))
        }
        if (tab == null) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.TravelExplore, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("向外探索", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { browser.open("https://tianshuharness.com/") }) { Text("天枢 · 官网") }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth()) {
            key(tab.id) {
                AndroidView(factory = { ctx -> FrameLayout(ctx).apply {
                    val web = browser.view(tab.id)
                    (web.parent as? ViewGroup)?.removeView(web)
                    addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                } }, onRelease = { it.removeAllViews() }, modifier = Modifier.fillMaxSize())
            }
            tab.error?.let { message ->
                Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        ChatIconButton("重新加载", Icons.Default.Refresh, { browser.reload(tab.id) })
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ChatIconButton("后退", Icons.AutoMirrored.Filled.ArrowBack, { tab?.let { browser.back(it.id) } }, tab?.back == true)
            ChatIconButton("前进", Icons.AutoMirrored.Filled.ArrowForward, { tab?.let { browser.forward(it.id) } }, tab?.forward == true)
            ChatIconButton(if (tab?.loading == true) "停止加载" else "重新加载", if (tab?.loading == true) Icons.Default.Stop else Icons.Default.Refresh,
                { tab?.let { if (it.loading) browser.stop(it.id) else browser.reload(it.id) } }, tab != null)
            ChatIconButton("新标签页", Icons.Default.Add, { tabsVisible = true })
            ChatIconButton("在外部浏览器打开", Icons.AutoMirrored.Filled.OpenInNew, {
                tab?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it.url))) }.onFailure { error = "没有可用的外部浏览器" } }
            }, tab != null)
        }
    }
    if (tabsVisible) ModalBottomSheet(onDismissRequest = { tabsVisible = false }, containerColor = MaterialTheme.colorScheme.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("标签页", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                ChatIconButton("关闭标签页面板", Icons.Default.Close, { tabsVisible = false })
            }
            LazyColumn(Modifier.heightIn(max = 420.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(state.tabs, key = { it.id }) { entry ->
                    Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { browser.select(entry.id); tabsVisible = false },
                        shape = RoundedCornerShape(8.dp), color = if (entry.id == state.activeId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            ChatIconButton("关闭 ${entry.title}", Icons.Default.Close, { browser.close(entry.id) })
                        }
                    }
                }
            }
            TextButton(onClick = { runCatching { browser.open("https://tianshuharness.com/"); tabsVisible = false }.onFailure { error = it.message } },
                modifier = Modifier.padding(horizontal = 16.dp), enabled = state.tabs.size < 8) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("新标签页")
            }
        }
    }
    approval?.let { request ->
        AlertDialog(onDismissRequest = { TianshuService.androidActionApproval.respond(request.id, false) },
            icon = { Icon(Icons.Default.Security, null) }, title = { Text("允许天枢${request.title}？") },
            text = { ApprovalDetails(request.detail, null) },
            confirmButton = { TextButton(onClick = { TianshuService.androidActionApproval.respond(request.id, true) }) { Text("仅本次允许") } },
            dismissButton = { TextButton(onClick = { TianshuService.androidActionApproval.respond(request.id, false) }) { Text("拒绝") } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("操作未完成") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } }) }
}

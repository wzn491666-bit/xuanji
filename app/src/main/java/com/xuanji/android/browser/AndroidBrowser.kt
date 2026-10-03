package com.xuanji.android.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Looper
import android.view.ViewGroup
import android.webkit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.coroutines.resume

/** All WebView and tab mutations are confined to Android's main thread. */
internal class AndroidBrowser private constructor(context: Context) {
    companion object {
        @Volatile private var instance: AndroidBrowser? = null
        fun get(context: Context): AndroidBrowser = instance ?: synchronized(this) {
            instance ?: AndroidBrowser(context.applicationContext).also { instance = it }
        }
    }

    private val app = context.applicationContext
    private val views = linkedMapOf<String, WebView>()
    private val snapshots = mutableMapOf<String, Pair<String, Long>>()
    val state = MutableStateFlow(BrowserState())
    var visible = false
        private set
    private var host: Context? = null
    var fileChooser: ((ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Unit)? = null
    private val snapshotScript by lazy { app.assets.open("browser/snapshot.js").bufferedReader().use { it.readText() } }
    private val actionScript by lazy { app.assets.open("browser/action.js").bufferedReader().use { it.readText() } }

    fun attach(context: Context) {
        mainThread()
        host = context
        visible = true
        views.forEach { (id, view) ->
            (view.context as MutableContextWrapper).baseContext = context
            if (id == state.value.activeId) view.onResume() else view.onPause()
        }
    }

    fun detach(context: Context) {
        mainThread()
        if (host !== context) return
        host = null
        visible = false
        fileChooser = null
        views.values.forEach { (it.context as MutableContextWrapper).baseContext = app; it.onPause() }
    }

    fun view(id: String): WebView = views[id] ?: error("标签页已关闭")

    @SuppressLint("SetJavaScriptEnabled")
    fun open(input: String): String {
        mainThread()
        val url = browserUrl(input)
        check(views.size < 8) { "最多保留 8 个标签页，请先关闭不需要的页面" }
        val id = UUID.randomUUID().toString()
        val view = WebView(MutableContextWrapper(host ?: app))
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            safeBrowsingEnabled = true
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                runCatching { browserUrl(request.url.toString()) }.isFailure
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                snapshots.remove(id)
                update(id) { it.copy(url = url, loading = true, progress = 0, error = null, generation = it.generation + 1) }
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (view.url == url) update(id) { it.copy(url = url, loading = false, progress = 100, back = view.canGoBack(), forward = view.canGoForward()) }
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (url == null) return
                if (state.value.tabs.firstOrNull { it.id == id }?.url != url) {
                    snapshots.remove(id)
                    update(id) { it.copy(url = url, generation = it.generation + 1) }
                }
                update(id) { it.copy(back = view.canGoBack(), forward = view.canGoForward()) }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) update(id) { it.copy(loading = false, error = "网页加载失败（${error.errorCode}）") }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) update(id) { it.copy(error = "网页返回 HTTP ${response.statusCode}") }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                update(id) { it.copy(loading = false, error = "证书验证失败，已停止加载") }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                close(id)
                return true
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) { update(id) { it.copy(progress = progress) } }
            override fun onReceivedTitle(view: WebView, title: String?) { update(id) { it.copy(title = title?.take(200).orEmpty().ifBlank { "网页" }) } }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                val picker = fileChooser
                if (visible && picker != null) picker(callback, params) else callback.onReceiveValue(null)
                return true
            }
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean = dialog(url, message, result, false)
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean = dialog(url, message, result, true)
        }
        views[id] = view
        state.value.activeId?.let { views[it]?.onPause() }
        state.value = state.value.copy(tabs = state.value.tabs + BrowserTab(id, url, loading = true), activeId = id)
        if (visible) view.onResume() else view.onPause()
        view.loadUrl(url)
        return id
    }

    fun select(id: String) {
        mainThread()
        val next = view(id)
        state.value.activeId?.let { views[it]?.onPause() }
        state.value = state.value.copy(activeId = id)
        if (visible) next.onResume()
    }
    fun navigate(id: String, input: String) {
        mainThread()
        val url = browserUrl(input)
        snapshots.remove(id)
        update(id) { it.copy(url = url, loading = true, error = null) }
        view(id).loadUrl(url)
    }
    fun back(id: String) { mainThread(); if (view(id).canGoBack()) { snapshots.remove(id); view(id).goBack() } }
    fun forward(id: String) { mainThread(); if (view(id).canGoForward()) { snapshots.remove(id); view(id).goForward() } }
    fun reload(id: String) { mainThread(); snapshots.remove(id); view(id).reload() }
    fun stop(id: String) { mainThread(); view(id).stopLoading(); update(id) { it.copy(loading = false) } }

    fun close(id: String) {
        mainThread()
        val web = views.remove(id) ?: return
        snapshots.remove(id)
        (web.parent as? ViewGroup)?.removeView(web)
        web.stopLoading()
        web.destroy()
        val tabs = state.value.tabs.filterNot { it.id == id }
        state.value = BrowserState(tabs, if (state.value.activeId == id) tabs.lastOrNull()?.id else state.value.activeId)
        if (visible) state.value.activeId?.let { views[it]?.onResume() }
    }

    suspend fun snapshot(id: String, options: JsonObject = JsonObject(emptyMap())): JsonObject = withContext(Dispatchers.Main.immediate) {
        val readOptions = browserSnapshotOptions(options)
        check(withTimeoutOrNull(15_000) { state.first { current -> current.tabs.none { it.id == id && it.loading } } } != null) {
            "网页仍在加载，请稍后重新读页或停止加载"
        }
        val tab = state.value.tabs.firstOrNull { it.id == id } ?: error("标签页已关闭")
        check(tab.url.isNotBlank() && tab.error == null) { tab.error ?: "网页尚未加载" }
        val token = UUID.randomUUID().toString()
        val result = evaluate(view(id), "($snapshotScript)(${JsonPrimitive(token)}, $readOptions)")
        check(result["url"]?.jsonPrimitive?.contentOrNull?.startsWith("http") == true) { "网页尚未就绪，请稍后重新读页" }
        check(state.value.tabs.firstOrNull { it.id == id }?.generation == tab.generation) { "读页期间发生导航，请重试" }
        snapshots[id] = token to tab.generation
        buildJsonObject { result.forEach { (key, value) -> put(key, value) }; put("tabId", id) }
    }

    fun assertTarget(id: String, snapshotId: String) {
        mainThread()
        check(visible && state.value.activeId == id) { "请先打开浏览器并切换到目标标签页" }
        val generation = state.value.active?.generation
        check(snapshots[id] == (snapshotId to generation)) { "页面快照已过期，请重新读页" }
    }

    suspend fun action(id: String, args: JsonObject): JsonObject = withContext(Dispatchers.Main.immediate) {
        assertTarget(id, args["snapshotId"]!!.jsonPrimitive.content)
        try { evaluate(view(id), "($actionScript)($args)") }
        finally { snapshots.remove(id) }
    }

    data class ControlTarget(val tabId: String, val url: String, val generation: Long, val activeId: String?)

    fun captureControlTarget(request: BrowserControl): ControlTarget {
        mainThread()
        check(visible) { "请先打开内置浏览器" }
        val tab = state.value.tabs.firstOrNull { it.id == request.tabId } ?: error("标签页已关闭")
        check(request.action in setOf("activate", "close") || state.value.activeId == request.tabId) { "请先切换到目标标签页" }
        return ControlTarget(tab.id, tab.url, tab.generation, state.value.activeId)
    }

    fun control(request: BrowserControl, expected: ControlTarget): JsonObject {
        mainThread()
        check(captureControlTarget(request) == expected) { "审批期间浏览器状态发生变化，请重新检查标签页" }
        val web = view(request.tabId)
        check(web.url == null || web.url == expected.url) { "网页已导航，请重新检查标签页" }
        when (request.action) {
            "navigate" -> navigate(request.tabId, request.url!!)
            "back" -> { check(web.canGoBack()) { "当前没有可后退的页面" }; back(request.tabId) }
            "forward" -> { check(web.canGoForward()) { "当前没有可前进的页面" }; forward(request.tabId) }
            "reload" -> reload(request.tabId)
            "stop" -> stop(request.tabId)
            "activate" -> select(request.tabId)
            "close" -> close(request.tabId)
            else -> error("未知浏览器控制操作")
        }
        snapshots.remove(request.tabId)
        return buildJsonObject {
            put("dispatched", true); put("action", request.action); put("tabId", request.tabId); put("needsSnapshot", true)
            put("navigationPending", request.action in setOf("navigate", "back", "forward", "reload"))
            put("state", stateSnapshot())
        }
    }

    fun stateSnapshot(): JsonObject {
        mainThread()
        return buildJsonObject {
            state.value.activeId?.let { put("activeTabId", it) }
            put("visible", visible); put("maxTabs", 8); put("remainingTabs", 8 - state.value.tabs.size)
            put("tabs", buildJsonArray { state.value.tabs.forEach { tab -> add(buildJsonObject {
                put("tabId", tab.id); put("url", tab.url); put("title", tab.title); put("loading", tab.loading)
                put("progress", tab.progress); put("canGoBack", tab.back); put("canGoForward", tab.forward)
                put("generation", tab.generation); tab.error?.let { put("error", it) }
            }) } })
        }
    }

    private suspend fun evaluate(view: WebView, expression: String): JsonObject = withTimeout(8_000) {
        suspendCancellableCoroutine<Result<JsonObject>> { continuation ->
            val guarded = "(function(){try{return {ok:true,value:$expression};}catch(e){return {ok:false,error:String(e.message).slice(0,200)};}})()"
            view.evaluateJavascript(guarded) { raw ->
                if (continuation.isActive) continuation.resume(runCatching {
                    val result = Json.parseToJsonElement(raw).jsonObject
                    check(result["ok"]?.jsonPrimitive?.booleanOrNull == true) { result["error"]?.jsonPrimitive?.contentOrNull ?: "网页脚本没有返回结果" }
                    result["value"]!!.jsonObject
                })
            }
        }.getOrThrow()
    }

    private fun update(id: String, transform: (BrowserTab) -> BrowserTab) {
        state.value = state.value.copy(tabs = state.value.tabs.map { if (it.id == id) transform(it) else it })
    }
    private fun dialog(url: String, message: String, result: JsResult, confirm: Boolean): Boolean {
        val context = host
        if (!visible || context == null) { result.cancel(); return true }
        val builder = android.app.AlertDialog.Builder(context).setTitle(runCatching { java.net.URI(url).host }.getOrDefault("网页"))
            .setMessage(message.take(4000)).setPositiveButton("确定") { _, _ -> result.confirm() }.setOnCancelListener { result.cancel() }
        if (confirm) builder.setNegativeButton("取消") { _, _ -> result.cancel() }
        builder.show()
        return true
    }
    private fun mainThread() = check(Looper.myLooper() == Looper.getMainLooper()) { "WebView requires the main thread" }
}

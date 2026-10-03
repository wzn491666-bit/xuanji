package com.xuanji.android.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class WebSearchClient(
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(22, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
    private val endpoints: Map<SearchEngine, String> = emptyMap(),
) {
    private val slots = Semaphore(3)

    suspend fun search(query: String, settings: SearchSettings,
        credentials: Map<SearchEngine, SearchCredential> = emptyMap()): WebSearchReport = coroutineScope {
        require(query.isNotBlank() && query.length <= 2000) { "搜索词不能为空，且不得超过 2000 字符" }
        require(settings.engines.isNotEmpty()) { "请至少选择一个搜索引擎" }
        val priority = listOf(SearchEngine.TAVILY, SearchEngine.EXA, SearchEngine.ANYSEARCH, SearchEngine.FIRECRAWL,
            SearchEngine.KEENABLE, SearchEngine.BING, SearchEngine.DUCKDUCKGO, SearchEngine.SEARXNG)
        val selected = priority.filter { it in settings.engines }
        val secrets = credentials.values.map { it.apiKey }.filter(String::isNotBlank)
        fun redact(text: String): String = secrets.fold(text) { value, key -> value.replace(key, "[已隐藏]") }
        suspend fun run(engine: SearchEngine): EngineSearchResult = slots.withPermit {
            val started = System.nanoTime()
            val credential = credentials[engine] ?: SearchCredential(endpoint = if (engine == SearchEngine.SEARXNG) settings.searxngUrl else "")
            try {
                check(credential.error == null) { credential.error.orEmpty() }
                check(!engine.credentialRequired || credential.apiKey.isNotBlank()) { "尚未配置 ${engine.label} API Key" }
                val request = request(engine, query.trim(), settings.depth, credential)
                val (rawBody, type) = fetch(request)
                val body = redact(rawBody)
                val sources = if (engine in setOf(SearchEngine.EXA, SearchEngine.KEENABLE) && credential.apiKey.isBlank())
                    parseSearchMcpResponse(engine, body, type, settings.depth)
                    else parseSearchResponse(engine, body, type, settings.depth)
                EngineSearchResult(engine, sources, elapsedMs = (System.nanoTime() - started) / 1_000_000)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                var message = e.message ?: "请求失败"
                credentials.values.map { it.apiKey }.filter(String::isNotBlank).forEach { message = message.replace(it, "[已隐藏]") }
                EngineSearchResult(engine, error = message.take(180), elapsedMs = (System.nanoTime() - started) / 1_000_000)
            }
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(65)
        val reports = if (!settings.automatic) selected.map { async { run(it) } }.map { it.await() }
            else buildList {
                for (engine in selected) {
                    val remaining = (deadline - System.nanoTime()) / 1_000_000
                    if (remaining <= 0) break
                    add(withTimeoutOrNull(remaining) { run(engine) } ?: EngineSearchResult(engine, error = "自动搜索时间预算已耗尽"))
                    if (count { it.successful } >= settings.depth.autoSources) break
                }
            }
        val allSources = mergeSearchSources(reports)
        val sources = allSources.take(settings.depth.outputLimit).map { it.copy(title = redact(it.title), url = redact(it.url), snippet = redact(it.snippet)) }
        WebSearchReport(redact(query.trim()), settings.depth, settings.automatic, reports, sources, allSources.size > sources.size)
    }

    private fun request(engine: SearchEngine, query: String, depth: SearchDepth, credential: SearchCredential): Request {
        if (engine in setOf(SearchEngine.EXA, SearchEngine.KEENABLE) && credential.apiKey.isBlank()) {
            val endpoint = endpoints[engine] ?: if (engine == SearchEngine.EXA) "https://mcp.exa.ai/mcp" else "https://api.keenable.ai/mcp"
            val payload = buildJsonObject {
                put("jsonrpc", "2.0"); put("id", 1); put("method", "tools/call")
                putJsonObject("params") {
                    put("name", if (engine == SearchEngine.EXA) "web_search_exa" else "search_web_pages")
                    putJsonObject("arguments") {
                        put("query", query)
                        if (engine == SearchEngine.EXA) put("numResults", depth.perEngine)
                    }
                }
            }
            return Request.Builder().url(endpoint).header("Accept", "application/json, text/event-stream")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        }
        val endpoint = endpoints[engine] ?: when (engine) {
            SearchEngine.TAVILY -> "https://api.tavily.com/search"
            SearchEngine.FIRECRAWL -> "https://api.firecrawl.dev/v2/search"
            SearchEngine.EXA -> "https://api.exa.ai/search"
            SearchEngine.ANYSEARCH -> "https://api.anysearch.com/v1/search"
            SearchEngine.KEENABLE -> "https://api.keenable.ai/v1/search"
            SearchEngine.DUCKDUCKGO -> "https://html.duckduckgo.com/html/"
            SearchEngine.BING -> "https://www.bing.com/search"
            SearchEngine.SEARXNG -> {
                check(credential.endpoint.isNotBlank()) { "请配置 SearXNG 实例地址" }
                val base = credential.endpoint.toHttpUrl()
                check(base.username.isEmpty() && base.password.isEmpty()) { "实例地址不得包含凭据" }
                base.newBuilder().addPathSegment("search").build().toString()
            }
        }
        val url = endpoint.toHttpUrl()
        check(credential.apiKey.isBlank() || url.isHttps || endpoints.containsKey(engine)) { "带密钥的请求必须使用 HTTPS" }
        val builder = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) Xuanji/0.2")
        if (engine in setOf(SearchEngine.BING, SearchEngine.DUCKDUCKGO, SearchEngine.SEARXNG)) {
            val target = url.newBuilder().addQueryParameter("q", query)
            when (engine) {
                SearchEngine.BING -> target.addQueryParameter("format", "rss").addQueryParameter("count", depth.perEngine.toString())
                SearchEngine.SEARXNG -> target.addQueryParameter("format", "json").addQueryParameter("categories", "general")
                else -> Unit
            }
            return builder.url(target.build()).get().build()
        }
        val body = buildJsonObject {
            put("query", query)
            when (engine) {
                SearchEngine.TAVILY -> { put("max_results", depth.perEngine); put("search_depth", if (depth == SearchDepth.HIGH) "advanced" else "basic") }
                SearchEngine.FIRECRAWL -> { put("limit", depth.perEngine); if (depth == SearchDepth.HIGH) putJsonObject("scrapeOptions") { put("formats", buildJsonArray { add(JsonPrimitive("markdown")) }) } }
                SearchEngine.EXA -> { put("numResults", depth.perEngine); put("type", "auto"); putJsonObject("contents") { putJsonObject("text") { put("maxCharacters", depth.snippetLimit) } } }
                SearchEngine.ANYSEARCH -> put("max_results", depth.perEngine)
                SearchEngine.KEENABLE -> put("mode", "realtime")
                else -> Unit
            }
        }
        builder.header("Accept", "application/json")
        if (credential.apiKey.isNotBlank()) builder.header(if (engine in setOf(SearchEngine.EXA, SearchEngine.KEENABLE)) "x-api-key" else "Authorization",
            if (engine in setOf(SearchEngine.EXA, SearchEngine.KEENABLE)) credential.apiKey else "Bearer ${credential.apiKey}")
        else if (engine == SearchEngine.TAVILY) builder.header("x-tavily-access-mode", "keyless")
        return builder.post(body.toString().toRequestBody("application/json".toMediaType())).build()
    }

    private suspend fun fetch(request: Request): Pair<String, String> = suspendCancellableCoroutine { continuation ->
        val transport = if (request.method == "GET") http.newBuilder().followRedirects(true).followSslRedirects(false).build() else http
        val call = transport.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        check(it.isSuccessful && it.code != 202) { "搜索服务返回 HTTP ${it.code}，可能限流或需要授权" }
                        val bytes = it.body.byteStream().use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                check(output.size() + count <= 1024 * 1024) { "搜索响应超过 1 MiB" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        String(bytes, Charsets.UTF_8) to it.header("Content-Type").orEmpty()
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }
}

internal fun parseSearchMcpResponse(engine: SearchEngine, body: String, type: String, depth: SearchDepth): List<SearchSource> {
    val messages = if (type.contains("text/event-stream", true)) body.replace("\r\n", "\n").split("\n\n").mapNotNull { event ->
        event.lines().filter { it.startsWith("data:") }.joinToString("\n") { it.removePrefix("data:").trimStart() }
            .takeIf { it.isNotBlank() && it != "[DONE]" }?.let { Json.parseToJsonElement(it).jsonObject }
    } else listOf(Json.parseToJsonElement(body).jsonObject)
    val response = messages.firstOrNull { it["id"]?.jsonPrimitive?.intOrNull == 1 } ?: error("${engine.label} MCP 缺少对应请求的响应")
    check(response["error"] == null) { "${engine.label} MCP 返回协议错误" }
    val result = response["result"] as? JsonObject ?: error("${engine.label} MCP 缺少结果")
    check(result["isError"]?.jsonPrimitive?.booleanOrNull != true) { "${engine.label} MCP 搜索失败" }
    val content = result["content"] as? JsonArray ?: error("${engine.label} MCP 缺少内容")
    val text = content.mapNotNull { (it as? JsonObject)?.takeIf { block -> block["type"]?.jsonPrimitive?.contentOrNull == "text" }
        ?.get("text")?.jsonPrimitive?.contentOrNull }.joinToString("\n")
    return text.split(Regex("\\n(?=Title:)" )).mapNotNull { block ->
        fun field(name: String) = block.lineSequence().firstOrNull { it.startsWith("$name:") }?.substringAfter(':')?.trim()
        val url = field("URL")?.let(::canonicalSearchUrl) ?: return@mapNotNull null
        val marker = if (engine == SearchEngine.EXA) "Highlights:" else "Snippets:"
        val snippet = block.substringAfter(marker, "").trim().take(depth.snippetLimit)
        SearchSource(field("Title").orEmpty().ifBlank { url }.take(200), url, snippet, setOf(engine),
            field("Published")?.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}.*").matches(it) })
    }.take(depth.perEngine)
}

internal fun parseSearchResponse(engine: SearchEngine, body: String, contentType: String, depth: SearchDepth): List<SearchSource> {
    fun source(title: String, url: String, text: String, date: String? = null): SearchSource? =
        canonicalSearchUrl(url)?.let { SearchSource(title.ifBlank { it }.take(200), it, text.take(depth.snippetLimit), setOf(engine), date) }
    if (engine == SearchEngine.BING) {
        val doc = Jsoup.parse(body, "", Parser.xmlParser())
        check(doc.selectFirst("rss") != null) { "Bing 未返回 RSS 搜索结果，可能遇到验证页" }
        return doc.select("item").mapNotNull { source(it.selectFirst("title")?.text().orEmpty(), it.selectFirst("link")?.text().orEmpty(), it.selectFirst("description")?.text().orEmpty()) }.take(depth.perEngine)
    }
    if (engine == SearchEngine.DUCKDUCKGO) {
        val doc = Jsoup.parse(body, "https://html.duckduckgo.com/")
        check(doc.select("#challenge-form, .anomaly-modal, form[action*=anomaly]").isEmpty()) { "DuckDuckGo 需要验证码，请切换引擎或稍后重试" }
        check(doc.select(".result, .no-results, .no-results__message").isNotEmpty()) { "DuckDuckGo 未返回可识别的结果页面" }
        return doc.select(".result").mapNotNull { row ->
            val link = row.selectFirst(".result__a") ?: return@mapNotNull null
            val target = link.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            source(link.text(), target.queryParameter("uddg") ?: target.toString(), row.selectFirst(".result__snippet")?.text().orEmpty())
        }.take(depth.perEngine)
    }
    check(!contentType.contains("text/html", true)) { "搜索服务返回 HTML 而非 JSON，可能需要验证" }
    val obj = Json.parseToJsonElement(body).jsonObject
    val entries = when (engine) {
        SearchEngine.ANYSEARCH -> {
            check(obj["code"]?.jsonPrimitive?.intOrNull == 0) { "AnySearch 返回业务错误，搜索未完成" }
            obj["data"]?.jsonObject?.get("results") as? JsonArray
        }
        SearchEngine.FIRECRAWL -> {
            check(obj["success"]?.jsonPrimitive?.booleanOrNull == true) { "Firecrawl 未确认搜索成功" }
            val data = obj["data"]
            if (data is JsonArray) data else (data as? JsonObject)?.get("web") as? JsonArray
        }
        else -> obj["results"] as? JsonArray
    } ?: error("${engine.label} 响应缺少结果列表")
    return entries.mapNotNull { item ->
        val entry = item.jsonObject
        fun str(key: String) = entry[key]?.jsonPrimitive?.contentOrNull
        source(str("title").orEmpty(), str("url").orEmpty(),
            str("markdown") ?: str("content") ?: str("text") ?: str("snippet") ?: str("description").orEmpty(),
            str("publishedDate") ?: str("published_at") ?: str("published_date"))
    }.take(depth.perEngine)
}

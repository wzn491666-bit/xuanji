package com.xuanji.android.search

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class SearchEngine(val wire: String, val label: String, val credentialRequired: Boolean = false) {
    TAVILY("tavily", "Tavily"), BING("bing", "Bing"), FIRECRAWL("firecrawl", "Firecrawl"),
    EXA("exa", "Exa"), DUCKDUCKGO("duckduckgo", "DuckDuckGo"), SEARXNG("searxng", "SearXNG"),
    ANYSEARCH("anysearch", "AnySearch"), KEENABLE("keenable", "Keenable"),
}

enum class SearchDepth(val wire: String, val label: String, val perEngine: Int, val outputLimit: Int,
    val snippetLimit: Int, val autoSources: Int) {
    LOW("low", "轻量", 3, 8, 350, 1), MEDIUM("medium", "均衡", 6, 16, 650, 2), HIGH("high", "深入", 10, 24, 1000, 3),
}

data class SearchSettings(val automatic: Boolean = true,
    val engines: Set<SearchEngine> = setOf(SearchEngine.ANYSEARCH, SearchEngine.BING, SearchEngine.DUCKDUCKGO),
    val depth: SearchDepth = SearchDepth.MEDIUM, val searxngUrl: String = "")

class SearchCredential(val apiKey: String = "", val endpoint: String = "", val error: String? = null) {
    override fun toString() = "SearchCredential(configured=${apiKey.isNotBlank()}, endpointConfigured=${endpoint.isNotBlank()})"
}

data class SearchSource(val title: String, val url: String, val snippet: String,
    val engines: Set<SearchEngine>, val publishedAt: String? = null)
data class EngineSearchResult(val engine: SearchEngine, val sources: List<SearchSource> = emptyList(),
    val error: String? = null, val elapsedMs: Long = 0) {
    val successful: Boolean get() = error == null && sources.isNotEmpty()
}

data class WebSearchReport(val query: String, val depth: SearchDepth, val automatic: Boolean,
    val engines: List<EngineSearchResult>, val sources: List<SearchSource>, val truncated: Boolean) {
    fun toJson(): JsonObject = buildJsonObject {
        put("query", query); put("depth", depth.wire); put("automatic", automatic)
        put("truncated", truncated)
        put("untrustedContent", true)
        put("engines", buildJsonArray { engines.forEach { result -> add(buildJsonObject {
            put("engine", result.engine.wire); put("status", if (result.error != null) "failed" else if (result.sources.isEmpty()) "empty" else "ok")
            put("count", result.sources.size); put("elapsedMs", result.elapsedMs)
            result.error?.let { put("error", it) }
        }) } })
        put("sources", buildJsonArray { sources.forEach { source -> add(buildJsonObject {
            put("title", source.title); put("url", source.url); put("snippet", source.snippet)
            put("engines", buildJsonArray { source.engines.forEach { add(JsonPrimitive(it.wire)) } })
            source.publishedAt?.let { put("publishedAt", it) }
        }) } })
    }
}

internal fun canonicalSearchUrl(value: String): String? {
    val url = value.toHttpUrlOrNull() ?: return null
    if (url.username.isNotBlank() || url.password.isNotBlank()) return null
    val builder = url.newBuilder().fragment(null)
    url.queryParameterNames.filter { it.startsWith("utm_", true) || it in setOf("fbclid", "gclid") }
        .forEach(builder::removeAllQueryParameters)
    return builder.build().toString()
}

internal fun mergeSearchSources(results: List<EngineSearchResult>): List<SearchSource> {
    val sources = linkedMapOf<String, SearchSource>()
    val scores = mutableMapOf<String, Double>()
    results.filter { it.error == null }.forEach { result ->
        val seen = mutableSetOf<String>()
        result.sources.forEachIndexed { rank, source ->
            val key = canonicalSearchUrl(source.url) ?: return@forEachIndexed
            if (!seen.add(key)) return@forEachIndexed
            val previous = sources[key]
            sources[key] = if (previous == null) source.copy(url = key, engines = setOf(result.engine))
                else previous.copy(engines = previous.engines + result.engine,
                    snippet = if (source.snippet.length > previous.snippet.length) source.snippet else previous.snippet,
                    publishedAt = previous.publishedAt ?: source.publishedAt)
            scores[key] = (scores[key] ?: 0.0) + 1.0 / (61 + rank)
        }
    }
    return sources.entries.sortedByDescending { scores[it.key] }.map { it.value }
}

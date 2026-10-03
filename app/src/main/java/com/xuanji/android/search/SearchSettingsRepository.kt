package com.xuanji.android.search

import android.content.Context
import com.xuanji.android.SecureSettings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class SearchSettingsRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("web_search", Context.MODE_PRIVATE)

    fun load(): SearchSettings {
        val defaults = SearchSettings()
        val enabled = prefs.getStringSet("engines", null)?.mapNotNull { wire -> SearchEngine.entries.firstOrNull { it.wire == wire } }?.toSet()
        return SearchSettings(prefs.getBoolean("automatic", true), enabled ?: defaults.engines,
            SearchDepth.entries.firstOrNull { it.wire == prefs.getString("depth", null) } ?: defaults.depth,
            prefs.getString("searxngUrl", "").orEmpty())
    }

    fun save(settings: SearchSettings) {
        require(settings.engines.isNotEmpty()) { "至少保留一个搜索引擎" }
        if (settings.searxngUrl.isNotBlank()) {
            val url = settings.searxngUrl.toHttpUrlOrNull() ?: error("SearXNG 需要 http 或 https 实例地址")
            require(url.username.isBlank() && url.password.isBlank() && url.query == null && url.fragment == null) { "实例地址不得包含凭据、查询或片段" }
        }
        check(prefs.edit().putBoolean("automatic", settings.automatic).putStringSet("engines", settings.engines.map { it.wire }.toSet())
            .putString("depth", settings.depth.wire).putString("searxngUrl", settings.searxngUrl.trim().trimEnd('/')).commit()) { "搜索配置保存失败" }
    }

    fun hasKey(engine: SearchEngine): Boolean = app.getSharedPreferences("search_secrets", Context.MODE_PRIVATE).contains("${engine.wire}Cipher")
    fun saveKey(engine: SearchEngine, value: String) = SecureSettings.saveSearchKey(app, engine.wire, value.trim())

    fun credentials(settings: SearchSettings): Map<SearchEngine, SearchCredential> = settings.engines.associateWith { engine ->
        try { SearchCredential(SecureSettings.loadSearchKey(app, engine.wire), if (engine == SearchEngine.SEARXNG) settings.searxngUrl else "") }
        catch (e: Exception) { SearchCredential(error = e.message ?: "搜索密钥无法读取") }
    }
}

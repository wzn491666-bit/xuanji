package com.xuanji.android.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.serialization.json.*

internal fun browserUrl(input: String): String {
    val value = input.trim()
    require(value.length in 1..8192) { "网页地址不能为空，且不得超过 8192 字符" }
    val candidate = if (value.contains("://")) value else "https://$value"
    val url = candidate.toHttpUrlOrNull() ?: error("仅支持 http 或 https 网页地址")
    require(url.username.isEmpty() && url.password.isEmpty()) { "网页地址不得包含用户名或密码" }
    return url.toString()
}

internal data class BrowserTab(val id: String, val url: String = "", val title: String = "新标签页",
    val progress: Int = 0, val loading: Boolean = false, val back: Boolean = false, val forward: Boolean = false,
    val error: String? = null, val generation: Long = 0)

internal data class BrowserState(val tabs: List<BrowserTab> = emptyList(), val activeId: String? = null) {
    val active: BrowserTab? get() = tabs.firstOrNull { it.id == activeId }
}

internal data class BrowserControl(val tabId: String, val action: String, val url: String? = null)

internal fun parseBrowserControl(args: JsonObject): BrowserControl {
    require(args.browserBoolean("confirm") == true) { "浏览器操作需要 confirm=true" }
    val id = args.requiredBrowserString("tabId", 128)
    val action = args.requiredBrowserString("action", 32)
    require(action in setOf("navigate", "back", "forward", "reload", "stop", "activate", "close")) { "未知浏览器控制操作" }
    require(action == "navigate" || !args.containsKey("url")) { "仅 navigate 操作接受 url" }
    return BrowserControl(id, action, if (action == "navigate") browserUrl(args.requiredBrowserString("url", 8192)) else null)
}

internal fun validateBrowserAction(args: JsonObject): String {
    require(args.browserBoolean("confirm") == true) { "网页操作需要 confirm=true" }
    args.requiredBrowserString("tabId", 128)
    args.requiredBrowserString("snapshotId", 128)
    val action = args.requiredBrowserString("action", 32)
    require(action in setOf("click", "type", "select", "check", "submit", "scroll")) { "未知网页操作" }
    val elementId = args.browserInt("elementId")
    require((action == "scroll" && !args.containsKey("elementId")) || (elementId != null && elementId in 1..100)) { "网页操作或元素编号无效" }
    when (action) {
        "type" -> args.requiredBrowserString("text", 16384, allowEmpty = true)
        "select" -> {
            args.requiredBrowserString("value", 512, allowEmpty = true)
            require(!args.containsKey("optionBy") || args["optionBy"]?.jsonPrimitive?.contentOrNull in setOf("value", "label")) { "optionBy 必须为 value 或 label" }
        }
        "check" -> require(args.browserBoolean("checked") != null) { "check 操作需要 checked 布尔值" }
        "scroll" -> {
            require(args["direction"]?.jsonPrimitive?.contentOrNull in setOf("up", "down", "left", "right", "top", "bottom")) { "滚动方向无效" }
            val amount = args.browserInt("amount")
            require(!args.containsKey("amount") || (amount != null && amount in 1..10000)) { "滚动距离必须为 1 到 10000 像素" }
        }
    }
    return action
}

internal fun browserSnapshotOptions(args: JsonObject): JsonObject = buildJsonObject {
    val offset = if (args.containsKey("textOffset")) args.browserInt("textOffset") else 0
    val maxChars = if (args.containsKey("maxChars")) args.browserInt("maxChars") else 12000
    require(offset != null && offset in 0..500000 && maxChars != null && maxChars in 1000..24000) { "网页读取范围无效" }
    put("textOffset", offset); put("maxChars", maxChars)
    if (args.containsKey("viewportOnly")) {
        val viewport = args.browserBoolean("viewportOnly") ?: error("viewportOnly 必须是布尔值")
        put("viewportOnly", viewport)
    }
    if (args.containsKey("region")) put("region", args.requiredBrowserString("region", 512))
}

private fun JsonObject.requiredBrowserString(key: String, max: Int, allowEmpty: Boolean = false): String {
    val value = this[key] as? JsonPrimitive
    require(value?.isString == true) { "$key 必须是字符串" }
    val text = value.content
    require(text.length <= max && (allowEmpty || text.isNotBlank())) { "$key 不能为空且不得超过 $max 字符" }
    return text
}

private fun JsonObject.browserBoolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

private fun JsonObject.browserInt(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

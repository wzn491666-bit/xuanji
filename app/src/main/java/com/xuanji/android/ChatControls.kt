package com.xuanji.android

import kotlinx.serialization.json.*

enum class ChatApprovalMode(val wire: String, val label: String, val detail: String) {
    MANUAL("manual", "请求批准", "执行操作前由你确认"),
    SAFE("auto-safe", "帮我批准", "自动批准低风险操作"),
    FULL("dangerously-skip-permissions", "完全访问权限", "天枢工具不再请求执行审批"),
}

data class SessionModelChoice(
    val id: String, val alias: String, val provider: String, val providerLabel: String,
    val keyId: String?, val keyLabel: String?, val current: Boolean, val description: String,
) {
    val spec: String get() = listOfNotNull(provider, keyId?.takeIf(String::isNotBlank), id).joinToString(":")
}

internal fun parseSessionModels(response: JsonObject): List<SessionModelChoice> =
    response["models"]?.jsonArray.orEmpty().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val provider = obj["provider"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        SessionModelChoice(id, obj["alias"]?.jsonPrimitive?.contentOrNull ?: id, provider,
            obj["providerLabel"]?.jsonPrimitive?.contentOrNull ?: provider,
            obj["keyId"]?.jsonPrimitive?.contentOrNull, obj["keyLabel"]?.jsonPrimitive?.contentOrNull,
            obj["current"]?.jsonPrimitive?.booleanOrNull == true, obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }.distinctBy { it.spec }

internal fun activeProviderModel(catalog: List<ProviderProfile>, spec: String): Pair<ProviderProfile, ProviderModel>? {
    val qualified = catalog.flatMap { p -> p.models.map { p to it } }
    return qualified.firstOrNull { (p, m) -> spec.startsWith("${p.name}:") && spec.endsWith(":${m.id}") }
        ?: qualified.firstOrNull { (_, m) -> spec == m.id }
}

internal fun reasoningLevels(provider: String, supported: Boolean): List<String> = when {
    !supported -> emptyList()
    provider in setOf("deepseek", "deepseek-spark", "kimi") -> listOf("off", "low", "high", "max")
    provider == "siliconflow" -> listOf("high", "max")
    else -> listOf("low", "medium", "high", "max")
}

internal fun awaitingAssistantReply(items: List<ChatItem>, running: Boolean, stopping: Boolean): Boolean {
    if (stopping) return false
    val index = items.indexOfLast { it is ChatItem.User }
    if (index < 0) return false
    val user = items[index] as ChatItem.User
    if (user.delivery in setOf(PromptDelivery.REJECTED, PromptDelivery.UNCERTAIN)) return false
    for (i in index + 1 until items.size) {
        val item = items[i]
        if (item is ChatItem.Assistant && item.text.isNotBlank()) return false
    }
    return user.delivery == PromptDelivery.SENDING || running
}

data class SessionHistoryEntry(
    val id: String, val title: String, val status: String, val model: String, val updatedAt: Long,
)

internal fun parseSessionHistory(response: JsonObject): List<SessionHistoryEntry> =
    response["sessions"]?.jsonArray.orEmpty().mapNotNull { element ->
        val record = element as? JsonObject ?: return@mapNotNull null
        val id = record["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        SessionHistoryEntry(id,
            record["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "新对话",
            record["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            record["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            record["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0,
        )
    }.distinctBy { it.id }.sortedByDescending { it.updatedAt }

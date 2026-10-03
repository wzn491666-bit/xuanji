package com.xuanji.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive

data class ProviderModel(
    val id: String,
    val supportsVision: Boolean,
    val effortSupported: Boolean,
)

data class ProviderProfile(
    val name: String,
    val label: String,
    val isDefault: Boolean,
    val hasCredential: Boolean,
    val keyless: Boolean,
    val models: List<ProviderModel>,
) {
    val isAvailable get() = hasCredential || keyless
}

internal fun parseProviderCatalog(response: JsonObject): List<ProviderProfile> =
    response["providers"]?.jsonArray.orEmpty().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val credentialSource = (obj["keyStatus"] as? JsonObject)?.get("source")?.jsonPrimitive?.contentOrNull
        ProviderProfile(
            name = name,
            label = obj["label"]?.jsonPrimitive?.contentOrNull ?: name,
            isDefault = obj["isDefault"]?.jsonPrimitive?.booleanOrNull == true,
            hasCredential = credentialSource != null && credentialSource != "none",
            keyless = obj["keyless"]?.jsonPrimitive?.booleanOrNull == true,
            models = obj["models"]?.jsonArray.orEmpty().mapNotNull { modelElement ->
                val model = modelElement as? JsonObject ?: return@mapNotNull null
                val id = model["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ProviderModel(
                    id = id,
                    supportsVision = model["supportsVision"]?.jsonPrimitive?.booleanOrNull == true,
                    effortSupported = model["effortSupported"]?.jsonPrimitive?.booleanOrNull == true,
                )
            },
        )
    }

internal enum class ProviderSyncAction { CONFIGURE, SELECT_STORED }

internal fun providerSyncAction(settings: Settings) =
    if (settings.useStoredCredential && settings.apiKey.isBlank()) ProviderSyncAction.SELECT_STORED
    else ProviderSyncAction.CONFIGURE

internal fun settingsForProviderActivation(current: Settings, profile: ProviderProfile, modelId: String): Settings {
    val model = profile.models.firstOrNull { it.id == modelId }
    return Settings(
        provider = profile.name,
        apiKey = "",
        model = modelId,
        reasoningEffort = if (model?.effortSupported == true) current.reasoningEffort else "auto",
        useStoredCredential = true,
    )
}

internal fun providerProbeSummary(response: JsonObject): String {
    if (response["ok"]?.jsonPrimitive?.booleanOrNull != true) {
        val reason = response["error"]?.jsonPrimitive?.contentOrNull ?: "模型端点未通过验证"
        return "连接失败：$reason"
    }
    val models = response["models"]?.jsonArray?.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> element["id"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }.orEmpty()
    return if (models.isEmpty()) "连接成功" else "连接成功 · ${models.take(5).joinToString()}"
}

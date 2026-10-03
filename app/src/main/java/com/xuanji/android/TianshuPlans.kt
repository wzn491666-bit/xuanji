package com.xuanji.android

import com.xuanji.android.ui.chat.NativeSlashRequest
import com.xuanji.android.ui.chat.PaletteAction
import kotlinx.serialization.json.*

data class TianshuPlanOption(val label: String, val description: String)
data class TianshuPlan(val slug: String, val title: String, val status: String, val content: String = "",
    val options: List<TianshuPlanOption> = emptyList(), val model: String = "") {
    val editable: Boolean get() = status == "submitted"
    val statusLabel: String get() = when (status) {
        "submitted" -> "待审批"
        "approved" -> "已批准"
        "executed" -> "已执行"
        "rejected" -> "已驳回"
        else -> status
    }
}
data class TianshuPlanDraft(val title: String, val content: String)
data class TianshuPlanCatalog(val plans: List<TianshuPlan>, val draft: TianshuPlanDraft?)

internal fun parseTianshuPlan(entry: JsonObject, requireContent: Boolean = false): TianshuPlan {
    fun text(key: String) = entry[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    require(text("slug").isNotBlank()) { "天枢计划缺少标识" }
    require(text("status") in setOf("submitted", "approved", "executed", "rejected")) { "天枢计划状态不兼容" }
    if (requireContent) require(entry["content"] is JsonPrimitive && entry["content"] != JsonNull) { "天枢未返回计划全文" }
    return TianshuPlan(text("slug"), text("title").ifBlank { text("slug") }, text("status"), text("content"),
        (entry["options"] as? JsonArray).orEmpty().map {
            val option = it.jsonObject
            TianshuPlanOption(option["label"]?.jsonPrimitive?.contentOrNull ?: error("计划方案缺少名称"),
                option["description"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }, text("model"))
}

internal fun parseTianshuPlanCatalog(response: JsonObject): TianshuPlanCatalog {
    val entries = response["plans"] as? JsonArray ?: error("天枢未返回计划列表")
    val draft = (response["draft"] as? JsonObject)?.let {
        TianshuPlanDraft(it["title"]?.jsonPrimitive?.contentOrNull ?: "起草中",
            it["content"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }
    return TianshuPlanCatalog(entries.map { parseTianshuPlan(it.jsonObject) }, draft)
}

internal val planActions = setOf(PaletteAction.PLAN_LIST, PaletteAction.PLAN_VIEW, PaletteAction.PLAN_EDIT,
    PaletteAction.PLAN_APPROVE, PaletteAction.PLAN_REJECT)

internal suspend fun executePlanCommand(client: TianshuClient, id: String, request: NativeSlashRequest): SessionCommandOutput {
    val parts = request.argument.split(Regex("\\s+"), limit = 2)
    val slug = parts.first()
    if (request.action == PaletteAction.PLAN_LIST || slug.isBlank()) {
        return SessionCommandOutput("计划", "", plans = client.sessionPlans(id))
    }
    // Reviewing is user participation: cancel the upstream auto-approval timer first.
    client.cancelPlanAutoApproval(id, slug)
    val plan = client.sessionPlan(id, slug)
    return when (request.action) {
        PaletteAction.PLAN_VIEW, PaletteAction.PLAN_EDIT -> SessionCommandOutput(plan.title, "", plan = plan,
            editPlan = request.action == PaletteAction.PLAN_EDIT && plan.editable)
        PaletteAction.PLAN_APPROVE -> {
            check(plan.editable) { "只有待审批计划可以批准" }
            client.approveSessionPlan(id, slug, parts.getOrNull(1))
            SessionCommandOutput("计划已批准", "天枢已接收执行任务。", plan = client.sessionPlan(id, slug))
        }
        PaletteAction.PLAN_REJECT -> {
            check(plan.editable) { "只有待审批计划可以驳回" }
            val feedback = parts.getOrNull(1)?.trim().orEmpty()
            if (feedback.isBlank()) SessionCommandOutput(plan.title, "", plan = plan)
            else {
                client.rejectSessionPlan(id, slug, feedback)
                SessionCommandOutput("计划已驳回", "反馈已交给天枢，计划保留在工作区。", plan = client.sessionPlan(id, slug))
            }
        }
        else -> error("不支持的计划命令")
    }
}

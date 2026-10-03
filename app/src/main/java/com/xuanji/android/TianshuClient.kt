package com.xuanji.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import java.io.IOException
import kotlin.coroutines.resumeWithException

internal class TianshuHttpException(val status: Int, body: String) :
    IllegalStateException("天枢返回 $status：$body") {
    val code: String? = runCatching { Json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

internal class PromptPreparationException(cause: Exception) : IllegalStateException(cause.message ?: "附件准备失败", cause)

internal class PromptReceiptException(message: String, val rejected: Boolean = false) : IllegalStateException(message)

internal fun requirePromptReceipt(response: JsonObject, sessionId: String, requestId: String): JsonObject {
    fun JsonObject.receiptText(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    val receipt = response["receipt"] as? JsonObject
        ?: throw PromptReceiptException("天枢未返回持久消息回执；请核对对话记录后使用原请求重试。")
    if (response.receiptText("id") != sessionId || receipt.receiptText("requestId") != requestId ||
        listOf("runId", "attemptId", "fingerprint").any { receipt.receiptText(it).isNullOrBlank() }) {
        throw PromptReceiptException("天枢消息回执与当前请求不匹配；请重新打开会话核对执行记录。")
    }
    when (receipt.receiptText("state")) {
        "accepted", "running", "settled" -> Unit
        "rejected" -> throw PromptReceiptException("天枢已拒绝这次发送，可以重试提交新请求。", rejected = true)
        "needs_attention" -> throw PromptReceiptException("天枢重启前的请求尚未结算，原请求不会自动重跑；请重新打开会话核对执行记录。")
        else -> throw PromptReceiptException("天枢返回未知消息回执状态，尚不能确认送达。")
    }
    return response
}

internal fun isDefinitivePromptRejection(error: Throwable, previouslyUncertain: Boolean = false): Boolean =
    (error is PromptReceiptException && error.rejected) || (!previouslyUncertain &&
        (error is PromptPreparationException ||
            (error is TianshuHttpException && error.status in 400..499 && error.code != "request_conflict")))

internal fun providerSetupPayload(provider: String, apiKey: String, model: String, baseUrl: String) = buildJsonObject {
    put("providerName", provider)
    put("apiKey", apiKey)
    put("makeDefault", true)
    if (model.isNotBlank()) putJsonObject("model") { put("id", model) }
    if (baseUrl.isNotBlank()) put("baseUrl", baseUrl)
}

/**
 * 天枢 HTTP 接口客户端。直接调用 `tianshu serve` 自带的接口，天枢源码一行不改。
 * 接口定义见天枢仓库 src/server/session-routes.ts 和 config-routes.ts。
 */
class TianshuClient(
    private val base: String = "http://127.0.0.1:${TianshuService.PORT}",
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // SSE 长连接不超时
        .build(),
    private val bearerToken: () -> String = { TianshuService.token },
) {
    private val jsonType = "application/json".toMediaType()
    private val commandHttp = http.newBuilder().retryOnConnectionFailure(false).build()

    private fun request(path: String) = Request.Builder()
        .url(base + path)
        .header("Authorization", "Bearer ${bearerToken()}")

    private suspend fun call(method: String, path: String, body: JsonObject? = null, streamedBody: okhttp3.RequestBody? = null,
        retrySafe: Boolean = true): JsonObject =
        withContext(Dispatchers.IO) {
            val req = request(path)
                .method(method, streamedBody ?: body?.toString()?.toRequestBody(jsonType)
                    ?: if (method in setOf("POST", "PUT", "PATCH")) "".toRequestBody(jsonType) else null)
                .build()
            val call = (if (retrySafe) http else commandHttp).newCall(req)
            call.timeout().timeout(90, TimeUnit.SECONDS)
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use { res ->
                                val text = res.body.string()
                                if (!res.isSuccessful) throw TianshuHttpException(res.code, text)
                                if (text.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(text).jsonObject
                            }
                        }
                        continuation.resumeWith(result)
                    }
                })
            }
        }

    /** 保存 API Key 并设为默认服务商。provider 是天枢预设名，如 deepseek、glm、anthropic。 */
    suspend fun setupProvider(provider: String, apiKey: String, model: String = "", baseUrl: String = "") = call(
        "POST", "/config/providers",
        providerSetupPayload(provider, apiKey, model, baseUrl),
    )

    suspend fun listProviders(): JsonObject = call("GET", "/config/providers")

    suspend fun selectDefaultProvider(name: String): JsonObject =
        call("POST", "/config/providers/${URLEncoder.encode(name, "UTF-8")}/default", buildJsonObject { })

    suspend fun deleteProvider(name: String): JsonObject =
        call("DELETE", "/config/providers/${URLEncoder.encode(name, "UTF-8")}")

    suspend fun testProvider(provider: String, apiKey: String, model: String = "", baseUrl: String = ""): JsonObject = call(
        "POST", "/config/providers/test-key",
        buildJsonObject {
            put("provider", provider)
            put("apiKey", apiKey)
            if (model.isNotBlank()) put("model", model)
            if (baseUrl.isNotBlank()) put("baseUrl", baseUrl)
        },
    )

    /** 新建会话，工作目录默认是手机存储根目录。返回会话 id。 */
    suspend fun createSession(model: String?, cwd: String = "/sdcard", reasoningEffort: String = "auto"): String =
        call("POST", "/sessions", buildJsonObject {
            put("cwd", cwd)
            if (!model.isNullOrBlank()) put("model", model)
            if (reasoningEffort.isNotBlank()) put("reasoningEffort", reasoningEffort)
        })["id"]!!.jsonPrimitive.content

    suspend fun skillCatalog(sessionId: String): SkillCatalog = parseSkillCatalog(call("GET", "/sessions/$sessionId/skills"))
    suspend fun listSkills(sessionId: String): List<com.xuanji.android.ui.chat.SkillEntry> = skillCatalog(sessionId).skills

    suspend fun setSkillEnabled(sessionId: String, name: String, enabled: Boolean) {
        require(name.isNotBlank()) { "请指定技能名" }
        val response = call("POST", "/sessions/$sessionId/skills", buildJsonObject {
            put("name", name); put("enabled", enabled)
        }, retrySafe = false)
        check(response["id"]?.jsonPrimitive?.contentOrNull == sessionId &&
            response["name"]?.jsonPrimitive?.contentOrNull == name &&
            response["enabled"]?.jsonPrimitive?.booleanOrNull == enabled) { "天枢未确认技能设置" }
    }

    suspend fun compactSession(sessionId: String): Boolean =
        call("POST", "/sessions/$sessionId/compact", buildJsonObject { }, retrySafe = false)["changed"]?.jsonPrimitive?.booleanOrNull
            ?: error("天枢未返回上下文压缩结果")

    suspend fun forkSession(sessionId: String, title: String = ""): String {
        val response = call("POST", "/sessions/$sessionId/fork", buildJsonObject {
            put("destination", "local"); put("source", "header")
            if (title.isNotBlank()) put("title", title.trim())
        }, retrySafe = false)["session"] as? JsonObject ?: error("天枢未返回分支会话")
        val forkedId = response["id"]?.jsonPrimitive?.contentOrNull
        check(!forkedId.isNullOrBlank() && forkedId != sessionId &&
            response["forkedFromId"]?.jsonPrimitive?.contentOrNull == sessionId) { "分支会话与当前会话不匹配" }
        return checkNotNull(forkedId)
    }

    suspend fun requestHandoff(sessionId: String, note: String = ""): JsonObject {
        val response = call("POST", "/sessions/$sessionId/handoff", buildJsonObject {
            if (note.isNotBlank()) put("note", note.trim())
        }, retrySafe = false)
        check(response["id"]?.jsonPrimitive?.contentOrNull == sessionId) { "天枢未确认交接任务受理" }
        return response
    }

    suspend fun teamCheckpoints(sessionId: String): List<TianshuTeamCheckpoint> =
        parseTeamCheckpoints(call("GET", "/sessions/$sessionId/team-checkpoints"))

    suspend fun resumeTeam(sessionId: String, groupId: String): Boolean {
        require(groupId.isNotBlank() && groupId !in setOf(".", "..") &&
            groupId.none { it.isWhitespace() || it.isISOControl() || it == '/' || it == '\\' }) { "请指定有效编队检查点" }
        val response = call("POST", "/sessions/$sessionId/team-resume", buildJsonObject { put("groupId", groupId) }, retrySafe = false)
        return response["resumed"]?.jsonPrimitive?.booleanOrNull ?: error("天枢未返回编队恢复结果")
    }

    suspend fun sessionQueue(sessionId: String): TianshuQueueSnapshot =
        parseTianshuQueue(call("GET", "/sessions/$sessionId/xuanji/queue"), sessionId)

    suspend fun queueMessage(sessionId: String, text: String): String {
        require(text.isNotBlank()) { "请填写跟进内容" }
        val response = call("POST", "/sessions/$sessionId/queue", buildJsonObject { put("text", text) }, retrySafe = false)
        check(response["queued"]?.jsonPrimitive?.booleanOrNull == true) { "天枢未确认消息入队" }
        return response["laneId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: error("入队回执缺少消息 id")
    }

    suspend fun retractQueuedMessage(sessionId: String, laneId: String) {
        require(laneId.isNotBlank())
        val response = call("POST", "/sessions/$sessionId/queue/retract", buildJsonObject { put("laneId", laneId) }, retrySafe = false)
        check(response["ok"]?.jsonPrimitive?.booleanOrNull == true) { "天枢未确认消息撤回" }
    }

    suspend fun steerSession(sessionId: String, text: String? = null, laneId: String? = null) {
        require((!text.isNullOrBlank()) xor (!laneId.isNullOrBlank())) { "请选择引导正文或一条排队消息" }
        val response = call("POST", "/sessions/$sessionId/steer", buildJsonObject {
            if (laneId != null) put("laneId", laneId) else put("text", checkNotNull(text))
        }, retrySafe = false)
        check(response["queued"]?.jsonPrimitive?.booleanOrNull == true) { "天枢未确认即时引导受理" }
    }

    suspend fun sessionDomains(sessionId: String): List<TianshuDomainChoice> =
        parseTianshuDomains(call("GET", "/sessions/$sessionId/domains"))

    suspend fun setSessionDomain(sessionId: String, key: String) {
        require(key.isNotBlank())
        val response = call("POST", "/sessions/$sessionId/domain", buildJsonObject { put("key", key) })
        check(response["id"]?.jsonPrimitive?.contentOrNull == sessionId && response["domain"]?.jsonPrimitive?.contentOrNull == key) {
            "天枢未确认星域切换"
        }
    }

    suspend fun setSessionMode(sessionId: String, planning: Boolean, state: String) {
        require(state == "off" || state == if (planning) "planning" else "asking")
        val response = call("POST", "/sessions/$sessionId/${if (planning) "plan-mode" else "ask-mode"}", buildJsonObject { put("state", state) })
        check(response["id"]?.jsonPrimitive?.contentOrNull == sessionId && response[if (planning) "planMode" else "askMode"]?.jsonPrimitive?.contentOrNull == state) {
            "天枢未确认模式切换"
        }
    }

    suspend fun reviewGate(sessionId: String): String =
        call("GET", "/sessions/$sessionId/review-gate")["mode"]?.jsonPrimitive?.contentOrNull ?: error("天枢未返回审查门状态")

    suspend fun setReviewGate(sessionId: String, mode: String) {
        require(mode in setOf("auto", "off"))
        val response = call("POST", "/sessions/$sessionId/review-gate", buildJsonObject { put("mode", mode) })
        check(response["id"]?.jsonPrimitive?.contentOrNull == sessionId && response["mode"]?.jsonPrimitive?.contentOrNull == mode) {
            "天枢未确认自动审查设置"
        }
    }

    suspend fun skipZen(sessionId: String): Boolean {
        val response = call("POST", "/sessions/$sessionId/zen", buildJsonObject { put("action", "skip") })
        check(response["id"]?.jsonPrimitive?.contentOrNull == sessionId) { "禅模式操作未得到确认" }
        return response["promoted"]?.jsonPrimitive?.booleanOrNull ?: error("天枢未返回禅模式操作结果")
    }

    suspend fun cockpitSnapshot(sessionId: String): JsonObject = call("GET", "/sessions/$sessionId/cockpit")

    suspend fun sessionTodos(sessionId: String): TianshuTodos =
        parseTianshuTodos(call("GET", "/sessions/$sessionId/xuanji/todos"))

    internal suspend fun updateSessionTodos(sessionId: String, command: TodoCommand, expectedRevision: String): TianshuTodos =
        parseTianshuTodos(call("POST", "/sessions/$sessionId/xuanji/todos", todoMutationPayload(command, expectedRevision)), requireDurable = true)

    private fun planPath(sessionId: String, slug: String): String {
        require(slug.isNotBlank() && slug !in setOf(".", "..") && slug.none { it == '/' || it == '\\' || it.isISOControl() }) { "计划标识无效" }
        return "/sessions/$sessionId/plans/${URLEncoder.encode(slug, "UTF-8").replace("+", "%20") }"
    }

    suspend fun sessionPlans(sessionId: String): TianshuPlanCatalog =
        parseTianshuPlanCatalog(call("GET", "/sessions/$sessionId/plans"))

    suspend fun sessionPlan(sessionId: String, slug: String): TianshuPlan =
        parseTianshuPlan(call("GET", planPath(sessionId, slug))["plan"]?.jsonObject ?: error("天枢未返回计划"), requireContent = true)

    suspend fun cancelPlanAutoApproval(sessionId: String, slug: String) {
        requirePlanReceipt(call("POST", planPath(sessionId, slug) + "/auto-approve/cancel"))
    }

    suspend fun updateSessionPlan(sessionId: String, slug: String, content: String) {
        require(content.isNotBlank()) { "计划不能为空" }
        requirePlanReceipt(call("PUT", planPath(sessionId, slug), buildJsonObject { put("content", content) }))
    }

    suspend fun approveSessionPlan(sessionId: String, slug: String, selectedApproach: String? = null) {
        requirePlanReceipt(call("POST", planPath(sessionId, slug) + "/approve", buildJsonObject {
            selectedApproach?.trim()?.takeIf(String::isNotEmpty)?.let { put("selectedApproach", it) }
        }))
    }

    suspend fun rejectSessionPlan(sessionId: String, slug: String, comment: String) {
        require(comment.isNotBlank()) { "请填写修改反馈" }
        requirePlanReceipt(call("POST", planPath(sessionId, slug) + "/reject", buildJsonObject { put("comment", comment) }))
    }

    private fun requirePlanReceipt(response: JsonObject) {
        check(response["ok"]?.jsonPrimitive?.booleanOrNull == true) { "天枢未确认计划操作" }
    }

    suspend fun sessionGoal(sessionId: String): TianshuGoal? = try {
        val response = call("GET", "/sessions/$sessionId/goal")
        if (response["goalId"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) null else parseTianshuGoal(response)
    } catch (e: TianshuHttpException) {
        if (e.status == 404) null else throw e
    }

    internal suspend fun prepareGoalAgent(sessionId: String) {
        // This official read endpoint initializes a missing agent without starting a model turn.
        check(call("GET", "/sessions/$sessionId/rewind-points")["points"] is JsonArray) { "天枢 agent 初始化接口不兼容" }
    }

    suspend fun createSessionGoal(sessionId: String, config: TianshuGoalConfig): TianshuGoal {
        val goal = parseTianshuGoal(call("POST", "/sessions/$sessionId/goal", goalConfigPayload(config)))
        check(goal.goal == config.goal && goal.status == "active" && goal.maxIterations == config.maxIterations &&
            goal.budgetMs == config.wallClockMs && (config.successCriteria.isEmpty() || goal.criteria == config.successCriteria)) { "天枢目标创建核对失败" }
        return goal
    }

    suspend fun controlSessionGoal(sessionId: String, action: String): TianshuGoal {
        require(action in setOf("pause", "resume", "cancel"))
        val goal = parseTianshuGoal(call("POST", "/sessions/$sessionId/goal/$action"))
        check(goal.status == when (action) { "pause" -> "paused"; "resume" -> "active"; else -> "complete" }) { "天枢未确认目标操作" }
        return goal
    }

    suspend fun sessionInsights(sessionId: String): JsonObject = call("GET", "/sessions/$sessionId/insights")

    suspend fun sessionWorkingTree(sessionId: String): JsonObject =
        call("GET", "/sessions/$sessionId/git/working-tree")

    suspend fun sessionFileDiff(sessionId: String, path: String): String =
        call("GET", "/sessions/$sessionId/git/diff?path=${URLEncoder.encode(path, "UTF-8")}")["diff"]
            ?.jsonPrimitive?.contentOrNull.orEmpty()

    suspend fun jobLogs(sessionId: String, jobId: String): String =
        call("GET", "/sessions/$sessionId/jobs/$jobId/logs")["logs"]?.jsonPrimitive?.contentOrNull.orEmpty()

    suspend fun killJob(sessionId: String, jobId: String): JsonObject =
        call("POST", "/sessions/$sessionId/jobs/$jobId/kill", buildJsonObject { })

    suspend fun sessionSnapshot(sessionId: String): JsonObject? = try {
        call("GET", "/sessions/$sessionId")
    } catch (e: TianshuHttpException) {
        if (e.status == 404) null else throw e
    }

    suspend fun sessionExists(sessionId: String): Boolean = sessionSnapshot(sessionId) != null

    /** Register the in-process Android MCP bridge with capability-specific approvals. */
    suspend fun registerAndroidMcp(endpoint: String, bridgeToken: String): JsonObject = call(
        "POST", "/mcp/servers",
        buildJsonObject {
            put("serverId", "android")
            put("url", endpoint)
            putJsonObject("headers") { put("Authorization", "Bearer $bridgeToken") }
            putJsonObject("policy") {
                putJsonObject("tools") {
                    ANDROID_MCP_TOOL_CAPABILITIES.forEach { (tool, capability) ->
                        putJsonObject(tool) { put("capability", capability) }
                    }
                }
            }
        },
    )

    suspend fun approveMcpServer(serverId: String): JsonObject =
        call("POST", "/mcp/servers/$serverId/approve")

    suspend fun mcpStatus(): JsonObject = call("GET", "/mcp/status")
    suspend fun sendPrompt(sessionId: String, text: String, requestId: String, attachments: List<PromptAttachment> = emptyList()): JsonObject {
        val payload = try {
            if (attachments.any { it.kind == AttachmentKind.IMAGE }) {
                val vision = call("GET", "/sessions/$sessionId/vision-bridge")
                check(vision["active"]?.jsonPrimitive?.booleanOrNull == true) {
                    vision["detail"]?.jsonPrimitive?.contentOrNull ?: "当前会话没有可用的识图模型，请切换模型或配置天枢识图桥。"
                }
            }
            withContext(Dispatchers.IO) { attachmentPromptBody(text, requestId, attachments) }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { throw PromptPreparationException(e) }
        val response = try {
            call("POST", "/sessions/$sessionId/prompt", streamedBody = payload)
        } catch (e: TianshuHttpException) {
            if (e.status != 409 || e.code == "request_conflict") throw e
            // The official prompt route omits a rejected receipt from its 409 body.
            // Read the durable ledger before deciding whether a retry may use a new id.
            try {
                val receipt = call("GET", "/sessions/$sessionId/requests/${URLEncoder.encode(requestId, "UTF-8")}")
                requirePromptReceipt(buildJsonObject { put("id", sessionId); put("receipt", receipt) }, sessionId, requestId)
                val snapshot = call("GET", "/sessions/$sessionId")
                buildJsonObject {
                    snapshot.forEach { (key, value) -> put(key, value) }
                    put("receipt", receipt)
                }
            } catch (receiptError: PromptReceiptException) { throw receiptError }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { throw e }
        }
        return requirePromptReceipt(response, sessionId, requestId)
    }

    suspend fun sessionModels(sessionId: String): List<SessionModelChoice> = parseSessionModels(call("GET", "/sessions/$sessionId/models"))

    suspend fun switchSessionModel(sessionId: String, modelId: String): JsonObject =
        call("POST", "/sessions/$sessionId/model", buildJsonObject { put("modelId", modelId) })

    suspend fun setApprovalMode(sessionId: String, mode: ChatApprovalMode) {
        val response = call("POST", "/sessions/$sessionId/approval-mode", buildJsonObject { put("approvalMode", mode.wire) })
        check(response["id"]?.jsonPrimitive?.contentOrNull == sessionId && response["approvalMode"]?.jsonPrimitive?.contentOrNull == mode.wire) {
            "天枢未确认审批模式，设置尚未生效"
        }
    }

    suspend fun abort(sessionId: String) = call("POST", "/sessions/$sessionId/abort")

    suspend fun listSessions(): List<SessionHistoryEntry> = parseSessionHistory(call("GET", "/sessions"))

    suspend fun historyEvents(sessionId: String, through: Long): List<SessionEvent> {
        if (through <= 0) return emptyList()
        val pages = mutableListOf<List<SessionEvent>>()
        var before = through + 1
        while (before > 0) {
            val response = call("GET", "/sessions/$sessionId/events?before=$before&limit=2000")
            val events = response["events"]?.jsonArray.orEmpty().map { SessionEvent.parse(it.toString()) }
            if (events.isEmpty()) break
            val first = events.first().seq
            check(first < before) { "历史记录游标没有前进" }
            pages.add(events)
            if (first <= (response["firstSeq"]?.jsonPrimitive?.longOrNull ?: 0)) break
            before = first
        }
        return pages.asReversed().flatten()
    }

    suspend fun setReasoningEffort(sessionId: String, effort: String) {
        require(effort in setOf("auto", "off", "low", "medium", "high", "max"))
        val response = call("POST", "/sessions/$sessionId/effort", buildJsonObject { put("effort", effort) })
        check(response["id"]?.jsonPrimitive?.contentOrNull == sessionId && response["effort"]?.jsonPrimitive?.contentOrNull == effort) {
            "天枢未确认思考强度，设置尚未生效"
        }
    }

    /** 回应天枢的审批请求（高风险操作前天枢会停下来问）。 */
    suspend fun answerApproval(sessionId: String, requestId: String, approve: Boolean) = call(
        "POST", "/sessions/$sessionId/interventions/$requestId/answer",
        buildJsonObject { put("decision", if (approve) "approve" else "deny") },
    )

    /**
     * 订阅会话事件流（SSE）。每条事件形如 {seq, ts, type, data}，
     * type 取值见天枢 src/server/protocol.ts 的 SessionEventType。
     */
    fun events(sessionId: String, since: Long = 0, onOpen: () -> Unit = {}): Flow<SessionEvent> = callbackFlow {
        val source = EventSources.createFactory(http).newEventSource(
            request("/sessions/$sessionId/stream?since=$since").build(),
            object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) { onOpen() }
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    val event = try {
                        SessionEvent.parse(data)
                    } catch (error: Exception) {
                        close(error)
                        eventSource.cancel()
                        return
                    }
                    // A failed enqueue must stop this stream before a later sequence is acknowledged.
                    if (trySend(event).isFailure) {
                        close(IllegalStateException("事件缓冲已满，正在重新同步"))
                        eventSource.cancel()
                    }
                }
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    close(if (response != null && !response.isSuccessful) TianshuHttpException(response.code, response.message)
                        else t ?: IllegalStateException("事件流断开 ${response?.code}"))
                }
                override fun onClosed(eventSource: EventSource) { close() }
            },
        )
        awaitClose { source.cancel() }
    }.buffer(64)
    companion object {
        val ANDROID_MCP_TOOL_CAPABILITIES = linkedMapOf(
            "android_read_screen" to "read",
            "android_list_files" to "read",
            "android_read_file" to "read",
            "android_shell_id" to "execute",
            "android_permission_status" to "read",
            "android_write_file" to "write",
            "android_accessibility_action" to "execute",
            "android_web_search" to "read",
            "android_browser_tabs" to "read",
            "android_browser_open" to "read",
            "android_browser_snapshot" to "read",
            "android_browser_action" to "execute",
        )
    }
}

data class SessionEvent(val seq: Long, val type: String, val data: JsonObject, val ts: Long = 0) {
    fun str(key: String) = data[key]?.jsonPrimitive?.contentOrNull

    companion object {
        fun parse(raw: String): SessionEvent {
            val o = Json.parseToJsonElement(raw).jsonObject
            return SessionEvent(
                seq = o["seq"]?.jsonPrimitive?.longOrNull ?: 0,
                type = o["type"]?.jsonPrimitive?.content ?: "",
                data = o["data"] as? JsonObject ?: JsonObject(emptyMap()),
                ts = o["ts"]?.jsonPrimitive?.longOrNull ?: 0,
            )
        }
    }
}



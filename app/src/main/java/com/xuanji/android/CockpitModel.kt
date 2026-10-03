package com.xuanji.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

data class VerificationCommand(val command: String, val status: String, val output: String = "")
data class HookStatus(val name: String, val status: String, val detail: String = "")
data class ReplayEntry(val label: String, val type: String, val detail: String = "")
data class FailureInfo(val category: String, val message: String)
data class TokenUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val reasoningTokens: Long? = null,
) {
    val totalTokens: Long get() = inputTokens + outputTokens
}
data class AgentWave(
    val wave: Int,
    val active: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
)
data class ConvergenceState(
    val status: String = "unknown",
    val score: Int? = null,
    val reason: String = "",
)

object CockpitReducer {
    fun mergeSnapshot(current: Cockpit, snapshot: JsonObject): Cockpit {
        val verification = snapshot["verification"] as? JsonObject
        val model = snapshot["model"] as? JsonObject
        val context = snapshot["context"] as? JsonObject
        val traceSnapshot = snapshot["trace"] as? JsonObject
        val runs = verification?.get("runs")?.jsonArray?.mapNotNull { item ->
            val run = item.jsonObject
            VerificationCommand(
                value(run, "tool", "command") ?: return@mapNotNull null,
                value(run, "status") ?: "unknown",
                value(run, "summary") ?: "",
            )
        }.orEmpty()
        val trace = traceSnapshot?.get("events")?.jsonArray?.map { item ->
            val event = item.jsonObject
            ReplayEntry(
                value(event, "name", "kind") ?: "事件",
                value(event, "kind", "status") ?: "trace",
                value(event, "summary", "status") ?: "",
            )
        }.orEmpty()
        val delivery = value(verification, "deliveryStatus")
        val tokens = TokenUsage(
            inputTokens = number(model, "inputTokens") ?: current.usage.inputTokens,
            outputTokens = number(model, "outputTokens") ?: current.usage.outputTokens,
            cacheReadTokens = number(model, "cacheReadTokens") ?: current.usage.cacheReadTokens,
            reasoningTokens = current.usage.reasoningTokens,
        )
        val estimated = number(context, "estimatedTokens")
        val max = number(context, "maxTokens")
        return current.copy(
            goal = value(snapshot, "intent") ?: current.goal,
            deliveryGate = when (delivery) {
                "verified" -> "GREEN"
                "failed" -> "RED"
                "blocked" -> "YELLOW"
                "unverified" -> null
                else -> current.deliveryGate
            },
            verificationCommands = if (verification?.containsKey("runs") == true) runs.takeLast(20) else current.verificationCommands,
            replay = if (traceSnapshot?.containsKey("events") == true) trace.takeLast(100) else current.replay,
            convergence = ConvergenceState(
                status = current.convergence.status,
                score = current.convergence.score,
                reason = current.convergence.reason,
            ),
            contextUsagePercent = if (estimated != null && max != null && max > 0) {
                ((estimated.toDouble() / max.toDouble()) * 100).toInt().coerceIn(0, 100)
            } else if (snapshot.containsKey("context") && context == null) null else current.contextUsagePercent,
            usage = tokens,
        )
    }

    fun onEvent(current: Cockpit, event: SessionEvent): Cockpit {
        val d = event.data
        return when (event.type) {
            "user" -> clearRunResults(current)
            "status" -> if (event.str("status") == "running") clearRunResults(current) else current
            "turn_complete" -> current.copy(usage = usageFrom(d, current.usage))
            "tool_result" -> if (event.str("name") == "deliver_task" && d["partial"]?.jsonPrimitive?.booleanOrNull != true) {
                val result = event.str("uiContent") ?: event.str("result").orEmpty()
                current.copy(
                    deliveryGate = Regex("^Delivery Gate: *(GREEN|YELLOW|RED)\\b", RegexOption.MULTILINE).find(result)?.groupValues?.get(1),
                    deliveryFailed = d["isError"]?.jsonPrimitive?.booleanOrNull == true,
                    evidence = (current.evidence + CockpitEvidence("交付结果", result.take(500), "tool_result")).takeLast(20),
                )
            } else current
            "context_budget" -> current.copy(
                contextUsagePercent = number(d, "inputTokens", "input_tokens")?.let { input ->
                    number(d, "windowTokens", "window_tokens")?.let { window ->
                        if (window > 0) ((input.toDouble() / window.toDouble()) * 100).toInt().coerceIn(0, 100) else null
                    }
                } ?: current.contextUsagePercent,
            )
            "hook_result" -> {
                val resultItems = d["results"]?.jsonArray.orEmpty()
                val hooks = resultItems.mapIndexedNotNull { index, item ->
                    val hook = item as? JsonObject ?: return@mapIndexedNotNull null
                    HookStatus(
                        value(hook, "script", "name", "hook") ?: "hook-${index + 1}",
                        when (hook["ok"]?.jsonPrimitive?.booleanOrNull) {
                            true -> "passed"
                            false -> "failed"
                            null -> value(hook, "status", "state") ?: "unknown"
                        },
                        value(hook, "output", "message", "error") ?: "",
                    )
                }
                current.copy(hooks = (current.hooks + hooks).takeLast(72))
            }
            "delegation" -> current.copy(
                replay = (current.replay + ReplayEntry(value(d, "workerId", "profile") ?: "代理", "delegation", value(d, "objective", "summary") ?: "")).takeLast(100),
            )
            "error" -> {
                val message = value(d, "error", "message", "text") ?: "未知错误"
                current.copy(failures = (current.failures + FailureInfo(classifyFailure(message), message)).takeLast(30))
            }
            else -> current
        }
    }

    private fun clearRunResults(current: Cockpit): Cockpit = current.copy(
        deliveryGate = null,
        deliveryFailed = false,
        verificationCommands = emptyList(),
        replay = emptyList(),
    )

    private fun value(data: JsonObject?, vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        data?.get(key)?.jsonPrimitive?.contentOrNull
    }

    private fun number(data: JsonObject?, vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        data?.get(key)?.jsonPrimitive?.longOrNull
            ?: data?.get(key)?.jsonPrimitive?.intOrNull?.toLong()
    }

    private fun usageFrom(data: JsonObject, current: TokenUsage): TokenUsage {
        val source = data["usage"]?.jsonObject ?: data
        return TokenUsage(
            inputTokens = number(source, "input_tokens", "inputTokens", "prompt_tokens") ?: current.inputTokens,
            outputTokens = number(source, "output_tokens", "outputTokens", "completion_tokens") ?: current.outputTokens,
            cacheReadTokens = number(source, "cache_read_input_tokens", "cacheReadTokens", "cached_tokens") ?: current.cacheReadTokens,
            reasoningTokens = number(source, "reasoning_tokens", "reasoningTokens") ?: current.reasoningTokens,
        )
    }

    private fun classifyFailure(message: String): String {
        val lower = message.lowercase()
        return when {
            "timeout" in lower || "timed out" in lower || "etimedout" in lower || "network" in lower || "connection" in lower -> "network"
            "permission" in lower || "denied" in lower || "approval" in lower -> "permission"
            "token" in lower || "context" in lower || "too long" in lower -> "context"
            "tool" in lower || "command" in lower || "exit code" in lower -> "tool"
            else -> "runtime"
        }
    }
}

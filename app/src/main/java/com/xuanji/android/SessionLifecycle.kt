package com.xuanji.android

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal inline fun <T> requestResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

internal fun runningAfterSessionEvent(current: Boolean, event: SessionEvent, snapshotSeq: Long = 0): Boolean {
    if (event.seq > 0 && event.seq <= snapshotSeq) return current
    return when (event.type) {
        "user" -> true
        "status" -> when (event.str("status")) {
            "running" -> true
            "aborted", "completed", "error", "failed", "idle", "interrupted" -> false
            else -> current
        }
        "done" -> false
        else -> current
    }
}

internal fun sessionSnapshotRunning(snapshot: JsonObject): Boolean =
    snapshot["status"]?.jsonPrimitive?.contentOrNull == "running"

internal fun sessionSnapshotSequence(snapshot: JsonObject): Long =
    snapshot["lastSeq"]?.jsonPrimitive?.longOrNull ?: 0L

internal fun streamFailureRequiresRecovery(error: Throwable?): Boolean =
    error is TianshuHttpException && error.status in setOf(401, 403, 404)

internal fun streamReconnectDelayMillis(attempt: Int): Long =
    (2_000L shl attempt.coerceIn(0, 4)).coerceAtMost(30_000L)

internal fun toolAfterResult(current: ChatItem.Tool, event: SessionEvent): ChatItem.Tool {
    val partial = event.data["partial"]?.jsonPrimitive?.booleanOrNull == true
    val result = event.str("uiContent") ?: event.str("result").orEmpty()
    return current.copy(
        result = if (partial) (current.result.orEmpty() + result).takeLast(200_000) else result,
        isError = event.data["isError"]?.jsonPrimitive?.booleanOrNull == true,
        streaming = partial,
    )
}

internal fun pendingApprovalsAfterEvent(current: List<Approval>, event: SessionEvent): List<Approval> = when (event.type) {
    "approval_required" -> {
        val requestId = event.str("requestId")
        if (requestId.isNullOrBlank()) current else {
            val input = event.data["input"]
            val detail = if (input is kotlinx.serialization.json.JsonPrimitive) input.contentOrNull.orEmpty()
                else input?.toString().orEmpty()
            current.filterNot { it.requestId == requestId } + Approval(requestId, event.str("toolName").orEmpty(), detail)
        }
    }
    "approval_resolved" -> current.filterNot { it.requestId == event.str("requestId") }
    "done" -> emptyList()
    else -> current
}

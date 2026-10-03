package com.xuanji.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class BackgroundJob(
    val id: String,
    val command: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val exitCode: Int? = null,
    val lastLine: String = "",
    val pid: Int? = null,
    val recentOutput: String = "",
)

data class BackgroundJobsState(
    val jobs: List<BackgroundJob> = emptyList(),
    val snapshotAt: Long = 0,
    val activeIdsAtSnapshot: Set<String> = emptySet(),
)

object BackgroundJobsReducer {
    fun onEvent(current: BackgroundJobsState, event: SessionEvent): BackgroundJobsState = when (event.type) {
        "job_snapshot" -> {
            val active = event.data["jobs"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.toJob() }
            val finished = current.jobs.filter { it.status != "running" && it.id !in active.map(BackgroundJob::id) }
            current.copy(
                jobs = (active + finished).sortedByDescending { it.startedAt }.take(50),
                snapshotAt = event.ts,
                activeIdsAtSnapshot = active.mapTo(mutableSetOf()) { it.id },
            )
        }
        "job" -> {
            val incoming = event.data.toJob() ?: return current
            if (current.snapshotAt > 0 && event.ts in 1..current.snapshotAt && incoming.status == "running") {
                return current
            }
            val previous = current.jobs.firstOrNull { it.id == incoming.id }
            val chunk = event.str("chunk").orEmpty()
            val merged = incoming.copy(recentOutput = (previous?.recentOutput.orEmpty() + chunk).takeLast(8_000))
            current.copy(jobs = (current.jobs.filterNot { it.id == merged.id } + merged).sortedByDescending { it.startedAt }.take(50))
        }
        else -> current
    }

    private fun JsonObject.toJob(): BackgroundJob? {
        val id = this["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val command = this["command"]?.jsonPrimitive?.contentOrNull ?: return null
        val status = this["status"]?.jsonPrimitive?.contentOrNull ?: return null
        return BackgroundJob(
            id = id,
            command = command,
            status = status,
            startedAt = this["startedAt"]?.jsonPrimitive?.longOrNull ?: 0,
            endedAt = this["endedAt"]?.jsonPrimitive?.longOrNull,
            exitCode = this["exitCode"]?.jsonPrimitive?.intOrNull,
            lastLine = this["lastLine"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            pid = this["pid"]?.jsonPrimitive?.intOrNull,
        )
    }
}

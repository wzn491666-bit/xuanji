package com.xuanji.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class AndroidActionApproval(val id: String, val title: String, val detail: String, val targetPackage: String? = null)

/** A local, fail-closed user gate for Android capabilities that change device state. */
class AndroidActionApprovalGate {
    val pending = MutableStateFlow<AndroidActionApproval?>(null)
    private val mutex = Mutex()
    private data class PendingDecision(val id: String, val decision: CompletableDeferred<Boolean>)
    @Volatile private var response: PendingDecision? = null
    @Volatile private var active = true
    @Volatile private var generation = 0L

    suspend fun request(title: String, detail: String, timeoutMillis: Long = 45_000, targetPackage: String? = null): Boolean {
        val requestGeneration = generation
        return withTimeoutOrNull(timeoutMillis) {
            mutex.withLock {
                if (!active || requestGeneration != generation) return@withLock false
                val decision = CompletableDeferred<Boolean>()
                val approval = AndroidActionApproval(UUID.randomUUID().toString(), title, detail, targetPackage)
                response = PendingDecision(approval.id, decision)
                pending.value = approval
                try {
                    decision.await() && active && requestGeneration == generation
                } finally {
                    pending.value = null
                    response = null
                }
            }
        } == true
    }

    fun respond(id: String, approved: Boolean): Boolean {
        val current = response
        return if (current?.id == id) current.decision.complete(approved) else false
    }

    fun cancelPending() {
        response?.decision?.complete(false)
    }

    fun stop() {
        active = false
        generation++
        cancelPending()
    }

    fun start() {
        active = true
    }
}

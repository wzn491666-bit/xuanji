package com.xuanji.android

/** Tracks locally rendered prompts so server-side command expansions are not shown twice. */
internal class UserPromptLedger {
    private data class Pending(val requestId: String, val submittedAt: Long)
    private val pending = ArrayDeque<Pending>()

    fun record(requestId: String, text: String, submittedAt: Long = 0) {
        if (text.isNotBlank() && pending.none { it.requestId == requestId }) pending.addLast(Pending(requestId, submittedAt))
    }

    fun consumeServerEvent(serverText: String, eventAt: Long = Long.MAX_VALUE): String? {
        val next = pending.firstOrNull() ?: return null
        if (serverText.isBlank() || eventAt < next.submittedAt) return null
        return pending.removeFirst().requestId
    }

    fun remove(requestId: String) {
        pending.removeAll { it.requestId == requestId }
    }

    fun clear() {
        pending.clear()
    }
}

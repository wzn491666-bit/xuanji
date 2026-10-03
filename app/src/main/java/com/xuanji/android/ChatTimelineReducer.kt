package com.xuanji.android

/** Pure chat timeline transitions for provider thinking deltas. */
object ChatTimelineReducer {
    private val thinkingBoundaryTypes = setOf(
        "user", "text_delta", "tool_use", "tool_result", "turn_complete", "error", "done", "status",
    )

    fun appendThinking(items: List<ChatItem>, delta: String): List<ChatItem> {
        if (delta.isEmpty()) return items
        val last = items.lastOrNull()
        return if (last is ChatItem.Thinking && last.streaming) {
            items.dropLast(1) + last.copy(text = last.text + delta)
        } else {
            items + ChatItem.Thinking(delta, streaming = true)
        }
    }

    fun finishThinking(items: List<ChatItem>): List<ChatItem> {
        val last = items.lastOrNull()
        return if (last is ChatItem.Thinking && last.streaming) {
            items.dropLast(1) + last.copy(streaming = false)
        } else {
            items
        }
    }

    fun onEventBoundary(items: List<ChatItem>, eventType: String): List<ChatItem> =
        if (eventType in thinkingBoundaryTypes) finishThinking(items) else items
}

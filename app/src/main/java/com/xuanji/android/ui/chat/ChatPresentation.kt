package com.xuanji.android.ui.chat

import com.xuanji.android.ChatItem

internal sealed interface ChatRow {
    val key: String
    data class Message(val index: Int, val item: ChatItem) : ChatRow {
        override val key = "message:$index"
    }
    data class Execution(val index: Int, val events: List<ChatItem>) : ChatRow {
        override val key = "execution:$index"
    }
}

/** Preserve every source event while grouping the process preceding each final reply. */
internal fun chatPresentation(items: List<ChatItem>): List<ChatRow> = buildList {
    var cursor = 0
    while (cursor < items.size) {
        if (items[cursor] is ChatItem.User) {
            add(ChatRow.Message(cursor, items[cursor]))
            cursor++
        }
        val start = cursor
        while (cursor < items.size && items[cursor] !is ChatItem.User) cursor++
        if (start == cursor) continue
        val hasProcess = (start until cursor).any { items[it] is ChatItem.Thinking || items[it] is ChatItem.Tool }
        if (!hasProcess) {
            (start until cursor).forEach { add(ChatRow.Message(it, items[it])) }
        } else {
            var replyStart = cursor
            while (replyStart > start && items[replyStart - 1] is ChatItem.Assistant) replyStart--
            add(ChatRow.Execution(start, items.subList(start, replyStart)))
            (replyStart until cursor).forEach { add(ChatRow.Message(it, items[it])) }
        }
    }
}

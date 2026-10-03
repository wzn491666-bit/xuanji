package com.xuanji.android

import com.xuanji.android.ui.chat.ChatRow
import com.xuanji.android.ui.chat.chatPresentation
import org.junit.Assert.*
import org.junit.Test

class ChatPresentationTest {
    @Test fun groupingPreservesEveryEventInOrderAcrossTurns() {
        val events = listOf(ChatItem.User("one"), ChatItem.Thinking("thought", false),
            ChatItem.Assistant("stage"), ChatItem.Tool("t1", "read_file", "{}", "done", false),
            ChatItem.Assistant("answer"), ChatItem.User("two"), ChatItem.Assistant("simple answer"))
        val rows = chatPresentation(events)
        assertEquals(events, rows.flatMap { when (it) {
            is ChatRow.Message -> listOf(it.item)
            is ChatRow.Execution -> it.events
        } })
        assertEquals(1, rows.count { it is ChatRow.Execution })
        assertEquals("answer", (rows[2] as ChatRow.Message).item.let { (it as ChatItem.Assistant).text })
    }

    @Test fun toolAfterStageReplyRemainsInItsOriginalPosition() {
        val events = listOf(ChatItem.Assistant("stage"), ChatItem.Tool("t1", "web_search", "{}", null, true))
        val group = chatPresentation(events).single() as ChatRow.Execution
        assertEquals(events, group.events)
    }

    @Test fun streamingDoesNotChangeExistingRowKeys() {
        val base = listOf(ChatItem.User("request"), ChatItem.Thinking("first", true))
        val next = listOf(base[0], ChatItem.Thinking("first and second", true), ChatItem.Tool("t1", "read_file", "{}", null, true))
        assertEquals(chatPresentation(base).map { it.key }, chatPresentation(next).map { it.key })
        assertTrue(chatPresentation(emptyList()).isEmpty())
    }
}

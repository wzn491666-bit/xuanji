package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTimelineReducerTest {
    @Test
    fun appendsThinkingDeltasWithoutMergingAssistantText() {
        val items = listOf<ChatItem>(ChatItem.Assistant("答案"))

        val result = ChatTimelineReducer.appendThinking(items, "思考片段")

        assertEquals(2, result.size)
        assertEquals(ChatItem.Assistant("答案"), result[0])
        assertEquals(ChatItem.Thinking("思考片段", streaming = true), result[1])
    }

    @Test
    fun consecutiveThinkingDeltasAppendToTheActiveItem() {
        val first = ChatTimelineReducer.appendThinking(emptyList(), "先")

        val second = ChatTimelineReducer.appendThinking(first, "后")

        assertEquals(ChatItem.Thinking("先后", streaming = true), second.single())
    }

    @Test
    fun emptyDeltaIsIgnoredAndFinishedThinkingStartsANewItem() {
        val first = ChatTimelineReducer.appendThinking(emptyList(), "第一段")

        val ignored = ChatTimelineReducer.appendThinking(first, "")
        val finished = ChatTimelineReducer.finishThinking(ignored)
        val next = ChatTimelineReducer.appendThinking(finished, "第二段")

        assertEquals(first, ignored)
        assertEquals(ChatItem.Thinking("第一段", streaming = false), next[0])
        assertEquals(ChatItem.Thinking("第二段", streaming = true), next[1])
    }

    @Test
    fun finishThinkingLeavesAssistantTailUnchanged() {
        val items = listOf<ChatItem>(ChatItem.Assistant("回答"))

        assertEquals(items, ChatTimelineReducer.finishThinking(items))
    }

    @Test
    fun metadataEventsDoNotSplitAThinkingSegment() {
        val first = ChatTimelineReducer.appendThinking(emptyList(), "前半")

        val afterPhase = ChatTimelineReducer.onEventBoundary(first, "phase")
        val afterTodo = ChatTimelineReducer.onEventBoundary(afterPhase, "todo_state")
        val completed = ChatTimelineReducer.appendThinking(afterTodo, "后半")

        assertEquals(ChatItem.Thinking("前半后半", streaming = true), completed.single())
    }

    @Test
    fun answerAndToolEventsEndTheCurrentThinkingSegment() {
        val thinking = ChatTimelineReducer.appendThinking(emptyList(), "内容")

        assertEquals(ChatItem.Thinking("内容", streaming = false), ChatTimelineReducer.onEventBoundary(thinking, "text_delta").single())
        assertEquals(ChatItem.Thinking("内容", streaming = false), ChatTimelineReducer.onEventBoundary(thinking, "tool_use").single())
        assertEquals(ChatItem.Thinking("内容", streaming = false), ChatTimelineReducer.onEventBoundary(thinking, "done").single())
    }
}

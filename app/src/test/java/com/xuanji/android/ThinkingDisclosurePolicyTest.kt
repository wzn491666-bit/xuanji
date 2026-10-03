package com.xuanji.android

import com.xuanji.android.ui.chat.ChatRow
import com.xuanji.android.ui.chat.ThinkingBodyMode
import com.xuanji.android.ui.chat.chatPresentation
import com.xuanji.android.ui.chat.thinkingGroupMode
import com.xuanji.android.ui.chat.thinkingSegmentMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingDisclosurePolicyTest {
    @Test fun expandedPreferenceOpensOnlyTheGroup() {
        assertEquals(ThinkingBodyMode.FULL, thinkingGroupMode(ThinkingDisplay.EXPANDED, null))
        assertEquals(ThinkingBodyMode.PREVIEW, thinkingSegmentMode(ThinkingDisplay.EXPANDED, null))
    }

    @Test fun openingCollapsedGroupDoesNotOpenAnyChild() {
        assertEquals(ThinkingBodyMode.FULL, thinkingGroupMode(ThinkingDisplay.COLLAPSED, true))
        assertEquals(ThinkingBodyMode.HIDDEN, thinkingSegmentMode(ThinkingDisplay.COLLAPSED, null))
        assertEquals(ThinkingBodyMode.FULL, thinkingSegmentMode(ThinkingDisplay.COLLAPSED, true))
    }

    @Test fun explicitChoicesSurviveDefaultPreferenceChanges() {
        ThinkingDisplay.entries.forEach { display ->
            assertEquals(ThinkingBodyMode.FULL, thinkingGroupMode(display, true))
            assertEquals(ThinkingBodyMode.HIDDEN, thinkingGroupMode(display, false))
            assertEquals(ThinkingBodyMode.FULL, thinkingSegmentMode(display, true))
        }
        assertEquals(ThinkingBodyMode.PREVIEW, thinkingSegmentMode(ThinkingDisplay.EXPANDED, false))
        assertEquals(ThinkingBodyMode.PREVIEW, thinkingSegmentMode(ThinkingDisplay.PREVIEW, false))
        assertEquals(ThinkingBodyMode.HIDDEN, thinkingSegmentMode(ThinkingDisplay.COLLAPSED, false))
    }

    @Test fun streamingAndAdditionalStepsKeepTheChildIdentity() {
        val firstThought = ChatItem.Thinking("first", true)
        val before = listOf(ChatItem.User("question"), firstThought, ChatItem.Assistant("stage"))
        val after = listOf(before[0], firstThought.copy(text = "first and more", streaming = false), before[2],
            ChatItem.Tool("tool-1", "read_file", "{}", "ok"), ChatItem.Thinking("second", true))
        val firstGroup = chatPresentation(before).filterIsInstance<ChatRow.Execution>().single()
        val laterGroup = chatPresentation(after).filterIsInstance<ChatRow.Execution>().single()
        val choices = mapOf(0 to true)

        assertEquals(firstGroup.key, laterGroup.key)
        assertEquals(0, laterGroup.events.indexOfFirst { it is ChatItem.Thinking })
        assertEquals(3, laterGroup.events.indexOfLast { it is ChatItem.Thinking })
        assertEquals(ThinkingBodyMode.FULL, thinkingSegmentMode(ThinkingDisplay.PREVIEW, choices[0]))
        assertEquals(ThinkingBodyMode.PREVIEW, thinkingSegmentMode(ThinkingDisplay.PREVIEW, choices[3]))
    }
}

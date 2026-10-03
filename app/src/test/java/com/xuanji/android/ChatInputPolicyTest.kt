package com.xuanji.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputPolicyTest {
    @Test
    fun unconfirmedPromptBlocksAnotherSubmission() {
        val result = chatSendAvailability(TianshuService.State.RUNNING, true, false, "下一条", pendingPrompt = true)
        assertFalse(result.enabled)
    }
    @Test
    fun commandIsBlockedUntilSessionIsReady() {
        val result = chatSendAvailability(TianshuService.State.RUNNING, false, false, "/plan ")
        assertFalse(result.enabled)
        assertTrue(result.message!!.contains("会话"))
    }

    @Test
    fun commandCanBeSentOnceServiceAndSessionAreReady() {
        val result = chatSendAvailability(TianshuService.State.RUNNING, true, false, "/plan 只读检查")
        assertTrue(result.enabled)
    }

    @Test
    fun blankCommandIsRejected() {
        assertFalse(chatSendAvailability(TianshuService.State.RUNNING, true, false, "  ").enabled)
    }
}

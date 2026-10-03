package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserPromptLedgerTest {
    @Test
    fun expandedServerPromptIsConsumedAfterLocalSlashInput() {
        val ledger = UserPromptLedger()
        ledger.record("request-1", "/plan 做一个表情包应用")

        assertEquals("request-1", ledger.consumeServerEvent("Run planning workflow with the task: 做一个表情包应用"))
        assertNull(ledger.consumeServerEvent("另一个回放事件"))
    }

    @Test
    fun clearRemovesPendingPrompt() {
        val ledger = UserPromptLedger()
        ledger.record("request-1", "/review")
        ledger.clear()

        assertNull(ledger.consumeServerEvent("expanded review prompt"))
    }

    @Test
    fun multipleLocalPromptsConsumeOneServerEchoEach() {
        val ledger = UserPromptLedger()
        ledger.record("request-1", "/plan one")
        ledger.record("request-2", "/review two")

        assertEquals("request-1", ledger.consumeServerEvent("expanded plan"))
        assertEquals("request-2", ledger.consumeServerEvent("expanded review"))
        assertNull(ledger.consumeServerEvent("replayed event"))
    }

    @Test
    fun rejectedPromptCannotConsumeTheNextServerEcho() {
        val ledger = UserPromptLedger()
        ledger.record("rejected", "/unknown")
        ledger.remove("rejected")
        ledger.record("accepted", "Hello")

        assertEquals("accepted", ledger.consumeServerEvent("Hello"))
    }

    @Test
    fun replayedUserEventBeforeSubmissionCannotConfirmNewPrompt() {
        val ledger = UserPromptLedger()
        ledger.record("new-request", "Hello", submittedAt = 2_000)

        assertNull(ledger.consumeServerEvent("old message", eventAt = 1_000))
        assertEquals("new-request", ledger.consumeServerEvent("Hello", eventAt = 2_100))
    }
}

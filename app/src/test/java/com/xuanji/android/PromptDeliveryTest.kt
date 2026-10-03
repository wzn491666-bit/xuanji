package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PromptDeliveryTest {
    @Test
    fun lateHttpFailureCannotUndoConfirmedServerEcho() {
        val items = listOf<ChatItem>(ChatItem.User("/plan test", "request-1", PromptDelivery.DELIVERED))

        val updated = updatePromptDelivery(items, "request-1", PromptDelivery.UNCERTAIN)

        assertEquals(PromptDelivery.DELIVERED, (updated.single() as ChatItem.User).delivery)
    }

    @Test
    fun durableAcceptanceDoesNotBlockTheComposerWhileEchoIsDelayed() {
        val items = listOf<ChatItem>(ChatItem.User("hello", "request-1", PromptDelivery.ACCEPTED))

        assertFalse(items.hasUnconfirmedPrompt())
    }
}

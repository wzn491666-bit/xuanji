package com.xuanji.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class ChatControlsTest {
    @Test fun glowStartsForSendingAndContinuesThroughThinkingAndTools() {
        val user = ChatItem.User("Question", requestId = "new", delivery = PromptDelivery.SENDING)
        assertTrue(awaitingAssistantReply(listOf(user), running = false, stopping = false))
        val working = listOf(user.copy(delivery = PromptDelivery.DELIVERED),
            ChatItem.Thinking("Thinking", true), ChatItem.Tool("t", "read_file", "{}"))
        assertTrue(awaitingAssistantReply(working, running = true, stopping = false))
        assertFalse(awaitingAssistantReply(working, running = true, stopping = true))
        assertFalse(awaitingAssistantReply(working, running = false, stopping = false))
    }

    @Test fun glowStopsAtTheFirstAnswerAndDoesNotReplayCompletedHistory() {
        val history = listOf(ChatItem.User("Old"), ChatItem.Assistant("Answer"))
        assertFalse(awaitingAssistantReply(history, running = false, stopping = false))
        val fresh = history + ChatItem.User("New", delivery = PromptDelivery.SENDING)
        assertTrue(awaitingAssistantReply(fresh, running = false, stopping = false))
        assertFalse(awaitingAssistantReply(fresh + ChatItem.Assistant("Started"), running = true, stopping = false))
        assertTrue(awaitingAssistantReply(fresh + ChatItem.Assistant(""), running = true, stopping = false))
    }

    @Test fun glowDoesNotPresentRejectedOrUncertainSubmissionsAsActiveWork() {
        listOf(PromptDelivery.REJECTED, PromptDelivery.UNCERTAIN).forEach { delivery ->
            assertFalse(awaitingAssistantReply(listOf(ChatItem.User("Failed", delivery = delivery)), true, false))
        }
        assertFalse(awaitingAssistantReply(emptyList(), true, false))
    }
    @Test fun modelPickerPreservesProviderAndKeyIdentity() {
        val models = parseSessionModels(Json.parseToJsonElement("""{"models":[
          {"id":"shared","alias":"Fast","provider":"deepseek","keyId":"first","current":true},
          {"id":"shared","alias":"Fast","provider":"deepseek","keyId":"second","current":false}
        ]}""").jsonObject)
        assertEquals(listOf("deepseek:first:shared", "deepseek:second:shared"), models.map { it.spec })
        val profile = ProviderProfile("deepseek", "DeepSeek", true, true, false, listOf(ProviderModel("shared", true, true)))
        assertEquals("shared", activeProviderModel(listOf(profile), models[1].spec)?.second?.id)
    }
    @Test fun deepseekDoesNotOfferTwoLabelsForTheSameWireLevel() {
        assertEquals(listOf("off", "low", "high", "max"), reasoningLevels("deepseek", true))
        assertTrue(reasoningLevels("custom", false).isEmpty())
        assertFalse(reasoningLevels("openai", true).contains("off"))
    }

    @Test fun historyKeepsDistinctSessionsAndSortsByRealUpdatedTime() {
        val entries = parseSessionHistory(Json.parseToJsonElement("""{"sessions":[
          {"id":"a","title":"Older","status":"completed","updatedAt":10},
          {"id":"b","title":"Newer","status":"aborted","updatedAt":20},
          {"title":"Invalid"}
        ]}""").jsonObject)
        assertEquals(listOf("b", "a"), entries.map { it.id })
        assertEquals("aborted", entries.first().status)
    }
}

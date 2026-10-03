package com.xuanji.android

import com.xuanji.android.ui.chat.NativeSlashResult
import com.xuanji.android.ui.chat.PaletteAction
import com.xuanji.android.ui.chat.resolveNativeSlash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSlashCommandTest {
    @Test
    fun planCommandsReachNativeReviewAndPreserveFeedback() {
        listOf("/plan-list", "/plan-view", "/plan-approve", "/plan-reject", "/plan-edit sample")
            .forEach { assertTrue(it, resolveNativeSlash(it) is NativeSlashResult.Execute) }
        val request = (resolveNativeSlash("/plan-reject sample 请保留用户数据，补充测试") as NativeSlashResult.Execute).request
        assertEquals("sample 请保留用户数据，补充测试", request.argument)
        assertTrue(resolveNativeSlash("/plan-view a b") is NativeSlashResult.Invalid)
        assertTrue(resolveNativeSlash("/plan-list extra") is NativeSlashResult.Invalid)
    }
    @Test
    fun serverControlsResolveLocallyAndRejectUnsupportedArguments() {
        listOf("/domain", "/domain list", "/domain tianshu", "/ask on", "/plan-mode off", "/fast", "/review off", "/review status")
            .forEach { assertTrue(it, resolveNativeSlash(it) is NativeSlashResult.Execute) }
        listOf("/domain tianshu extra", "/ask maybe", "/plan-mode maybe", "/fast on")
            .forEach { assertTrue(it, resolveNativeSlash(it) is NativeSlashResult.Invalid) }
    }
    @Test
    fun modelAndHistoryArgumentsReachTheirNativeHandlers() {
        val model = (resolveNativeSlash(" /MODEL provider:key:model-id ") as NativeSlashResult.Execute).request
        assertEquals(PaletteAction.MODELS, model.action)
        assertEquals("provider:key:model-id", model.argument)
        val resume = (resolveNativeSlash("/resume 12") as NativeSlashResult.Execute).request
        assertEquals(PaletteAction.HISTORY, resume.action)
        assertEquals("12", resume.argument)
    }

    @Test
    fun invalidArgumentsCannotSilentlyTurnIntoDifferentOperations() {
        listOf("/new discard history", "/resume id extra", "/effort super", "/thinking off", "/todo clear", "/tools enable x", "/permission full")
            .forEach { assertTrue(it, resolveNativeSlash(it) is NativeSlashResult.Invalid) }
    }

    @Test
    fun thinkingAndApprovalAcceptTheExistingWireValues() {
        assertEquals("expanded", (resolveNativeSlash("/thinking expanded") as NativeSlashResult.Execute).request.argument)
        assertEquals("dangerously-skip-permissions", (resolveNativeSlash("/permission dangerously-skip-permissions") as NativeSlashResult.Execute).request.argument)
    }

    @Test
    fun skillInvocationAndWorkflowArgumentsStayOnTheSidecarPath() {
        assertEquals(PaletteAction.SKILLS, (resolveNativeSlash("/skill list") as NativeSlashResult.Execute).request.action)
        assertNull(resolveNativeSlash("/skill qa check the app"))
        assertNull(resolveNativeSlash("/team max implement the app"))
        assertNull(resolveNativeSlash("/review l3 security"))
        assertNull(resolveNativeSlash("/release version 2"))
        assertNull(resolveNativeSlash("plain question"))
    }
}

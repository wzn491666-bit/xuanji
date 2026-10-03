package com.xuanji.android

import com.xuanji.android.ui.chat.*
import org.junit.Assert.*
import org.junit.Test

class NativeCommandPolicyTest {
    private fun request(text: String) = (resolveNativeSlash(text) as NativeSlashResult.Execute).request

    @Test fun goalsAndPlansDistinguishReadingPausingAndStartingWork() {
        listOf("/goal-status", "/goal-pause", "/goal-cancel", "/plan-list", "/plan-view sample").forEach {
            assertFalse(it, nativeSlashBlocked(request(it), canSwitch = false, sessionReady = true))
            assertTrue(it, nativeSlashBlocked(request(it), canSwitch = false, sessionReady = false))
            assertTrue(it, nativeSlashBlocked(request(it), canSwitch = false, sessionReady = true, controlBusy = true))
        }
        listOf("/goal task", "/goal-resume", "/plan-approve sample", "/plan-reject sample feedback", "/plan-edit sample").forEach {
            assertTrue(it, nativeSlashBlocked(request(it), canSwitch = false, sessionReady = true))
            assertFalse(it, nativeSlashBlocked(request(it), canSwitch = true, sessionReady = true))
        }
    }

    @Test fun domainReadCanRunDuringWorkButMutationsRequireIdleAndConnectedSession() {
        assertFalse(nativeSlashBlocked(request("/domain list"), canSwitch = false, sessionReady = true))
        assertFalse(nativeSlashBlocked(request("/domain LIST"), canSwitch = false, sessionReady = true))
        assertFalse(nativeSlashBlocked(request("/plan-mode status"), canSwitch = false, sessionReady = true))
        assertTrue(nativeSlashBlocked(request("/domain tianshu"), canSwitch = false, sessionReady = true))
        assertTrue(nativeSlashBlocked(request("/domain"), canSwitch = true, sessionReady = false))
        listOf("/plan-mode on", "/ask on").forEach {
            assertTrue(nativeSlashBlocked(request(it), canSwitch = false, sessionReady = true))
        }
        assertFalse(nativeSlashBlocked(request("/fast"), canSwitch = false, sessionReady = true))
        assertTrue(nativeSlashBlocked(request("/fast"), canSwitch = false, sessionReady = true, stopping = true))
    }

    @Test fun disconnectedSessionCanOpenLocalControlsAndReconnect() {
        listOf("/help", "/settings", "/reconnect", "/effort", "/permission", "/model", "/sessions", "/tools status", "/mcp status").forEach {
            assertFalse(it, nativeSlashBlocked(request(it), canSwitch = false, sessionReady = false))
        }
    }

    @Test fun modelListIsReadableDuringTaskButSwitchRequiresReadySession() {
        assertFalse(nativeSlashBlocked(request("/model list"), canSwitch = false, sessionReady = false))
        assertTrue(nativeSlashBlocked(request("/model deepseek:default:reasoner"), canSwitch = true, sessionReady = false))
        assertTrue(nativeSlashBlocked(request("/model reasoner"), canSwitch = false, sessionReady = true))
        assertFalse(nativeSlashBlocked(request("/model reasoner"), canSwitch = true, sessionReady = true))
    }

    @Test fun offlineAndBusySessionsCannotSilentlyAcceptControlUpdates() {
        listOf("/effort max", "/permission auto-safe").forEach {
            assertTrue(nativeSlashBlocked(request(it), canSwitch = true, sessionReady = false))
            assertTrue(nativeSlashBlocked(request(it), canSwitch = true, sessionReady = true, controlBusy = true))
            assertTrue(nativeSlashBlocked(request(it), canSwitch = true, sessionReady = true, stopping = true))
        }
    }

    @Test fun historySelectionAndNewSessionRespectInFlightWork() {
        assertTrue(nativeSlashBlocked(request("/new"), canSwitch = false, sessionReady = true))
        assertTrue(nativeSlashBlocked(request("/resume 2"), canSwitch = false, sessionReady = true))
        assertFalse(nativeSlashBlocked(request("/sessions"), canSwitch = false, sessionReady = true))
    }
}

package com.xuanji.android

import com.xuanji.android.ui.chat.NativeSlashResult
import com.xuanji.android.ui.chat.PaletteAction
import com.xuanji.android.ui.chat.nativeSlashBlocked
import com.xuanji.android.ui.chat.resolveNativeSlash
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SessionQueueTest {
    @Test fun commandsResolveWithoutConsumingOrdinarySkillWorkflows() {
        val expected = mapOf("/compact" to PaletteAction.COMPACT, "/fork 新分支" to PaletteAction.FORK,
            "/handoff 保留所有决策" to PaletteAction.HANDOFF, "/team-resume team-1" to PaletteAction.TEAM_RESUME,
            "/queue 后续任务" to PaletteAction.QUEUE, "/steer 优先测试" to PaletteAction.STEER,
            "/skill off qa" to PaletteAction.SKILL_MANAGE, "/skill enable qa" to PaletteAction.SKILL_MANAGE)
        expected.forEach { (text, action) -> assertEquals(text, action, request(text).action) }
        assertNull(resolveNativeSlash("/skill qa 详细检查"))
        listOf("/compact extra", "/team-resume ../team", "/skill on", "/skill off qa extra",
            "/queue retract", "/queue retract a b", "/steer --lane", "/steer --lane a b")
            .forEach { assertTrue(it, resolveNativeSlash(it) is NativeSlashResult.Invalid) }
        assertEquals("on\tqa", request("/skill on\tqa").argument)
    }

    @Test fun queueAndSteerOnlyMutateWhileRunningButListAndRetractWorkIdle() {
        listOf("/queue followup", "/steer guide", "/steer --lane a").forEach {
            assertTrue(it, nativeSlashBlocked(request(it), true, true, running = false))
            assertFalse(it, nativeSlashBlocked(request(it), false, true, running = true))
            assertTrue(it, nativeSlashBlocked(request(it), false, true, stopping = true, running = true))
            assertTrue(it, nativeSlashBlocked(request(it), false, false, running = true))
        }
        listOf("/queue", "/queue list", "/queue retract a", "/steer", "/team-resume").forEach {
            assertFalse(it, nativeSlashBlocked(request(it), true, true, running = false))
            assertFalse(it, nativeSlashBlocked(request(it), false, true, running = true))
        }
        listOf("/compact", "/fork", "/handoff", "/team-resume team-1", "/skill on qa").forEach {
            assertTrue(it, nativeSlashBlocked(request(it), false, true, running = true))
            assertFalse(it, nativeSlashBlocked(request(it), true, true, running = false))
        }
    }

    @Test fun queuePayloadsKeepTextDistinctFromLaneControl() {
        assertEquals(QueueCommand.Enqueue("retract the test"), parseQueueCommand("-- retract the test"))
        assertEquals(QueueCommand.Retract("lane-1"), parseQueueCommand("retract lane-1"))
        assertEquals(SteerCommand.Text("lane-1"), parseSteerCommand("lane-1"))
        assertEquals(SteerCommand.Lane("lane-1"), parseSteerCommand("--lane lane-1"))
    }

    @Test fun authoritativeSnapshotWinsOverOldReplayAndReportsRestartLoss() {
        val old = TianshuQueueSnapshot(listOf(TianshuQueueEntry("a", "followup", "queued", 1000)), 10)
        val restarted = reconcileQueueSnapshot(old, TianshuQueueSnapshot(emptyList(), 15))
        assertEquals("interrupted", restarted.entries.single().status)
        val delayed = event(12, "queue_pending", """{"laneId":"a","text":"followup"}""")
        assertEquals(restarted, queueAfterEvent(restarted, delayed))
        assertEquals(restarted, reconcileQueueSnapshot(restarted, old))
        val fresh = event(16, "queue_pending", """{"laneId":"b","text":"new","imageCount":1,"documentNames":["a.pdf"]}""")
        val next = queueAfterEvent(restarted, fresh)!!
        assertTrue(next.entries.first { it.id == "b" }.hasAttachments)
        val merged = queueAfterEvent(next, event(17, "queue_status", """{"laneId":"b","status":"merged"}"""))!!
        assertEquals("merged", merged.entries.first { it.id == "b" }.status)
    }

    @Test fun unknownQueueStatusOrSessionMismatchCannotAppearAsReady() {
        val bad = Json.parseToJsonElement("""{"id":"s","seq":1,"lanes":[{"id":"a","text":"x","status":"unknown","ts":1}]}""").jsonObject
        assertTrue(runCatching { parseTianshuQueue(bad, "s") }.isFailure)
        assertTrue(runCatching { parseTianshuQueue(bad, "other") }.isFailure)
    }

    private fun request(text: String) = (resolveNativeSlash(text) as NativeSlashResult.Execute).request
    private fun event(seq: Long, type: String, data: String) = SessionEvent(seq, type, Json.parseToJsonElement(data).jsonObject, 2000)
}

package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundJobsReducerTest {
    @Test
    fun reconnectSnapshotPreventsOldStartedEventFromResurrectingAJob() {
        val snapshot = SessionEvent.parse("""{"seq":0,"ts":2000,"type":"job_snapshot","data":{"jobs":[]}}""")
        val oldStart = SessionEvent.parse("""{"seq":4,"ts":1000,"type":"job","data":{"id":"old","command":"sleep 10","status":"running","startedAt":900,"lastLine":"","kind":"started"}}""")

        val state = BackgroundJobsReducer.onEvent(BackgroundJobsReducer.onEvent(BackgroundJobsState(), snapshot), oldStart)

        assertFalse(state.jobs.any { it.id == "old" && it.status == "running" })
    }

    @Test
    fun liveJobEventsUpdateOutputAndExitStatus() {
        val snapshot = SessionEvent.parse("""{"seq":0,"ts":2000,"type":"job_snapshot","data":{"jobs":[]}}""")
        val started = SessionEvent.parse("""{"seq":5,"ts":2100,"type":"job","data":{"id":"new","command":"npm run dev","status":"running","startedAt":2050,"lastLine":"ready","kind":"started"}}""")
        val output = SessionEvent.parse("""{"seq":6,"ts":2200,"type":"job","data":{"id":"new","command":"npm run dev","status":"running","startedAt":2050,"lastLine":"compiled","kind":"output","chunk":"compiled\n"}}""")
        val exited = SessionEvent.parse("""{"seq":7,"ts":2300,"type":"job","data":{"id":"new","command":"npm run dev","status":"exited","exitCode":0,"startedAt":2050,"endedAt":2300,"lastLine":"compiled","kind":"exit"}}""")

        val state = listOf(snapshot, started, output, exited).fold(BackgroundJobsState(), BackgroundJobsReducer::onEvent)

        assertEquals("exited", state.jobs.single().status)
        assertEquals(0, state.jobs.single().exitCode)
        assertTrue(state.jobs.single().recentOutput.contains("compiled"))
    }

    @Test
    fun reconnectSnapshotKeepsOnlyActuallyRunningJobs() {
        val prior = BackgroundJobsState(jobs = listOf(BackgroundJob("lost", "watch", "running", 1000)))
        val snapshot = SessionEvent.parse("""{"seq":0,"ts":2000,"type":"job_snapshot","data":{"jobs":[{"id":"live","command":"serve","status":"running","startedAt":1500,"lastLine":"ready"}]}}""")

        val state = BackgroundJobsReducer.onEvent(prior, snapshot)

        assertEquals(listOf("live"), state.jobs.map { it.id })
    }
}

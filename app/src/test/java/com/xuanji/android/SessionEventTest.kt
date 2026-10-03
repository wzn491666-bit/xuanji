package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Test

/** 天枢 SSE 事件解析：格式来自天枢 src/server/protocol.ts 的 SessionEvent */
class SessionEventTest {
    @Test
    fun parsesToolResult() {
        val e = SessionEvent.parse(
            """{"seq":12,"ts":1,"type":"tool_result","data":{"id":"t1","name":"bash","isError":false,"result":"ok"}}"""
        )
        assertEquals(12L, e.seq)
        assertEquals("tool_result", e.type)
        assertEquals("ok", e.str("result"))
        assertEquals(null, e.str("missing"))
    }

    @Test
    fun syntheticEventsWithoutDataDoNotCrash() {
        val e = SessionEvent.parse("""{"seq":0,"type":"replay_window"}""")
        assertEquals(0L, e.seq)
        assertEquals(null, e.str("floorSeq"))
    }

    @Test
    fun parsesThinkingDelta() {
        val e = SessionEvent.parse(
            """{"seq":13,"type":"thinking_delta","data":{"text":"考虑步骤"}}"""
        )

        assertEquals("thinking_delta", e.type)
        assertEquals("考虑步骤", e.str("text"))
    }
}

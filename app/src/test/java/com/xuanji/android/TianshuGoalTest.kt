package com.xuanji.android

import com.xuanji.android.ui.chat.NativeSlashResult
import com.xuanji.android.ui.chat.resolveNativeSlash
import org.junit.Assert.*
import org.junit.Test

class TianshuGoalTest {
    @Test fun quotedCriteriaAndBudgetsPreserveUnicodeAndEscapeCharacters() {
        val config = parseGoalConfig("整理 Android 工作区 --max 12 --budget 1.5 --criteria '[\"保留数据\",\"测试通过\"]'")
        assertEquals("整理 Android 工作区", config.goal)
        assertEquals(12, config.maxIterations)
        assertEquals(90000L, config.wallClockMs)
        assertEquals(listOf("保留数据", "测试通过"), config.successCriteria)
        val special = TianshuGoalConfig("检查 C:\\folder\\'示例' 和 \"图片\"", 5, 90000L, listOf("don't delete", "含 \"引号\""))
        assertEquals(special, parseGoalConfig(goalCommandArgument(special)))
    }

    @Test fun badQuotesDuplicateFlagsAndNonStringCriteriaAreRejected() {
        listOf("task --max 1 --max 2", "task --criteria '[1]'", "task --criteria '{}'", "task --budget Infinity",
            "task --max", "'unclosed", "task --max 2147483648")
            .forEach { assertNotNull(it, runCatching { parseGoalConfig(it) }.exceptionOrNull()) }
    }

    @Test fun kickoffUsesTheOfficialCompletionMarkers() {
        assertTrue(goalKickoff("完成目标").startsWith("[GOAL MODE] 完成目标\n\n"))
        assertTrue(goalKickoff("完成目标").contains("GOAL ACHIEVED"))
        assertTrue(goalKickoff("完成目标").contains("GOAL BLOCKED"))
        assertEquals("/goal 完成目标", displayGoalPrompt(goalKickoff("完成目标")))
        assertEquals("[GOAL MODE] 用户自己的消息", displayGoalPrompt("[GOAL MODE] 用户自己的消息"))
        assertEquals(2L, parseGoalConfig("task --budget 0.000025").wallClockMs)
    }
    @Test fun goalLifecycleCommandsAreNativeAndInvalidFlagsNeverRunAsTasks() {
        listOf("/goal", "/goal-status", "/goal-pause", "/goal-resume", "/goal-cancel", "/goal-criteria")
            .forEach { assertTrue(it, resolveNativeSlash(it) is NativeSlashResult.Execute) }
        listOf("/goal task --max 0", "/goal task --budget NaN", "/goal task --unknown 4", "/goal-criteria set []")
            .forEach { assertTrue(it, resolveNativeSlash(it) is NativeSlashResult.Invalid) }
    }
}

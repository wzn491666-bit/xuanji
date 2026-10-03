package com.xuanji.android

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogTest {
    @Test
    fun oversizedLinesAreCappedAndTheNextLineIsPreserved() {
        val lines = mutableListOf<String>()
        readBoundedLogLines(StringReader("x".repeat(100_000) + "\nready\n"), 128, {}, lines::add)
        assertEquals(2, lines.size)
        assertTrue(lines.first().length <= 150)
        assertTrue(lines.first().endsWith("[truncated]"))
        assertEquals("ready", lines.last())
    }
}

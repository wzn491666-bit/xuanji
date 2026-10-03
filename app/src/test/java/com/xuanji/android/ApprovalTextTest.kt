package com.xuanji.android

import com.xuanji.android.ui.chat.approvalTextChunks
import org.junit.Assert.*
import org.junit.Test

class ApprovalTextTest {
    @Test fun fullTextIsPreservedAcrossChunkBoundariesWithoutSplittingSurrogates() {
        val text = "a".repeat(3999) + "\uD83D\uDE80" + "b".repeat(8000)
        val chunks = approvalTextChunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 4000 })
        assertTrue(chunks.none { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
        assertTrue(approvalTextChunks("").isEmpty())
    }
}

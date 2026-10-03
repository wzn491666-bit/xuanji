package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Test

class FilePreviewPaginationTest {
    @Test
    fun splitsLongPreviewsOnLineBoundariesAndPreservesSource() {
        val source = (1..5).joinToString("\n") { "line $it" }

        val pages = splitFilePreviewPages(source, linesPerPage = 2)

        assertEquals(listOf(1, 3, 5), pages.map { it.startLine })
        assertEquals(listOf(2, 2, 1), pages.map { it.lineCount })
        assertEquals(source, pages.joinToString("") { it.content })
    }

    @Test
    fun doesNotCreateAnEmptyTrailingPageWhenSourceEndsWithANewline() {
        val source = "one\ntwo\nthree\n"

        val pages = splitFilePreviewPages(source, linesPerPage = 2)

        assertEquals(listOf(1, 3), pages.map { it.startLine })
        assertEquals(source, pages.joinToString("") { it.content })
    }

    @Test
    fun emptyContentStillHasOneRenderablePage() {
        assertEquals(listOf(FilePreviewPage(1, "")), splitFilePreviewPages("", linesPerPage = 4))
    }

    @Test
    fun boundsPagesForVeryLongSingleLineFiles() {
        val pages = splitFilePreviewPages("abcdefghijkl", linesPerPage = 4, maxCharactersPerPage = 5)

        assertEquals(listOf("abcde", "fghij", "kl"), pages.map { it.content })
        assertEquals(listOf(1, 1, 1), pages.map { it.startLine })
        assertEquals(listOf(false, true, true), pages.map { it.continuesPreviousLine })
    }

    @Test
    fun veryLargePageCharacterLimitDoesNotOverflowAfterLineSplit() {
        val source = (1..5).joinToString("\n") { "line $it" }

        val pages = splitFilePreviewPages(source, linesPerPage = 2, maxCharactersPerPage = Int.MAX_VALUE)

        assertEquals(source, pages.joinToString("") { it.content })
        assertEquals(listOf(1, 3, 5), pages.map { it.startLine })
    }
}

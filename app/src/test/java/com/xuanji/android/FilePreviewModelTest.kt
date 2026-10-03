package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Test

class FilePreviewModelTest {
    @Test
    fun recognizesMarkdownAndCodeFiles() {
        assertEquals(FilePreviewFormat.MARKDOWN, previewFormatForPath("/sdcard/README.md"))
        assertEquals(FilePreviewFormat.CODE, previewFormatForPath("/sdcard/src/Main.kt"))
        assertEquals(FilePreviewFormat.CODE, previewFormatForPath("/sdcard/config.json"))
        assertEquals(FilePreviewFormat.TEXT, previewFormatForPath("/sdcard/notes.txt"))
        assertEquals(FilePreviewFormat.IMAGE, previewFormatForPath("/sdcard/photos/portrait.webp"))
        assertEquals(FilePreviewFormat.IMAGE, previewFormatForPath("/sdcard/cover.JPEG"))
    }

    @Test
    fun extensionMatchingIsCaseInsensitiveAndIgnoresQueryLikeNames() {
        assertEquals(FilePreviewFormat.MARKDOWN, previewFormatForPath("/sdcard/Guide.MARKDOWN"))
        assertEquals(FilePreviewFormat.CODE, previewFormatForPath("/sdcard/theme.CSS"))
        assertEquals(FilePreviewFormat.TEXT, previewFormatForPath("/sdcard/archive.tar"))
    }

    @Test
    fun highlightsCodeTokensWithoutChangingSourceOffsets() {
        val source = "val answer = 42 // note"
        val spans = codeSpansForPreview(source, "Main.kt")

        assertEquals(listOf("val", "42", "// note"), spans.map { source.substring(it.start, it.end) })
        assertEquals(listOf(CodeSpanKind.KEYWORD, CodeSpanKind.NUMBER, CodeSpanKind.COMMENT), spans.map { it.kind })
    }

    @Test
    fun recognizesHashCommentsForPythonAndShell() {
        val source = "# note\nprint('hello')"
        val spans = codeSpansForPreview(source, "script.py")

        assertEquals("# note", source.substring(spans.first().start, spans.first().end))
        assertEquals(CodeSpanKind.COMMENT, spans.first().kind)
    }

    @Test
    fun truncatedUtf8DropsOnlyAnIncompleteTrailingCodePoint() {
        assertEquals("璇玑", decodeUtf8Preview("璇玑中".toByteArray().copyOf(7), truncated = true))
        assertEquals(null, decodeUtf8Preview(byteArrayOf(0xC3.toByte(), 0x28), truncated = false))
    }
}

package com.xuanji.android

import com.xuanji.android.ui.chat.*
import org.junit.Assert.*
import org.junit.Test

class MarkdownModelTest {
    private fun text(value: String) = listOf(MarkdownInline.Text(value))

    @Test fun thinkingPreviewRemovesFormattingButPreservesLiteralCode() {
        val preview = MarkdownParser.plainText("# Heading\n\n**Strong** and `a_b #literal`.\n\n```\nx*y\n```")
        assertTrue(preview.contains("Heading"))
        assertTrue(preview.contains("Strong"))
        assertTrue(preview.contains("a_b #literal"))
        assertTrue(preview.contains("x*y"))
        assertFalse(preview.contains("# Heading"))
        assertFalse(preview.contains("**Strong**"))
        assertFalse(preview.contains("```"))
    }

    @Test fun thinkingPreviewRetainsTableContent() {
        val preview = MarkdownParser.plainText("| **Name** | Result |\n| --- | --- |\n| a\\|b | `pass` |")
        assertTrue(preview.contains("Name"))
        assertTrue(preview.contains("a|b"))
        assertTrue(preview.contains("pass"))
        assertFalse(preview.contains("**Name**"))
    }

    @Test fun parsesHeadingsAndNestedInlineFormatting() {
        val blocks = MarkdownParser.parse("# 标题\n\n**重点和 *斜体*** 与 `id`。")
        assertEquals(MarkdownBlock.Heading(1, text("标题")), blocks[0])
        assertEquals(
            listOf(MarkdownInline.Strong(listOf(MarkdownInline.Text("重点和 "), MarkdownInline.Emphasis(text("斜体")))), MarkdownInline.Text(" 与 "), MarkdownInline.Code("id"), MarkdownInline.Text("。")),
            (blocks[1] as MarkdownBlock.Paragraph).content,
        )
    }

    @Test fun escapedPipeRemainsInsideTableCell() {
        val table = MarkdownParser.parse("| Name | Result |\n| --- | --- |\n| a\\|b | pass |").single() as MarkdownBlock.Table
        assertEquals(listOf(listOf(MarkdownCell(text("a|b")), MarkdownCell(text("pass")))), table.rows)
    }

    @Test fun tildeFenceIsCodeRatherThanLiteralMarkdown() {
        assertEquals(MarkdownBlock.Code("text", "hello"), MarkdownParser.parse("~~~text\nhello\n~~~").single())
    }

    @Test fun tableCanFollowParagraphWithoutBlankLine() {
        val blocks = MarkdownParser.parse("Summary\n| Name | Result |\n| --- | --- |\n| a | pass |")
        assertEquals(2, blocks.size)
        assertTrue(blocks[1] is MarkdownBlock.Table)
    }

    @Test fun preservesOrderedStartAndNestedBlocks() {
        val block = MarkdownParser.parse("3. first\n   - nested\n4. second").single() as MarkdownBlock.ListBlock
        assertEquals(3, block.startNumber)
        assertEquals(2, block.items.size)
        assertTrue(block.items[0][1] is MarkdownBlock.ListBlock)
    }

    @Test fun tableRetainsAlignmentAndInlineFormatting() {
        val table = MarkdownParser.parse("| **Name** | Result |\n| :--- | ---: |\n| x | `ok` |").single() as MarkdownBlock.Table
        assertEquals(MarkdownAlignment.RIGHT, table.headers[1].alignment)
        assertEquals(listOf(MarkdownInline.Strong(text("Name"))), table.headers[0].content)
        assertEquals(listOf(MarkdownInline.Code("ok")), table.rows[0][1].content)
    }

    @Test fun closesUnterminatedFenceAtEndOfInput() {
        assertEquals(MarkdownBlock.Code("", "未闭合\n代码"), MarkdownParser.parse("```\n未闭合\n代码").single())
    }

    @Test fun preservesLinksAndTreatsHtmlAsLiteralText() {
        val p = MarkdownParser.parse("[site](https://example.com) <b>plain</b>").single() as MarkdownBlock.Paragraph
        assertEquals(MarkdownInline.Link("https://example.com", text("site")), p.content.first())
        assertTrue(p.content.contains(MarkdownInline.Text("<b>")))
        assertFalse(safeMarkdownLink("javascript:alert(1)"))
        assertFalse(safeMarkdownLink("file:///sdcard/private.txt"))
        assertFalse(safeMarkdownLink("intent://settings"))
        assertFalse(safeMarkdownLink("https:broken"))
        assertTrue(safeMarkdownLink("https://example.com/path"))
    }

    @Test fun handlesPlainPipesAndStrikethrough() {
        assertEquals(MarkdownBlock.Paragraph(text("a | b") + MarkdownInline.Text("\n") + text("c | d")), MarkdownParser.parse("a | b\nc | d").single())
        val p = MarkdownParser.parse("~~old~~").single() as MarkdownBlock.Paragraph
        assertEquals(listOf(MarkdownInline.Strike(text("old"))), p.content)
    }
}

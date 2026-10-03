package com.xuanji.android.ui.chat

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.text.TextContentRenderer
import java.net.URI

sealed interface MarkdownBlock {
    data class Paragraph(val content: List<MarkdownInline>) : MarkdownBlock
    data class Heading(val level: Int, val content: List<MarkdownInline>) : MarkdownBlock
    data class Code(val language: String, val text: String) : MarkdownBlock
    data class Quote(val blocks: List<MarkdownBlock>) : MarkdownBlock
    data class ListBlock(val items: List<List<MarkdownBlock>>, val startNumber: Int? = null) : MarkdownBlock
    data class Table(val headers: List<MarkdownCell>, val rows: List<List<MarkdownCell>>) : MarkdownBlock
    data object Rule : MarkdownBlock
}

enum class MarkdownAlignment { LEFT, CENTER, RIGHT }
data class MarkdownCell(val content: List<MarkdownInline>, val alignment: MarkdownAlignment = MarkdownAlignment.LEFT)

sealed interface MarkdownInline {
    data class Text(val value: String) : MarkdownInline
    data class Strong(val children: List<MarkdownInline>) : MarkdownInline
    data class Emphasis(val children: List<MarkdownInline>) : MarkdownInline
    data class Code(val value: String) : MarkdownInline
    data class Link(val destination: String, val children: List<MarkdownInline>) : MarkdownInline
    data class Strike(val children: List<MarkdownInline>) : MarkdownInline
}

object MarkdownParser {
    private val textRenderer = TextContentRenderer.builder()
        .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create()))
        .build()
    private val parser = Parser.builder()
        .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create()))
        .build()

    fun parse(source: String): List<MarkdownBlock> = blocks(parser.parse(source))

    fun plainText(source: String): String = textRenderer.render(parser.parse(source))

    private fun blocks(parent: Node): List<MarkdownBlock> = parent.children().mapNotNull { node ->
        when (node) {
            is Paragraph -> MarkdownBlock.Paragraph(inlines(node))
            is Heading -> MarkdownBlock.Heading(node.level, inlines(node))
            is FencedCodeBlock -> MarkdownBlock.Code(node.info.orEmpty().substringBefore(' '), node.literal.trimEnd('\n'))
            is IndentedCodeBlock -> MarkdownBlock.Code("", node.literal.trimEnd('\n'))
            is BlockQuote -> MarkdownBlock.Quote(blocks(node))
            is BulletList -> MarkdownBlock.ListBlock(node.children().map { blocks(it) })
            is OrderedList -> MarkdownBlock.ListBlock(node.children().map { blocks(it) }, node.markerStartNumber ?: 1)
            is ThematicBreak -> MarkdownBlock.Rule
            is TableBlock -> {
                val head = node.children().filterIsInstance<TableHead>().firstOrNull()
                val body = node.children().filterIsInstance<TableBody>().firstOrNull()
                MarkdownBlock.Table(head?.firstChild?.let(::cells).orEmpty(), body?.children()?.map(::cells).orEmpty())
            }
            // HTML remains literal text; assistant content never enters a WebView.
            is HtmlBlock -> MarkdownBlock.Paragraph(listOf(MarkdownInline.Text(node.literal.trimEnd())))
            else -> null
        }
    }

    private fun cells(row: Node): List<MarkdownCell> = row.children().filterIsInstance<TableCell>().map {
        val alignment = when (it.alignment) {
            TableCell.Alignment.CENTER -> MarkdownAlignment.CENTER
            TableCell.Alignment.RIGHT -> MarkdownAlignment.RIGHT
            else -> MarkdownAlignment.LEFT
        }
        MarkdownCell(inlines(it), alignment)
    }

    private fun inlines(parent: Node): List<MarkdownInline> = parent.children().flatMap { node ->
        when (node) {
            is Text -> listOf(MarkdownInline.Text(node.literal))
            is StrongEmphasis -> listOf(MarkdownInline.Strong(inlines(node)))
            is Emphasis -> listOf(MarkdownInline.Emphasis(inlines(node)))
            is Code -> listOf(MarkdownInline.Code(node.literal))
            is Link -> listOf(MarkdownInline.Link(node.destination, inlines(node)))
            is Image -> listOf(MarkdownInline.Link(node.destination, inlines(node)))
            is Strikethrough -> listOf(MarkdownInline.Strike(inlines(node)))
            is SoftLineBreak, is HardLineBreak -> listOf(MarkdownInline.Text("\n"))
            is HtmlInline -> listOf(MarkdownInline.Text(node.literal))
            else -> inlines(node)
        }
    }

    private fun Node.children(): List<Node> = buildList {
        var child = firstChild
        while (child != null) {
            add(child)
            child = child.next
        }
    }
}

internal fun safeMarkdownLink(destination: String): Boolean = runCatching {
    val uri = URI(destination)
    when (uri.scheme?.lowercase()) {
        "http", "https" -> !uri.host.isNullOrBlank()
        "mailto" -> !uri.schemeSpecificPart.isNullOrBlank()
        else -> false
    }
}.getOrDefault(false)

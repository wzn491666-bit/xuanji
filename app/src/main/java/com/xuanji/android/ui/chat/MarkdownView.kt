package com.xuanji.android.ui.chat

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MarkdownView(text: String, modifier: Modifier = Modifier) {
    val blocks by produceState<List<MarkdownBlock>>(emptyList(), text) {
        value = withContext(Dispatchers.Default) { MarkdownParser.parse(text) }
    }
    MarkdownView(blocks, modifier)
}

@Composable
internal fun MarkdownView(blocks: List<MarkdownBlock>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { MarkdownBlockContent(it) }
    }
}

internal data class PreparedMarkdown(val source: String, val blocks: List<MarkdownBlock>)

/** Keep the last parsed body and conflate incoming text without cancelling each parse. */
@Composable
internal fun rememberPreparedMarkdown(text: String, initial: PreparedMarkdown? = null): PreparedMarkdown? {
    var prepared by remember { mutableStateOf(initial) }
    val latestText by rememberUpdatedState(text)
    LaunchedEffect(Unit) {
        snapshotFlow { latestText }.collect { source ->
            if (prepared?.source != source) {
                prepared = withContext(Dispatchers.Default) { PreparedMarkdown(source, MarkdownParser.parse(source)) }
            }
        }
    }
    return prepared
}

@Composable
fun MarkdownBlockContent(block: MarkdownBlock, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        when (block) {
            is MarkdownBlock.Paragraph -> InlineText(block.content)
            is MarkdownBlock.Heading -> InlineText(
                block.content,
                style = when (block.level) {
                    1 -> MaterialTheme.typography.titleLarge
                    2 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                }.copy(fontWeight = FontWeight.SemiBold),
            )
            is MarkdownBlock.Code -> CodeBlock(block)
            is MarkdownBlock.Quote -> {
                val ruleColor = MaterialTheme.colorScheme.outlineVariant
                Column(Modifier.drawBehind {
                    drawLine(ruleColor, Offset.Zero, Offset(0f, size.height), strokeWidth = 2.dp.toPx())
                }.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.blocks.forEach { MarkdownBlockContent(it) }
                }
            }
            is MarkdownBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.items.forEachIndexed { index, children ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            block.startNumber?.let { "${it + index}." } ?: "•",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(min = if (block.startNumber == null) 20.dp else 28.dp).padding(end = 6.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            children.forEach { MarkdownBlockContent(it) }
                        }
                    }
                }
            }
            is MarkdownBlock.Table -> TableBlock(block)
            MarkdownBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun InlineText(
    content: List<MarkdownInline>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    align: TextAlign = TextAlign.Start,
) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val codeFontSize = MaterialTheme.typography.bodyMedium.fontSize
    val annotation = remember(content, scheme, uriHandler, codeFontSize) {
        buildAnnotatedString {
            fun appendInlines(nodes: List<MarkdownInline>) {
                nodes.forEach { node ->
                    when (node) {
                        is MarkdownInline.Text -> append(node.value)
                        is MarkdownInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { appendInlines(node.children) }
                        is MarkdownInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(node.children) }
                        is MarkdownInline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInlines(node.children) }
                        is MarkdownInline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = codeFontSize, background = scheme.surfaceContainerHighest)) {
                            append(" ${node.value} ")
                        }
                        is MarkdownInline.Link -> {
                            if (safeMarkdownLink(node.destination)) {
                                withLink(LinkAnnotation.Clickable(
                                    tag = node.destination,
                                    styles = TextLinkStyles(style = SpanStyle(color = scheme.secondary, textDecoration = TextDecoration.Underline)),
                                    linkInteractionListener = { runCatching { uriHandler.openUri(node.destination) } },
                                )) { appendInlines(node.children) }
                            } else appendInlines(node.children)
                        }
                    }
                }
            }
            appendInlines(content)
        }
    }
    Text(annotation, modifier.fillMaxWidth(), style = style, textAlign = align)
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(block.language.ifBlank { "代码" }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("code", block.text))) } }) {
                    Icon(Icons.Default.ContentCopy, "复制代码", Modifier.size(16.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                Text(block.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TableBlock(table: MarkdownBlock.Table) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = maxOf(104.dp, maxWidth / table.headers.size.coerceAtLeast(1))
        Column(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            TableRow(table.headers, true, cellWidth)
            table.rows.forEach { TableRow(it, false, cellWidth) }
        }
    }
}

@Composable
private fun TableRow(cells: List<MarkdownCell>, header: Boolean, cellWidth: Dp) {
    Row(Modifier.height(IntrinsicSize.Min).background(if (header) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)) {
        cells.forEach { cell ->
            Box(Modifier.width(cellWidth).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(12.dp)) {
                InlineText(
                    cell.content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal),
                    align = when (cell.alignment) {
                        MarkdownAlignment.LEFT -> TextAlign.Start
                        MarkdownAlignment.CENTER -> TextAlign.Center
                        MarkdownAlignment.RIGHT -> TextAlign.End
                    },
                )
            }
        }
    }
}

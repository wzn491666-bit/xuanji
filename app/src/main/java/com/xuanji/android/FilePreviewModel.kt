package com.xuanji.android

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

enum class FilePreviewFormat { MARKDOWN, CODE, TEXT, IMAGE }
enum class CodeSpanKind { KEYWORD, STRING, COMMENT, NUMBER }
data class CodeSpan(val start: Int, val end: Int, val kind: CodeSpanKind)
data class FilePreviewPage(
    val startLine: Int,
    val content: String,
    val continuesPreviousLine: Boolean = false,
) {
    val lineCount: Int
        get() = when {
            content.isEmpty() -> 1
            content.endsWith('\n') -> content.count { it == '\n' }
            else -> content.count { it == '\n' } + 1
        }
}

fun splitFilePreviewPages(
    source: String,
    linesPerPage: Int = 160,
    maxCharactersPerPage: Int = 32 * 1024,
): List<FilePreviewPage> {
    require(linesPerPage > 0) { "每页行数必须大于 0" }
    require(maxCharactersPerPage > 0) { "每页字符数必须大于 0" }
    if (source.isEmpty()) return listOf(FilePreviewPage(1, ""))

    val pages = mutableListOf<FilePreviewPage>()
    var start = 0
    var firstLine = 1
    while (start < source.length) {
        val characterLimit = start + minOf(maxCharactersPerPage, source.length - start)
        var end = characterLimit
        var lastCompleteLine = -1
        var linesInChunk = 0
        var newline = source.indexOf('\n', start)
        while (newline >= 0 && newline < characterLimit) {
            linesInChunk++
            if (linesInChunk <= linesPerPage) lastCompleteLine = newline + 1
            if (linesInChunk >= linesPerPage && newline + 1 < source.length) {
                end = newline + 1
                break
            }
            newline = source.indexOf('\n', newline + 1)
        }
        if (end == characterLimit && characterLimit < source.length && lastCompleteLine > start) {
            end = lastCompleteLine
        }
        if (end <= start) end = characterLimit

        val chunk = source.substring(start, end)
        pages += FilePreviewPage(
            startLine = firstLine,
            content = chunk,
            continuesPreviousLine = start > 0 && source[start - 1] != '\n',
        )
        firstLine += chunk.count { it == '\n' }
        start = end
    }
    return pages
}

fun previewFormatForPath(path: String): FilePreviewFormat {
    val extension = path.substringAfterLast('/', path).substringAfterLast('.', "").lowercase()
    return when (extension) {
        "md", "markdown", "mdown", "mkd" -> FilePreviewFormat.MARKDOWN
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif" -> FilePreviewFormat.IMAGE
        "kt", "kts", "java", "js", "jsx", "ts", "tsx", "json", "xml", "html", "css", "scss", "py", "sh", "bash", "zsh", "yml", "yaml", "toml", "gradle", "properties", "sql", "c", "h", "cpp", "hpp", "rs", "go", "swift", "dart", "ini", "conf", "log" -> FilePreviewFormat.CODE
        else -> FilePreviewFormat.TEXT
    }
}

fun decodeUtf8Preview(bytes: ByteArray, truncated: Boolean): String? {
    fun decode(end: Int): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes, 0, end)).toString()
    return try {
        decode(bytes.size)
    } catch (_: CharacterCodingException) {
        if (!truncated) return null
        for (end in bytes.size - 1 downTo maxOf(0, bytes.size - 4)) {
            try { return decode(end) } catch (_: CharacterCodingException) { }
        }
        null
    }
}

fun codeSpansForPreview(source: String, path: String): List<CodeSpan> {
    val extension = path.substringAfterLast('.', "").lowercase()
    val hashComments = extension in setOf("py", "sh", "bash", "zsh", "yml", "yaml", "toml", "ini", "properties")
    val keywords = setOf("as", "async", "await", "break", "class", "const", "continue", "data", "else", "enum", "export", "false", "fun", "for", "from", "if", "import", "in", "interface", "let", "null", "object", "private", "public", "return", "static", "suspend", "this", "throw", "true", "try", "type", "val", "var", "when", "while")
    val spans = mutableListOf<CodeSpan>()
    var i = 0
    while (i < source.length) {
        val start = i
        when {
            source.startsWith("//", i) || source.startsWith("/*", i) || (hashComments && source[i] == '#') -> {
                i = if (source.startsWith("/*", i)) {
                    val end = source.indexOf("*/", i + 2)
                    if (end < 0) source.length else end + 2
                } else source.indexOf('\n', i).let { if (it < 0) source.length else it }
                spans += CodeSpan(start, i, CodeSpanKind.COMMENT)
            }
            source[i] == '\'' || source[i] == '"' || source[i] == '`' -> {
                val quote = source[i++]
                while (i < source.length) {
                    if (source[i] == '\\') i = (i + 2).coerceAtMost(source.length)
                    else if (source[i++] == quote) break
                }
                spans += CodeSpan(start, i, CodeSpanKind.STRING)
            }
            source[i].isDigit() -> {
                i++
                while (i < source.length && (source[i].isDigit() || source[i] == '.' || source[i] == '_')) i++
                spans += CodeSpan(start, i, CodeSpanKind.NUMBER)
            }
            source[i].isLetter() || source[i] == '_' -> {
                i++
                while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                if (source.substring(start, i) in keywords) spans += CodeSpan(start, i, CodeSpanKind.KEYWORD)
            }
            else -> i++
        }
    }
    return spans
}


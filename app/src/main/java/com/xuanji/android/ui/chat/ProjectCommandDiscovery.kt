package com.xuanji.android.ui.chat

import java.io.File
import java.io.IOException
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

private const val MAX_PROJECT_COMMAND_BYTES = 64 * 1024
private val PROJECT_COMMAND_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")

fun discoverProjectCommands(cwd: String, sharedStorageRoot: File, runtimeRootfs: File): List<MobileCommand> {
    require(cwd.startsWith('/') && cwd.split('/').none { it == ".." } && '\u0000' !in cwd) { "工作目录无效" }
    val sharedPath = cwd == "/sdcard" || cwd.startsWith("/sdcard/")
    val root = (if (sharedPath) sharedStorageRoot else runtimeRootfs).canonicalFile
    val relative = if (sharedPath) cwd.removePrefix("/sdcard").trimStart('/') else cwd.trimStart('/')
    val project = File(root, relative).canonicalFile
    require(project.toPath().startsWith(root.toPath())) { "工作目录超出运行环境" }
    val directory = File(project, ".rivet/commands").canonicalFile
    require(directory.toPath().startsWith(project.toPath())) { "命令目录超出当前工作区" }
    if (!directory.exists()) return emptyList()
    require(directory.isDirectory) { "命令路径不是目录" }
    val files = directory.listFiles() ?: error("命令目录读取失败，请检查文件权限")
    return files.filter { Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS) && it.name.endsWith(".md") }
        .filter { PROJECT_COMMAND_NAME.matches(it.name.removeSuffix(".md")) }
        .sortedBy { it.name }
        .map { file ->
            val name = "/${file.name.removeSuffix(".md")}"
            try {
                val body = readProjectCommand(file, directory)
                // Tianshu 3.27.0 commands are raw Markdown with $ARGUMENTS substitution;
                // skill frontmatter is not an official command metadata contract.
                MobileCommand(name, "执行当前工作区的自定义命令", if (body.contains("\$ARGUMENTS")) "[参数]" else "",
                    "项目命令", mobileExecutable = true, custom = true)
            } catch (error: Exception) {
                val reason = when (error) {
                    is IOException -> "命令文件读取失败，请检查文件权限"
                    is SecurityException -> "没有读取命令文件的权限"
                    else -> error.message ?: "命令文件无效"
                }
                MobileCommand(name, "不可用：$reason", group = "项目命令", custom = true)
            }
        }
}

private fun readProjectCommand(file: File, directory: File): String {
    require(file.canonicalFile.toPath().startsWith(directory.toPath())) { "命令文件超出命令目录" }
    require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && Files.isReadable(file.toPath())) {
        "命令文件不是普通可读文件"
    }
    val bytes = ByteBuffer.allocate(MAX_PROJECT_COMMAND_BYTES + 1)
    Files.newByteChannel(file.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
        require(channel.size() <= MAX_PROJECT_COMMAND_BYTES) { "命令文件超过 64 KiB 限制" }
        while (bytes.hasRemaining() && channel.read(bytes) != -1) {}
    }
    require(bytes.position() <= MAX_PROJECT_COMMAND_BYTES) { "命令文件超过 64 KiB 限制" }
    (bytes as Buffer).flip()
    val body = try {
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(bytes).toString()
    } catch (_: CharacterCodingException) {
        throw IllegalArgumentException("命令文件不是有效 UTF-8")
    }
    require(body.removePrefix("\uFEFF").isNotBlank()) { "命令文件为空" }
    require('\u0000' !in body) { "命令文件含无效的空字符" }
    return body
}

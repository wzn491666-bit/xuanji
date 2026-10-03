package com.xuanji.android

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Restricts MCP file tools to the user-visible shared storage tree. */
internal fun resolveSharedStoragePath(input: String, root: File): File {
    val trimmed = input.trim()
    require(trimmed.isNotEmpty()) { "path is required" }
    val relative = when {
        trimmed == "/sdcard" -> ""
        trimmed.startsWith("/sdcard/") -> trimmed.removePrefix("/sdcard/")
        trimmed == root.path -> ""
        trimmed.startsWith(root.path + File.separator) -> trimmed.removePrefix(root.path + File.separator)
        !trimmed.startsWith("/") -> trimmed
        else -> throw IllegalArgumentException("path must stay inside /sdcard")
    }
    val candidate = File(root, relative).canonicalFile
    val rootPath = root.canonicalFile.path
    require(candidate.path == rootPath || candidate.path.startsWith(rootPath + File.separator)) {
        "path must stay inside /sdcard"
    }
    return candidate
}

internal fun readSharedStorageBytes(file: File, maxBytes: Int): ByteArray {
    require(maxBytes in 1 until Int.MAX_VALUE)
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= maxBytes) { "文件超过读取上限。" }
        }
    }
    return output.toByteArray()
}

/** Replace only after a complete write, so an interrupted write preserves existing content. */
internal fun writeSharedStorageText(target: File, bytes: ByteArray, overwrite: Boolean) {
    require(!target.isDirectory) { "path is a directory" }
    val parent = requireNotNull(target.parentFile) { "path has no parent" }
    Files.createDirectories(parent.toPath())
    val staged = File.createTempFile(".xuanji-", ".tmp", parent)
    try {
        FileOutputStream(staged).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (overwrite) {
            try {
                Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } else {
            Files.move(staged.toPath(), target.toPath())
        }
    } finally {
        staged.delete()
    }
}

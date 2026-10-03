package com.xuanji.android

import android.os.Environment
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

data class WorkspaceFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
)

data class FilePreview(
    val path: String,
    val content: String,
    val isBinary: Boolean,
    val truncated: Boolean,
    val format: FilePreviewFormat = previewFormatForPath(path),
    val sizeBytes: Long = content.toByteArray().size.toLong(),
    val absolutePath: String = "",
)

/** Read-only workspace access used by the mobile file preview. */
class WorkspaceRepository {
    private val root = Environment.getExternalStorageDirectory().canonicalFile

    fun list(path: String = "/sdcard"): List<WorkspaceFile> {
        val directory = resolveSharedStoragePath(path, root)
        require(directory.isDirectory) { "不是目录：${directory.path}" }
        return directory.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?.take(300)
            ?.map { file ->
                WorkspaceFile(
                    name = file.name,
                    path = "/sdcard/${file.relativeTo(root).path.replace(File.separatorChar, '/')}",
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0,
                )
            }
            .orEmpty()
    }

    fun read(path: String, maxBytes: Int = MAX_BYTES): FilePreview {
        val file = resolveSharedStoragePath(path, root)
        require(file.isFile) { "文件不存在：${file.path}" }
        require(maxBytes > 0) { "预览长度必须大于 0" }
        val format = previewFormatForPath(path)
        if (format == FilePreviewFormat.IMAGE) {
            return FilePreview(
                path = "/sdcard/${file.relativeTo(root).path.replace(File.separatorChar, '/')}",
                content = "",
                isBinary = false,
                truncated = false,
                format = format,
                sizeBytes = file.length(),
                absolutePath = file.path,
            )
        }
        val bytes = readPrefix(file, maxBytes + 4)
        val truncated = file.length() > maxBytes
        val previewBytes = bytes.take(maxBytes).toByteArray()
        val text = decodeUtf8Preview(previewBytes, truncated)
        val binary = previewBytes.any { it == 0.toByte() } || text == null
        return FilePreview(
            path = "/sdcard/${file.relativeTo(root).path.replace(File.separatorChar, '/')}",
            content = if (binary) "二进制文件（${file.length()} bytes），暂不提供文本预览。"
            else text.orEmpty(),
            isBinary = binary,
            truncated = truncated,
            format = format,
            sizeBytes = file.length(),
        )
    }

    private fun readPrefix(file: File, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var remaining = limit
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
        return output.toByteArray()
    }

    private companion object { const val MAX_BYTES = 1024 * 1024 }
}

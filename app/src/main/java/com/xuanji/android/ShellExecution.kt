package com.xuanji.android

import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

enum class ShellBackend { ROOT, SHIZUKU, APP }

fun selectPrivilegedBackend(rootGranted: Boolean, shizukuGranted: Boolean): ShellBackend? = when {
    rootGranted -> ShellBackend.ROOT
    shizukuGranted -> ShellBackend.SHIZUKU
    else -> null
}

data class ShellResult(val backend: ShellBackend, val exitCode: Int, val output: String, val timedOut: Boolean)

internal object ShellProcesses {
    val active = ConcurrentHashMap.newKeySet<Process>()

    fun destroyAll() {
        active.forEach { runCatching { it.destroyForcibly() } }
    }
}

internal fun runShell(command: String, backend: ShellBackend, timeoutSeconds: Long = 30): ShellResult {
    val args = when (backend) {
        ShellBackend.ROOT -> listOf("su", "-c", command)
        else -> listOf("/system/bin/sh", "-c", command)
    }
    val process = ProcessBuilder(args).redirectErrorStream(true).start()
    ShellProcesses.active.add(process)
    val output = StringBuffer()
    val reader = Thread {
        runCatching {
            process.inputStream.bufferedReader().use { stream ->
                val buffer = CharArray(4096)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (output.length < 65536) output.append(buffer, 0, minOf(read, 65536 - output.length))
                }
            }
        }
    }.apply { isDaemon = true; start() }
    try {
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        reader.join(1_000)
        return ShellResult(backend, if (completed) process.exitValue() else -1, output.toString(), !completed)
    } finally {
        process.destroyForcibly()
        ShellProcesses.active.remove(process)
        runCatching { process.inputStream.close() }
        runCatching { process.outputStream.close() }
        runCatching { process.errorStream.close() }
        reader.interrupt()
    }
}

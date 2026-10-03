package com.xuanji.android

data class CommandCheckOutput(
    val root: String? = null,
    val shizuku: String? = null,
) {
    fun forBackend(backend: ShellBackend): String? = when (backend) {
        ShellBackend.ROOT -> root
        ShellBackend.SHIZUKU -> shizuku
        ShellBackend.APP -> null
    }

    fun withResult(backend: ShellBackend, value: String): CommandCheckOutput = when (backend) {
        ShellBackend.ROOT -> copy(root = value)
        ShellBackend.SHIZUKU -> copy(shizuku = value)
        ShellBackend.APP -> this
    }
}

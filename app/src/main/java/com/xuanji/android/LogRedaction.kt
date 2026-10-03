package com.xuanji.android

internal fun redactLogLine(line: String, secrets: Collection<String>): String =
    secrets.asSequence().filter { it.length >= 8 }.sortedByDescending(String::length)
        .fold(line) { result, secret -> result.replace(secret, "[redacted]") }

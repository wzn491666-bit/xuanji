package com.xuanji.android

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import kotlinx.coroutines.CancellationException
import org.junit.Test

class RuntimeDataCopyTest {
    @Test
    fun preservesHomeAndTianshuDataDuringRuntimeUpdate() {
        val temp = Files.createTempDirectory("runtime-data-test")
        try {
            val source = temp.resolve("old/root")
            val destination = temp.resolve("new/root")
            Files.createDirectories(source.resolve(".rivet/sessions"))
            Files.writeString(source.resolve(".rivet/sessions/chat.json"), "saved session")
            Files.writeString(source.resolve("notes.txt"), "saved notes")

            copyTreeNoFollow(source, destination)

            assertEquals("saved session", Files.readString(destination.resolve(".rivet/sessions/chat.json")))
            assertEquals("saved notes", Files.readString(destination.resolve("notes.txt")))
            assertEquals("saved session", Files.readString(source.resolve(".rivet/sessions/chat.json")))
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancellationStopsHomeCopyBeforeTouchingDestination() {
        val temp = Files.createTempDirectory("runtime-copy-cancel")
        try {
            val source = temp.resolve("old/root")
            val destination = temp.resolve("new/root")
            Files.createDirectories(source)
            Files.writeString(source.resolve("notes.txt"), "saved notes")

            assertThrows(CancellationException::class.java) {
                copyTreeNoFollow(source, destination) { throw CancellationException("stopped") }
            }
            assertFalse(Files.exists(destination))
            assertEquals("saved notes", Files.readString(source.resolve("notes.txt")))
        } finally {
            temp.toFile().deleteRecursively()
        }
    }
}

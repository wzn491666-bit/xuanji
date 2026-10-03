package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException

class SharedStorageWriteTest {
    @get:Rule val folder = TemporaryFolder()

    @Test(expected = FileAlreadyExistsException::class)
    fun existingFileIsPreservedWhenOverwriteIsFalse() {
        val target = folder.newFile("note.txt").apply { writeText("original") }
        try {
            writeSharedStorageText(target, "replacement".toByteArray(StandardCharsets.UTF_8), false)
        } finally {
            assertEquals("original", target.readText())
        }
    }

    @Test
    fun overwriteReplacesCompleteContent() {
        val target = folder.newFile("note.txt").apply { writeText("long original content") }

        writeSharedStorageText(target, "new".toByteArray(StandardCharsets.UTF_8), true)

        assertEquals("new", target.readText())
        assertFalse(folder.root.listFiles().orEmpty().any { it.name.startsWith(".xuanji-") })
    }

    @Test
    fun newFileCanBeWrittenInNewDirectory() {
        val target = File(folder.root, "nested/note.txt")

        writeSharedStorageText(target, "你好".toByteArray(StandardCharsets.UTF_8), false)

        assertEquals("你好", target.readText())
    }
}

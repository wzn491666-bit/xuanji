package com.xuanji.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class AndroidMcpStorageTest {
    @get:Rule val temp = TemporaryFolder()
    private val root = File("/storage/emulated/0").canonicalFile

    @Test
    fun resolvesSdcardAliasInsideSharedStorage() {
        assertEquals(
            File(root, "Documents/readme.txt").canonicalFile,
            resolveSharedStoragePath("/sdcard/Documents/readme.txt", root),
        )
    }

    @Test
    fun rejectsTraversalOutsideSharedStorage() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveSharedStoragePath("/sdcard/../data/data/com.xuanji.android", root)
        }
    }

    @Test
    fun relativePathsAreResolvedFromSharedStorageRoot() {
        assertEquals(
            File(root, "notes/today.txt").canonicalFile,
            resolveSharedStoragePath("notes/today.txt", root),
        )
    }

    @Test
    fun readLimitIsEnforcedWhileReading() {
        val file = temp.newFile("large.txt")
        file.writeText("12345678")

        assertThrows(IllegalArgumentException::class.java) {
            readSharedStorageBytes(file, 4)
        }
        assertEquals("12345678", readSharedStorageBytes(file, 8).toString(Charsets.UTF_8))
    }
}

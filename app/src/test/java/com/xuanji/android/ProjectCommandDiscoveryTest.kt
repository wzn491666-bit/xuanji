package com.xuanji.android

import com.xuanji.android.ui.chat.discoverProjectCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.nio.file.Files
import java.io.File

class ProjectCommandDiscoveryTest {
    @Test
    fun discoversOnlyServerCompatibleMarkdownCommandNamesFromActualWorkspace() {
        val temp = Files.createTempDirectory("project-commands").toFile()
        try {
            val storage = File(temp, "storage").apply { mkdirs() }
            val runtime = File(temp, "rootfs").apply { mkdirs() }
            val commands = File(storage, "project/.rivet/commands").apply { mkdirs() }
            File(commands, "release.md").writeText("Do not expose this command body")
            File(commands, "check-build.md").writeText("check")
            File(commands, "bad name.md").writeText("ignored")
            File(commands, "other.txt").writeText("ignored")
            File(commands, "folder.md").mkdirs()
            File(runtime, "root/.rivet/commands").apply { mkdirs(); File(this, "private-project.md").writeText("other workspace") }
            val discovered = discoverProjectCommands("/sdcard/project", storage, runtime)
            assertEquals(listOf("/check-build", "/release"), discovered.map { it.name })
            assertTrue(discovered.all { it.custom && it.mobileExecutable && !it.description.contains("body") })
            assertEquals(listOf("/private-project"), discoverProjectCommands("/root", storage, runtime).map { it.name })
        } finally { temp.deleteRecursively() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotDiscoverCommandsOutsideTheMappedWorkspace() {
        discoverProjectCommands("/sdcard/../outside", File("storage"), File("rootfs"))
    }

    @Test
    fun validatesContentsBeforeMakingAProjectCommandExecutable() = withWorkspace { storage, runtime, commands ->
        File(commands, "valid.md").writeText("检查构建。")
        File(commands, "empty.md").writeText("\uFEFF \r\n\t")
        File(commands, "large.md").writeBytes(ByteArray(64 * 1024 + 1) { 'a'.code.toByte() })
        File(commands, "malformed.md").writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        File(commands, "binary.md").writeText("text\u0000hidden")

        val discovered = discoverProjectCommands("/sdcard/project", storage, runtime)
        assertEquals(listOf("/valid"), discovered.filter { it.mobileExecutable }.map { it.name })
        listOf("/empty", "/large", "/malformed", "/binary").forEach { name ->
            val command = discovered.single { it.name == name }
            assertTrue(command.custom)
            assertTrue(command.description.startsWith("不可用："))
            assertEquals("", command.argsHint)
        }
        assertTrue(discovered.single { it.name == "/malformed" }.description.contains("UTF-8"))
    }

    @Test
    fun acceptsExactlyTheByteLimitAndDoesNotConfuseCharactersWithBytes() = withWorkspace { storage, runtime, commands ->
        File(commands, "limit.md").writeBytes(ByteArray(64 * 1024) { 'a'.code.toByte() })
        File(commands, "unicode.md").writeText("星".repeat(22_000), Charsets.UTF_8)
        val discovered = discoverProjectCommands("/sdcard/project", storage, runtime)
        assertTrue(discovered.single { it.name == "/limit" }.mobileExecutable)
        assertFalse(discovered.single { it.name == "/unicode" }.mobileExecutable)
    }

    @Test
    fun exposesArgumentsOnlyWhenTheOfficialPlaceholderIsPresent() = withWorkspace { storage, runtime, commands ->
        File(commands, "fixed.md").writeText("Run the fixed check.")
        File(commands, "parameterized.md").writeText("Review \$ARGUMENTS.")
        val discovered = discoverProjectCommands("/sdcard/project", storage, runtime)
        assertEquals("", discovered.single { it.name == "/fixed" }.argsHint)
        assertEquals("[参数]", discovered.single { it.name == "/parameterized" }.argsHint)
    }

    @Test
    fun doesNotInventCommandMetadataFromSkillFrontmatter() = withWorkspace { storage, runtime, commands ->
        File(commands, "prose.md").writeText("---\ndescription: Not official command metadata\nargument-hint: <secret>\n---\nRun a fixed check.")
        val command = discoverProjectCommands("/sdcard/project", storage, runtime).single()
        assertEquals("执行当前工作区的自定义命令", command.description)
        assertEquals("", command.argsHint)
        assertTrue(command.mobileExecutable)
    }

    @Test
    fun rejectsACommandDirectoryEscapingTheProjectInsideSharedStorage() = withWorkspace { storage, runtime, commands ->
        commands.delete()
        val outside = File(storage, "other-project").apply { mkdirs() }
        File(outside, "escape.md").writeText("outside")
        createLinkOrSkip(commands, outside)
        assertThrows(IllegalArgumentException::class.java) {
            discoverProjectCommands("/sdcard/project", storage, runtime)
        }
    }

    @Test
    fun doesNotDiscoverSymlinkedCommandFiles() = withWorkspace { storage, runtime, commands ->
        val regular = File(commands, "regular.md").apply { writeText("valid") }
        val outside = File(storage, "outside.md").apply { writeText("outside") }
        createLinkOrSkip(File(commands, "inside-link.md"), regular)
        createLinkOrSkip(File(commands, "outside-link.md"), outside)
        assertEquals(listOf("/regular"), discoverProjectCommands("/sdcard/project", storage, runtime).map { it.name })
    }

    private fun createLinkOrSkip(link: File, target: File) {
        try { Files.createSymbolicLink(link.toPath(), target.toPath()) }
        catch (error: Exception) { assumeNoException("Host cannot create symbolic links", error) }
    }

    private inline fun withWorkspace(block: (File, File, File) -> Unit) {
        val temp = Files.createTempDirectory("project-command-check").toFile()
        try {
            val storage = File(temp, "storage").apply { mkdirs() }
            val runtime = File(temp, "rootfs").apply { mkdirs() }
            val commands = File(storage, "project/.rivet/commands").apply { mkdirs() }
            block(storage, runtime, commands)
        } finally { temp.deleteRecursively() }
    }
}

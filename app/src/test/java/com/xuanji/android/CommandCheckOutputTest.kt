package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandCheckOutputTest {
    @Test
    fun commandOutputIsKeptSeparateForEachPrivilegedBackend() {
        val rootOnly = CommandCheckOutput().withResult(ShellBackend.ROOT, "uid=0(root)")

        assertEquals("uid=0(root)", rootOnly.forBackend(ShellBackend.ROOT))
        assertNull(rootOnly.forBackend(ShellBackend.SHIZUKU))
    }

    @Test
    fun updatingOneBackendKeepsTheOtherBackendResult() {
        val output = CommandCheckOutput()
            .withResult(ShellBackend.ROOT, "uid=0(root)")
            .withResult(ShellBackend.SHIZUKU, "uid=0(root) · Shizuku")

        assertEquals("uid=0(root)", output.forBackend(ShellBackend.ROOT))
        assertEquals("uid=0(root) · Shizuku", output.forBackend(ShellBackend.SHIZUKU))
    }
}

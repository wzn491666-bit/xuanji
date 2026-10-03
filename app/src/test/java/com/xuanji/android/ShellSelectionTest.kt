package com.xuanji.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellSelectionTest {
    @Test fun shizukuUserServiceHasStableProcessNameSuffix() {
        assertEquals("xuanji-shell", shizukuProcessNameSuffix())
    }

    @Test fun rootIsPreferredWhenBothChannelsAreGranted() {
        assertEquals(ShellBackend.ROOT, selectPrivilegedBackend(rootGranted = true, shizukuGranted = true))
    }

    @Test fun shizukuIsUsedWhenRootIsUnavailable() {
        assertEquals(ShellBackend.SHIZUKU, selectPrivilegedBackend(rootGranted = false, shizukuGranted = true))
    }

    @Test fun noGrantDoesNotSilentlyUseAppShell() {
        assertEquals(null, selectPrivilegedBackend(rootGranted = false, shizukuGranted = false))
    }
}

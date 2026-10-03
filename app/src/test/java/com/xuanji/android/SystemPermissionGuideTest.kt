package com.xuanji.android

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemPermissionGuideTest {
    @Test
    fun overlaySettingsOpenTheAppSpecificPage() {
        assertEquals(
            SystemSettingsIntentSpec(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:com.xuanji.android",
            ),
            systemSettingsIntentSpec(SystemSettingsTarget.OVERLAY, "com.xuanji.android", sdkInt = 36),
        )
    }

    @Test
    fun batteryOptimizationUsesTheUserManagedSystemList() {
        assertEquals(
            SystemSettingsIntentSpec(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, null),
            systemSettingsIntentSpec(SystemSettingsTarget.BATTERY_OPTIMIZATION, "com.xuanji.android", sdkInt = 36),
        )
    }

    @Test
    fun overlayAndBatteryGuidesRespectTheirApiLevel() {
        assertNull(systemSettingsIntentSpec(SystemSettingsTarget.OVERLAY, "com.xuanji.android", sdkInt = 22))
        assertNull(systemSettingsIntentSpec(SystemSettingsTarget.BATTERY_OPTIMIZATION, "com.xuanji.android", sdkInt = 22))
    }

    @Test
    fun appDetailsRouteIsAvailableOnEverySupportedAndroidVersion() {
        assertEquals(
            SystemSettingsIntentSpec(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:com.xuanji.android",
            ),
            systemSettingsIntentSpec(SystemSettingsTarget.APP_DETAILS, "com.xuanji.android", sdkInt = 26),
        )
    }

    @Test
    fun storageAccessUsesRuntimePermissionsBeforeAndroidEleven() {
        assertEquals(StorageAccessMethod.LEGACY_RUNTIME_PERMISSIONS, storageAccessMethod(sdkInt = 29))
        assertEquals(StorageAccessMethod.ALL_FILES_SETTINGS, storageAccessMethod(sdkInt = 30))
    }

    @Test
    fun allFilesSettingsRouteIsOnlyAvailableOnAndroidElevenAndLater() {
        assertNull(systemSettingsIntentSpec(SystemSettingsTarget.ALL_FILES_ACCESS, "com.xuanji.android", sdkInt = 29))
        assertEquals(
            SystemSettingsIntentSpec(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                "package:com.xuanji.android",
            ),
            systemSettingsIntentSpec(SystemSettingsTarget.ALL_FILES_ACCESS, "com.xuanji.android", sdkInt = 30),
        )
    }

    @Test
    fun startupPermissionHintNamesXiaomiHyperOsAndKeepsGenericFallbackHonest() {
        assertEquals(
            "小米 / Redmi / POCO：检查「自启动」与「后台运行」开关。",
            manufacturerStartupHint("Xiaomi", "Redmi"),
        )
        assertEquals(
            "部分品牌会提供自启动开关；若设置中没有该项，可忽略。",
            manufacturerStartupHint("Google", "Pixel"),
        )
    }
}

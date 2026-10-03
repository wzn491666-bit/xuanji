package com.xuanji.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings

enum class SystemSettingsTarget { OVERLAY, BATTERY_OPTIMIZATION, ALL_FILES_ACCESS, APP_DETAILS }
enum class StorageAccessMethod { LEGACY_RUNTIME_PERMISSIONS, ALL_FILES_SETTINGS }

data class SystemSettingsIntentSpec(
    val action: String,
    val dataUri: String?,
)

data class RuntimePermissionState(
    val storageAccessGranted: Boolean,
    val overlayGranted: Boolean,
    val batteryOptimizationExempt: Boolean,
)

internal fun storageAccessMethod(sdkInt: Int): StorageAccessMethod =
    if (sdkInt >= Build.VERSION_CODES.R) StorageAccessMethod.ALL_FILES_SETTINGS
    else StorageAccessMethod.LEGACY_RUNTIME_PERMISSIONS

fun legacyStoragePermissions(): Array<String> = arrayOf(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
)

fun hasStorageAccess(context: Context): Boolean = when (storageAccessMethod(Build.VERSION.SDK_INT)) {
    StorageAccessMethod.ALL_FILES_SETTINGS ->
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    StorageAccessMethod.LEGACY_RUNTIME_PERMISSIONS -> legacyStoragePermissions().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
}

fun storageAccessSettingsIntent(context: Context): Intent? {
    val spec = systemSettingsIntentSpec(SystemSettingsTarget.ALL_FILES_ACCESS, context.packageName, Build.VERSION.SDK_INT)
        ?: return null
    return Intent(spec.action).apply { spec.dataUri?.let { data = Uri.parse(it) } }
}

internal fun systemSettingsIntentSpec(
    target: SystemSettingsTarget,
    packageName: String,
    sdkInt: Int,
): SystemSettingsIntentSpec? = when (target) {
    SystemSettingsTarget.OVERLAY -> if (sdkInt >= Build.VERSION_CODES.M) {
        SystemSettingsIntentSpec(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName")
    } else null
    SystemSettingsTarget.BATTERY_OPTIMIZATION -> if (sdkInt >= Build.VERSION_CODES.M) {
        SystemSettingsIntentSpec(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, null)
    } else null
    SystemSettingsTarget.ALL_FILES_ACCESS -> if (sdkInt >= Build.VERSION_CODES.R) {
        SystemSettingsIntentSpec(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName")
    } else null
    SystemSettingsTarget.APP_DETAILS -> SystemSettingsIntentSpec(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:$packageName",
    )
}

fun readRuntimePermissionState(context: Context): RuntimePermissionState {
    val manager = context.getSystemService(PowerManager::class.java)
    return RuntimePermissionState(
        storageAccessGranted = hasStorageAccess(context),
        overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context),
        batteryOptimizationExempt = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            manager?.isIgnoringBatteryOptimizations(context.packageName) == true,
    )
}

internal fun manufacturerStartupHint(manufacturer: String, brand: String): String {
    val deviceBrand = "$manufacturer $brand".lowercase()
    return if (listOf("xiaomi", "redmi", "poco", "hyperos", "miui").any(deviceBrand::contains)) {
        "小米 / Redmi / POCO：检查「自启动」与「后台运行」开关。"
    } else {
        "部分品牌会提供自启动开关；若设置中没有该项，可忽略。"
    }
}

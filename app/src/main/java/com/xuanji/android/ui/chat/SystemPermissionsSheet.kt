package com.xuanji.android.ui.chat

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.selection.SelectionContainer
import com.xuanji.android.CommandStatus
import com.xuanji.android.RuntimePermissionState
import com.xuanji.android.ShellBackend
import com.xuanji.android.SystemSettingsTarget
import com.xuanji.android.StorageAccessMethod
import com.xuanji.android.manufacturerStartupHint
import com.xuanji.android.legacyStoragePermissions
import com.xuanji.android.storageAccessMethod
import com.xuanji.android.systemSettingsIntentSpec
import com.xuanji.android.ui.theme.Danger
import com.xuanji.android.ui.theme.Success
import com.xuanji.android.ui.theme.Warning
import com.xuanji.android.XuanjiViewModel

private enum class StatusTone { READY, ATTENTION, OPTIONAL, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPermissionsSheet(
    vm: XuanjiViewModel,
    commandStatus: CommandStatus,
    rootCommandCheck: String?,
    shizukuCommandCheck: String?,
    accessibilityEnabled: Boolean,
    permissionState: RuntimePermissionState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val mcpStatus = vm.androidMcpStatus.collectAsStateWithLifecycle().value
    val mcpError = vm.androidMcpError.collectAsStateWithLifecycle().value
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refreshSystemPermissions() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun openSystemSettings(target: SystemSettingsTarget) {
        val spec = systemSettingsIntentSpec(target, context.packageName, Build.VERSION.SDK_INT) ?: return
        val intent = Intent(spec.action).apply {
            spec.dataUri?.let { data = Uri.parse(it) }
        }
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
    fun openAccessibilitySettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
    fun openShizuku() {
        val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        runCatching { context.startActivity(launch) }
            .onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("设备能力", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("系统权限", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("按需开启 · 可随时在系统设置中撤回", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { PermissionSectionLabel("文件与运行保障") }
                item {
                    AccessCard(
                        icon = Icons.Default.Folder,
                        title = "共享存储",
                        status = if (permissionState.storageAccessGranted) "已授权" else "未授权",
                        tone = if (permissionState.storageAccessGranted) StatusTone.READY else StatusTone.ATTENTION,
                        description = if (permissionState.storageAccessGranted)
                            "璇玑可以浏览共享存储中的文件。授权方式会根据 Android 版本自动选择。"
                        else
                            "用于工作台文件浏览。Android 11+ 进入所有文件访问设置；更早版本请求存储权限。",
                    ) {
                        Button(onClick = {
                            if (storageAccessMethod(Build.VERSION.SDK_INT) == StorageAccessMethod.ALL_FILES_SETTINGS) {
                                openSystemSettings(SystemSettingsTarget.ALL_FILES_ACCESS)
                            } else {
                                storagePermissionLauncher.launch(legacyStoragePermissions())
                            }
                        }) {
                            Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "管理文件权限" else "请求存储权限")
                        }
                    }
                }
                item {
                    AccessCard(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "后台运行",
                        status = if (permissionState.batteryOptimizationExempt) "不受电池优化" else "系统可能暂停服务",
                        tone = if (permissionState.batteryOptimizationExempt) StatusTone.READY else StatusTone.ATTENTION,
                        description = if (permissionState.batteryOptimizationExempt)
                            "天枢服务不受电池优化限制。厂商自启动选项仍需按设备设置检查。"
                        else
                            "锁屏后若服务容易停止，可在系统列表中将璇玑设为不受电池优化。",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { openSystemSettings(SystemSettingsTarget.BATTERY_OPTIMIZATION) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            ) { Text("电池设置", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            TextButton(
                                onClick = { openSystemSettings(SystemSettingsTarget.APP_DETAILS) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            ) { Text("应用详情", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        Text(
                            manufacturerStartupHint(Build.MANUFACTURER, Build.BRAND),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    AccessCard(
                        icon = Icons.Default.AccessibilityNew,
                        title = "无障碍服务",
                        status = if (accessibilityEnabled) "已启用" else "未启用",
                        tone = if (accessibilityEnabled) StatusTone.READY else StatusTone.OPTIONAL,
                        description = "用于读取屏幕内容和执行辅助操作；每次操作仍遵循天枢的审批策略。",
                    ) {
                        Button(onClick = ::openAccessibilitySettings) {
                            Text(if (accessibilityEnabled) "管理无障碍服务" else "开启无障碍服务")
                        }
                    }
                }
                item {
                    AccessCard(
                        icon = Icons.Default.Layers,
                        title = "悬浮窗",
                        status = if (permissionState.overlayGranted) "已授权" else "按需开启",
                        tone = if (permissionState.overlayGranted) StatusTone.READY else StatusTone.OPTIONAL,
                        description = "当前版本不依赖悬浮窗。仅在使用悬浮控件功能时开启。",
                    ) {
                        Button(onClick = { openSystemSettings(SystemSettingsTarget.OVERLAY) }) {
                            Text(if (permissionState.overlayGranted) "管理悬浮窗权限" else "打开悬浮窗设置")
                        }
                    }
                }

                item { PermissionSectionLabel("特权命令通道") }
                item {
                    AccessCard(
                        icon = Icons.Default.Security,
                        title = "Root / KernelSU",
                        status = commandStatus.root,
                        tone = when {
                            commandStatus.root == "已授权" -> StatusTone.READY
                            commandStatus.root == "未检查" -> StatusTone.OPTIONAL
                            else -> StatusTone.ATTENTION
                        },
                        description = "仅在设备已 Root 且璇玑通过授权时可用。",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = vm::checkRoot) { Text("检查并请求 Root") }
                            if (commandStatus.root == "已授权") {
                                TextButton(onClick = { vm.checkCommand(ShellBackend.ROOT) }) { Text("运行 id 检查") }
                            }
                        }
                        if (rootCommandCheck != null && commandStatus.root == "已授权") {
                            SelectionContainer {
                                Text(
                                    rootCommandCheck,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                item {
                    AccessCard(
                        icon = Icons.Default.SettingsSuggest,
                        title = "Shizuku",
                        status = commandStatus.shizuku,
                        tone = when (commandStatus.shizuku) {
                            "已授权" -> StatusTone.READY
                            "待授权" -> StatusTone.ATTENTION
                            else -> StatusTone.OPTIONAL
                        },
                        description = "Shizuku 运行后，可单独授权璇玑使用系统命令。",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = ::openShizuku) { Text("打开 Shizuku") }
                            if (commandStatus.shizuku == "待授权") {
                                Button(onClick = vm::requestShizuku) { Text("请求授权") }
                            }
                            if (commandStatus.shizuku == "已授权") {
                                Button(onClick = { vm.checkCommand(ShellBackend.SHIZUKU) }) { Text("运行 id 检查") }
                            }
                        }
                        if (shizukuCommandCheck != null && commandStatus.shizuku == "已授权") {
                            SelectionContainer {
                                Text(
                                    shizukuCommandCheck,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                item { PermissionSectionLabel("桥接状态") }
                item {
                    AccessCard(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Android MCP",
                        status = when (mcpStatus.state) {
                            "running" -> "已连接 · ${mcpStatus.toolCount} 个工具"
                            "error" -> "启动失败"
                            else -> "未启动"
                        },
                        tone = when (mcpStatus.state) {
                            "running" -> StatusTone.READY
                            "error" -> StatusTone.FAILED
                            else -> StatusTone.ATTENTION
                        },
                        description = mcpError?.take(180)
                            ?: "手机能力由本地 MCP 桥接给天枢；写入与设备操作仍受审批保护。",
                    ) { }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp)) {
                Text("完成")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PermissionSectionLabel(label: String) {
    Text(
        label,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AccessCard(
    icon: ImageVector,
    title: String,
    status: String,
    tone: StatusTone,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusPill(status, tone)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun StatusPill(label: String, tone: StatusTone) {
    val scheme = MaterialTheme.colorScheme
    val color = when (tone) {
        StatusTone.READY -> Success
        StatusTone.ATTENTION -> Warning
        StatusTone.OPTIONAL -> scheme.onSurfaceVariant
        StatusTone.FAILED -> Danger
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.13f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

package com.xuanji.android.ui.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xuanji.android.Settings
import com.xuanji.android.ProjectDocumentationActivity
import com.xuanji.android.StorageAccessMethod
import com.xuanji.android.hasStorageAccess
import com.xuanji.android.legacyStoragePermissions
import com.xuanji.android.storageAccessMethod
import com.xuanji.android.storageAccessSettingsIntent

/** 天枢预设服务商（名字必须和天枢 src/config/provider-presets.ts 一致） */
private val providers = listOf(
    "deepseek" to "DeepSeek",
    "glm" to "智谱 GLM",
    "kimi" to "Kimi",
    "minimax" to "MiniMax",
    "openai" to "OpenAI",
    "dashscope" to "通义千问",
    "siliconflow" to "硅基流动",
    "openrouter" to "OpenRouter",
)

/** 首次启动引导页：项目摘要 → 连接模型 → 文件权限。 */
@Composable
fun OnboardingScreen(saving: Boolean, error: String?, onDone: (Settings) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    var provider by remember { mutableStateOf("deepseek") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var reasoningEffort by remember { mutableStateOf("auto") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("璇玑", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("0${step + 1} / 03", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            when (step) {
                0 -> {
                    Text("天枢，于掌中启航。", style = MaterialTheme.typography.headlineMedium)
                    Text("璇玑将天枢运行时与 Android 的对话、文件、终端、浏览器和系统工具连接起来。",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("来源与许可", style = MaterialTheme.typography.titleMedium)
                    Text("璇玑原创代码采用 Apache-2.0。本应用包含 Tianshu-harness、Android 开源组件、Linux 运行环境和开放字体，各组件保留原作者版权与各自许可证。璇玑是独立整合项目。",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("模型请求发送至你配置的服务商。搜索内容发送至所选引擎；系统操作受 Android 权限与执行审批控制。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { ProjectDocumentationActivity.open(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("阅读完整技术文档与许可")
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("继续设置") }
                }
                1 -> {
                    Text("连接模型", style = MaterialTheme.typography.headlineMedium)
                    Text("选择服务商并输入凭据", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    providers.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (key, label) ->
                                FilterChip(
                                    selected = provider == key,
                                    onClick = { provider = key },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        apiKey, { apiKey = it.trim() },
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        model, { model = it.trim() },
                        label = { Text("模型名（可不填）") },
                        supportingText = { Text("不填则使用该服务商的默认模型") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        baseUrl, { baseUrl = it.trim() },
                        label = { Text("自定义 API 地址（可不填）") },
                        placeholder = { Text("https://api.example.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("思考强度", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("auto", "low", "medium", "high", "max").forEach { effort ->
                            FilterChip(selected = reasoningEffort == effort, onClick = { reasoningEffort = effort }, label = { Text(effort) })
                        }
                    }
                    Button({ step = 2 }, Modifier.fillMaxWidth().height(52.dp), enabled = apiKey.isNotBlank()) {
                        Text("下一步")
                    }
                }
                else -> PermissionStep(saving) { onDone(Settings(provider, apiKey, model, baseUrl, reasoningEffort)) }
            }
        }
    }
}

@Composable
private fun PermissionStep(saving: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(hasStorageAccess(context)) }
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = hasStorageAccess(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasStorageAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Text("授予文件权限", style = MaterialTheme.typography.headlineMedium)
    Text(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            "天枢需要文件访问权限才能浏览共享存储。你可以现在授权，也可以稍后在系统权限中开启。"
        else
            "天枢需要读取共享存储。Android ${Build.VERSION.SDK_INT} 会使用系统运行时存储权限。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Card(
        Modifier.fillMaxWidth().clickable(enabled = !granted) {
            when (storageAccessMethod(Build.VERSION.SDK_INT)) {
                StorageAccessMethod.ALL_FILES_SETTINGS -> storageAccessSettingsIntent(context)?.let(context::startActivity)
                StorageAccessMethod.LEGACY_RUNTIME_PERMISSIONS -> legacyPermissionLauncher.launch(legacyStoragePermissions())
            }
        },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "所有文件访问权限" else "共享存储权限",
                Modifier.weight(1f),
            )
            Text(
                when {
                    granted -> "已授权"
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> "去设置 ›"
                    else -> "请求权限 ›"
                },
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Button(onFinish, Modifier.fillMaxWidth().height(52.dp), enabled = !saving) {
        if (saving) {
            CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(if (saving) "正在保存配置…" else if (granted) "启动天枢" else "跳过，稍后授权")
    }
}

package com.xuanji.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuanji.android.XuanjiViewModel
import com.xuanji.android.ui.chat.ChatScreen
import com.xuanji.android.ui.onboarding.OnboardingScreen

/** 根界面：没配置过就进引导页，配置好就进对话页 */
@Composable
fun XuanjiApp(vm: XuanjiViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val savingSettings by vm.savingSettings.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val externalUri = LocalUriHandler.current
    val uriHandler = remember(context, externalUri) { object : UriHandler {
        override fun openUri(uri: String) {
            runCatching {
                if (uri.startsWith("https://", true) || uri.startsWith("http://", true))
                    com.xuanji.android.browser.BrowserActivity.open(context, uri)
                else externalUri.openUri(uri)
            }.onFailure { vm.error.value = it.message ?: "无法打开链接" }
        }
    } }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        if (settings.isComplete) ChatScreen(vm) else OnboardingScreen(
            saving = savingSettings,
            error = error,
            onDone = vm::saveSettings,
        )
    }
}

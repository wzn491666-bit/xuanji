package com.xuanji.android

/** Result of validating a chat submission before it reaches the HTTP client. */
data class ChatSendAvailability(
    val enabled: Boolean,
    val message: String? = null,
)

fun chatSendAvailability(
    serviceState: TianshuService.State,
    sessionReady: Boolean,
    running: Boolean,
    text: String,
    pendingPrompt: Boolean = false,
): ChatSendAvailability = when {
    text.isBlank() -> ChatSendAvailability(false)
    serviceState != TianshuService.State.RUNNING -> ChatSendAvailability(false, "天枢尚未运行")
    !sessionReady -> ChatSendAvailability(false, "会话正在准备，请稍候")
    running -> ChatSendAvailability(false, "当前任务仍在运行")
    pendingPrompt -> ChatSendAvailability(false, "上一条消息尚未确认，请查看其发送状态")
    else -> ChatSendAvailability(true)
}

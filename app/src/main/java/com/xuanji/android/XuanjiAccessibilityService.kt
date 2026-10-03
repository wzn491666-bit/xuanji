package com.xuanji.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import android.os.Bundle

/**
 * 璇玑无障碍服务
 *
 * 让天枢 AI 能够读取和操控手机屏幕：
 * - 读取当前屏幕的控件树（文字/坐标/可点击性）
 * - 模拟点击/输入/滚动/返回
 * - 截图并发给视觉模型理解
 *
 * 使用前需要用户在系统设置里手动开启：
 * 设置 → 无障碍 → 已下载的服务 → 开启"璇玑屏幕助手"
 */
class XuanjiAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "XuanjiA11y"

        // 全局单例，供其他组件调用
        var instance: XuanjiAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val serviceName = ComponentName(context, XuanjiAccessibilityService::class.java)
                .flattenToString()
            return enabled.split(':').any { it.equals(serviceName, ignoreCase = true) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "无障碍服务已连接")

        // 配置监听的事件类型
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 天枢通过主动调用 readScreen() 获取屏幕内容
        // 这里不需要处理每个事件，避免性能开销
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * 读取当前屏幕的控件树
     * 返回所有可见的文字、坐标和交互属性
     */
    fun readScreen(): List<ScreenNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = mutableListOf<ScreenNode>()
        try {
            traverseNode(root, nodes, remaining = intArrayOf(2_000))
        } finally {
            root.recycle()
        }
        return nodes
    }

    internal fun activeTarget(): AccessibilityTarget? {
        val root = rootInActiveWindow ?: return null
        return try {
            root.packageName?.toString()?.let { AccessibilityTarget(it, root.windowId) }
        } finally {
            root.recycle()
        }
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        result: MutableList<ScreenNode>,
        ancestorClickable: Boolean = false,
        ancestorScrollable: Boolean = false,
        remaining: IntArray,
        depth: Int = 0,
    ) {
        if (result.size >= 300 || remaining[0]-- <= 0 || depth >= 64) return
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val hasClickAction = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
        val clickable = nodeHasClickSemantics(node.isClickable, hasClickAction, ancestorClickable)
        val hasScrollAction = node.actionList.any {
            it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ||
                    it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val scrollable = node.isScrollable || hasScrollAction || ancestorScrollable

        if (node.isVisibleToUser && (node.text != null || node.contentDescription != null)) {
            result.add(ScreenNode(
                text = (node.text?.toString() ?: node.contentDescription?.toString().orEmpty()).take(4_096),
                x = bounds.centerX(),
                y = bounds.centerY(),
                width = bounds.width(),
                height = bounds.height(),
                clickable = clickable,
                editable = node.isEditable,
                scrollable = scrollable
            ))
        }

        for (i in 0 until node.childCount) {
            if (result.size >= 300 || remaining[0] <= 0) break
            node.getChild(i)?.let { child ->
                try {
                    traverseNode(child, result, clickable, scrollable, remaining, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    /**
     * 点击指定坐标
     */
    fun tap(x: Float, y: Float): Boolean {
        val path = android.graphics.Path().apply { moveTo(x, y); lineTo(x, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * 滑动
     */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300): Boolean {
        val path = android.graphics.Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * 按下返回键
     */
    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    /**
     * 回到桌面
     */
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Set text in the first editable node on screen. */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            val target = findEditable(root, intArrayOf(2_000)) ?: return false
            return try {
                target.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) },
                )
            } finally {
                target.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    private fun findEditable(node: AccessibilityNodeInfo, remaining: IntArray, depth: Int = 0): AccessibilityNodeInfo? {
        if (remaining[0]-- <= 0 || depth >= 64) return null
        if (node.isEditable && node.isVisibleToUser && node.isEnabled) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            if (remaining[0] <= 0) break
            val child = node.getChild(i) ?: continue
            val found = try {
                findEditable(child, remaining, depth + 1)
            } finally {
                child.recycle()
            }
            if (found != null) return found
        }
        return null
    }
}

data class ScreenNode(
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean
)

internal data class AccessibilityTarget(val packageName: String, val windowId: Int)

internal suspend fun awaitAccessibilityTarget(
    expected: AccessibilityTarget,
    timeoutMillis: Long = 5_000,
    currentTarget: suspend () -> AccessibilityTarget?,
): Boolean = withTimeoutOrNull(timeoutMillis) {
    while (currentTarget() != expected) delay(100)
    true
} == true

package com.xuanji.android.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
internal fun ChatFocusStars(focused: Boolean, transfer: State<Float>) {
    val foreground = rememberChatForeground()
    val motion = rememberSystemMotionScale()
    val opacity = animateFloatAsState(if (focused && foreground) 1f else 0f,
        tween(if (motion == 0f) 0 else 320), label = "composer starlight")
    val phase = remember { mutableDoubleStateOf(0.0) }
    val covered by remember(transfer) { derivedStateOf { transfer.value >= 0.999f } }
    val moving = focused && foreground && motion > 0f && !covered
    LaunchedEffect(moving, motion) {
        if (!moving) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            phase.doubleValue += ((now - last) / 1_000_000_000.0).coerceAtMost(0.05) / motion
            last = now
        }
    }
    Box(Modifier.fillMaxSize().imePadding().drawWithCache {
        val height = 264.dp.toPx().coerceAtMost(size.height * 0.6f)
        val points = List(18) { i -> Offset(size.width * ((i * 0.618034f + 0.09f) % 1f),
            size.height - 78.dp.toPx() - height * (0.08f + ((i * 0.382f) % 0.7f))) }
        val colors = List(18) { i -> if (i % 5 == 0) OrbitGold else lerp(OrbitInk, OrbitBlue, i / 17f) }
        onDrawBehind {
            val a = opacity.value * (1f - transfer.value.coerceIn(0f, 1f))
            if (a > 0f) {
                points.forEachIndexed { i, center ->
                    val twinkle = (0.45 + 0.55 * ((sin(phase.doubleValue * 1.3 + i * 2.4) + 1.0) / 2.0)).toFloat()
                    val radius = (if (i % 4 == 0) 3.2f else 1.7f) * density * twinkle
                    val color = colors[i].copy(alpha = a * twinkle * 0.56f)
                    drawLine(color, center - Offset(radius, 0f), center + Offset(radius, 0f), 0.8.dp.toPx())
                    drawLine(color, center - Offset(0f, radius * 1.25f), center + Offset(0f, radius * 1.25f), 0.8.dp.toPx())
                }
            }
        }
    })
}

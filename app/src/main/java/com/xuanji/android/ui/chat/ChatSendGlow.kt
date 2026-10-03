package com.xuanji.android.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
internal fun ChatSendGlow(request: String, waiting: Boolean, effort: String, transfer: MutableFloatState, visible: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberSystemMotionScale()
    val foreground = rememberChatForeground()
    val opacity = remember { mutableFloatStateOf(0f) }
    val opening = remember { mutableFloatStateOf(0f) }
    val phase = remember { mutableDoubleStateOf(0.0) }
    val latestPower by rememberUpdatedState(effortEnergy(effort))
    LaunchedEffect(request, waiting, foreground, motion, visible) {
        if (!foreground || !visible) {
            opacity.floatValue = 0f
            transfer.floatValue = 0f
            return@LaunchedEffect
        }
        if (motion == 0f) {
            opacity.floatValue = if (waiting) 0.72f else 0f
            opening.floatValue = 1f
            transfer.floatValue = if (waiting) 1f else 0f
            return@LaunchedEffect
        }
        val from = opacity.floatValue
        val startOpening = opening.floatValue
        if (!waiting && from == 0f) return@LaunchedEffect
        val start = withFrameNanos { it }
        var last = start
        var elapsed: Float
        do {
            val now = withFrameNanos { it }
            val delta = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f) / motion
            last = now
            phase.doubleValue += delta.toDouble() * (0.8f + latestPower * 0.4f)
            elapsed = (now - start) / 1_000_000_000f / motion
            val fraction = (elapsed / if (waiting) 0.75f else 0.55f).coerceIn(0f, 1f)
            val smooth = fraction * fraction * (3f - 2f * fraction)
            opacity.floatValue = if (waiting) from + (0.84f - from) * smooth else from * (1f - smooth)
            opening.floatValue = if (waiting) startOpening + (1f - startOpening) * smooth else startOpening
            transfer.floatValue = opacity.floatValue / 0.84f
        } while (waiting || elapsed < 0.55f)
        opacity.floatValue = 0f
        opening.floatValue = 0f
        transfer.floatValue = 0f
    }
    Box(Modifier.fillMaxWidth().height(208.dp).drawWithCache {
        val center = Offset(size.width * 0.5f, 110.dp.toPx())
        val dark = scheme.background.luminance() < 0.5f
        onDrawBehind {
            val power = latestPower
            val breathing = (0.9 + 0.1 * sin(phase.doubleValue * 0.8)).toFloat()
            drawStellarOrbits(center, size.width * 0.25f, 20.dp.toPx(), phase.doubleValue, power,
                opacity.floatValue * breathing, dark, opening.floatValue)
            if (opacity.floatValue > 0f) {
                val ink = if (dark) androidx.compose.ui.graphics.Color(0xFFA7DAE8) else OrbitBlue
                val point = center - Offset(0f, 20.dp.toPx())
                val ray = (2f + opening.floatValue * 3f).dp.toPx()
                drawLine(ink.copy(alpha = opacity.floatValue * 0.6f), point - Offset(ray, 0f), point + Offset(ray, 0f), 0.8.dp.toPx())
                drawLine(ink.copy(alpha = opacity.floatValue * 0.6f), point - Offset(0f, ray), point + Offset(0f, ray), 0.8.dp.toPx())
            }
        }
    })
}

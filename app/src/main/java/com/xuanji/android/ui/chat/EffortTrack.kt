package com.xuanji.android.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun EffortTrack(
    position: State<Float>, lastIndex: Int, effort: String, active: Boolean,
    summit: Int, enabled: Boolean, visible: Boolean, levels: List<String> = emptyList(),
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val cyan = if (dark) Color(0xFF81C8D5) else Color(0xFF2B8198)
    val ink = if (dark) Color(0xFFA3BCC9) else Color(0xFF315C74)
    val gold = if (dark) Color(0xFFE2BB75) else Color(0xFFBE914C)
    val motionScale = rememberSystemMotionScale()
    val foreground = rememberChatForeground()
    val moving = enabled && visible && foreground && motionScale > 0f && effort != "off"
    val phase = remember { mutableDoubleStateOf(0.0) }
    val crown = remember { mutableFloatStateOf(1f) }
    val currentLevels by rememberUpdatedState(levels)
    val currentEffort by rememberUpdatedState(effort)
    val currentActive by rememberUpdatedState(active)
    var seenSummit by remember { mutableIntStateOf(summit) }
    LaunchedEffect(moving, motionScale) {
        if (!moving) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f) / motionScale
            previous = now
            val power = if (currentLevels.isEmpty()) effortEnergy(currentEffort)
                else effortEnergyAt(position.value, currentLevels)
            phase.doubleValue += seconds.toDouble() * (0.12f + power * 0.07f) *
                (if (currentActive) 1.4f else 1f)
        }
    }
    LaunchedEffect(summit, motionScale, foreground, visible) {
        val reached = summit != seenSummit
        seenSummit = summit
        if (!reached || motionScale == 0f || !foreground || !visible) {
            crown.floatValue = 1f
            return@LaunchedEffect
        }
        crown.floatValue = 0f
        val start = withFrameNanos { it }
        do {
            crown.floatValue = ((withFrameNanos { it } - start) / (950_000_000f * motionScale)).coerceIn(0f, 1f)
        } while (crown.floatValue < 1f)
    }
    Box(Modifier.fillMaxWidth().height(80.dp).drawWithCache {
        val height = 56.dp.toPx()
        val top = (size.height - height) / 2f
        val mid = size.height / 2f
        // Native Slider reserves half of its 48dp thumb slot at both ends.
        val reservation = 24.dp.toPx()
        val left = -reservation
        val width = size.width + reservation * 2f
        val rail = Rect(left, top, size.width + reservation, top + height)
        val mask = Path().apply { addRoundRect(RoundRect(rail, CornerRadius(height / 2f))) }
        val guideStroke = Stroke(0.8.dp.toPx())
        val orbitStroke = Stroke(0.8.dp.toPx())
        val roundStroke = Stroke(1.dp.toPx())
        val orbitCenter = Offset(size.width / 2f, mid)
        val radiusX = size.width / 2f + 13.dp.toPx()
        val railBase = if (dark) scheme.surfaceContainerLow else Color(0xFFF7F9FA)
        onDrawBehind {
            // Only draw reads the clock; animation never measures or recomposes the control.
            val fraction = (position.value / lastIndex.coerceAtLeast(1)).coerceIn(0f, 1f)
            val tip = size.width * fraction
            val power = (if (levels.isEmpty()) effortEnergy(effort) else effortEnergyAt(position.value, levels)).coerceIn(0f, 4f)
            val opacity = if (enabled) 1f else 0.45f
            val t = phase.doubleValue
            val celebration = sin(crown.floatValue * PI.toFloat()).coerceAtLeast(0f)
            drawRoundRect(railBase, rail.topLeft, Size(width, height), CornerRadius(height / 2f))
            drawRoundRect(scheme.outlineVariant.copy(alpha = if (dark) 0.65f else 0.7f), rail.topLeft,
                Size(width, height), CornerRadius(height / 2f), style = roundStroke)
            clipPath(mask) {
                drawLine(ink.copy(alpha = 0.13f * opacity), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
                drawLine(cyan.copy(alpha = 0.46f * opacity), Offset(0f, mid), Offset(tip, mid), 1.2.dp.toPx())
                repeat(5) { index ->
                    val activation = (power - index + 1f).coerceIn(0f, 1f)
                    val radiusY = (7f + index * 3.3f) * density
                    val oval = Rect(orbitCenter.x - radiusX, mid - radiusY, orbitCenter.x + radiusX, mid + radiusY)
                    val orbitColor = if (index == 4) gold else if (index % 2 == 0) cyan else ink
                    drawOval(orbitColor.copy(alpha = (0.055f + activation * 0.12f) * opacity), oval.topLeft, oval.size, style = orbitStroke)
                    if (activation > 0f && enabled && power > 0f) {
                        val angle = (((t * (1.0 + index * 0.16) + index * 1.7) % (PI * 2)) *
                            if (index % 2 == 0) 1.0 else -1.0).toFloat()
                        val point = Offset(orbitCenter.x + cos(angle) * radiusX, mid + sin(angle) * radiusY)
                        val arcStart = angle * 180f / PI.toFloat() - if (index % 2 == 0) 13f else 0f
                        drawArc(orbitColor.copy(alpha = activation * 0.32f), arcStart, 13f, false,
                            oval.topLeft, oval.size, style = Stroke(1.4.dp.toPx()))
                        drawCircle(orbitColor.copy(alpha = activation * 0.7f), 1.5.dp.toPx(), point)
                    }
                }
                val divisions = (lastIndex.coerceAtLeast(1) * 8).coerceAtMost(48)
                repeat(divisions + 1) { index ->
                    val x = size.width * index / divisions
                    val major = index % 8 == 0
                    val length = if (major) 3.dp.toPx() else 1.5.dp.toPx()
                    drawLine(ink.copy(alpha = if (major) 0.22f else 0.11f), Offset(x, top + 7.dp.toPx()),
                        Offset(x, top + 7.dp.toPx() + length), guideStroke.width)
                }
                repeat(lastIndex + 1) { index ->
                    val x = size.width * index / lastIndex.coerceAtLeast(1)
                    val selected = kotlin.math.abs(x - tip) < 1.dp.toPx()
                    drawCircle((if (x <= tip) cyan else ink).copy(alpha = (if (selected) 0.8f else 0.28f) * opacity),
                        if (selected) 2.5.dp.toPx() else 2.dp.toPx(), Offset(x, mid))
                }
                if (celebration > 0f && power >= 3.9f) {
                    val center = Offset(tip, mid)
                    val radius = (21f + crown.floatValue * 2f) * density
                    drawCircle(gold.copy(alpha = celebration * 0.7f), radius, center, style = Stroke(1.dp.toPx()))
                    repeat(8) { ray ->
                        val angle = ray * PI.toFloat() / 4f + PI.toFloat() / 8f
                        val vector = Offset(cos(angle), sin(angle))
                        drawLine(gold.copy(alpha = celebration * 0.62f), center + vector * (radius - 2.dp.toPx()),
                            center + vector * (radius + 1.dp.toPx()), 0.9.dp.toPx())
                    }
                }
            }
        }
    })
}

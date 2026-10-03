package com.xuanji.android.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val StellarTurn = 6.2831855f
private val StellarCenter = Offset(108f, 108f)
private val StellarBlue = Color(0xFF244EA2)
private val StellarGold = Color(0xFFB98D42)

/** Engraved stellar globe. Frame changes invalidate drawing, never its parent's layout. */
@Composable
internal fun StellarBrand(modifier: Modifier = Modifier, animated: Boolean = true) {
    val foreground = rememberChatForeground()
    val motionScale = rememberSystemMotionScale()
    val phase = remember { mutableFloatStateOf(0f) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    LaunchedEffect(foreground, motionScale, animated) {
        if (!foreground || motionScale == 0f || !animated) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val delta = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
            previous = now
            phase.floatValue = (phase.floatValue + delta * StellarTurn / (44f * motionScale)) % (StellarTurn * 4f)
        }
    }
    Box(modifier.size(216.dp).semantics { contentDescription = "璇玑恒星、星环与环绕轨道" }.drawWithCache {
        val unit = size.minDimension / 216f
        val left = (size.width - 216f * unit) / 2f
        val top = (size.height - 216f * unit) / 2f
        val globe = Path().apply { addOval(androidx.compose.ui.geometry.Rect(52f, 52f, 164f, 164f)) }
        val first = StellarOrbit(91f, 29f, -24f, 0.65f, StellarGold)
        val second = StellarOrbit(78f, 21f, 59f, 3.7f, StellarBlue)
        val orbitStroke = Stroke(0.65f)
        val ringStroke = Stroke(2.1f)
        val ringHighlightStroke = Stroke(0.45f)
        val ringBrush = Brush.linearGradient(
            listOf(Color(0xFF8EA7C0), Color(0xFFE9EEF2), Color(0xFFB9A57D)),
            start = Offset(28f, 154f), end = Offset(184f, 64f),
        )
        val fineStroke = Stroke(0.34f)
        val contourStroke = Stroke(0.46f)
        val limbStroke = Stroke(0.75f)
        val highlightStroke = Stroke(1.1f)
        val innerLimbStroke = Stroke(0.45f)
        val globeBrush = Brush.radialGradient(
            0f to Color(0xFFFFFEF7), 0.26f to Color(0xFFFFF0CE),
            0.45f to Color(0xFFD9EDF1), 0.66f to Color(0xFF85BBDC),
            0.84f to Color(0xFF487EB9), 1f to Color(0xFF284E9C),
            center = Offset(85f, 80f), radius = 105f,
        )
        val pearlBrush = Brush.radialGradient(
            0f to Color(0xFFFFFEF4), 0.45f to Color(0xFFFFF2CD).copy(alpha = 0.64f),
            1f to Color.Transparent, center = Offset(91f, 88f), radius = 32f,
        )
        val edgeBrush = Brush.linearGradient(
            listOf(Color(0xFFD9C395), Color(0xFFA1D7E5), StellarBlue),
            start = Offset(67f, 61f), end = Offset(151f, 155f),
        )
        val currents = stellarCurrents()
        val contours = stellarContours()
        val filaments = stellarFilaments()
        val core = stellarCore()
        val highlight = Path().apply {
            moveTo(69f, 83f)
            cubicTo(76f, 63f, 103f, 54f, 124f, 63f)
        }
        val glint = stellarSpark(171f, 54f, 3.5f)
        val smallGlint = stellarSpark(45f, 163f, 2.1f)
        val rearInk = if (dark) Color(0xFF8DA7C6) else Color(0xFF8298AF)
        val frontInk = if (dark) Color(0xFFA8BFDA) else Color(0xFF5E7F9E)
        val goldInk = if (dark) Color(0xFFE4C289) else StellarGold
        onDrawBehind {
            withTransform({ translate(left, top); scale(unit, unit, pivot = Offset.Zero) }) {
                val angle = phase.floatValue
                drawPath(first.rear, rearInk.copy(alpha = 0.40f), style = ringStroke)
                drawPath(second.rear, rearInk.copy(alpha = 0.46f), style = orbitStroke)
                drawStellarTrail(first, angle + first.start, 1f, front = false)
                drawStellarTrail(second, second.start - angle * 0.75f, -1f, front = false)
                if (sin(angle + first.start) < 0f) drawStellarSatellite(first, angle + first.start)
                if (sin(second.start - angle * 0.75f) < 0f) drawStellarSatellite(second, second.start - angle * 0.75f)

                drawPath(globe, globeBrush)
                clipPath(globe) {
                    drawCircle(pearlBrush, 32f, Offset(91f, 88f))
                    for (path in currents) drawPath(path, StellarBlue.copy(alpha = 0.31f), style = fineStroke)
                    for (path in contours) drawPath(path, StellarBlue.copy(alpha = 0.22f), style = contourStroke)
                    drawPath(filaments, Color(0xFF1B4B98).copy(alpha = 0.35f), style = fineStroke)
                    drawPath(core, StellarGold.copy(alpha = 0.55f), style = fineStroke)
                    drawPath(highlight, Color.White.copy(alpha = 0.78f), style = highlightStroke)
                }
                drawPath(globe, edgeBrush, style = limbStroke)
                drawCircle(Color.White.copy(alpha = 0.26f), 53.6f, StellarCenter, style = innerLimbStroke)

                drawPath(first.front, ringBrush, alpha = 0.85f, style = ringStroke)
                drawPath(first.front, Color.White.copy(alpha = 0.62f), style = ringHighlightStroke)
                drawPath(second.front, frontInk.copy(alpha = 0.70f), style = orbitStroke)
                drawStellarTrail(first, angle + first.start, 1f, front = true)
                drawStellarTrail(second, second.start - angle * 0.75f, -1f, front = true)
                if (sin(angle + first.start) >= 0f) drawStellarSatellite(first, angle + first.start)
                if (sin(second.start - angle * 0.75f) >= 0f) drawStellarSatellite(second, second.start - angle * 0.75f)
                drawPath(glint, goldInk.copy(alpha = 0.78f))
                drawPath(smallGlint, frontInk.copy(alpha = 0.56f))
            }
        }
    })
}

private class StellarOrbit(
    val major: Float, val minor: Float, degrees: Float, val start: Float, val color: Color,
) {
    private val rotation = degrees * PI.toFloat() / 180f
    private val cosine = cos(rotation)
    private val sine = sin(rotation)
    val rear = arc(PI.toFloat(), StellarTurn)
    val front = arc(0f, PI.toFloat())

    fun point(angle: Float): Offset {
        val x = major * cos(angle)
        val y = minor * sin(angle)
        return StellarCenter + Offset(x * cosine - y * sine, x * sine + y * cosine)
    }

    private fun arc(from: Float, to: Float) = Path().apply {
        repeat(97) { index ->
            val point = point(from + (to - from) * index / 96f)
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }
}

private fun DrawScope.drawStellarSatellite(orbit: StellarOrbit, angle: Float) {
    val point = orbit.point(angle)
    val depth = sin(angle)
    val radius = (if (orbit.color == StellarGold) 3.35f else 2.5f) * (0.94f + depth * 0.06f)
    drawCircle(orbit.color.copy(alpha = if (depth < 0f) 0.66f else 1f), radius, point)
    drawCircle(Color(0xFFFFFCF1).copy(alpha = 0.9f), radius * 0.34f, point + Offset(-radius * 0.27f, -radius * 0.32f))
}

private fun DrawScope.drawStellarTrail(orbit: StellarOrbit, angle: Float, direction: Float, front: Boolean) {
    repeat(10) { step ->
        val from = angle - direction * step * 0.018f
        val to = from - direction * 0.018f
        if ((sin((from + to) / 2f) >= 0f) == front) {
            drawLine(orbit.color.copy(alpha = (1f - step / 10f) * if (front) 0.25f else 0.12f),
                orbit.point(from), orbit.point(to), strokeWidth = 1.05f, cap = StrokeCap.Round)
        }
    }
}

private fun stellarCurrents(): Array<Path> = Array(32) { line ->
    val radius = 7f + line * 2.1f
    Path().apply {
        repeat(181) { sample ->
            val angle = StellarTurn * sample / 180f
            val ripple = sin(angle * 3f + radius * 0.14f) * 1.8f + sin(angle * 7f + radius * 0.17f) * 0.65f
            val around = angle + sin(angle * 2f + radius * 0.03f) * 0.16f
            val x = 95f + line * 0.22f + cos(around) * (radius + ripple)
            val y = 92f + line * 0.12f + sin(around) * (radius + ripple) * 0.78f
            if (sample == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
}

// Spherical projection curves the fine crosshatching into the shaded hemisphere.
private fun stellarContours(): Array<Path> = Array(19) { line ->
    val latitude = -1.39f + line * 2.78f / 18f
    Path().apply {
        repeat(121) { sample ->
            val longitude = -PI.toFloat() / 2f + PI.toFloat() * sample / 120f
            val wave = sin(longitude * 5f + latitude * 6f) * cos(longitude) * 0.031f
            val point = stellarSurface(
                cos(latitude + wave) * sin(longitude) * 55.6f,
                sin(latitude + wave) * 55.6f,
            )
            if (sample == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }
}

private fun stellarSurface(x: Float, y: Float): Offset {
    val angle = -0.44f
    return StellarCenter + Offset(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle))
}

private fun stellarFilaments() = Path().apply {
    repeat(61) { index ->
        val y = -49f + index * 1.62f
        val edge = sqrt((55f * 55f - y * y).coerceAtLeast(0f))
        val x = edge * (0.58f + (index % 4) * 0.06f)
        val from = stellarSurface(x - 4.5f, y)
        val middle = stellarSurface(x + 0.8f, y - 1.0f)
        val to = stellarSurface(edge - 0.8f, y + 0.9f)
        moveTo(from.x, from.y)
        quadraticTo(middle.x, middle.y, to.x, to.y)
    }
    repeat(38) { index ->
        val angle = index * 2.399963f
        val radius = sqrt((index + 0.5f) / 38f) * 42f
        val x = 101f + cos(angle) * radius
        val y = 104f + sin(angle) * radius
        val curl = angle + 0.8f
        moveTo(x, y)
        cubicTo(x + cos(curl) * 2f, y + sin(curl) * 2f,
            x + cos(curl + 0.6f) * 5f, y + sin(curl + 0.6f) * 5f,
            x + cos(curl + 1.0f) * 7f, y + sin(curl + 1.0f) * 7f)
    }
}

private fun stellarCore() = Path().apply {
    repeat(9) { line ->
        val radius = 4f + line * 1.9f
        repeat(97) { sample ->
            val angle = StellarTurn * sample / 96f
            val ripple = sin(angle * 3f + line * 0.68f) * 0.7f + sin(angle * 7f + line) * 0.35f
            val x = 91f + cos(angle) * (radius + ripple)
            val y = 88f + sin(angle) * (radius + ripple) * 0.77f
            if (sample == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
}

private fun stellarSpark(x: Float, y: Float, radius: Float) = Path().apply {
    moveTo(x, y - radius)
    quadraticTo(x + radius * 0.16f, y - radius * 0.16f, x + radius, y)
    quadraticTo(x + radius * 0.16f, y + radius * 0.16f, x, y + radius)
    quadraticTo(x - radius * 0.16f, y + radius * 0.16f, x - radius, y)
    quadraticTo(x - radius * 0.16f, y - radius * 0.16f, x, y - radius)
    close()
}

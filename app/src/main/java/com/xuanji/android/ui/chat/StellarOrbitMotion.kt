package com.xuanji.android.ui.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val OrbitInk = Color(0xFF315C74)
internal val OrbitBlue = Color(0xFF2B8198)
internal val OrbitGold = Color(0xFFBE914C)

/** Fine orbital paths, with all frame values consumed by the drawing phase. */
internal fun DrawScope.drawStellarOrbits(
    center: Offset, radiusX: Float, radiusY: Float, phase: Double, power: Float,
    opacity: Float, dark: Boolean, opening: Float = 1f, crown: Float = 0f,
    partial: Boolean = false,
) {
    if (opacity <= 0f || radiusX <= 0f || radiusY <= 0f) return
    val p = power.coerceIn(0f, 4f)
    val open = opening.coerceIn(0f, 1f)
    val cool = if (dark) Color(0xFF88D6E3) else lerp(OrbitInk, OrbitBlue, p / 4f)
    val gold = if (dark) Color(0xFFE2C581) else OrbitGold
    val baseAlpha = opacity.coerceIn(0f, 1f)
    repeat(6) { ring ->
        val presence = (1.4f + p - ring).coerceIn(0f, 1f)
        if (presence > 0f) {
            val rx = radiusX + ring * 11.dp.toPx()
            val ry = radiusY + ring * 9.dp.toPx()
            val origin = center - Offset(rx, ry)
            val bounds = Size(rx * 2, ry * 2)
            val ink = if (ring == 4) gold else cool
            val alpha = baseAlpha * presence
            withTransform({ rotate(if (partial) -8f else -16f + ring * 5.5f, center) }) {
                val start = if (partial) 204f else 18f + ring * 29f
                val sweep = if (partial) 134f else 276f
                drawArc(ink.copy(alpha = alpha * if (dark) 0.25f else 0.18f), start, sweep * open,
                    false, origin, bounds, style = Stroke(0.8.dp.toPx()))
                drawArc(ink.copy(alpha = alpha * 0.5f), start + sweep * 0.42f,
                    sweep * 0.16f * open, false, origin, bounds, style = Stroke(1.15.dp.toPx()))
                val count = 2 + ring % 2
                repeat(count) { point ->
                    val travel = ((phase * (0.035 + ring * 0.009) + point / count.toDouble() + ring * 0.173) % 1.0).toFloat()
                    val angle = start + sweep * travel
                    val radians = angle * PI.toFloat() / 180f
                    val pos = center + Offset(cos(radians) * rx, sin(radians) * ry)
                    val fade = sin(travel * PI.toFloat()).coerceAtLeast(0f) * open
                    drawArc(ink.copy(alpha = alpha * fade * 0.58f), angle - 6f - p,
                        6f + p, false, origin, bounds, style = Stroke(1.1.dp.toPx()))
                    drawCircle(ink.copy(alpha = alpha * fade), (1.25f + p * 0.12f).dp.toPx(), pos)
                    if (point == 0 && ring % 2 == 0) {
                        val ray = (2.5f + p * 0.3f).dp.toPx()
                        drawLine(ink.copy(alpha = alpha * fade * 0.55f), pos - Offset(ray, 0f), pos + Offset(ray, 0f), 0.6.dp.toPx())
                        drawLine(ink.copy(alpha = alpha * fade * 0.55f), pos - Offset(0f, ray), pos + Offset(0f, ray), 0.6.dp.toPx())
                    }
                }
            }
        }
    }
    if (crown > 0f) {
        val radius = radiusX + 58.dp.toPx()
        val height = radiusY + 48.dp.toPx()
        drawArc(gold.copy(alpha = baseAlpha * crown * 0.65f), if (partial) 210f else 15f,
            if (partial) 122f else 330f, false, center - Offset(radius, height),
            Size(radius * 2, height * 2), style = Stroke(1.2.dp.toPx()))
    }
}

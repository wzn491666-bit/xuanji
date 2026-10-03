package com.xuanji.android.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.sin

internal fun effortEnergy(effort: String): Float = when (effort) {
    "off" -> 0f; "low" -> 1f; "medium" -> 2f; "high" -> 3f; "max" -> 4f; else -> 2f
}

internal fun effortEnergyAt(position: Float, levels: List<String>): Float {
    if (levels.isEmpty()) return 0f
    val bounded = position.coerceIn(0f, levels.lastIndex.toFloat())
    val lower = floor(bounded).toInt()
    val from = effortEnergy(levels[lower])
    val to = effortEnergy(levels[(lower + 1).coerceAtMost(levels.lastIndex)])
    return from + (to - from) * (bounded - lower)
}

@Composable
internal fun ChatEffortBackdrop(
    effort: String, preview: State<Float>, interacting: State<Boolean>, transfer: State<Float>, visible: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberSystemMotionScale()
    val foreground = rememberChatForeground()
    val target = preview.value.takeUnless { it.isNaN() } ?: effortEnergy(effort)
    val power = animateFloatAsState(target, if (motion == 0f) snap() else
        spring(dampingRatio = 1f, stiffness = 380f), label = "orbital density")
    val phase = remember { mutableDoubleStateOf(0.0) }
    val crown = remember { mutableFloatStateOf(0f) }
    val covered by remember(transfer) { derivedStateOf { transfer.value >= 0.999f } }
    val emitting = visible && foreground && motion > 0f && target > 0f && !covered
    val atSummit = interacting.value && target >= 3.95f
    LaunchedEffect(emitting, motion) {
        if (!emitting) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val delta = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f) / motion
            phase.doubleValue += delta.toDouble() * (0.6f + power.value * 0.42f)
            last = now
        }
    }
    LaunchedEffect(atSummit, foreground, motion, visible) {
        crown.floatValue = 0f
        if (!atSummit || !foreground || motion == 0f || !visible) return@LaunchedEffect
        val start = withFrameNanos { it }
        var progress: Float
        do {
            progress = ((withFrameNanos { it } - start) / 950_000_000f / motion).coerceIn(0f, 1f)
            crown.floatValue = sin(progress * Math.PI.toFloat()).coerceAtLeast(0f)
        } while (progress < 1f)
        crown.floatValue = 0f
    }
    Box(Modifier.fillMaxSize().imePadding().drawWithCache {
        val dark = scheme.background.luminance() < 0.5f
        val center = Offset(size.width * 0.5f, size.height + 54.dp.toPx())
        val radiusY = 164.dp.toPx().coerceAtMost(size.height * 0.36f)
        onDrawBehind {
            drawRect(scheme.background)
            if (visible) drawStellarOrbits(center, size.width * 0.65f, radiusY, phase.doubleValue,
                power.value, (if (interacting.value) 0.88f else 0.52f) * (1f - transfer.value.coerceIn(0f, 1f)),
                dark, crown = crown.floatValue, partial = true)
        }
    })
}

package com.xuanji.android.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.roundToInt

internal fun effortLabel(effort: String) = when (effort) {
    "off" -> "关闭"; "low" -> "低"; "medium" -> "中"; "high" -> "高"; "max" -> "最高"; else -> "自动"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReasoningEffortControl(
    effort: String, levels: List<String>, busy: Boolean,
    onSelect: (String) -> Unit,
    onPreview: (Float?, Boolean) -> Unit = { _, _ -> },
) {
    val scheme = MaterialTheme.colorScheme
    val motionScale = rememberSystemMotionScale()
    val dark = scheme.surface.luminance() < 0.5f
    val orbitCyan = if (dark) Color(0xFF81C8D5) else Color(0xFF2B8198)
    val orbitInk = if (dark) Color(0xFFA3BCC9) else Color(0xFF315C74)
    val orbitGold = if (dark) Color(0xFFE2BB75) else Color(0xFFBE914C)
    val haptics = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val dragging by interactions.collectIsDraggedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val engaged = dragging || pressed
    val sliderColors = SliderDefaults.colors(activeTrackColor = orbitCyan, inactiveTrackColor = scheme.outlineVariant,
        activeTickColor = orbitCyan, inactiveTickColor = orbitInk.copy(alpha = 0.3f))
    val visualPosition = remember(effort, levels) { mutableFloatStateOf(levels.indexOf(effort).takeIf { it >= 0 }?.toFloat() ?: levels.indexOf("high").coerceAtLeast(0).toFloat()) }
    var position by visualPosition
    var summit by remember { mutableIntStateOf(0) }
    var previewTouched by remember { mutableStateOf(false) }
    val thumbScale = animateFloatAsState(if (engaged) 1.06f else 1f,
        if (motionScale == 0f) tween(0) else spring(dampingRatio = 1f, stiffness = 550f), label = "orbit thumb pressure")
    LaunchedEffect(effort, busy, levels, engaged) {
        if (!busy && !engaged) position = levels.indexOf(effort).takeIf { it >= 0 }?.toFloat() ?: levels.indexOf("high").coerceAtLeast(0).toFloat()
    }
    val notifyPreview by rememberUpdatedState(onPreview)
    LaunchedEffect(position, engaged, busy, levels, previewTouched) {
        val previewing = levels.size > 1 && (engaged || busy && previewTouched)
        notifyPreview(if (previewing) effortEnergyAt(position, levels) else null, engaged)
        if (!engaged && !busy) previewTouched = false
    }
    DisposableEffect(Unit) { onDispose { notifyPreview(null, false) } }
    val selected = levels.getOrNull(position.roundToInt()) ?: "auto"
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth().height(48.dp).padding(horizontal = 36.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(if (busy) "正在应用…" else effortLabel(if (engaged) selected else effort),
                    transitionSpec = { (fadeIn(tween(if (motionScale == 0f) 0 else 110)) togetherWith
                        fadeOut(tween(if (motionScale == 0f) 0 else 70))).using(null) }, label = "effort label") { label ->
                    Text(label, style = MaterialTheme.typography.titleMedium, color = orbitInk)
                }
            }
            IconButton(onClick = { onSelect("auto") }, enabled = !busy && levels.isNotEmpty(), modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                Icon(Icons.Default.RestartAlt, "恢复自动思考强度", Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
            }
        }
        if (levels.size > 1) {
            Slider(value = position, onValueChange = {
                if (!busy) {
                    previewTouched = true
                    val bounded = it.coerceIn(0f, levels.lastIndex.toFloat())
                    if (bounded.roundToInt() != position.roundToInt()) {
                        val reachingMax = levels.getOrNull(bounded.roundToInt()) == "max"
                        haptics.performHapticFeedback(if (reachingMax) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                        if (reachingMax) summit++
                    }
                    position = bounded
                }
            },
                onValueChangeFinished = {
                    val committed = levels[position.roundToInt().coerceIn(0, levels.lastIndex)]
                    if (!busy && committed != effort) onSelect(committed)
                },
                enabled = !busy, valueRange = 0f..levels.lastIndex.toFloat(), steps = levels.size - 2,
                interactionSource = interactions,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "思考强度"
                    stateDescription = if (busy) "正在应用思考强度" else effortLabel(if (engaged) selected else effort)
                },
                thumb = {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(42.dp).graphicsLayer {
                            scaleX = thumbScale.value; scaleY = thumbScale.value; alpha = if (busy) 0.52f else 1f
                        }) {
                            val radius = size.minDimension / 2f - 1.dp.toPx()
                            drawCircle(orbitInk.copy(alpha = 0.05f), radius, center + Offset(0f, 1.dp.toPx()))
                            drawCircle(scheme.surface, radius, center)
                            drawCircle(orbitInk.copy(alpha = if (engaged) 0.42f else 0.22f), radius, center, style = Stroke(0.9.dp.toPx()))
                            drawCircle(orbitCyan.copy(alpha = 0.26f), radius - 3.dp.toPx(), center, style = Stroke(0.6.dp.toPx()))
                            rotate(-28f, center) {
                                val ovalSize = Size(30.dp.toPx(), 12.dp.toPx())
                                drawOval(orbitCyan.copy(alpha = 0.55f), center - Offset(ovalSize.width / 2f, ovalSize.height / 2f),
                                    ovalSize, style = Stroke(0.9.dp.toPx()))
                            }
                            val length = 6.5.dp.toPx()
                            val waist = 1.7.dp.toPx()
                            val star = Path().apply {
                                moveTo(center.x, center.y - length)
                                lineTo(center.x + waist, center.y - waist)
                                lineTo(center.x + length, center.y)
                                lineTo(center.x + waist, center.y + waist)
                                lineTo(center.x, center.y + length)
                                lineTo(center.x - waist, center.y + waist)
                                lineTo(center.x - length, center.y)
                                lineTo(center.x - waist, center.y - waist)
                                close()
                            }
                            drawPath(star, orbitCyan)
                            drawCircle(if (selected == "max") orbitGold else scheme.surface, 1.2.dp.toPx(), center)
                        }
                    }
                },
                track = {
                    EffortTrack(visualPosition, levels.lastIndex, selected, engaged, summit, !busy, true, levels)
                },
                colors = sliderColors,
            )
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
                levels.forEach { level -> Text(effortLabel(level), style = MaterialTheme.typography.labelMedium,
                    color = if (selected == level && effort != "auto") orbitCyan else scheme.onSurfaceVariant) }
            }
        } else Text("此模型不支持思考强度调节", style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
    }
}

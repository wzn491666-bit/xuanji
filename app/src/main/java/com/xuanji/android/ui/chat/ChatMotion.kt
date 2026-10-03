package com.xuanji.android.ui.chat

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun rememberChatForeground(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return foreground
}

@Composable
internal fun rememberSystemMotionScale(): Float {
    val resolver = LocalContext.current.contentResolver
    fun readScale() = runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
        .getOrDefault(1f).coerceIn(0f, 10f)
    var scale by remember(resolver) { mutableFloatStateOf(readScale()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = readScale() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
}

/** Keep the popup mounted through its exit, without animating layout or hit-target sizes. */
@Composable
internal fun AnimatedChatPopup(
    expanded: Boolean, positionProvider: PopupPositionProvider, onDismiss: () -> Unit,
    origin: TransformOrigin, content: @Composable () -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    val transition = rememberTransition(state, label = "chat popover")
    val alpha = transition.animateFloat(transitionSpec = { tween(if (targetState) 150 else 110) }, label = "opacity") { if (it) 1f else 0f }
    val scale = transition.animateFloat(transitionSpec = { spring(dampingRatio = 1f, stiffness = 650f) }, label = "scale") { if (it) 1f else 0.96f }
    val offset = transition.animateFloat(transitionSpec = { tween(if (targetState) 190 else 110, easing = FastOutSlowInEasing) }, label = "offset") { if (it) 0f else 6f }
    if (state.currentState || state.targetState) Popup(positionProvider, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = expanded)) {
        Box(Modifier.graphicsLayer {
            this.alpha = alpha.value
            scaleX = scale.value; scaleY = scale.value
            translationY = offset.value * density * if (origin.pivotFractionY == 0f) -1f else 1f
            transformOrigin = origin
        }) { content() }
    }
}

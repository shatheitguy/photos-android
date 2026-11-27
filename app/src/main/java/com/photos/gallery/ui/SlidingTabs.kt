package com.photos.gallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tab buttons with a highlight you can drag, like the iOS tab bar: press and slide across the bar,
 * the highlight follows your finger, and the tab under it is chosen when you let go.
 */
@Composable
fun SlidingTabs(
    count: Int,
    selected: Int,
    itemWidth: Dp,
    itemHeight: Dp,
    highlight: Color,
    onSelect: (Int) -> Unit,
    item: @Composable (index: Int, active: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val wPx = with(LocalDensity.current) { itemWidth.toPx() }
    val pos = remember { Animatable(selected.coerceAtLeast(0).toFloat()) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var dragging by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (dragging) 1.12f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "tabScale")
    val springy = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
    LaunchedEffect(selected) { if (!dragging && selected >= 0) pos.animateTo(selected.toFloat(), springy) }
    val shown = selected >= 0 || dragging
    val last = (count - 1).toFloat()

    Box(
        Modifier.size(itemWidth * count, itemHeight).pointerInput(count, wPx) {
            var lastNearest = -1
            fun settle() {
                val target = pos.value.roundToInt().coerceIn(0, count - 1)
                dragging = false
                scope.launch { pos.animateTo(target.toFloat(), springy) }
                if (target != currentSelected) currentOnSelect(target)
            }
            detectHorizontalDragGestures(
                onDragStart = { o ->
                    dragging = true
                    lastNearest = pos.value.roundToInt()
                    scope.launch { pos.animateTo((o.x / wPx - 0.5f).coerceIn(0f, last), springy) }
                },
                onDragEnd = { settle() },
                onDragCancel = { settle() },
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    scope.launch {
                        pos.snapTo((pos.value + dx / wPx).coerceIn(-0.15f, last + 0.15f))
                        val n = pos.value.roundToInt().coerceIn(0, count - 1)
                        if (n != lastNearest) {
                            lastNearest = n
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
            )
        },
    ) {
        Box(
            Modifier.size(itemWidth, itemHeight)
                .graphicsLayer {
                    translationX = pos.value * wPx
                    scaleX = scale
                    scaleY = scale
                    alpha = if (shown) 1f else 0f
                }
                .clip(CircleShape)
                .background(highlight),
        )
        Row {
            for (i in 0 until count) {
                Box(Modifier.size(itemWidth, itemHeight)) {
                    item(i, shown && abs(pos.value - i) < 0.5f)
                }
            }
        }
    }
}

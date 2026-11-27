package com.photos.gallery.ui

// Shared between Photos and Files (copied into each app with its package name).

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

class PopupItem(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    /** Starts a new group (drawn with a thick separator above it). */
    val group: Boolean = false,
    val checked: Boolean = false,
    val run: () -> Unit,
)

/** The popup covers the whole window; the menu is placed inside it next to the anchor. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset.Zero
}

/**
 * An iOS-style menu that springs open right beside its anchor. Put it inside the anchor's Box.
 * With [spotlight] (long-press menus) everything else dims and the pressed item stays bright, like iOS.
 */
@Composable
fun IosPopupMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<PopupItem>, spotlight: Boolean = false) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    // A very light tap as the menu pops up, like iOS.
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(expanded) { if (expanded) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK) }
    // Where the anchor is on screen.
    var anchor by remember { mutableStateOf<Rect?>(null) }
    Spacer(
        Modifier.size(0.dp).onGloballyPositioned { me ->
            me.parentLayoutCoordinates?.let { p -> anchor = Rect(p.localToScreen(Offset.Zero), p.size.toSize()) }
        }
    )
    if (!visible.currentState && !visible.targetState) return
    val c = LocalIos.current
    val d = LocalDensity.current
    val bg = if (c.dark) Color(0xF52A2A2D) else Color(0xFAFFFFFF)
    val edge = if (c.dark) Color(0x26FFFFFF) else Color(0x14000000)
    val band = if (c.dark) Color(0x14FFFFFF) else Color(0x0F000000)
    val line = if (c.dark) Color(0x1FFFFFFF) else Color(0x14000000)
    val shape = RoundedCornerShape(24.dp)
    val anyChecked = items.any { it.checked }

    Popup(popupPositionProvider = WholeWindow, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        var origin by remember { mutableStateOf<Offset?>(null) }
        var menuSize by remember { mutableStateOf(IntSize.Zero) }
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val o = origin
            val a = anchor
            // The anchor in this popup's own coordinates.
            val local = if (o != null && a != null) a.translate(-o.x, -o.y) else null
            val gap = with(d) { 8.dp.toPx() }
            val side = with(d) { 12.dp.toPx() }
            val top = with(d) { 44.dp.toPx() }
            val bottom = with(d) { 24.dp.toPx() }
            val roomBelow = if (local != null) h - local.bottom - gap - bottom else h
            val roomAbove = if (local != null) local.top - gap - top else 0f
            val maxH = with(d) { maxOf(roomBelow, roomAbove).toDp() }.coerceIn(180.dp, 520.dp)
            val below = local == null || menuSize.height <= roomBelow || roomBelow >= roomAbove
            val right = local != null && local.center.x > w / 2
            val x = when {
                local == null -> side
                right -> local.right - menuSize.width
                else -> local.left
            }.coerceIn(side, (w - menuSize.width - side).coerceAtLeast(side))
            val y = when {
                local == null -> top
                below -> local.bottom + gap
                else -> local.top - gap - menuSize.height
            }.coerceIn(top, (h - menuSize.height - bottom).coerceAtLeast(top))
            val ready = local != null && menuSize != IntSize.Zero

            if (spotlight && local != null) {
                AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
                    val pad = with(d) { 4.dp.toPx() }
                    val radius = with(d) { 14.dp.toPx() }
                    val dim = if (c.dark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.3f)
                    Canvas(Modifier.fillMaxSize()) {
                        // Dim everything except a rounded window around the pressed item.
                        val hole = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addRoundRect(
                                RoundRect(
                                    local.left - pad, local.top - pad, local.right + pad, local.bottom + pad,
                                    CornerRadius(radius, radius),
                                )
                            )
                        }
                        drawPath(hole, dim)
                    }
                }
            }

            val originX = if (right) 1f else 0f
            val originY = if (below) 0f else 1f
            Box(
                Modifier.offset { IntOffset(x.toInt(), y.toInt()) }
                    .onSizeChanged { menuSize = it }
                    .alpha(if (ready) 1f else 0f),
            ) {
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(tween(130)) + scaleIn(tween(200), initialScale = 0.82f, transformOrigin = TransformOrigin(originX, originY)),
                    exit = fadeOut(tween(110)) + scaleOut(tween(130), targetScale = 0.9f, transformOrigin = TransformOrigin(originX, originY)),
                ) {
                    Column(
                        Modifier.width(252.dp)
                            .shadow(22.dp, shape, clip = false)
                            .clip(shape).background(bg).border(0.5.dp, edge, shape)
                            .pointerInput(Unit) { detectTapGestures { } } // taps on the menu's padding don't close it
                            .padding(vertical = 4.dp)
                            .heightIn(max = maxH).verticalScroll(rememberScrollState()),
                    ) {
                        items.forEachIndexed { i, it ->
                            if (i > 0) {
                                if (it.group) Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(6.dp).background(band))
                                else Box(Modifier.padding(start = if (anyChecked) 40.dp else 16.dp).fillMaxWidth().height(0.5.dp).background(line))
                            }
                            val col = if (it.destructive) c.red else c.label
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 46.dp).tap { onDismiss(); it.run() }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (anyChecked) Box(Modifier.width(24.dp)) {
                                    if (it.checked) Text("✓", fontSize = 16.sp, color = c.label)
                                }
                                Text(it.label, fontSize = 16.sp, color = col, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (it.icon != null) {
                                    Spacer(Modifier.width(10.dp))
                                    Icon(it.icon, null, tint = col, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

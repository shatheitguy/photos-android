package com.photos.gallery.ui

// Shared between Photos and Files (copied into each app with its package name).

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Places the menu under (or above) its anchor and always keeps it fully on screen. */
private class MenuPlacement(private val gap: Int, private val side: Int, private val top: Int, private val bottom: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val right = anchorBounds.center.x > windowSize.width / 2
        val x = (if (right) anchorBounds.right - popupContentSize.width else anchorBounds.left)
            .coerceIn(side, (windowSize.width - popupContentSize.width - side).coerceAtLeast(side))
        val fitsBelow = anchorBounds.bottom + gap + popupContentSize.height <= windowSize.height - bottom
        val y = (if (fitsBelow) anchorBounds.bottom + gap else anchorBounds.top - gap - popupContentSize.height)
            .coerceIn(top, (windowSize.height - popupContentSize.height - bottom).coerceAtLeast(top))
        return IntOffset(x, y)
    }
}

/** An iOS-style context menu that springs open from its anchor. Put it inside the anchor's Box. */
@Composable
fun IosPopupMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<PopupItem>) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    if (!visible.currentState && !visible.targetState) return
    val c = LocalIos.current
    val d = LocalDensity.current
    val placement = remember(d) {
        with(d) { MenuPlacement(gap = 8.dp.roundToPx(), side = 12.dp.roundToPx(), top = 44.dp.roundToPx(), bottom = 24.dp.roundToPx()) }
    }
    val bg = if (c.dark) Color(0xFF2A2A2D) else Color(0xFFF9F9F9)
    val band = if (c.dark) Color(0xFF161618) else Color(0xFFE3E3E8)
    val line = if (c.dark) Color(0x33FFFFFF) else Color(0x1F000000)
    val anyChecked = items.any { it.checked }
    Popup(popupPositionProvider = placement, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(130)) + scaleIn(tween(200), initialScale = 0.82f, transformOrigin = TransformOrigin(0.85f, 0f)),
            exit = fadeOut(tween(110)) + scaleOut(tween(130), targetScale = 0.9f, transformOrigin = TransformOrigin(0.85f, 0f)),
        ) {
            Column(
                Modifier.padding(6.dp).width(252.dp)
                    .shadow(22.dp, RoundedCornerShape(16.dp), clip = false)
                    .clip(RoundedCornerShape(16.dp)).background(bg)
                    .heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            ) {
                items.forEachIndexed { i, it ->
                    if (i > 0) {
                        if (it.group) Box(Modifier.fillMaxWidth().height(8.dp).background(band))
                        else Box(Modifier.fillMaxWidth().height(0.5.dp).background(line))
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

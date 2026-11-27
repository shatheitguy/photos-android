package com.photos.gallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** One recognised word: its text, where it sits in the picture, and which line it belongs to. */
class LiveWord(val text: String, val box: android.graphics.Rect, val line: Int)

/** Every word in reading order, for selecting text right on the photo. */
fun com.google.mlkit.vision.text.Text.words(): List<LiveWord> {
    val out = ArrayList<LiveWord>()
    var line = 0
    for (block in textBlocks) {
        for (l in block.lines) {
            for (e in l.elements) {
                val b = e.boundingBox ?: continue
                out += LiveWord(e.text, b, line)
            }
            line++
        }
    }
    return out
}

/** Joins the chosen words: spaces within a line, new lines between lines. */
private fun textOf(words: List<LiveWord>, range: IntRange): String = buildString {
    var lastLine = -1
    for (i in range) {
        val w = words[i]
        if (lastLine >= 0) append(if (w.line != lastLine) "\n" else " ")
        append(w.text)
        lastLine = w.line
    }
}

/**
 * Live Text, the iPhone way: the photo dims a little, text lines glow, and you tap a word,
 * double-tap a line or drag across words to select. Copy · Select All · Share appear above the selection.
 * The picture must be shown fitted to this box (not zoomed); [picW] × [picH] is the size the words were read at.
 */
@Composable
fun LiveTextOverlay(picW: Int, picH: Int, words: List<LiveWord>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val accent = systemAccent()
    var sel by remember(words) { mutableStateOf<IntRange?>(null) }
    var anchor by remember { mutableIntStateOf(-1) }
    val shown by animateFloatAsState(1f, tween(220), label = "lt")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val s = min(w / picW, h / picH)
        val ox = (w - picW * s) / 2f
        val oy = (h - picH * s) / 2f
        val pad = with(density) { 3.dp.toPx() }
        val rects = remember(words, s, ox, oy) {
            words.map { Rect(ox + it.box.left * s - pad, oy + it.box.top * s - pad, ox + it.box.right * s + pad, oy + it.box.bottom * s + pad) }
        }
        val lines = remember(rects) {
            words.indices.groupBy { words[it].line }.values.map { idx ->
                idx.map { rects[it] }.reduce { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) } to idx
            }
        }
        val grow = with(density) { 10.dp.toPx() }
        fun hit(p: Offset): Int = rects.indexOfFirst { it.inflate(grow).contains(p) }
        fun nearest(p: Offset): Int = rects.indices.minByOrNull { (rects[it].center - p).getDistanceSquared() } ?: -1

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(rects) {
                    detectTapGestures(
                        onTap = { p ->
                            val i = hit(p)
                            when {
                                i >= 0 -> { sel = i..i; Haptics.tick(ctx) }
                                sel != null -> sel = null
                                else -> onClose()
                            }
                        },
                        onDoubleTap = { p ->
                            val i = hit(p)
                            if (i >= 0) {
                                val idx = lines.first { i in it.second }.second
                                sel = idx.first()..idx.last()
                                Haptics.tick(ctx)
                            }
                        },
                    )
                }
                .pointerInput(rects) {
                    detectDragGestures(
                        onDragStart = { p ->
                            anchor = hit(p).takeIf { it >= 0 } ?: nearest(p)
                            if (anchor >= 0) { sel = anchor..anchor; Haptics.tick(ctx) }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (anchor < 0) return@detectDragGestures
                            val j = nearest(change.position)
                            if (j >= 0) {
                                val r = min(anchor, j)..max(anchor, j)
                                if (r != sel) { sel = r; Haptics.tick(ctx) }
                            }
                        },
                    )
                },
        ) {
            // Dim the photo a little and let each line of text glow.
            drawRect(Color.Black.copy(alpha = 0.32f * shown))
            val r = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            for ((box, _) in lines) {
                drawRoundRect(Color.White.copy(alpha = 0.16f * shown), box.topLeft, box.size, r)
            }
            sel?.let { range ->
                for (i in range) drawRoundRect(accent.copy(alpha = 0.45f), rects[i].topLeft, rects[i].size, CornerRadius(4.dp.toPx(), 4.dp.toPx()))
            }
        }

        // Copy · Select All · Share, just above the selection.
        sel?.let { range ->
            var menu by remember { mutableStateOf(IntSize.Zero) }
            val bounds = range.map { rects[it] }.reduce { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }
            val gap = with(density) { 10.dp.toPx() }
            val edge = with(density) { 12.dp.toPx() }
            val topSafe = with(density) { 110.dp.toPx() }
            val above = bounds.top - gap - menu.height > topSafe
            val x = (bounds.center.x - menu.width / 2f).coerceIn(edge, (w - menu.width - edge).coerceAtLeast(edge))
            val y = if (above) bounds.top - gap - menu.height else bounds.bottom + gap
            Row(
                Modifier.offset { IntOffset(x.toInt(), y.toInt()) }.onSizeChanged { menu = it }
                    .shadow(14.dp, RoundedCornerShape(12.dp), clip = false).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xF2333336)).border(0.5.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Callout("Copy") {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Text", textOf(words, range)))
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    sel = null
                }
                Divider()
                Callout("Select All") { sel = words.indices }
                Divider()
                Callout("Share") {
                    runCatching {
                        ctx.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, textOf(words, range)), null)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Callout(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.tap(onClick = onClick).padding(horizontal = 15.dp, vertical = 11.dp),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.width(0.5.dp).height(22.dp).background(Color.White.copy(alpha = 0.25f)))
}

/** The Live Text button: glass normally, filled with the accent colour while Live Text is on. */
@Composable
fun LiveTextButton(active: Boolean, onClick: () -> Unit) {
    val c = LocalIos.current
    val bg = if (active) systemAccent() else if (c.dark) Color(0xE62A2A2D) else Color(0xF2FFFFFF)
    val fg = if (active || c.dark) Color.White else Color.Black
    Box(
        Modifier.size(52.dp).shadow(10.dp, CircleShape, clip = false).clip(CircleShape).background(bg)
            .border(0.5.dp, if (c.dark) Color(0x26FFFFFF) else Color(0x14000000), CircleShape).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(LiveTextIcon, null, tint = fg, modifier = Modifier.size(24.dp))
    }
}

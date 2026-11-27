package com.photos.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One iOS adjustment. Values run -1..1, or 0..1 when [positive]. */
class Adjustment(val name: String, val icon: ImageVector, val positive: Boolean = false)

object Adj {
    const val Auto = 0
    const val Exposure = 1
    const val Brilliance = 2
    const val Highlights = 3
    const val Shadows = 4
    const val Contrast = 5
    const val Brightness = 6
    const val BlackPoint = 7
    const val Saturation = 8
    const val Vibrance = 9
    const val Warmth = 10
    const val Tint = 11
    const val Sharpness = 12
    const val Definition = 13
    const val Noise = 14
    const val Vignette = 15

    val list: List<Adjustment> by lazy {
        listOf(
            Adjustment("Auto", Icons.auto, positive = true),
            Adjustment("Exposure", Icons.exposure),
            Adjustment("Brilliance", Icons.brilliance),
            Adjustment("Highlights", Icons.highlights),
            Adjustment("Shadows", Icons.shadows),
            Adjustment("Contrast", IosIcons.Contrast),
            Adjustment("Brightness", IosIcons.Sun),
            Adjustment("Black Point", Icons.blackPoint),
            Adjustment("Saturation", IosIcons.Drop),
            Adjustment("Vibrance", Icons.vibrance),
            Adjustment("Warmth", IosIcons.Thermo),
            Adjustment("Tint", Icons.tint),
            Adjustment("Sharpness", Icons.sharpness, positive = true),
            Adjustment("Definition", Icons.definition, positive = true),
            Adjustment("Noise Reduction", Icons.noise, positive = true),
            Adjustment("Vignette", Icons.vignette),
        )
    }
    val count get() = list.size

    /** Saturation, warmth and tint, which a colour matrix can do (so the crop view shows them too). */
    fun colorMatrix(a: List<Float>): android.graphics.ColorMatrix {
        val m = android.graphics.ColorMatrix()
        m.setSaturation((1f + a[Saturation] + a[Auto] * 0.08f).coerceAtLeast(0f))
        val w = a[Warmth] * 0.12f
        val t = a[Tint] * 0.10f
        m.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    1f + w, 0f, 0f, 0f, 0f,
                    0f, 1f - t, 0f, 0f, 0f,
                    0f, 0f, 1f - w, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        return m
    }

    /** Average brightness 0..1, used by Auto. */
    fun meanLuma(b: Bitmap): Float {
        val step = max(1, max(b.width, b.height) / 64)
        var sum = 0.0
        var n = 0
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val p = b.getPixel(x, y)
                sum += (0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)) / 255.0
                n++
                x += step
            }
            y += step
        }
        return if (n == 0) 0.5f else (sum / n).toFloat()
    }

    private fun toneLut(a: List<Float>, mean: Float): IntArray {
        val auto = a[Auto]
        val exposure = a[Exposure] + auto * ((0.47f - mean) * 1.4f).coerceIn(-0.5f, 0.5f)
        val brilliance = a[Brilliance] + auto * 0.3f
        val highlights = a[Highlights] - auto * 0.2f
        val shadows = a[Shadows] + auto * 0.25f
        val contrast = a[Contrast] + auto * 0.08f
        val brightness = a[Brightness]
        val black = a[BlackPoint] + auto * 0.05f
        val gain = 2f.pow(exposure * 1.4f)
        return IntArray(256) { i ->
            var x = i / 255f
            x *= gain
            x += brilliance * 0.55f * x * (1f - x) * (1f - x)
            x -= brilliance * 0.18f * x * x * (1f - x)
            val hi = ((x - 0.5f) * 2f).coerceIn(0f, 1f)
            x += highlights * 0.28f * hi * (1f - hi * 0.4f)
            val lo = ((0.5f - x) * 2f).coerceIn(0f, 1f)
            x += shadows * 0.28f * lo * (1f - lo * 0.4f)
            x = (x - 0.5f) * (1f + contrast * 0.55f) + 0.5f
            x += brightness * 0.22f * (1f - abs(2f * x - 1f) * 0.6f)
            val k = black * 0.14f
            x = (x - k) / (1f - k)
            (x.coerceIn(0f, 1f) * 255f).roundToInt()
        }
    }

    fun isNeutral(a: List<Float>) = a.all { it == 0f }

    /**
     * Applies every adjustment and [cm] (filter + colour) to [src]. Tone is a per-pixel curve;
     * sharpness, definition and noise reduction use a blurred copy; vignette darkens towards the corners.
     */
    fun process(src: Bitmap, a: List<Float>, cm: android.graphics.ColorMatrix, mean: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val sharp = a[Sharpness]
        val def = a[Definition]
        val noise = a[Noise]
        val small = if (sharp > 0f || noise > 0f) boxBlur(px, w, h, 1) else null
        val wide = if (def > 0f) boxBlur(px, w, h, max(2, max(w, h) / 160)) else null
        val lut = toneLut(a, mean)
        val m = cm.array
        val vib = a[Vibrance]
        val vig = a[Vignette]
        val cx = w / 2f
        val cy = h / 2f
        val maxD = sqrt(cx * cx + cy * cy)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = px[i]
                var r = ((p shr 16) and 255).toFloat()
                var g = ((p shr 8) and 255).toFloat()
                var b = (p and 255).toFloat()
                if (small != null) {
                    val s = small[i]
                    val sr = ((s shr 16) and 255).toFloat(); val sg = ((s shr 8) and 255).toFloat(); val sb = (s and 255).toFloat()
                    if (noise > 0f) { val k = noise * 0.75f; r += (sr - r) * k; g += (sg - g) * k; b += (sb - b) * k }
                    if (sharp > 0f) { val k = sharp * 1.3f; r += (r - sr) * k; g += (g - sg) * k; b += (b - sb) * k }
                }
                if (wide != null) {
                    val s = wide[i]
                    val k = def * 0.55f
                    r += (r - ((s shr 16) and 255)) * k; g += (g - ((s shr 8) and 255)) * k; b += (b - (s and 255)) * k
                }
                r = lut[r.toInt().coerceIn(0, 255)].toFloat()
                g = lut[g.toInt().coerceIn(0, 255)].toFloat()
                b = lut[b.toInt().coerceIn(0, 255)].toFloat()
                var nr = m[0] * r + m[1] * g + m[2] * b + m[4]
                var ng = m[5] * r + m[6] * g + m[7] * b + m[9]
                var nb = m[10] * r + m[11] * g + m[12] * b + m[14]
                if (vib != 0f) {
                    // Vibrance boosts dull colours more than strong ones, and leaves greys alone.
                    val mx = max(nr, max(ng, nb)); val mn = min(nr, min(ng, nb))
                    val sat = if (mx <= 0f) 0f else (mx - mn) / mx
                    val k = vib * (1f - sat) * 0.9f
                    val grey = (nr + ng + nb) / 3f
                    nr += (nr - grey) * k; ng += (ng - grey) * k; nb += (nb - grey) * k
                }
                if (vig != 0f) {
                    val dx = x - cx; val dy = y - cy
                    val d = sqrt(dx * dx + dy * dy) / maxD
                    val t = ((d - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val f = 1f - vig * 0.65f * t * t * (3f - 2f * t)
                    nr *= f; ng *= f; nb *= f
                }
                px[i] = (0xFF shl 24) or
                    (nr.toInt().coerceIn(0, 255) shl 16) or
                    (ng.toInt().coerceIn(0, 255) shl 8) or
                    nb.toInt().coerceIn(0, 255)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        pass(src, tmp, w, h, r, true)
        pass(tmp, out, w, h, r, false)
        return out
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            var sr = 0; var sg = 0; var sb = 0
            fun at(k: Int): Int {
                val c = k.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + c] else src[c * w + line]
            }
            for (k in -r..r) { val p = at(k); sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255 }
            val n = 2 * r + 1
            for (k in 0 until len) {
                val idx = if (horizontal) line * w + k else k * w + line
                dst[idx] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val add = at(k + r + 1); val sub = at(k - r)
                sr += ((add shr 16) and 255) - ((sub shr 16) and 255)
                sg += ((add shr 8) and 255) - ((sub shr 8) and 255)
                sb += (add and 255) - (sub and 255)
            }
        }
    }
}

private val Yellow = Color(0xFFFFD60A)

/** iOS Adjust panel: a row of round tools with value rings, and a ruler dial below. */
@Composable
fun AdjustPanel(adj: MutableList<Float>, tool: Int, onTool: (Int) -> Unit) {
    val list = Adj.list
    val listState = rememberLazyListState()
    val sw = LocalConfiguration.current.screenWidthDp.dp
    val itemW = 58.dp
    LaunchedEffect(tool) { listState.animateScrollToItem(tool) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = (sw - itemW) / 2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(list) { i, t ->
                ToolCircle(t, adj[i], i == tool, Modifier.width(itemW)) { onTool(i) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Ruler(adj[tool], list[tool].positive) { adj[tool] = it }
    }
}

@Composable
private fun ToolCircle(t: Adjustment, v: Float, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(54.dp).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(46.dp)) {
            val stroke = 2.dp.toPx()
            drawCircle(Color.White.copy(alpha = if (selected) 0.35f else 0.18f), radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            if (v != 0f) {
                drawArc(
                    if (selected) Yellow else Color.White, -90f, 360f * v, false,
                    topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        if (selected && v != 0f) {
            Text((v * 100).roundToInt().toString(), color = Yellow, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(t.icon, null, tint = if (selected) Yellow else Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/** A dial of tick marks: drag sideways to change the value, double-tap to reset. */
@Composable
private fun Ruler(value: Float, positive: Boolean, onChange: (Float) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val gap = with(density) { 7.dp.toPx() } // one step = 0.02
    val current by rememberUpdatedState(value)
    val min = if (positive) 0f else -1f
    Canvas(
        Modifier.fillMaxWidth().height(42.dp)
            .pointerInput(positive) {
                detectTapGestures(onDoubleTap = { onChange(0f) })
            }
            .pointerInput(positive) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    val old = current
                    val nv = (old - dx / gap * 0.02f).coerceIn(min, 1f)
                    val snapped = if (abs(nv) < 0.015f) 0f else nv
                    if ((old != 0f && snapped == 0f) || (snapped == 1f && old != 1f) || (snapped == min && old != min)) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else if ((old * 50).roundToInt() != (snapped * 50).roundToInt()) {
                        // A very light tick for every mark the dial passes, like iOS.
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    }
                    onChange(snapped)
                }
            },
    ) {
        val cx = size.width / 2f
        val steps = 50
        val start = (min * steps).toInt()
        for (s in start..steps) {
            val x = cx + (s - value * steps) * gap
            if (x < -gap || x > size.width + gap) continue
            val major = s % 5 == 0
            val fade = 1f - (abs(x - cx) / cx).coerceIn(0f, 1f) * 0.8f
            val hgt = if (s == 0) 18.dp.toPx() else if (major) 14.dp.toPx() else 8.dp.toPx()
            val top = size.height / 2 - hgt / 2
            drawLine(Color.White.copy(alpha = (if (major) 0.75f else 0.4f) * fade), Offset(x, top), Offset(x, top + hgt), 1.5.dp.toPx())
        }
        // The fixed centre marker.
        drawLine(Yellow, Offset(cx, 2.dp.toPx()), Offset(cx, size.height - 2.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

private object Icons {
    private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            strokes.forEach {
                addPath(PathParser().parsePathString(it).toNodes(), stroke = SolidColor(Color.White), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round)
            }
            fills.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.White)) }
        }.build()

    private const val Circle = "M12 4 a8 8 0 1 1 0 16 a8 8 0 1 1 0 -16 Z"
    val auto = icon("auto", "M5 19 L15 9", "M14 5 L15 3 M18 9 L20 8 M17 5 L19 3 M14 9 L15 10", fills = listOf("M15 7 l2 2 l-2 2 l-2 -2 Z"))
    val exposure = icon("exposure", Circle, "M8 10 h4 M10 8 v4 M12 15 h4", fills = listOf())
    val brilliance = icon("brilliance", Circle, fills = listOf("M12 4 a8 8 0 0 1 0 16 Z"))
    val highlights = icon("highlights", Circle, "M6 9 h12 M5 12 h14", fills = listOf())
    val shadows = icon("shadows", Circle, fills = listOf("M4 12 a8 8 0 0 0 16 0 Z"))
    val blackPoint = icon("black", Circle, fills = listOf("M12 7 a5 5 0 1 1 0 10 a5 5 0 1 1 0 -10 Z"))
    val vibrance = icon("vibrance", "M12 3 C9 8 6 11 6 14 a6 6 0 0 0 12 0 C18 11 15 8 12 3 Z", "M9 14 a3 3 0 0 0 3 3")
    val tint = icon("tint", "M14 4 l6 6 M17 7 l-9 9 l-3 1 l1 -3 l9 -9", fills = listOf())
    val sharpness = icon("sharp", "M12 4 L20 19 H4 Z", fills = listOf("M12 4 L12 19 H4 Z"))
    val definition = icon("definition", "M12 4 L20 19 H4 Z", "M12 9 L16.5 17 H7.5 Z")
    val noise = icon("noise", Circle, fills = listOf(
        "M9 9 a1 1 0 1 1 0 0.01 Z", "M13 8 a1 1 0 1 1 0 0.01 Z", "M15.5 12 a1 1 0 1 1 0 0.01 Z",
        "M11 12 a1 1 0 1 1 0 0.01 Z", "M8.5 14.5 a1 1 0 1 1 0 0.01 Z", "M13 15.5 a1 1 0 1 1 0 0.01 Z",
    ))
    val vignette = icon("vignette", "M4 5 h16 v14 h-16 Z", "M12 8 a4 4 0 1 1 0 8 a4 4 0 1 1 0 -8 Z")
}

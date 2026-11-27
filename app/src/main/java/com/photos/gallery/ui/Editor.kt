package com.photos.gallery.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photos.gallery.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private data class Look(val sat: Float = 0f, val con: Float = 0f, val bri: Float = 0f, val warm: Float = 0f)

private val FilterNames = listOf(
    "Original", "Vivid", "Vivid Warm", "Vivid Cool", "Dramatic", "Dramatic Warm", "Dramatic Cool", "Mono", "Silvertone", "Noir",
)
private val FilterLooks = listOf(
    Look(),
    Look(0.35f, 0.12f),
    Look(0.35f, 0.12f, 0f, 0.35f),
    Look(0.35f, 0.12f, 0f, -0.35f),
    Look(-0.2f, 0.45f, -0.12f),
    Look(-0.2f, 0.45f, -0.12f, 0.35f),
    Look(-0.2f, 0.45f, -0.12f, -0.35f),
    Look(-1f),
    Look(-1f, 0.15f, 0.15f),
    Look(-1f, 0.8f, -0.05f),
)

private object EditMath {
    fun matrix(l: Look): android.graphics.ColorMatrix {
        val m = android.graphics.ColorMatrix()
        m.setSaturation((1f + l.sat).coerceAtLeast(0f))
        val k = 1f + l.con
        val t = 128f * (1f - k) + l.bri * 80f
        m.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    k, 0f, 0f, 0f, t,
                    0f, k, 0f, 0f, t,
                    0f, 0f, k, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        val r = 1f + l.warm * 0.15f
        val b = 1f - l.warm * 0.15f
        m.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    r, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, b, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        return m
    }

    fun combined(filter: Int, adj: Look): android.graphics.ColorMatrix {
        val m = matrix(FilterLooks[filter])
        m.postConcat(matrix(adj))
        return m
    }

    /** Turns the picture (rotation in degrees) and mirrors it left to right on screen. */
    fun transform(src: Bitmap, rotation: Int, flip: Boolean): Bitmap {
        val rot = ((rotation % 360) + 360) % 360
        if (rot == 0 && !flip) return src
        val m = android.graphics.Matrix().apply {
            postRotate(rot.toFloat())
            if (flip) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun cut(b: Bitmap, c: Crop): Bitmap {
        if (c.isFull) return b
        val x = (c.l * b.width).roundToInt().coerceIn(0, b.width - 1)
        val y = (c.t * b.height).roundToInt().coerceIn(0, b.height - 1)
        val w = ((c.r - c.l) * b.width).roundToInt().coerceIn(1, b.width - x)
        val h = ((c.b - c.t) * b.height).roundToInt().coerceIn(1, b.height - y)
        return Bitmap.createBitmap(b, x, y, w, h)
    }

    fun render(src: Bitmap, cm: android.graphics.ColorMatrix, rotation: Int, flip: Boolean, crop: Crop): Bitmap {
        val cut = cut(transform(src, rotation, flip), crop)
        val out = Bitmap.createBitmap(cut.width, cut.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        android.graphics.Canvas(out).drawBitmap(cut, 0f, 0f, paint)
        return out
    }
}

private val Yellow = Color(0xFFFFD60A)

@Composable
fun EditorScreen(media: Media, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(media.id) { preview = ImageLoader.full(ctx, media.uri, 1600, software = true) }
    var tab by remember { mutableIntStateOf(0) }
    var filter by remember { mutableIntStateOf(0) }
    val adj = remember { mutableStateListOf(*Array(Adj.count) { 0f }) }
    var tool by remember { mutableIntStateOf(0) }
    var rotation by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf(Crop()) }
    var ratio by remember { mutableIntStateOf(0) }
    var showOriginal by remember { mutableStateOf(false) }
    // The photo turned and mirrored as it will be saved; the crop is drawn on top of this.
    val turned by produceState<Bitmap?>(null, preview, rotation, flip) {
        val p = preview ?: return@produceState
        value = withContext(Dispatchers.Default) { EditMath.transform(p, rotation, flip) }
    }
    // The cropped picture, shrunk for a quick live preview of the adjustments.
    val cut = remember(turned, crop) {
        turned?.let { t ->
            val c = EditMath.cut(t, crop)
            val k = 1280f / maxOf(c.width, c.height)
            if (k < 1f) Bitmap.createScaledBitmap(c, (c.width * k).toInt().coerceAtLeast(1), (c.height * k).toInt().coerceAtLeast(1), true) else c
        }
    }
    val cropped = remember(cut) { cut?.asImageBitmap() }
    val mean = remember(cut) { cut?.let { Adj.meanLuma(it) } ?: 0.5f }
    fun applyRatio(i: Int) {
        ratio = i
        val b = turned ?: return
        crop = crop.fitRatio(CropRatios[i].second, b.width.toFloat() / b.height)
    }
    fun rotateLeft() {
        rotation += if (flip) 90 else -90
        crop = crop.turnedLeft()
        val b = turned
        if (ratio > 0 && b != null) {
            // Width and height swap; keep the chosen frame shape.
            crop = crop.fitRatio(CropRatios[ratio].second, b.height.toFloat() / b.width)
        }
    }
    var saving by remember { mutableStateOf(false) }
    val adjNow = adj.toList()
    val cm = remember(filter, adjNow) {
        EditMath.matrix(FilterLooks[filter]).apply { postConcat(Adj.colorMatrix(adjNow)) }
    }
    // Live preview with every adjustment applied pixel by pixel; the last result stays up while the next is worked out.
    var processed by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(cut, adjNow, filter) {
        val c = cut ?: return@LaunchedEffect
        if (Adj.isNeutral(adjNow)) { processed = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(16)
        processed = withContext(Dispatchers.Default) { Adj.process(c, adjNow, cm, mean).asImageBitmap() }
    }
    val changed = filter != 0 || adj.any { it != 0f } || rotation % 360 != 0 || flip || !crop.isFull

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Cancel", color = Color.White, fontSize = 17.sp, modifier = Modifier.tap(onClick = onClose))
                Spacer(Modifier.weight(1f))
                Text(
                    when (tab) {
                        0 -> Adj.list[tool].name.uppercase()
                        1 -> FilterNames[filter].uppercase()
                        else -> "CROP"
                    },
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Done",
                    color = if (changed) Yellow else Color.Gray,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.tap(enabled = changed && !saving) {
                        saving = true
                        scope.launch {
                            val src = ImageLoader.full(ctx, media.uri, 3072, software = true) ?: preview
                            val out = if (src != null) withContext(Dispatchers.Default) {
                                runCatching {
                                    if (Adj.isNeutral(adjNow)) EditMath.render(src, cm, rotation, flip, crop)
                                    else {
                                        val c = EditMath.cut(EditMath.transform(src, rotation, flip), crop)
                                        Adj.process(c, adjNow, cm, mean)
                                    }
                                }.getOrNull()
                            } else null
                            val ok = out != null && store.saveEdited(media, out)
                            saving = false
                            Toast.makeText(ctx, if (ok) "Saved a copy to Photos Edits" else "Couldn't save the photo", Toast.LENGTH_SHORT).show()
                            store.requestReload()
                            if (ok) onClose()
                        }
                    },
                )
            }

            Box(
                Modifier.weight(1f).fillMaxWidth().padding(16.dp).pointerInput(tab) {
                    if (tab == 2) return@pointerInput
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
                contentAlignment = Alignment.Center,
            ) {
                val full = turned
                val filterNow = if (showOriginal) null else ColorFilter.colorMatrix(ColorMatrix(cm.array))
                when {
                    full == null -> CircularProgressIndicator(color = Color.White)
                    tab == 2 -> {
                        val ib = remember(full) { full.asImageBitmap() }
                        val r = CropRatios[ratio].second
                        val px = if (r < 0f) full.width.toFloat() / full.height else r
                        CropView(ib, crop, px, filterNow) { crop = it }
                    }
                    cropped != null -> {
                        val done = processed
                        if (done != null && !showOriginal) Image(done, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Image(cropped, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = filterNow)
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                when (tab) {
                    0 -> AdjustPanel(adj, tool) { tool = it }
                    1 -> FilterStrip(preview, filter) { filter = it }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CropRatios.indices.toList()) { i ->
                                val sel = i == ratio
                                Text(
                                    CropRatios[i].first, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (sel) Color.Black else Color.White,
                                    modifier = Modifier.clip(CircleShape).background(if (sel) Yellow else Color.White.copy(alpha = 0.14f))
                                        .tap { applyRatio(i) }.padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            EditorButton(IosIcons.Rotate, "Rotate") { rotateLeft() }
                            EditorButton(FlipIcon, "Flip") { flip = !flip; crop = crop.mirrored() }
                            EditorButton(IosIcons.Sliders, "Reset") {
                                rotation = 0; flip = false; crop = Crop(); ratio = 0
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                EditorTab(IosIcons.Sliders, "Adjust", tab == 0) { tab = 0 }
                EditorTab(IosIcons.Filters, "Filters", tab == 1) { tab = 1 }
                EditorTab(IosIcons.Crop, "Crop", tab == 2) { tab = 2 }
            }
        }
        if (saving) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).blockTouches(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Yellow)
            }
        }
    }
}

private val AdjustNames = listOf("Brightness", "Contrast", "Saturation", "Warmth")
private val AdjustIcons: List<ImageVector> by lazy { listOf(IosIcons.Sun, IosIcons.Contrast, IosIcons.Drop, IosIcons.Thermo) }

@Composable
private fun AdjustTools(adj: MutableList<Float>, tool: Int, onTool: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            AdjustNames.indices.forEach { i ->
                val sel = i == tool
                val v = adj[i]
                Box(
                    Modifier.size(54.dp).clip(CircleShape)
                        .border(2.dp, if (sel) Yellow else if (v != 0f) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                        .tap { onTool(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sel && v != 0f) {
                        Text((v * 100).roundToInt().toString(), color = Yellow, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(AdjustIcons[i], null, tint = if (sel) Yellow else Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        Slider(
            value = adj[tool],
            onValueChange = { adj[tool] = if (abs(it) < 0.03f) 0f else it },
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Yellow, inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FilterStrip(preview: Bitmap?, selected: Int, onSelect: (Int) -> Unit) {
    val small = remember(preview) {
        preview?.let {
            val s = 180f / maxOf(it.width, it.height)
            Bitmap.createScaledBitmap(it, (it.width * s).toInt().coerceAtLeast(1), (it.height * s).toInt().coerceAtLeast(1), true).asImageBitmap()
        }
    }
    val matrices = remember { FilterLooks.map { ColorMatrix(EditMath.matrix(it).array) } }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(FilterNames.indices.toList()) { i ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.tap { onSelect(i) }) {
                Text(
                    FilterNames[i].uppercase(), color = if (i == selected) Yellow else Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(4.dp))
                        .border(if (i == selected) 2.dp else 0.dp, Yellow, RoundedCornerShape(4.dp))
                        .background(Color.DarkGray)
                ) {
                    if (small != null) {
                        Image(small, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = ColorFilter.colorMatrix(matrices[i]))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorTab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.tap(onClick = onClick).padding(6.dp)) {
        Icon(icon, null, tint = if (selected) Yellow else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
        Text(label, fontSize = 10.sp, color = if (selected) Yellow else Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun EditorButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.tap(onClick = onClick)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

// ---------- Crop ----------

/** The crop frame as fractions (0..1) of the turned picture. */
data class Crop(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f) {
    val isFull get() = l <= 0.001f && t <= 0.001f && r >= 0.999f && b >= 0.999f

    /** The same frame after the picture turns a quarter to the left. */
    fun turnedLeft() = Crop(t, 1f - r, b, 1f - l)

    fun mirrored() = Crop(1f - r, t, 1f - l, b)

    /**
     * The biggest frame of [ratio] (width / height in pixels; 0 = freeform, -1 = the picture's own shape),
     * centred where the current frame is. [pic] is the picture's width / height.
     */
    fun fitRatio(ratio: Float, pic: Float): Crop {
        if (ratio == 0f) return this
        val px = if (ratio < 0) pic else ratio
        val k = px / pic
        var h = minOf(1f, 1f / k)
        var w = h * k
        if (w > 1f) { w = 1f; h = w / k }
        val cx = ((l + r) / 2f).coerceIn(w / 2f, 1f - w / 2f)
        val cy = ((t + b) / 2f).coerceIn(h / 2f, 1f - h / 2f)
        return Crop(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }
}

val CropRatios = listOf(
    "Freeform" to 0f, "Original" to -1f, "Square" to 1f, "16:9" to 16f / 9f,
    "4:3" to 4f / 3f, "3:2" to 3f / 2f, "9:16" to 9f / 16f, "3:4" to 3f / 4f,
)

private enum class Grab { Move, TL, TR, BL, BR, L, R, T, B }

/** The picture with an iOS crop frame: drag the corners or edges to resize, drag inside to move. */
@Composable
private fun CropView(
    img: androidx.compose.ui.graphics.ImageBitmap,
    crop: Crop,
    ratio: Float,
    filter: ColorFilter?,
    onCrop: (Crop) -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inset = with(density) { 14.dp.toPx() }
    val touch = with(density) { 30.dp.toPx() }
    var grab by remember { mutableStateOf<Grab?>(null) }
    val current by rememberUpdatedState(crop)
    val onChange by rememberUpdatedState(onCrop)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val a = img.width.toFloat() / img.height
        val dw = min(fullW - inset * 2, (fullH - inset * 2) * a)
        val dh = dw / a
        val ox = (fullW - dw) / 2f
        val oy = (fullH - dh) / 2f
        val k = if (ratio > 0f) ratio / a else 0f
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxSize().pointerInput(dw, dh, k) {
                detectDragGestures(
                    onDragStart = { p ->
                        val c = current
                        val l = ox + c.l * dw
                        val r = ox + c.r * dw
                        val t = oy + c.t * dh
                        val b = oy + c.b * dh
                        fun near(x: Float, y: Float) = abs(p.x - x) < touch && abs(p.y - y) < touch
                        val inY = p.y in t..b
                        val inX = p.x in l..r
                        grab = when {
                            near(l, t) -> Grab.TL
                            near(r, t) -> Grab.TR
                            near(l, b) -> Grab.BL
                            near(r, b) -> Grab.BR
                            k == 0f && inY && abs(p.x - l) < touch -> Grab.L
                            k == 0f && inY && abs(p.x - r) < touch -> Grab.R
                            k == 0f && inX && abs(p.y - t) < touch -> Grab.T
                            k == 0f && inX && abs(p.y - b) < touch -> Grab.B
                            inX && inY -> Grab.Move
                            else -> null
                        }
                    },
                    onDragEnd = { grab = null },
                    onDragCancel = { grab = null },
                    onDrag = { change, d ->
                        val g = grab ?: return@detectDragGestures
                        change.consume()
                        onChange(dragCrop(current, g, d.x / dw, d.y / dh, k))
                    },
                )
            },
        ) {
            val l = ox + crop.l * dw
            val r = ox + crop.r * dw
            val t = oy + crop.t * dh
            val b = oy + crop.b * dh
            drawImage(
                img,
                dstOffset = androidx.compose.ui.unit.IntOffset(ox.roundToInt(), oy.roundToInt()),
                dstSize = androidx.compose.ui.unit.IntSize(dw.roundToInt(), dh.roundToInt()),
                colorFilter = filter,
            )
            val dim = Color.Black.copy(alpha = 0.6f)
            drawRect(dim, Offset(ox, oy), Size(dw, t - oy))
            drawRect(dim, Offset(ox, b), Size(dw, oy + dh - b))
            drawRect(dim, Offset(ox, t), Size(l - ox, b - t))
            drawRect(dim, Offset(r, t), Size(ox + dw - r, b - t))
            val thin = 1.dp.toPx()
            drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(thin))
            if (grab != null) {
                for (i in 1..2) {
                    val x = l + (r - l) * i / 3f
                    val y = t + (b - t) * i / 3f
                    drawLine(Color.White.copy(alpha = 0.55f), Offset(x, t), Offset(x, b), thin)
                    drawLine(Color.White.copy(alpha = 0.55f), Offset(l, y), Offset(r, y), thin)
                }
            }
            val len = 22.dp.toPx()
            val w = 3.5.dp.toPx()
            val o = w / 2f
            fun corner(x: Float, y: Float, sx: Float, sy: Float) {
                val cx = x - o * sx
                val cy = y - o * sy
                drawLine(Color.White, Offset(cx, cy), Offset(x + len * sx, cy), w)
                drawLine(Color.White, Offset(cx, cy), Offset(cx, y + len * sy), w)
            }
            corner(l, t, 1f, 1f)
            corner(r, t, -1f, 1f)
            corner(l, b, 1f, -1f)
            corner(r, b, -1f, -1f)
        }
    }
}

private fun dragCrop(c: Crop, g: Grab, dx: Float, dy: Float, k: Float): Crop {
    val m = 0.08f
    if (g == Grab.Move) {
        val mx = dx.coerceIn(-c.l, 1f - c.r)
        val my = dy.coerceIn(-c.t, 1f - c.b)
        return Crop(c.l + mx, c.t + my, c.r + mx, c.b + my)
    }
    var l = c.l
    var t = c.t
    var r = c.r
    var b = c.b
    when (g) {
        Grab.TL -> { l += dx; t += dy }
        Grab.TR -> { r += dx; t += dy }
        Grab.BL -> { l += dx; b += dy }
        Grab.BR -> { r += dx; b += dy }
        Grab.L -> l += dx
        Grab.R -> r += dx
        Grab.T -> t += dy
        Grab.B -> b += dy
        Grab.Move -> Unit
    }
    l = l.coerceIn(0f, c.r - m)
    r = r.coerceIn(c.l + m, 1f)
    t = t.coerceIn(0f, c.b - m)
    b = b.coerceIn(c.t + m, 1f)
    if (k > 0f) {
        // Locked shape: the height follows the width, anchored at the opposite corner.
        val top = g == Grab.TL || g == Grab.TR
        val left = g == Grab.TL || g == Grab.BL
        var w = r - l
        var h = w / k
        val roomH = if (top) c.b else 1f - c.t
        if (h > roomH) { h = roomH; w = h * k }
        val roomW = if (left) c.r else 1f - c.l
        if (w > roomW) { w = roomW; h = w / k }
        if (left) l = c.r - w else r = c.l + w
        if (top) t = c.b - h else b = c.t + h
    }
    return Crop(l, t, r, b)
}

/** Two mirrored triangles: the Flip button. */
private val FlipIcon: ImageVector by lazy {
    ImageVector.Builder("flip", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            PathParser().parsePathString("M10 5 L3 19 L10 19 Z").toNodes(),
            stroke = SolidColor(Color.White), strokeLineWidth = 1.6f, strokeLineJoin = StrokeJoin.Round,
        )
        addPath(PathParser().parsePathString("M14 5 L21 19 L14 19 Z").toNodes(), fill = SolidColor(Color.White))
        addPath(PathParser().parsePathString("M12 3 L12 21").toNodes(), stroke = SolidColor(Color.White), strokeLineWidth = 1.2f)
    }.build()
}

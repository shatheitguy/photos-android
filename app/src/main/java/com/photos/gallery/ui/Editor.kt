package com.photos.gallery.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    fun render(src: Bitmap, cm: android.graphics.ColorMatrix, rotation: Int): Bitmap {
        val rot = ((rotation % 360) + 360) % 360
        val w = if (rot % 180 == 0) src.width else src.height
        val h = if (rot % 180 == 0) src.height else src.width
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val matrix = android.graphics.Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postRotate(rot.toFloat())
            postTranslate(w / 2f, h / 2f)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(src, matrix, paint)
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
    val adj = remember { mutableStateListOf(0f, 0f, 0f, 0f) }
    var tool by remember { mutableIntStateOf(0) }
    var rotation by remember { mutableIntStateOf(0) }
    var showOriginal by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val look = Look(sat = adj[2], con = adj[1] * 0.5f, bri = adj[0] * 0.6f, warm = adj[3])
    val cm = remember(filter, look) { EditMath.combined(filter, look) }
    val changed = filter != 0 || adj.any { it != 0f } || rotation % 360 != 0
    val rot by animateFloatAsState(rotation.toFloat(), label = "rot")

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
                        0 -> AdjustNames[tool].uppercase()
                        1 -> FilterNames[filter].uppercase()
                        else -> "ROTATE"
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
                                runCatching { EditMath.render(src, cm, rotation) }.getOrNull()
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

            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(16.dp).pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = preview
                if (bmp == null) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    val ib = remember(bmp) { bmp.asImageBitmap() }
                    val boxW = constraints.maxWidth.toFloat()
                    val boxH = constraints.maxHeight.toFloat()
                    val a = bmp.width.toFloat() / bmp.height
                    val dw = min(boxW, boxH * a)
                    val dh = dw / a
                    val quarter = (abs(rotation) / 90) % 2 == 1
                    val fitScale = if (quarter) min(boxW / dh, boxH / dw) else 1f
                    val s by animateFloatAsState(fitScale, label = "fit")
                    Image(
                        ib, null,
                        Modifier.fillMaxSize().graphicsLayer {
                            rotationZ = rot
                            scaleX = s
                            scaleY = s
                        },
                        contentScale = ContentScale.Fit,
                        colorFilter = if (showOriginal) null else ColorFilter.colorMatrix(ColorMatrix(cm.array)),
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                when (tab) {
                    0 -> AdjustTools(adj, tool) { tool = it }
                    1 -> FilterStrip(preview, filter) { filter = it }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        EditorButton(IosIcons.Rotate, "Rotate") { rotation -= 90 }
                        EditorButton(IosIcons.Sliders, "Reset") {
                            rotation = 0
                            filter = 0
                            for (i in adj.indices) adj[i] = 0f
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                EditorTab(IosIcons.Sliders, "Adjust", tab == 0) { tab = 0 }
                EditorTab(IosIcons.Filters, "Filters", tab == 1) { tab = 1 }
                EditorTab(IosIcons.Crop, "Rotate", tab == 2) { tab = 2 }
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

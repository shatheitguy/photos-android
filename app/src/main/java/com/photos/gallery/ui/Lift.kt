package com.photos.gallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}

/** On-device text recognition and subject cut-out (Google Play services, nothing leaves the phone). */
object Vision {
    private val texts by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val subjects by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    }
    private var asked = false

    /** Downloads the recognisers in the background the first time, so they're ready when needed. */
    fun prepare(ctx: Context) {
        if (asked) return
        asked = true
        runCatching {
            ModuleInstall.getClient(ctx).installModules(
                ModuleInstallRequest.newBuilder().addApi(texts).addApi(subjects).build()
            )
        }
    }

    suspend fun text(bmp: Bitmap): String? = runCatching {
        texts.process(InputImage.fromBitmap(bmp, 0)).awaitResult().text.trim().ifEmpty { null }
    }.getOrNull()

    /** The subject on a transparent background, trimmed to its edges; null when nothing stands out. */
    suspend fun subject(bmp: Bitmap): Result<Pair<Bitmap, android.graphics.Rect>> = runCatching {
        val fg = subjects.process(InputImage.fromBitmap(bmp, 0)).awaitResult().foregroundBitmap
            ?: error("No subject found")
        withContext(Dispatchers.Default) {
            val bounds = opaqueBounds(fg) ?: error("No subject found")
            fg to bounds
        }
    }

    private fun opaqueBounds(b: Bitmap): android.graphics.Rect? {
        val w = b.width
        val h = b.height
        val row = IntArray(w)
        var l = w; var t = h; var r = -1; var btm = -1
        for (y in 0 until h) {
            b.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) > 40) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    btm = y
                }
            }
        }
        if (r < l || btm < t) return null
        return android.graphics.Rect(l, t, r + 1, btm + 1)
    }
}

/** Lifted subject: dims the photo, shows the subject glowing, with Copy, Share and Save as Sticker. */
@Composable
fun LiftOverlay(source: Bitmap, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalIos.current
    var lifted by remember(source) { mutableStateOf<Pair<Bitmap, android.graphics.Rect>?>(null) }
    LaunchedEffect(source) {
        Vision.subject(source)
            .onSuccess { lifted = it }
            .onFailure {
                val msg = it.message.orEmpty()
                Toast.makeText(
                    ctx,
                    if (msg.contains("download", true) || msg.contains("module", true) || msg.contains("unavailable", true))
                        "Getting this feature ready. Try again in a moment." else "No subject found in this photo",
                    Toast.LENGTH_SHORT,
                ).show()
                onClose()
            }
    }
    BackHandler(onBack = onClose)
    val shine = rememberInfiniteTransition(label = "shine")
    val sweep by shine.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val glow by shine.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).tap(onClick = onClose)) {
        val l = lifted
        if (l == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.7f))
        } else {
            val img = remember(l) { l.first.asImageBitmap() }
            val pop = remember(l) { Animatable(0.94f) }
            LaunchedEffect(l) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
            // Glow: a white silhouette behind the subject.
            Image(
                img, null,
                Modifier.fillMaxSize().graphicsLayer { scaleX = pop.value * 1.006f; scaleY = pop.value * 1.006f; alpha = glow }
                    .blur(5.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color.White),
            )
            // The subject with a shine sweeping across it.
            Image(
                img, null,
                Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value; compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val x = size.width * sweep
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.55f), 1f to Color.Transparent,
                                start = androidx.compose.ui.geometry.Offset(x - 160f, 0f),
                                end = androidx.compose.ui.geometry.Offset(x + 160f, size.height * 0.25f),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    },
                contentScale = ContentScale.Fit,
            )
            LiftCallout(l, pop.value, c.dark) { action ->
                when (action) {
                    0 -> copySubject(ctx, l)
                    1 -> shareSubject(ctx, l)
                    else -> saveSticker(ctx, l)
                }
                onClose()
            }
        }
    }
}

/** The iOS callout above the lifted subject: Copy | Share | Add Sticker, with a little arrow. */
@Composable
private fun BoxScope.LiftCallout(l: Pair<Bitmap, android.graphics.Rect>, pop: Float, dark: Boolean, onAction: (Int) -> Unit) {
    val (bmp, r) = l
    val density = androidx.compose.ui.platform.LocalDensity.current
    var menu by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val sc = minOf(w / bmp.width, h / bmp.height) * pop
        val ox = (w - bmp.width * sc) / 2f
        val oy = (h - bmp.height * sc) / 2f
        val cx = ox + r.exactCenterX() * sc
        val top = oy + r.top * sc
        val bottom = oy + r.bottom * sc
        val gap = with(density) { 14.dp.toPx() }
        val edge = with(density) { 12.dp.toPx() }
        val safeTop = with(density) { 90.dp.toPx() }
        val above = top - gap - menu.height > safeTop
        val y = if (above) top - gap - menu.height else (bottom + gap).coerceAtMost(h - menu.height - edge * 6)
        val x = (cx - menu.width / 2f).coerceIn(edge, (w - menu.width - edge).coerceAtLeast(edge))
        val bg = if (dark) Color(0xF2333336) else Color(0xF2FFFFFF)
        val fg = if (dark) Color.White else Color.Black
        Column(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }
                .onSizeChanged { menu = it }
                .graphicsLayer {
                    alpha = shown.value.coerceIn(0f, 1f)
                    val s = 0.8f + 0.2f * shown.value
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(((cx - x) / menu.width.coerceAtLeast(1)).coerceIn(0f, 1f), if (above) 1f else 0f)
                },
            horizontalAlignment = Alignment.Start,
        ) {
            val arrowX = with(density) { (cx - x).toDp() } - 8.dp
            if (!above) Arrow(bg, up = true, Modifier.padding(start = arrowX.coerceAtLeast(14.dp)))
            Row(
                Modifier.shadow(16.dp, androidx.compose.foundation.shape.RoundedCornerShape(12.dp), clip = false)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(bg).blockTouches(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiftButton("Copy", fg) { onAction(0) }
                Box(Modifier.width(0.5.dp).height(36.dp).background(fg.copy(alpha = 0.22f)))
                LiftButton("Share", fg) { onAction(1) }
                Box(Modifier.width(0.5.dp).height(36.dp).background(fg.copy(alpha = 0.22f)))
                LiftButton("Add Sticker", fg) { onAction(2) }
            }
            if (above) Arrow(bg, up = false, Modifier.padding(start = arrowX.coerceAtLeast(14.dp)))
        }
    }
}

@Composable
private fun Arrow(color: Color, up: Boolean, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(16.dp, 8.dp)) {
        val p = Path().apply {
            if (up) { moveTo(0f, size.height); lineTo(size.width / 2f, 0f); lineTo(size.width, size.height) }
            else { moveTo(0f, 0f); lineTo(size.width / 2f, size.height); lineTo(size.width, 0f) }
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun LiftButton(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.tap(onClick = onClick).padding(horizontal = 15.dp, vertical = 11.dp),
    )
}

private fun trimmed(l: Pair<Bitmap, android.graphics.Rect>): Bitmap {
    val (b, r) = l
    return Bitmap.createBitmap(b, r.left, r.top, r.width(), r.height())
}

private fun subjectFile(ctx: Context, l: Pair<Bitmap, android.graphics.Rect>): android.net.Uri {
    val dir = File(ctx.cacheDir, "lift").apply { mkdirs() }
    val f = File(dir, "Subject.png")
    f.outputStream().use { trimmed(l).compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(ctx, ctx.packageName + ".updates", f)
}

private fun copySubject(ctx: Context, l: Pair<Bitmap, android.graphics.Rect>) {
    runCatching {
        val uri = subjectFile(ctx, l)
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newUri(ctx.contentResolver, "Subject", uri))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }
}

private fun shareSubject(ctx: Context, l: Pair<Bitmap, android.graphics.Rect>) {
    runCatching {
        val uri = subjectFile(ctx, l)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun saveSticker(ctx: Context, l: Pair<Bitmap, android.graphics.Rect>) {
    val ok = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Sticker_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Stickers")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
        ctx.contentResolver.openOutputStream(uri)!!.use { trimmed(l).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.isSuccess
    Toast.makeText(ctx, if (ok) "Saved to Stickers" else "Couldn't save the sticker", Toast.LENGTH_SHORT).show()
}

/** The text found in a photo, ready to select, copy or share. */
@Composable
fun LiveTextPanel(text: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalIos.current
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Text("Text in Photo", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .background(if (c.dark) c.card else Color.White).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            SelectionContainer { Text(text, fontSize = 16.sp, color = c.label, lineHeight = 22.sp) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelButton("Copy All", Modifier.weight(1f)) {
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Text", text))
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                onClose()
            }
            PanelButton("Share", Modifier.weight(1f)) {
                runCatching {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }
            }
        }
    }
}

@Composable
private fun PanelButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalIos.current
    Box(
        modifier.height(50.dp).clip(CircleShape).background(if (c.dark) c.card else Color.White).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label)
    }
}

/** The Live Text button icon: a viewfinder with lines of text. */
val LiveTextIcon: ImageVector by lazy {
    ImageVector.Builder("livetext", 24.dp, 24.dp, 24f, 24f).apply {
        val stroke = SolidColor(Color.White)
        addPath(
            PathParser().parsePathString(
                "M3 8 V5 a2 2 0 0 1 2 -2 H8 M16 3 H19 a2 2 0 0 1 2 2 V8 M21 16 V19 a2 2 0 0 1 -2 2 H16 M8 21 H5 a2 2 0 0 1 -2 -2 V16"
            ).toNodes(),
            stroke = stroke, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round,
        )
        addPath(
            PathParser().parsePathString("M7.5 9 H16.5 M7.5 12 H16.5 M7.5 15 H13").toNodes(),
            stroke = stroke, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round,
        )
    }.build()
}

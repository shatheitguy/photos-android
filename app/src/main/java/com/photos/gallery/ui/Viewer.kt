@file:OptIn(ExperimentalMaterial3Api::class)

package com.photos.gallery.ui

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.photos.gallery.data.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private fun clampOffset(o: Offset, scale: Float, box: IntSize, img: IntSize): Offset {
    if (box.width == 0 || img.width <= 0 || img.height <= 0) return Offset.Zero
    val fit = min(box.width / img.width.toFloat(), box.height / img.height.toFloat())
    val mx = max(0f, (img.width * fit * scale - box.width) / 2f)
    val my = max(0f, (img.height * fit * scale - box.height) / 2f)
    return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
}

@Composable
private fun SystemBarsVisible(visible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    LaunchedEffect(visible) {
        val ctl = WindowCompat.getInsetsController(window, view)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) ctl.show(WindowInsetsCompat.Type.systemBars()) else ctl.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) {
        onDispose { WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
fun ViewerScreen(ref: AlbumRef, startId: Long, onClose: () -> Unit) {
    val store = LocalStore.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val c = LocalIos.current
    val items = rememberItems(ref)
    val initial = remember { items.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = initial) { items.size }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onClose() }
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    var chrome by remember { mutableStateOf(true) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var showInfo by remember { mutableStateOf(false) }
    val trash = ref.kind == Kind.Trash
    val ctx = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(Unit) { Vision.prepare(ctx) }
    // A plain (software) copy of the photo on screen, used for Live Text and lifting the subject.
    var still by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var liveText by remember { mutableStateOf<String?>(null) }
    var showText by remember { mutableStateOf(false) }
    var liveWords by remember { mutableStateOf<List<LiveWord>>(emptyList()) }
    var liveMode by remember { mutableStateOf(false) }
    var albumMove by remember { mutableStateOf<Boolean?>(null) }
    var lifting by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var subject by remember { mutableStateOf<Pair<android.graphics.Bitmap, android.graphics.Rect>?>(null) }
    LaunchedEffect(current.id) {
        still = null
        liveText = null
        liveWords = emptyList()
        subject = null
        liveMode = false
        if (current.isVideo || current.isAnimated || trash) return@LaunchedEffect
        kotlinx.coroutines.delay(350) // wait until swiping settles
        val b = ImageLoader.full(ctx, current.uri, 2048, software = true) ?: return@LaunchedEffect
        still = b
        val read = Vision.read(b)
        liveWords = read?.words().orEmpty()
        liveText = read?.text?.trim()?.ifEmpty { null }
        subject = Vision.subject(b).getOrNull()
    }
    // A light tick each time a swipe lands on the next or previous photo.
    var lastPage by remember { mutableIntStateOf(pager.currentPage) }
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage != lastPage) {
            lastPage = pager.currentPage
        }
    }
    SystemBarsVisible(chrome)
    val bg by animateColorAsState(if (chrome) c.bg else Color.Black, label = "viewerBg")
    val fade = (abs(dragY) / 700f).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize().background(bg.copy(alpha = 1f - fade))) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1,
            key = { items.getOrNull(it)?.id ?: it },
        ) { page ->
            val m = items.getOrNull(page)
            if (m != null) {
                MediaPage(
                    m = m,
                    active = page == pager.currentPage,
                    dragY = if (page == pager.currentPage) dragY else 0f,
                    onTap = { chrome = !chrome },
                    onDrag = { dragY = it },
                    onDismiss = onClose,
                    onSwipeUp = { if (!trash) showInfo = true },
                    fitted = liveMode && page == pager.currentPage,
                    onLongPress = {
                        val b = still
                        if (page == pager.currentPage && b != null) {
                            Haptics.press(ctx)
                            lifting = b
                        }
                    },
                )
            }
        }
        val b = still
        if (liveMode && b != null && liveWords.isNotEmpty()) {
            androidx.activity.compose.BackHandler { liveMode = false }
            LiveTextOverlay(b.width, b.height, liveWords) { liveMode = false }
        }
        val showChrome = chrome && fade < 0.05f
        AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            ViewerTopBar(current, trash, onBack = onClose, onAlbum = { albumMove = it })
        }
        AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 6.dp)) {
                if (liveText != null) {
                    Row(Modifier.fillMaxWidth().padding(end = 18.dp, bottom = 10.dp)) {
                        Spacer(Modifier.weight(1f))
                        LiveTextButton(liveMode) { liveMode = !liveMode }
                    }
                }
                if (!trash && items.size > 1) Filmstrip(items, pager)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (trash) {
                        ViewerGlassText("Delete", c.red) { actions.deleteForever(listOf(current)) }
                        Spacer(Modifier.weight(1f))
                        ViewerGlassText("Recover", c.label) { actions.restore(listOf(current)) }
                    } else {
                        ViewerGlassCircle(IosIcons.Share) { actions.share(listOf(current)) }
                        Spacer(Modifier.weight(1f))
                        val fav = current.id in store.favorites
                        ViewerGlassCapsule {
                            ViewerCapsuleIcon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder) {
                                store.toggleFavorite(listOf(current.id))
                            }
                            ViewerCapsuleIcon(IosIcons.Info) { showInfo = true }
                            if (!current.isVideo && !current.isAnimated) {
                                ViewerCapsuleIcon(IosIcons.Sliders) { nav.push(Route.Editor(current)) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        ViewerGlassCircle(IosIcons.Trash) { actions.trash(listOf(current)) }
                    }
                }
            }
        }
    }
    lifting?.let { b -> LiftOverlay(b, subject) { lifting = null } }
    albumMove?.let { move ->
        ModalBottomSheet(onDismissRequest = { albumMove = null }, containerColor = c.grouped) {
            AlbumPicker(move, listOf(current)) { albumMove = null }
        }
    }
    if (showText) {
        ModalBottomSheet(onDismissRequest = { showText = false }, containerColor = c.grouped) {
            LiveTextPanel(liveText.orEmpty()) { showText = false }
        }
    }
    if (showInfo) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }, containerColor = c.grouped) {
            InfoPanel(current, store.favorites.contains(current.id))
        }
    }
}

@Composable
private fun glassColors(): Triple<Color, Color, Color> {
    val c = LocalIos.current
    return if (c.dark) Triple(Color(0xE62A2A2D), Color(0x26FFFFFF), Color.White)
    else Triple(Color(0xF2FFFFFF), Color(0x14000000), Color.Black)
}

@Composable
private fun ViewerGlassCircle(icon: ImageVector, onClick: () -> Unit) {
    val (bg, border, fg) = glassColors()
    Box(
        Modifier.size(52.dp).shadow(10.dp, CircleShape, clip = false).clip(CircleShape).background(bg)
            .border(0.5.dp, border, CircleShape).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ViewerGlassCapsule(content: @Composable RowScope.() -> Unit) {
    val (bg, border, _) = glassColors()
    Row(
        Modifier.height(52.dp).shadow(10.dp, CircleShape, clip = false).clip(CircleShape).background(bg)
            .border(0.5.dp, border, CircleShape).blockTouches().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ViewerCapsuleIcon(icon: ImageVector, onClick: () -> Unit) {
    val (_, _, fg) = glassColors()
    Box(Modifier.size(width = 50.dp, height = 52.dp).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ViewerGlassText(text: String, color: Color, onClick: () -> Unit) {
    val (bg, border, _) = glassColors()
    Box(
        Modifier.height(52.dp).shadow(10.dp, CircleShape, clip = false).clip(CircleShape).background(bg)
            .border(0.5.dp, border, CircleShape).tap(onClick = onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ViewerTopBar(m: Media, trash: Boolean, onBack: () -> Unit, onAlbum: (Boolean) -> Unit) {
    val c = LocalIos.current
    val (bg, border, fg) = glassColors()
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp).height(52.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { ViewerGlassCircle(IosIcons.ChevronLeft, onBack) }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            if (trash) {
                Text(Fmt.daysLeft(m), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label)
            } else {
                Text(Fmt.dayTitle(m.date), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label)
                Text(Fmt.time(m.dateTaken), fontSize = 13.sp, color = c.secondary)
            }
        }
        if (!trash && m.id >= 0) {
            Box(Modifier.align(Alignment.CenterEnd)) { ViewerMoreButton(m, bg, border, fg, onAlbum) }
        }
    }
}

/** iOS-style scrubber: drag the strip and the photo above follows the thumbnail in the centre. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Filmstrip(items: List<Media>, pager: PagerState) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(pager.currentPage)
    val w = LocalConfiguration.current.screenWidthDp.dp
    val itemW = 30.dp
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var scrubbing by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    var lastTick by remember { mutableIntStateOf(-1) }
    // Scrubbing starts when a finger drags the strip and always ends once the strip stops,
    // decided in one place so a quick flick can't leave it stuck (which pulled the photo back after every swipe).
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val idx = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: -1
            Triple(idx, listState.isScrollInProgress, dragged)
        }.collect { (idx, scrolling, isDragged) ->
            if (isDragged) scrubbing = true
            if (scrubbing && idx >= 0 && idx != lastTick) {
                // A very light tick for each photo passed while scrubbing, like iOS.
                if (lastTick >= 0) Unit
                lastTick = idx
            }
            if (!scrubbing) lastTick = idx
            if (scrubbing && idx >= 0 && idx != pager.currentPage && !pager.isScrollInProgress) pager.scrollToPage(idx)
            if (!scrolling && !isDragged) scrubbing = false
        }
    }
    LaunchedEffect(pager.currentPage) {
        if (!scrubbing) listState.animateScrollToItem(pager.currentPage)
    }
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        modifier = Modifier.fillMaxWidth().height(44.dp),
        contentPadding = PaddingValues(horizontal = (w - itemW) / 2),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(items, key = { _, m -> m.id }) { i, m ->
            val sel = i == pager.currentPage
            Box(
                Modifier.width(itemW).height(if (sel) 44.dp else 38.dp)
                    .zIndex(if (sel) 1f else 0f)
                    .graphicsLayer { if (sel) scaleX = 1.3f }
                    .clip(RoundedCornerShape(3.dp))
                    .tap { scope.launch { pager.scrollToPage(i) } }
            ) {
                Thumb(m, Modifier.fillMaxSize(), 192)
            }
        }
    }
}

@Composable
private fun InfoPanel(m: Media, favorite: Boolean) {
    val c = LocalIos.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.fullDate(m.dateTaken), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label, modifier = Modifier.weight(1f))
            if (favorite) Icon(Icons.Filled.Favorite, null, tint = c.label, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.mime.substringAfter('/').uppercase().ifBlank { "FILE" },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.label,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(c.fill).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(m.name, fontSize = 15.sp, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (m.bucket.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(m.bucket, fontSize = 15.sp, color = c.secondary)
            }
            Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(0.5.dp).background(c.separator))
            val parts = buildList {
                if (m.width > 0 && m.height > 0) {
                    add(String.format(Locale.US, "%.1f MP", m.width.toLong() * m.height / 1_000_000.0))
                    add("${m.width} × ${m.height}")
                }
                if (m.size > 0) add(Fmt.size(m.size))
                if (m.isVideo) add(Fmt.duration(m.duration))
            }
            Text(parts.joinToString("  •  "), fontSize = 15.sp, color = c.label)
            if (m.relPath.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(m.relPath, fontSize = 13.sp, color = c.secondary)
            }
        }
    }
}

@Composable
fun MediaPage(
    m: Media,
    active: Boolean,
    dragY: Float,
    onTap: () -> Unit,
    onDrag: (Float) -> Unit,
    onDismiss: () -> Unit,
    onSwipeUp: () -> Unit,
    onLongPress: () -> Unit = {},
    fitted: Boolean = false,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val thumb = rememberThumbnail(m.uri, if (m.isVideo) 1080 else 512)
    var full by remember(m.id) { mutableStateOf<ImageBitmap?>(null) }
    var anim by remember(m.id) { mutableStateOf<Drawable?>(null) }
    var playing by remember(m.id) { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(m.id) {
        if (!m.isVideo) {
            full = ImageLoader.full(ctx, m.uri, 2560)?.asImageBitmap()
            if (m.isAnimated) anim = ImageLoader.animated(ctx, m.uri)
        }
    }
    LaunchedEffect(fitted) {
        if (fitted) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(active) {
        if (!active) {
            playing = false
            scale = 1f
            offset = Offset.Zero
        }
    }
    val img = full ?: thumb
    val dims = rememberUpdatedState(if (img != null) IntSize(img.width, img.height) else IntSize(m.width, m.height))
    val tapCb = rememberUpdatedState(onTap)
    val dragCb = rememberUpdatedState(onDrag)
    val dismissCb = rememberUpdatedState(onDismiss)
    val upCb = rememberUpdatedState(onSwipeUp)
    val longCb = rememberUpdatedState(onLongPress)

    Box(
        Modifier.fillMaxSize()
            .pointerInput(m.id) {
                detectTapGestures(
                    onTap = { tapCb.value() },
                    onLongPress = { if (!m.isVideo && scale < 1.05f && dragY == 0f) longCb.value() },
                    onDoubleTap = { p ->
                        if (!m.isVideo) {
                            val target = if (scale > 1.05f) 1f else 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val to = if (target == 1f) Offset.Zero else clampOffset((center - p) * (target - 1f), target, size, dims.value)
                            val s0 = scale
                            val o0 = offset
                            scope.launch {
                                animate(0f, 1f, animationSpec = tween(260)) { v, _ ->
                                    scale = s0 + (target - s0) * v
                                    offset = o0 + (to - o0) * v
                                }
                            }
                        }
                    },
                )
            }
            .pointerInput(m.id) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var mode = 0 // 0 undecided, 1 zoom/pan, 2 vertical drag, 3 leave to pager
                    var total = Offset.Zero
                    var dy = 0f
                    do {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.count { it.pressed }
                        if (mode != 2 && (pressed >= 2 || (mode == 0 && scale > 1.01f))) mode = 1
                        when (mode) {
                            1 -> {
                                val z = ev.calculateZoom()
                                val pan = ev.calculatePan()
                                val ns = (scale * z).coerceIn(1f, 5f)
                                offset = clampOffset(offset + pan, ns, size, dims.value)
                                scale = ns
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            0 -> {
                                total += ev.calculatePan()
                                if (abs(total.y) > slop && abs(total.y) > abs(total.x)) {
                                    mode = 2
                                    dy = total.y
                                } else if (abs(total.x) > slop) {
                                    mode = 3
                                }
                            }
                            2 -> {
                                dy += ev.calculatePan().y
                                dragCb.value(dy)
                                ev.changes.forEach { it.consume() }
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (mode == 2) {
                        if (dy > 180f) {
                            dismissCb.value()
                        } else {
                            if (dy < -120f) upCb.value()
                            val start = dy
                            scope.launch { animate(start, 0f, animationSpec = tween(220)) { v, _ -> dragCb.value(v) } }
                        }
                    }
                    if (scale < 1.02f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val layer = Modifier.fillMaxSize().graphicsLayer {
            val shrink = 1f - (abs(dragY) / 2500f).coerceIn(0f, 0.25f)
            scaleX = scale * shrink
            scaleY = scale * shrink
            translationX = offset.x
            translationY = offset.y + dragY
        }
        val a = anim
        when {
            a != null -> AndroidView(
                factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { v ->
                    if (v.drawable !== a) {
                        v.setImageDrawable(a)
                        (a as? AnimatedImageDrawable)?.start()
                    }
                },
                modifier = layer,
            )
            playing -> VideoPlayer(m.uri, layer)
            img != null -> Image(img, null, layer, contentScale = ContentScale.Fit)
        }
        if (m.isVideo && !playing) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).tap { playing = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(IosIcons.Play, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun VideoPlayer(uri: Uri, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                val vv = VideoView(ctx)
                addView(
                    vv,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
                )
                val mc = MediaController(ctx)
                mc.setAnchorView(vv)
                vv.setMediaController(mc)
                vv.setVideoURI(uri)
                vv.setOnPreparedListener {
                    vv.start()
                    mc.show(2500)
                }
                tag = vv
            }
        },
        onRelease = { (it.tag as? VideoView)?.stopPlayback() },
        modifier = modifier,
    )
}

@Composable
fun ExternalViewer(uri: Uri, mime: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val m = remember(uri) {
        Media(id = -1, uri = uri, isVideo = mime.startsWith("video"), dateTaken = System.currentTimeMillis(), mime = mime)
    }
    var chrome by remember { mutableStateOf(true) }
    var dragY by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MediaPage(m, true, dragY, onTap = { chrome = !chrome }, onDrag = { dragY = it }, onDismiss = onClose, onSwipeUp = {})
        if (chrome) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BarIcon(IosIcons.ChevronLeft, tint = Color.White) { onClose() }
                Spacer(Modifier.weight(1f))
                BarIcon(IosIcons.Share, tint = Color.White) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = mime.ifBlank { "image/*" }
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        ctx.startActivity(Intent.createChooser(send, null))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}

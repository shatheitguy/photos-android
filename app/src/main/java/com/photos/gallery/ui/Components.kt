@file:OptIn(ExperimentalFoundationApi::class)

package com.photos.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photos.gallery.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Space reserved at the bottom for the floating tab bar. */
val TabBarHeight = 84.dp

fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

/** Makes a layer hit-testable so touches don't fall through to screens underneath. */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent() }
}

fun Modifier.pinchToColumns(columns: Int, levels: List<Int>, onChange: (Int) -> Unit): Modifier =
    pointerInput(columns) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoom = 1f
            var done = false
            do {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                if (ev.changes.count { it.pressed } >= 2) {
                    zoom *= ev.calculateZoom()
                    ev.changes.forEach { it.consume() }
                    if (!done) {
                        val i = levels.indexOf(columns).coerceAtLeast(0)
                        if (zoom > 1.25f && i > 0) {
                            onChange(levels[i - 1]); done = true
                        } else if (zoom < 0.8f && i < levels.lastIndex) {
                            onChange(levels[i + 1]); done = true
                        }
                    }
                }
            } while (ev.changes.any { it.pressed })
        }
    }

class SelectionState {
    var active by mutableStateOf(false)
    var ids by mutableStateOf(emptySet<Long>())
    fun toggle(id: Long) {
        ids = if (id in ids) ids - id else ids + id
    }
    fun clear() {
        active = false
        ids = emptySet()
    }
}

@Composable
fun rememberItems(ref: AlbumRef): List<Media> {
    val store = LocalStore.current
    val all = store.all
    val hidden = store.hidden
    val fav = store.favorites
    val trash = store.trash
    return remember(ref, all, hidden, fav, trash) { store.itemsFor(ref) }
}

@Composable
fun Thumb(m: Media, modifier: Modifier = Modifier, size: Int = 400, contentScale: ContentScale = ContentScale.Crop) {
    val bmp = rememberThumbnail(m.uri, size)
    val fit = contentScale == ContentScale.Fit
    Box(modifier.background(if (bmp == null) LocalIos.current.placeholder else if (fit) LocalIos.current.bg else Color.Transparent)) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = contentScale)
    }
}

@Composable
fun SelectedBadge(modifier: Modifier = Modifier) {
    val c = LocalIos.current
    Box(
        modifier.size(22.dp).clip(CircleShape).background(Color.White).padding(1.5.dp).clip(CircleShape).background(c.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun GridCell(
    m: Media,
    size: Int,
    favorite: Boolean,
    selecting: Boolean,
    selected: Boolean,
    trashDays: Int?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    aspect: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.aspectRatio(1f).combinedClickable(
            interactionSource = src, indication = null, onLongClick = onLongClick, onClick = onClick,
        )
    ) {
        if (aspect) {
            // Aspect Ratio Grid: shrink to the photo's own shape so labels and the tick sit on the photo, like iOS.
            val bmp = rememberThumbnail(m.uri, size)
            val ratio = when {
                bmp != null && bmp.height > 0 -> bmp.width.toFloat() / bmp.height
                m.width > 0 && m.height > 0 -> m.width.toFloat() / m.height
                else -> 1f
            }.coerceIn(0.3f, 3.5f)
            Box(Modifier.align(Alignment.Center).padding(1.dp).aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)) {
                Thumb(m, Modifier.fillMaxSize(), size)
                CellOverlays(m, favorite, selecting, selected, trashDays)
            }
        } else {
            Thumb(m, Modifier.fillMaxSize(), size)
            CellOverlays(m, favorite, selecting, selected, trashDays)
        }
    }
}

@Composable
private fun BoxScope.CellOverlays(m: Media, favorite: Boolean, selecting: Boolean, selected: Boolean, trashDays: Int?) {
    run {
        if (m.isVideo || favorite || trashDays != null) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))
                )
            )
        }
        if (m.isVideo) {
            Text(
                Fmt.duration(m.duration), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 5.dp, vertical = 3.dp),
            )
        }
        if (favorite) {
            Icon(
                Icons.Filled.Favorite, null, tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(13.dp),
            )
        }
        if (trashDays != null) {
            Text(
                if (trashDays == 1) "1 day" else "$trashDays days", color = Color.White, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 5.dp, vertical = 3.dp),
            )
        }
        if (selecting && selected) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.22f)))
            SelectedBadge(Modifier.align(Alignment.BottomEnd).padding(5.dp))
        }
    }
}

val ColumnLevels = listOf(1, 3, 5, 8)

@Composable
fun MediaGrid(
    items: List<Media>,
    state: LazyGridState,
    columns: Int,
    selection: SelectionState,
    contentPadding: PaddingValues,
    onOpen: (Media) -> Unit,
    modifier: Modifier = Modifier,
    onColumns: ((Int) -> Unit)? = null,
    trash: Boolean = false,
    header: (LazyGridScope.() -> Unit)? = null,
    footer: String? = null,
) {
    val store = LocalStore.current
    val c = LocalIos.current
    val favs = store.favorites
    val haptics = LocalHapticFeedback.current
    val screenW = LocalConfiguration.current.screenWidthDp
    // The column setting is for a phone-width screen; wider screens get proportionally more columns.
    val shown = if (screenW > 480) (columns * screenW / 400f).toInt().coerceIn(columns, 14) else columns
    val px = with(LocalDensity.current) { (screenW.dp / shown).roundToPx() }
    val thumbSize = when {
        px > 700 -> 1080
        px > 300 -> 400
        px > 200 -> 256
        else -> 192
    }
    val idIndex = remember(items) { HashMap<Long, Int>(items.size).also { map -> items.forEachIndexed { i, m -> map[m.id] = i } } }
    val now = remember { System.currentTimeMillis() }
    val aspectGrid = AppPrefs.get(androidx.compose.ui.platform.LocalContext.current).flag(PrefKeys.AspectGrid, false)
    val gridCtx = androidx.compose.ui.platform.LocalContext.current
    // Long-press (without dragging) opens the iPhone photo menu.
    var menuFor by remember { mutableStateOf<Media?>(null) }
    var menuAt by remember { mutableLongStateOf(0L) }
    var addTo by remember { mutableStateOf<Media?>(null) }

    fun indexAt(x: Float, y: Float): Int {
        val info = state.layoutInfo
        val yy = y + info.viewportStartOffset
        val hit = info.visibleItemsInfo.firstOrNull {
            x >= it.offset.x && x < it.offset.x + it.size.width && yy >= it.offset.y && yy < it.offset.y + it.size.height
        } ?: return -1
        return (hit.key as? Long)?.let { idIndex[it] } ?: -1
    }

    val scope = rememberCoroutineScope()
    // Drag-to-select: long-press a photo (or swipe sideways while selecting) and keep dragging.
    // Moving near the top/bottom edge auto-scrolls and keeps extending the selection.
    val dragSelect = Modifier.pointerInput(items) {
        val slop = viewConfiguration.touchSlop
        val edge = 70.dp.toPx()
        val maxSpeed = 28.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var pos = down.position
            var swiped = false
            val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                    if (!ch.pressed || ch.isConsumed || ev.changes.count { it.pressed } > 1) return@withTimeoutOrNull false
                    val d = ch.position - down.position
                    if (selection.active && abs(d.x) > slop && abs(d.x) > abs(d.y)) {
                        ch.consume()
                        pos = ch.position
                        swiped = true
                        return@withTimeoutOrNull true
                    }
                    if (d.getDistance() > slop) return@withTimeoutOrNull false
                }
                @Suppress("UNREACHABLE_CODE")
                false
            } ?: true // timed out without moving = long press
            if (!decided) return@awaitEachGesture

            val start = indexAt(down.position.x, down.position.y).takeIf { it >= 0 }
                ?: indexAt(pos.x, pos.y).takeIf { it >= 0 }
                ?: return@awaitEachGesture
            if (!swiped && !selection.active && !trash) {
                // Held still: show the photo menu. If the finger then moves, switch to drag-select instead.
                menuFor = items[start]
                menuAt = android.os.SystemClock.uptimeMillis()
                Haptics.press(gridCtx)
                var moved = false
                while (true) {
                    // Initial pass: the photo underneath must not see the finger lift as a tap.
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) { ch.consume(); break }
                    if ((ch.position - down.position).getDistance() > slop * 2) {
                        moved = true
                        pos = ch.position
                        break
                    }
                }
                if (!moved) return@awaitEachGesture
                menuFor = null
            }
            if (!selection.active) selection.active = true
            Haptics.tick(gridCtx)
            val base = selection.ids
            val adding = items[start].id !in base
            var lastEnd = -1
            fun apply(end: Int) {
                if (end < 0 || end == lastEnd) return
                lastEnd = end
                val range = (minOf(start, end)..maxOf(start, end)).map { items[it].id }
                selection.ids = if (adding) base + range else base - range.toSet()
            }
            apply(start)
            var last = pos
            val auto = scope.launch {
                while (isActive) {
                    val info = state.layoutInfo
                    val topZone = info.beforeContentPadding + edge
                    val bottomZone = size.height - info.afterContentPadding - edge
                    val speed = when {
                        last.y < topZone -> -maxSpeed * ((topZone - last.y) / edge).coerceIn(0.2f, 1.5f)
                        last.y > bottomZone -> maxSpeed * ((last.y - bottomZone) / edge).coerceIn(0.2f, 1.5f)
                        else -> 0f
                    }
                    if (speed != 0f) {
                        state.scrollBy(speed)
                        apply(indexAt(last.x, last.y.coerceIn(topZone - edge + 1f, bottomZone + edge - 1f)))
                    }
                    delay(16)
                }
            }
            try {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    ev.changes.forEach { it.consume() }
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    last = ch.position
                    apply(indexAt(last.x, last.y))
                }
            } finally {
                auto.cancel()
            }
        }
    }
    val pinch = if (onColumns != null) Modifier.pinchToColumns(columns, ColumnLevels, onColumns) else Modifier
    menuFor?.let { m -> PhotoContextMenu(m, onDismiss = { menuFor = null }, onAddTo = { addTo = m }) }
    addTo?.let { m -> AddToAlbumSheet(m) { addTo = null } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(shown),
        modifier = modifier.fillMaxSize().then(pinch).then(dragSelect),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        header?.invoke(this)
        items(items, key = { it.id }, contentType = { 1 }) { m ->
            GridCell(
                m = m,
                size = thumbSize,
                favorite = m.id in favs,
                selecting = selection.active,
                selected = m.id in selection.ids,
                trashDays = if (trash) ((m.dateExpires * 1000 - now) / 86_400_000L).coerceAtLeast(0).toInt() else null,
                onClick = {
                    when {
                        selection.active -> selection.toggle(m.id)
                        // Ignore the lift right after a long-press opened the menu.
                        menuFor != null || android.os.SystemClock.uptimeMillis() - menuAt < 700 -> Unit
                        else -> onOpen(m)
                    }
                },
                onLongClick = {},
                aspect = aspectGrid,
            )
        }
        if (footer != null) {
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    footer, color = c.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                )
            }
        }
    }
}

enum class PhotosTab(val label: String) { Library("Library"), Albums("Albums"), Search("Search") }

@Composable
fun FloatingTabBar(current: PhotosTab, onSelect: (PhotosTab) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalIos.current
    val tickCtx = androidx.compose.ui.platform.LocalContext.current
    val container = if (c.dark) Color(0xF22A2A2D) else Color(0xF8FFFFFF)
    val selectedBg = systemAccent()
    val border = if (c.dark) Color(0x26FFFFFF) else Color(0x14000000)
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.shadow(14.dp, CircleShape, clip = false).clip(CircleShape).background(container)
                .border(0.5.dp, border, CircleShape).blockTouches().padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tabs = listOf(PhotosTab.Library, PhotosTab.Albums)
            SlidingTabs(
                count = tabs.size,
                selected = tabs.indexOf(current),
                itemWidth = 92.dp,
                itemHeight = 56.dp,
                highlight = selectedBg,
                onSelect = { Haptics.tick(tickCtx); onSelect(tabs[it]) },
            ) { i, active ->
                val t = tabs[i]
                val col = if (active) Color.White else c.label
                Column(
                    Modifier.fillMaxSize().clip(CircleShape).tap { Haptics.tick(tickCtx); onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (t == PhotosTab.Library) IosIcons.Library else IosIcons.Albums, null,
                        tint = col, modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, fontSize = 12.sp, lineHeight = 14.sp, color = col, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(64.dp).shadow(14.dp, CircleShape, clip = false).clip(CircleShape).background(container)
                .border(0.5.dp, border, CircleShape).tap { Haptics.tick(tickCtx); onSelect(PhotosTab.Search) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                IosIcons.Search, null, tint = c.label,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun GlassCircle(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color(0xCC2C2C2E))
            .border(0.5.dp, Color(0x33FFFFFF), CircleShape).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun GlassPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(46.dp).clip(CircleShape).background(Color(0xCC2C2C2E))
            .border(0.5.dp, Color(0x33FFFFFF), CircleShape).tap(onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun <T> SegmentedControl(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(Color(0xE62C2C2E)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { o ->
            val sel = o == selected
            Text(
                label(o), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(if (sel) Color(0xFF636366) else Color.Transparent)
                    .tap { onSelect(o) }.padding(horizontal = 13.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
fun Pill(text: String, onDark: Boolean = false, onClick: () -> Unit) {
    val c = LocalIos.current
    Text(
        text,
        color = if (onDark) Color.White else c.label,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape)
            .background(if (onDark) Color.Black.copy(alpha = 0.35f) else c.fill)
            .tap(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
fun LargeTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalIos.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text, style = IosType.largeTitle, color = c.label, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, divider: Boolean = true, onAction: () -> Unit = {}) {
    val c = LocalIos.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        if (divider) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.5.dp).background(c.separator))
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = IosType.title2, color = c.label, modifier = Modifier.weight(1f))
            if (action != null) Text(action, color = c.label, fontSize = 17.sp, modifier = Modifier.tap(onClick = onAction))
        }
    }
}

@Composable
fun ListRow(icon: ImageVector, title: String, count: Int?, locked: Boolean = false, onClick: () -> Unit) {
    val c = LocalIos.current
    Column(Modifier.fillMaxWidth().tap(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = c.label, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, fontSize = 17.sp, color = c.label, modifier = Modifier.weight(1f))
            if (locked) Icon(IosIcons.Lock, null, tint = c.secondary, modifier = Modifier.size(16.dp))
            else if (count != null) Text(Fmt.num(count), fontSize = 17.sp, color = c.secondary)
            Spacer(Modifier.width(6.dp))
            Icon(IosIcons.ChevronRight, null, tint = c.tertiary, modifier = Modifier.size(14.dp))
        }
        Box(Modifier.padding(start = 54.dp).fillMaxWidth().height(0.5.dp).background(c.separator))
    }
}

@Composable
fun AlbumTile(ref: AlbumRef, items: List<Media>, width: Dp, onClick: () -> Unit) {
    val c = LocalIos.current
    Column(Modifier.width(width).tap(onClick = onClick)) {
        Box(
            Modifier.size(width).clip(RoundedCornerShape(8.dp)).background(c.placeholder),
            contentAlignment = Alignment.Center,
        ) {
            val cover = items.lastOrNull()
            if (cover != null) Thumb(cover, Modifier.fillMaxSize(), 400)
            else Icon(
                if (ref.kind == Kind.Favorites) Icons.Filled.FavoriteBorder else IosIcons.Library, null,
                tint = c.tertiary, modifier = Modifier.size(width * 0.3f),
            )
        }
        Text(
            ref.title, fontSize = 15.sp, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(Fmt.num(items.size), fontSize = 15.sp, color = c.secondary)
    }
}

@Composable
fun NavTopBar(title: String, backLabel: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    val c = LocalIos.current
    Column(Modifier.fillMaxWidth().background(c.bar).blockTouches()) {
        Box(Modifier.statusBarsPadding().fillMaxWidth().height(48.dp)) {
            if (onBack != null) Row(
                Modifier.align(Alignment.CenterStart).padding(start = 4.dp).tap(onClick = onBack).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(IosIcons.ChevronLeft, null, tint = c.label, modifier = Modifier.size(24.dp))
                Text(backLabel, fontSize = 17.sp, color = c.label)
            }
            Text(
                title, style = IosType.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).widthIn(max = if (isTablet()) 360.dp else 190.dp),
            )
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), content = actions)
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
    }
}

@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c = LocalIos.current
    Column(modifier.fillMaxWidth().background(c.bar).blockTouches()) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
fun BarIcon(icon: ImageVector, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
    val c = LocalIos.current
    val col = tint ?: c.label
    Box(Modifier.size(44.dp).tap(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (enabled) col else col.copy(alpha = 0.35f), modifier = Modifier.size(24.dp))
    }
}

@Composable
fun TextAction(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text, color = if (enabled) color else color.copy(alpha = 0.35f), fontSize = 17.sp,
        modifier = Modifier.tap(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
fun selectionGlass(): Triple<Color, Color, Color> {
    val c = LocalIos.current
    return if (c.dark) Triple(Color(0xE62A2A2D), Color(0x26FFFFFF), Color.White)
    else Triple(Color(0xF2FFFFFF), Color(0x14000000), Color.Black)
}

@Composable
private fun SelGlassCircle(icon: ImageVector, enabled: Boolean, tint: Color? = null, onClick: () -> Unit) {
    val (bg, border, fg) = selectionGlass()
    val col = tint ?: fg
    Box(
        Modifier.size(50.dp).shadow(10.dp, CircleShape, clip = false).clip(CircleShape).background(bg)
            .border(0.5.dp, border, CircleShape).tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) col else col.copy(alpha = 0.3f), modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun SelGlassText(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    val (bg, border, _) = selectionGlass()
    Box(
        Modifier.height(50.dp).shadow(10.dp, CircleShape, clip = false).clip(CircleShape).background(bg)
            .border(0.5.dp, border, CircleShape).tap(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) color else color.copy(alpha = 0.3f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** iOS 26 style selection controls: floating glass buttons over a soft fade. */
@Composable
fun StandardSelectionBar(items: List<Media>, sel: SelectionState, kind: Kind, modifier: Modifier = Modifier) {
    val store = LocalStore.current
    val actions = LocalActions.current
    val c = LocalIos.current
    val (bg, border, fg) = selectionGlass()
    val chosen = remember(items, sel.ids) { items.filter { it.id in sel.ids } }
    val ids = chosen.map { it.id }
    val n = chosen.size
    val title = when {
        n == 0 -> "Select Items"
        chosen.all { it.isVideo } -> if (n == 1) "1 Video Selected" else "${Fmt.num(n)} Videos Selected"
        chosen.none { it.isVideo } -> if (n == 1) "1 Photo Selected" else "${Fmt.num(n)} Photos Selected"
        else -> "${Fmt.num(n)} Items Selected"
    }
    val fade = if (c.dark) Color.Black else Color.White
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, fade.copy(alpha = 0.55f), fade.copy(alpha = 0.85f))))
            .blockTouches()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (kind == Kind.Trash) {
                SelGlassText(if (n == 0) "Delete All" else "Delete", c.red, items.isNotEmpty()) {
                    actions.deleteForever(if (n == 0) items else chosen) { sel.clear() }
                }
                Spacer(Modifier.weight(1f))
                SelGlassText(if (n == 0) "Recover All" else "Recover", c.label, items.isNotEmpty()) {
                    actions.restore(if (n == 0) items else chosen) { sel.clear() }
                }
            } else {
                SelGlassCircle(IosIcons.Share, n > 0) { actions.share(chosen) }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.height(50.dp).shadow(10.dp, CircleShape, clip = false).clip(CircleShape).background(bg)
                        .border(0.5.dp, border, CircleShape).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    @Composable
                    fun Item(icon: ImageVector, tint: Color = fg, onClick: () -> Unit) {
                        Box(
                            Modifier.size(width = 48.dp, height = 50.dp).tap(enabled = n > 0, onClick = onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, null, tint = if (n > 0) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(23.dp))
                        }
                    }
                    if (kind == Kind.Hidden) {
                        Item(IosIcons.Eye) { store.setHidden(ids, false); sel.clear() }
                    } else {
                        val allFav = n > 0 && ids.all { it in store.favorites }
                        Item(if (allFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder) {
                            store.toggleFavorite(ids); sel.clear()
                        }
                        Item(IosIcons.EyeSlash) { store.setHidden(ids, true); sel.clear() }
                    }
                }
                Spacer(Modifier.width(10.dp))
                SelGlassCircle(IosIcons.Trash, n > 0, tint = c.red) { actions.trash(chosen) { sel.clear() } }
            }
        }
        Text(
            title, color = c.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-24).dp),
        )
    }
}

@Composable
fun SummaryCard(cover: Media, title: String, subtitle: String, aspect: Float, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(14.dp)).tap(onClick = onClick)
    ) {
        Thumb(cover, Modifier.fillMaxSize(), 1080)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f), 0.4f to Color.Transparent)
            )
        )
        Column(Modifier.align(Alignment.TopStart).padding(14.dp)) {
            Text(
                title, color = Color.White,
                style = TextStyle(fontSize = if (big) 30.sp else 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            )
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

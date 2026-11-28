package com.photos.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photos.gallery.data.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class LibMode(val label: String) { Years("Years"), Months("Months"), Days("Days"), All("All Photos") }

enum class LibFilter(val label: String, val ref: AlbumRef) {
    All("All Items", AlbumRef.All),
    Favorites("Favorites", AlbumRef.Favorites),
    Photos("Photos", AlbumRef.Photos),
    Videos("Videos", AlbumRef.Videos),
    Screenshots("Screenshots", AlbumRef.Screenshots),
}

class LibraryUi(private val prefs: AppPrefs) {
    private val modeState = mutableStateOf(LibMode.entries.getOrElse(prefs.int("libMode", LibMode.All.ordinal)) { LibMode.All })
    private val filterState = mutableStateOf(LibFilter.entries.getOrElse(prefs.int("libFilter", LibFilter.All.ordinal)) { LibFilter.All })

    /** Remembered between launches. */
    var mode: LibMode
        get() = modeState.value
        set(v) { modeState.value = v; prefs.putInt("libMode", v.ordinal) }

    /** Remembered between launches. */
    var filter: LibFilter
        get() = filterState.value
        set(v) { filterState.value = v; prefs.putInt("libFilter", v.ordinal) }
    private val newestFirstState = mutableStateOf(prefs.int("libNewestFirst", 0) == 1)

    /** Order of the library: oldest at the top (like iPhone) or newest at the top. Remembered between launches. */
    var newestFirst: Boolean
        get() = newestFirstState.value
        set(v) { newestFirstState.value = v; prefs.putInt("libNewestFirst", if (v) 1 else 0); initialized.clear() }
    val allState = LazyGridState()
    val daysState = LazyGridState()
    val monthsState = LazyListState()
    val yearsState = LazyListState()
    private val columnsState = mutableIntStateOf(prefs.int("libColumns", 3))
    var columns: Int
        get() = columnsState.intValue
        set(v) { columnsState.intValue = v; prefs.putInt("libColumns", v) }
    val selection = SelectionState()
    val initialized = mutableSetOf<LibMode>()

    suspend fun scrollToNewest() {
        if (newestFirst) {
            when (mode) {
                LibMode.All -> allState.animateScrollToItem(0)
                LibMode.Days -> daysState.animateScrollToItem(0)
                LibMode.Months -> monthsState.animateScrollToItem(0)
                LibMode.Years -> yearsState.animateScrollToItem(0)
            }
            return
        }
        when (mode) {
            LibMode.All -> allState.layoutInfo.totalItemsCount.let { if (it > 0) allState.animateScrollToItem(it - 1) }
            LibMode.Days -> daysState.layoutInfo.totalItemsCount.let { if (it > 0) daysState.animateScrollToItem(it - 1) }
            LibMode.Months -> monthsState.layoutInfo.totalItemsCount.let { if (it > 0) monthsState.animateScrollToItem(it - 1) }
            LibMode.Years -> yearsState.layoutInfo.totalItemsCount.let { if (it > 0) yearsState.animateScrollToItem(it - 1) }
        }
    }
}

private sealed interface DayEntry {
    data class Header(val day: Long, val count: Int) : DayEntry
    data class Cell(val m: Media, val hero: Boolean, val more: Int, val day: Long) : DayEntry
}

private fun buildDays(items: List<Media>): List<DayEntry> {
    val out = ArrayList<DayEntry>()
    var i = 0
    while (i < items.size) {
        val d = items[i].epochDay
        var j = i
        while (j < items.size && items[j].epochDay == d) j++
        val group = items.subList(i, j)
        out += DayEntry.Header(d, group.size)
        val candidates = group.indices.filter { !group[it].isVideo && !group[it].isScreenshot }
        val heroIdx = if (candidates.isNotEmpty()) candidates[candidates.size / 2] else 0
        out += DayEntry.Cell(group[heroIdx], true, 0, d)
        val rest = group.filterIndexed { k, _ -> k != heroIdx }
        val take = when {
            rest.size >= 6 -> 6
            rest.size >= 3 -> 3
            else -> rest.size
        }
        val shown = if (rest.size <= take) rest else List(take) { rest[it * rest.size / take] }
        shown.forEachIndexed { k, m ->
            out += DayEntry.Cell(m, false, if (k == shown.lastIndex) rest.size - take else 0, d)
        }
        i = j
    }
    return out
}

@Composable
fun LibraryScreen(store: PhotosStore, ui: LibraryUi, limited: Boolean, onManageAccess: () -> Unit) {
    val c = LocalIos.current
    val inOrder = rememberItems(ui.filter.ref)
    val items = remember(inOrder, ui.newestFirst) { if (ui.newestFirst) inOrder.asReversed() else inOrder }
    BackHandler(ui.selection.active) { ui.selection.clear() }
    val days = remember(items) { buildDays(items) }
    val months = remember(items) { items.groupBy { it.yearMonth }.entries.map { it.key to it.value } }
    val years = remember(items) { items.groupBy { it.date.year }.entries.map { it.key to it.value } }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        when (ui.mode) {
            LibMode.All -> AllPhotos(ui, items, limited, onManageAccess)
            LibMode.Days -> DaysView(ui, days)
            LibMode.Months -> MonthsView(ui, months, days)
            LibMode.Years -> YearsView(ui, years, months)
        }
        if (store.loaded && items.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (ui.filter == LibFilter.All) "No Photos or Videos" else "No ${ui.filter.label}",
                    style = IosType.title2, color = c.label,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (ui.filter == LibFilter.All) "Photos and videos you take will appear here."
                    else "Tap the filter button to show all items.",
                    color = c.secondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
            }
        }
        LibraryHeader(ui, items)
        if (ui.selection.active) {
            StandardSelectionBar(items, ui.selection, Kind.All, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun LibraryHeader(ui: LibraryUi, items: List<Media>) {
    val c = LocalIos.current
    val shadow = Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 10f)
    var menu by remember { mutableStateOf(false) }
    val sel = ui.selection
    Box(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.8f), 0.55f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent,
            )
        )
    ) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val title = when {
                    !sel.active -> "Library"
                    sel.ids.isEmpty() -> "Select Items"
                    else -> "${Fmt.num(sel.ids.size)} Selected"
                }
                Text(title, color = Color.White, style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, shadow = shadow))
                if (!sel.active) {
                    val sub = buildString {
                        append(Fmt.num(items.size)).append(if (items.size == 1) " Item" else " Items")
                        if (ui.filter != LibFilter.All) append(" · ").append(ui.filter.label)
                    }
                    Text(sub, color = Color.White, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, shadow = shadow))
                }
            }
            if (!sel.active) {
                Box {
                    GlassCircle(IosIcons.Filter) { menu = true }
                    val prefs = AppPrefs.get(LocalContext.current)
                    val aspect = prefs.flag(PrefKeys.AspectGrid, false)
                    IosPopupMenu(
                        menu, { menu = false },
                        // Tapping the view you're already in flips the order: oldest first ↔ newest first.
                        LibMode.entries.map { m ->
                            val on = ui.mode == m
                            PopupItem(m.label, checked = on) { if (on) ui.newestFirst = !ui.newestFirst else ui.mode = m }
                        } +
                            LibFilter.entries.mapIndexed { i, f ->
                                PopupItem(f.label, group = i == 0, checked = ui.filter == f) {
                                    if (ui.filter != f) { ui.filter = f; ui.initialized.clear() }
                                }
                            } +
                            PopupItem("Aspect Ratio Grid", IosIcons.Panorama, group = true, checked = aspect) {
                                prefs.setFlag(PrefKeys.AspectGrid, !aspect)
                            },
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            if (items.isNotEmpty() && (sel.active || ui.mode == LibMode.All)) {
                GlassPill(if (sel.active) "Cancel" else "Select") {
                    if (sel.active) sel.clear() else sel.active = true
                }
            }
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(
        text, color = LocalIos.current.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun MenuOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalIos.current
    DropdownMenuItem(
        text = { Text(text, color = c.label, fontSize = 16.sp) },
        onClick = onClick,
        trailingIcon = {
            if (selected) Icon(Icons.Filled.Check, null, tint = c.label, modifier = Modifier.size(20.dp))
        },
        modifier = Modifier.widthIn(min = 200.dp),
    )
}

private val HeaderSpace = 96.dp

@Composable
private fun AllPhotos(ui: LibraryUi, items: List<Media>, limited: Boolean, onManage: () -> Unit) {
    val nav = LocalNav.current
    val c = LocalIos.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val headerCount = if (limited) 1 else 0
    LaunchedEffect(items.isNotEmpty(), ui.filter, ui.newestFirst) {
        if (items.isNotEmpty() && LibMode.All !in ui.initialized) {
            ui.initialized += LibMode.All
            ui.allState.scrollToItem(if (ui.newestFirst) 0 else items.size + headerCount)
        }
    }
    val photos = remember(items) { items.count { !it.isVideo } }
    val header: (LazyGridScope.() -> Unit)? = if (limited) {
        {
            item(key = "limited", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp)).background(c.card).padding(14.dp)
                ) {
                    Text("Limited access", style = IosType.headline, color = c.label)
                    Text("Photos can only see the items you selected.", fontSize = 14.sp, color = c.secondary)
                    Text(
                        "Manage", color = c.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp).tap(onClick = onManage),
                    )
                }
            }
        }
    } else null
    MediaGrid(
        items = items,
        state = ui.allState,
        columns = ui.columns,
        selection = ui.selection,
        contentPadding = PaddingValues(top = top + HeaderSpace, bottom = bottom + TabBarHeight + 12.dp),
        onOpen = { nav.push(Route.Viewer(ui.filter.ref, it.id)) },
        onColumns = { ui.columns = it },
        header = header,
        footer = if (items.isEmpty()) null else Fmt.counts(photos, items.size - photos),
    )
}

@Composable
private fun DaysView(ui: LibraryUi, entries: List<DayEntry>) {
    val nav = LocalNav.current
    val c = LocalIos.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(entries.isNotEmpty(), ui.filter, ui.newestFirst) {
        if (entries.isNotEmpty() && LibMode.Days !in ui.initialized) {
            ui.initialized += LibMode.Days
            ui.daysState.scrollToItem(if (ui.newestFirst) 0 else entries.lastIndex)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = ui.daysState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = top + HeaderSpace, bottom = bottom + TabBarHeight + 12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(
            entries,
            key = { e -> if (e is DayEntry.Header) "h${e.day}" else "c${(e as DayEntry.Cell).m.id}" },
            span = { e -> GridItemSpan(if (e is DayEntry.Header || (e as DayEntry.Cell).hero) maxLineSpan else 1) },
            contentType = { e -> if (e is DayEntry.Header) 0 else 1 },
        ) { e ->
            when (e) {
                is DayEntry.Header -> {
                    val date = LocalDate.ofEpochDay(e.day)
                    Row(
                        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 22.dp, bottom = 6.dp)
                            .tap { nav.push(Route.Album(AlbumRef(Kind.Day, Fmt.dayTitle(date), e.day))) },
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(Fmt.dayHeader(date), style = IosType.title2.copy(fontSize = 20.sp), color = c.label, modifier = Modifier.weight(1f))
                        Text(Fmt.num(e.count), fontSize = 15.sp, color = c.secondary)
                        Icon(IosIcons.ChevronRight, null, tint = c.tertiary, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp).size(13.dp))
                    }
                }
                is DayEntry.Cell -> {
                    val shape = RoundedCornerShape(if (e.hero) 12.dp else 6.dp)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(if (e.hero) 1.5f else 1f).clip(shape).tap {
                            nav.push(Route.Viewer(AlbumRef(Kind.Day, Fmt.dayTitle(e.m.date), e.day), e.m.id))
                        }
                    ) {
                        Thumb(e.m, Modifier.fillMaxSize(), if (e.hero) 1080 else 400)
                        if (e.m.isVideo) {
                            Text(
                                Fmt.duration(e.m.duration), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                            )
                        }
                        if (e.more > 0) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                Text("+${e.more}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthsView(ui: LibraryUi, months: List<Pair<YearMonth, List<Media>>>, days: List<DayEntry>) {
    val scope = rememberCoroutineScope()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(months.isNotEmpty(), ui.filter, ui.newestFirst) {
        if (months.isNotEmpty() && LibMode.Months !in ui.initialized) {
            ui.initialized += LibMode.Months
            ui.monthsState.scrollToItem(if (ui.newestFirst) 0 else months.lastIndex)
        }
    }
    LazyColumn(
        state = ui.monthsState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + HeaderSpace, bottom = bottom + TabBarHeight + 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(months, key = { it.first.toString() }) { (ym, list) ->
            val photos = list.count { !it.isVideo }
            val cover = list.filter { !it.isVideo && !it.isScreenshot }.let { if (it.isEmpty()) list[list.size / 2] else it[it.size / 2] }
            SummaryCard(cover, Fmt.monthYear(ym), Fmt.counts(photos, list.size - photos), 1.25f) {
                val idx = days.indexOfFirst { it is DayEntry.Header && YearMonth.from(LocalDate.ofEpochDay(it.day)) == ym }
                ui.initialized += LibMode.Days
                ui.mode = LibMode.Days
                if (idx >= 0) scope.launch { ui.daysState.scrollToItem(idx) }
            }
        }
    }
}

@Composable
private fun YearsView(ui: LibraryUi, years: List<Pair<Int, List<Media>>>, months: List<Pair<YearMonth, List<Media>>>) {
    val scope = rememberCoroutineScope()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(years.isNotEmpty(), ui.filter, ui.newestFirst) {
        if (years.isNotEmpty() && LibMode.Years !in ui.initialized) {
            ui.initialized += LibMode.Years
            ui.yearsState.scrollToItem(if (ui.newestFirst) 0 else years.lastIndex)
        }
    }
    LazyColumn(
        state = ui.yearsState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + HeaderSpace, bottom = bottom + TabBarHeight + 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(years, key = { it.first }) { (year, list) ->
            val cover = list.filter { !it.isVideo && !it.isScreenshot }.let { if (it.isEmpty()) list[list.size / 2] else it[it.size / 2] }
            SummaryCard(cover, year.toString(), "", 1.0f, big = true) {
                val idx = months.indexOfFirst { it.first.year == year }
                ui.initialized += LibMode.Months
                ui.mode = LibMode.Months
                if (idx >= 0) scope.launch { ui.monthsState.scrollToItem(idx) }
            }
        }
    }
}

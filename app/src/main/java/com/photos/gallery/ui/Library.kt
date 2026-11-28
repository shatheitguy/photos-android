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
        set(v) { modeState.value = v; prefs.putInt("libMode", v.ordinal); backStack.clear() }

    /** Remembered between launches. */
    var filter: LibFilter
        get() = filterState.value
        set(v) { filterState.value = v; prefs.putInt("libFilter", v.ordinal) }
    private val newestFirstState = mutableStateOf(prefs.int("libNewestFirst", 0) == 1)

    /** Order of the library: oldest at the top (like iPhone) or newest at the top. Remembered between launches. */
    var newestFirst: Boolean
        get() = newestFirstState.value
        set(v) { newestFirstState.value = v; prefs.putInt("libNewestFirst", if (v) 1 else 0); initialized.clear() }
    private fun levelState(key: String) = mutableIntStateOf(prefs.int(key, 1))
    private val yearColsState = levelState("libYearCols")
    private val monthColsState = levelState("libMonthCols")
    private val dayColsState = levelState("libDayCols")

    /** Pinch level of Years / Months / Days: 1 is the big cards, more columns is the compact grid. Remembered. */
    var yearCols: Int
        get() = yearColsState.intValue
        set(v) { yearColsState.intValue = v; prefs.putInt("libYearCols", v); initialized -= LibMode.Years }
    var monthCols: Int
        get() = monthColsState.intValue
        set(v) { monthColsState.intValue = v; prefs.putInt("libMonthCols", v); initialized -= LibMode.Months }
    var dayCols: Int
        get() = dayColsState.intValue
        set(v) { dayColsState.intValue = v; prefs.putInt("libDayCols", v); initialized -= LibMode.Days }
    val yearsGrid = LazyGridState()
    val monthsGrid = LazyGridState()
    val daysGrid = LazyGridState()

    /** Where a tapped year or month should open in the next view. */
    var jumpYear by mutableStateOf<Int?>(null)
    var jumpMonth by mutableStateOf<YearMonth?>(null)

    /** Views you came from by tapping a year or month, so Back goes up a level instead of leaving the app. */
    val backStack = mutableStateListOf<LibMode>()

    fun drillTo(m: LibMode) {
        backStack += mode
        initialized += m
        modeState.value = m
    }

    fun back() {
        modeState.value = backStack.removeAt(backStack.lastIndex)
    }
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
                LibMode.Days -> if (dayCols > 1) daysGrid.animateScrollToItem(0) else daysState.animateScrollToItem(0)
                LibMode.Months -> if (monthCols > 1) monthsGrid.animateScrollToItem(0) else monthsState.animateScrollToItem(0)
                LibMode.Years -> if (yearCols > 1) yearsGrid.animateScrollToItem(0) else yearsState.animateScrollToItem(0)
            }
            return
        }
        val grid = when (mode) {
            LibMode.Days -> daysGrid.takeIf { dayCols > 1 }
            LibMode.Months -> monthsGrid.takeIf { monthCols > 1 }
            LibMode.Years -> yearsGrid.takeIf { yearCols > 1 }
            LibMode.All -> null
        }
        if (grid != null) {
            grid.layoutInfo.totalItemsCount.let { if (it > 0) grid.animateScrollToItem(it - 1) }
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
    val navStack = LocalNav.current
    BackHandler(ui.selection.active && !navStack.canPop) { ui.selection.clear() }
    BackHandler(!ui.selection.active && !navStack.canPop && ui.backStack.isNotEmpty()) { ui.back() }
    val days = remember(items) { buildDays(items) }
    val months = remember(items) { items.groupBy { it.yearMonth }.entries.map { it.key to it.value } }
    val years = remember(items) { items.groupBy { it.date.year }.entries.map { it.key to it.value } }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        when (ui.mode) {
            LibMode.All -> AllPhotos(ui, items, limited, onManageAccess)
            LibMode.Days -> if (ui.dayCols > 1) CompactDays(ui, items) else DaysView(ui, days)
            LibMode.Months -> if (ui.monthCols > 1) CompactMonths(ui, months) else MonthsView(ui, months)
            LibMode.Years -> if (ui.yearCols > 1) CompactYears(ui, years) else YearsView(ui, years)
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
    LaunchedEffect(ui.jumpMonth, entries) {
        val jm = ui.jumpMonth ?: return@LaunchedEffect
        val idx = entries.indexOfFirst { it is DayEntry.Header && YearMonth.from(LocalDate.ofEpochDay(it.day)) == jm }
        if (idx >= 0) ui.daysState.scrollToItem(idx)
        ui.jumpMonth = null
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = ui.daysState,
        modifier = Modifier.fillMaxSize().pinchToColumns(ui.dayCols, DayLevels) { ui.dayCols = it },
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
private fun MonthsView(ui: LibraryUi, months: List<Pair<YearMonth, List<Media>>>) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(months.isNotEmpty(), ui.filter, ui.newestFirst) {
        if (months.isNotEmpty() && LibMode.Months !in ui.initialized) {
            ui.initialized += LibMode.Months
            ui.monthsState.scrollToItem(if (ui.newestFirst) 0 else months.lastIndex)
        }
    }
    LaunchedEffect(ui.jumpYear, months) {
        val jy = ui.jumpYear ?: return@LaunchedEffect
        val idx = months.indexOfFirst { it.first.year == jy }
        if (idx >= 0) ui.monthsState.scrollToItem(idx)
        ui.jumpYear = null
    }
    LazyColumn(
        state = ui.monthsState,
        modifier = Modifier.fillMaxSize().pinchToColumns(ui.monthCols, GroupLevels) { ui.monthCols = it },
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + HeaderSpace, bottom = bottom + TabBarHeight + 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(months, key = { it.first.toString() }) { (ym, list) ->
            val photos = list.count { !it.isVideo }
            val cover = list.filter { !it.isVideo && !it.isScreenshot }.let { if (it.isEmpty()) list[list.size / 2] else it[it.size / 2] }
            SummaryCard(cover, Fmt.monthYear(ym), Fmt.counts(photos, list.size - photos), 1.25f) {
                ui.jumpMonth = ym
                ui.drillTo(LibMode.Days)
            }
        }
    }
}

@Composable
private fun YearsView(ui: LibraryUi, years: List<Pair<Int, List<Media>>>) {
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
        modifier = Modifier.fillMaxSize().pinchToColumns(ui.yearCols, GroupLevels) { ui.yearCols = it },
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + HeaderSpace, bottom = bottom + TabBarHeight + 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(years, key = { it.first }) { (year, list) ->
            val cover = list.filter { !it.isVideo && !it.isScreenshot }.let { if (it.isEmpty()) list[list.size / 2] else it[it.size / 2] }
            SummaryCard(cover, year.toString(), "", 1.0f, big = true) {
                ui.jumpYear = year
                ui.drillTo(LibMode.Months)
            }
        }
    }
}

// ---------- Compact (pinched-in) Years / Months / Days ----------

private val GroupLevels = listOf(1, 2, 3)
private val DayLevels = listOf(1, 4, 6)

private fun coverOf(list: List<Media>): Media =
    list.filter { !it.isVideo && !it.isScreenshot }.let { if (it.isEmpty()) list[list.size / 2] else it[it.size / 2] }

private sealed interface GridTile {
    val key: String
    data class Head(override val key: String, val text: String, val tag: Any) : GridTile
    data class Item(override val key: String, val cover: Media, val label: String, val tag: Any) : GridTile
}

@Composable
private fun CompactMonths(ui: LibraryUi, months: List<Pair<YearMonth, List<Media>>>) {
    val tiles = remember(months) {
        val out = ArrayList<GridTile>()
        var year = -1
        for ((ym, list) in months) {
            if (ym.year != year) { year = ym.year; out += GridTile.Head("y$year", year.toString(), year) }
            val name = ym.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            out += GridTile.Item("m$ym", coverOf(list), name, ym)
        }
        out
    }
    LaunchedEffect(ui.jumpYear, tiles) {
        val jy = ui.jumpYear ?: return@LaunchedEffect
        val idx = tiles.indexOfFirst { it is GridTile.Head && it.tag == jy }
        if (idx >= 0) ui.monthsGrid.scrollToItem(idx)
        ui.jumpYear = null
    }
    CompactGrid(ui, LibMode.Months, ui.monthsGrid, ui.monthCols, GroupLevels, { ui.monthCols = it }, tiles) { t ->
        ui.jumpMonth = t.tag as YearMonth
        ui.drillTo(LibMode.Days)
    }
}

@Composable
private fun CompactYears(ui: LibraryUi, years: List<Pair<Int, List<Media>>>) {
    val tiles = remember(years) { years.map { (y, list) -> GridTile.Item("y$y", coverOf(list), y.toString(), y) } }
    CompactGrid(ui, LibMode.Years, ui.yearsGrid, ui.yearCols, GroupLevels, { ui.yearCols = it }, tiles) { t ->
        ui.jumpYear = t.tag as Int
        ui.drillTo(LibMode.Months)
    }
}

@Composable
private fun CompactDays(ui: LibraryUi, items: List<Media>) {
    val nav = LocalNav.current
    val tiles = remember(items) {
        val out = ArrayList<GridTile>()
        var month: YearMonth? = null
        for ((day, list) in items.groupBy { it.epochDay }) {
            val date = LocalDate.ofEpochDay(day)
            val ym = YearMonth.from(date)
            if (ym != month) { month = ym; out += GridTile.Head("h$ym", Fmt.monthYear(ym), ym) }
            out += GridTile.Item("d$day", coverOf(list), date.dayOfMonth.toString(), day)
        }
        out
    }
    LaunchedEffect(ui.jumpMonth, tiles) {
        val jm = ui.jumpMonth ?: return@LaunchedEffect
        val idx = tiles.indexOfFirst { it is GridTile.Head && it.tag == jm }
        if (idx >= 0) ui.daysGrid.scrollToItem(idx)
        ui.jumpMonth = null
    }
    CompactGrid(ui, LibMode.Days, ui.daysGrid, ui.dayCols, DayLevels, { ui.dayCols = it }, tiles) { t ->
        val day = t.tag as Long
        nav.push(Route.Album(AlbumRef(Kind.Day, Fmt.dayTitle(LocalDate.ofEpochDay(day)), day)))
    }
}

@Composable
private fun CompactGrid(
    ui: LibraryUi, mode: LibMode, state: LazyGridState, cols: Int, levels: List<Int>, onCols: (Int) -> Unit,
    tiles: List<GridTile>, onOpen: (GridTile.Item) -> Unit,
) {
    val c = LocalIos.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(tiles.isNotEmpty(), ui.filter, ui.newestFirst, cols) {
        if (tiles.isNotEmpty() && mode !in ui.initialized) {
            ui.initialized += mode
            state.scrollToItem(if (ui.newestFirst) 0 else tiles.lastIndex)
        }
    }
    val small = cols > 3
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        state = state,
        modifier = Modifier.fillMaxSize().pinchToColumns(cols, levels, onCols),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = top + HeaderSpace, bottom = bottom + TabBarHeight + 12.dp),
        horizontalArrangement = Arrangement.spacedBy(if (small) 3.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (small) 3.dp else 8.dp),
    ) {
        items(
            tiles, key = { it.key },
            span = { t -> GridItemSpan(if (t is GridTile.Head) maxLineSpan else 1) },
            contentType = { t -> if (t is GridTile.Head) 0 else 1 },
        ) { t ->
            when (t) {
                is GridTile.Head -> Text(
                    t.text, style = IosType.title2, color = c.label,
                    modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 4.dp),
                )
                is GridTile.Item -> Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(if (small) 6.dp else 12.dp)).tap { onOpen(t) },
                ) {
                    Thumb(t.cover, Modifier.fillMaxSize(), if (cols <= 2) 720 else 400)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f), 0.45f to Color.Transparent)))
                    Text(
                        t.label, color = Color.White,
                        fontSize = when { small -> 13.sp; cols == 3 -> 16.sp; else -> 20.sp }, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(if (small) 5.dp else 10.dp),
                    )
                }
            }
        }
    }
}

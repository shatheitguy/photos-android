package com.photos.gallery.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photos.gallery.data.*

@Composable
fun rememberMyAlbums(): List<Pair<AlbumRef, List<Media>>> {
    val store = LocalStore.current
    val lib = store.library
    val favs = store.favorites
    val buckets = store.buckets
    return remember(lib, favs, buckets) {
        buildList {
            add(AlbumRef.All to lib)
            add(AlbumRef.Favorites to lib.filter { it.id in favs })
            buckets.forEach { add(AlbumRef(Kind.Bucket, it.name, it.id) to it.items) }
        }
    }
}

data class MediaType(val ref: AlbumRef, val icon: ImageVector, val count: Int)

@Composable
fun rememberMediaTypes(): List<MediaType> {
    val lib = LocalStore.current.library
    return remember(lib) {
        listOf(
            MediaType(AlbumRef.Videos, IosIcons.Video, lib.count { it.isVideo }),
            MediaType(AlbumRef.Selfies, IosIcons.Selfie, lib.count { it.isSelfie }),
            MediaType(AlbumRef.Screenshots, IosIcons.Screenshot, lib.count { it.isScreenshot }),
            MediaType(AlbumRef.Recordings, IosIcons.Recording, lib.count { it.isScreenRecording }),
            MediaType(AlbumRef.Panoramas, IosIcons.Panorama, lib.count { it.isPanorama }),
            MediaType(AlbumRef.Animated, IosIcons.Animated, lib.count { it.isAnimated }),
        ).filter { it.count > 0 }
    }
}

@Composable
fun AlbumsScreen(state: LazyListState) {
    val store = LocalStore.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val c = LocalIos.current
    val albums = rememberMyAlbums()
    val pairs = remember(albums) { albums.chunked(2) }
    val types = rememberMediaTypes()
    val dupCount = store.duplicates.size
    val screenW = LocalConfiguration.current.screenWidthDp
    val perRow = if (screenW >= 900) 5 else if (screenW >= 600) 4 else 2
    val tileW = ((screenW - 16 - 12 * (perRow - 1) - 44) / perRow).dp
    val memW = (screenW * 0.78f).coerceAtMost(420f).dp
    val prefs = AppPrefs.get(LocalContext.current)
    val memories = store.memories
    val featured = store.featured
    val showMem = prefs.flag(PrefKeys.Memories, true) && memories.isNotEmpty()
    val showFeat = prefs.flag(PrefKeys.Featured, true) && featured.isNotEmpty()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(bottom = bottom + TabBarHeight + 24.dp),
    ) {
        item { LargeTitle("Albums") { AlbumsMoreButton() } }
        memorySections(
            memories = if (prefs.flag(PrefKeys.Memories, true)) memories else emptyList(),
            featured = if (prefs.flag(PrefKeys.Featured, true)) featured else emptyList(),
            cardWidth = memW,
            onMemory = { nav.push(Route.Story(it)) },
            onFeatured = { nav.push(Route.Viewer(AlbumRef.Featured, it.id)) },
        )
        item { SectionHeader("My Albums", "See All", divider = showMem || showFeat) { nav.push(Route.AllAlbums) } }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pairs) { pair ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        pair.forEach { (ref, list) -> AlbumTile(ref, list, tileW) { nav.push(Route.Album(ref)) } }
                    }
                }
            }
        }
        if (types.isNotEmpty()) {
            item { SectionHeader("Media Types") }
            items(types) { t -> ListRow(t.icon, t.ref.title, t.count) { nav.push(Route.Album(t.ref)) } }
        }
        item { SectionHeader("Utilities") }
        if (dupCount > 0) {
            item { ListRow(IosIcons.Duplicate, "Duplicates", dupCount) { nav.push(Route.Album(AlbumRef.Duplicates)) } }
        }
        if (prefs.flag(PrefKeys.ShowHidden, true)) {
            item {
                ListRow(IosIcons.EyeSlash, "Hidden", null, locked = prefs.flag(PrefKeys.Passcode, true)) {
                    actions.unlock { nav.push(Route.Album(AlbumRef.Hidden)) }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            item {
                ListRow(IosIcons.Trash, "Recently Deleted", null, locked = prefs.flag(PrefKeys.Passcode, true)) {
                    actions.unlock { nav.push(Route.Album(AlbumRef.Trash)) }
                }
            }
        }
    }
}

@Composable
fun AllAlbumsScreen(onBack: () -> Unit) {
    val nav = LocalNav.current
    val c = LocalIos.current
    val albums = rememberMyAlbums()
    val sw = LocalConfiguration.current.screenWidthDp
    val cols = if (sw >= 900) 5 else if (sw >= 600) 4 else 2
    val w = ((sw - 16 * 2 - 16 * (cols - 1)) / cols).dp
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 64.dp, bottom = bottom + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(albums) { (ref, list) -> AlbumTile(ref, list, w) { nav.push(Route.Album(ref)) } }
        }
        NavTopBar("My Albums", "Albums", onBack)
    }
}

private fun emptyMessage(kind: Kind): String = when (kind) {
    Kind.Favorites -> "Tap the heart on a photo or video to add it to Favorites."
    Kind.Hidden -> "Items you hide will appear here and won't show in your Library."
    Kind.Trash -> "Deleted items stay here before they're removed for good."
    Kind.Duplicates -> "No duplicate photos found."
    else -> "There's nothing here yet."
}

@Composable
fun AlbumScreen(ref: AlbumRef, onBack: () -> Unit) {
    val store = LocalStore.current
    val nav = LocalNav.current
    val c = LocalIos.current
    val items = rememberItems(ref)
    val state = rememberLazyGridState()
    var cols by remember { mutableIntStateOf(3) }
    val sel = remember { SelectionState() }
    val trash = ref.kind == Kind.Trash
    var didScroll by remember { mutableStateOf(false) }
    BackHandler(sel.active) { sel.clear() }
    val headerCount = if (trash) 1 else 0
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty() && !didScroll) {
            didScroll = true
            state.scrollToItem(items.size + headerCount)
        }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val photos = remember(items) { items.count { !it.isVideo } }
    val trashHeader: (LazyGridScope.() -> Unit)? = if (trash) {
        {
            item(key = "trash-note", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Items show the days remaining before deletion. After that time, items will be permanently deleted.",
                    fontSize = 13.sp, color = c.secondary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    } else null

    Box(Modifier.fillMaxSize().background(c.bg)) {
        MediaGrid(
            items = items,
            state = state,
            columns = cols,
            selection = sel,
            contentPadding = PaddingValues(top = top + 50.dp, bottom = bottom + if (sel.active) 70.dp else 20.dp),
            onOpen = { nav.push(Route.Viewer(ref, it.id)) },
            onColumns = { cols = it },
            trash = trash,
            header = trashHeader,
            footer = if (items.isEmpty()) null else Fmt.counts(photos, items.size - photos),
        )
        if (store.loaded && items.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No Photos or Videos", style = IosType.title2, color = c.label)
                Spacer(Modifier.height(6.dp))
                Text(emptyMessage(ref.kind), fontSize = 15.sp, color = c.secondary, textAlign = TextAlign.Center)
            }
        }
        NavTopBar(ref.title, "Back", backUnlessRoot(onBack)) {
            if (items.isNotEmpty()) {
                Pill(if (sel.active) "Cancel" else "Select") { if (sel.active) sel.clear() else sel.active = true }
            }
        }
        if (sel.active) StandardSelectionBar(items, sel, ref.kind, Modifier.align(Alignment.BottomCenter))
    }
}


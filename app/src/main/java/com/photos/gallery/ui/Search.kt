package com.photos.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photos.gallery.data.*
import java.time.YearMonth

@Composable
fun SearchScreen() {
    val store = LocalStore.current
    val nav = LocalNav.current
    val c = LocalIos.current
    val focus = LocalFocusManager.current
    var q by rememberSaveable { mutableStateOf("") }
    val lib = store.library
    val query = q.trim()
    val results = remember(query, lib, store.favorites) { if (query.isEmpty()) emptyList() else store.search(query).asReversed() }
    val albums = rememberMyAlbums()
    val types = rememberMediaTypes()
    val months = remember(lib) { lib.groupBy { it.yearMonth }.entries.map { it.key to it.value }.asReversed() }
    val albumHits = remember(query, albums) { if (query.isEmpty()) emptyList() else albums.filter { it.first.title.contains(query, true) } }
    val monthHits = remember(query, months) {
        if (query.isEmpty()) emptyList() else months.filter { Fmt.monthYear(it.first).contains(query, true) }.take(6)
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().background(c.bg)) {
        LargeTitle("Search")
        SearchField(q, { q = it }, { focus.clearFocus() }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = bottom + TabBarHeight + 20.dp),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            if (query.isEmpty()) {
                if (types.isNotEmpty()) {
                    full("h-types") { SectionHeader("Media Types", divider = false) }
                    items(types, key = { "t" + it.ref.title }, span = { GridItemSpan(maxLineSpan) }) { t ->
                        ListRow(t.icon, t.ref.title, t.count) { nav.push(Route.Album(t.ref)) }
                    }
                }
                full("h-albums") { SectionHeader("Albums") }
                full("albums") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(albums) { (ref, list) -> AlbumTile(ref, list, 110.dp) { nav.push(Route.Album(ref)) } }
                    }
                }
                if (months.isNotEmpty()) {
                    full("h-months") { SectionHeader("Moments") }
                    items(months.take(12), key = { "m" + it.first }, span = { GridItemSpan(maxLineSpan) }) { (ym, list) ->
                        MonthRow(ym, list)
                    }
                }
            } else {
                if (albumHits.isNotEmpty()) {
                    full("h-ah") { SectionHeader("Albums", divider = false) }
                    items(albumHits, key = { "a" + it.first.title + it.first.key }, span = { GridItemSpan(maxLineSpan) }) { (ref, list) ->
                        SuggestionRow(list.lastOrNull(), ref.title, Fmt.num(list.size)) { nav.push(Route.Album(ref)) }
                    }
                }
                if (monthHits.isNotEmpty()) {
                    full("h-mh") { SectionHeader("Moments", divider = albumHits.isNotEmpty()) }
                    items(monthHits, key = { "mh" + it.first }, span = { GridItemSpan(maxLineSpan) }) { (ym, list) ->
                        MonthRow(ym, list)
                    }
                }
                full("h-res") {
                    SectionHeader(
                        if (results.isEmpty()) "No Results" else "Photos",
                        if (results.isEmpty()) null else "See All",
                        divider = albumHits.isNotEmpty() || monthHits.isNotEmpty(),
                    ) { nav.push(Route.Album(AlbumRef(Kind.Search, "“$query”", query = query))) }
                }
                if (results.isEmpty()) {
                    full("empty") {
                        Text(
                            "Try searching for a month, a year, an album name, “videos” or “screenshots”.",
                            color = c.secondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    items(results, key = { it.id }) { m ->
                        Box(Modifier.aspectRatio(1f).tap {
                            focus.clearFocus()
                            nav.push(Route.Viewer(AlbumRef(Kind.Search, query, query = query), m.id))
                        }) {
                            Thumb(m, Modifier.fillMaxSize(), 400)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun MonthRow(ym: YearMonth, list: List<Media>) {
    val nav = LocalNav.current
    val key = ym.year * 12L + (ym.monthValue - 1)
    SuggestionRow(list.getOrNull(list.size / 2), Fmt.monthYear(ym), Fmt.num(list.size)) {
        nav.push(Route.Album(AlbumRef(Kind.Month, Fmt.monthYear(ym), key)))
    }
}

@Composable
private fun SuggestionRow(cover: Media?, title: String, trailing: String, onClick: () -> Unit) {
    val c = LocalIos.current
    Row(
        Modifier.fillMaxWidth().tap(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(c.placeholder)) {
            if (cover != null) Thumb(cover, Modifier.fillMaxSize(), 256)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 17.sp, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(trailing, fontSize = 15.sp, color = c.secondary)
        Spacer(Modifier.width(6.dp))
        Icon(IosIcons.ChevronRight, null, tint = c.tertiary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalIos.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 17.sp, color = c.label),
        cursorBrush = SolidColor(c.label),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(c.fill).padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = c.secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Photos, Albums, Months…", color = c.secondary, fontSize = 17.sp)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(c.secondary.copy(alpha = 0.6f)).tap { onChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, null, tint = c.bg, modifier = Modifier.size(12.dp))
                    }
                }
            }
        },
    )
}

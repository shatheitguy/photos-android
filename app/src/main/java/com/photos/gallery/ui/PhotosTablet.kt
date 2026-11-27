package com.photos.gallery.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.photos.gallery.data.*

/** The iPad Photos sidebar. [album] is the album shown in the pane, or null for [tab]. */
@Composable
fun PhotosSidebar(tab: PhotosTab, album: AlbumRef?, onTab: (PhotosTab) -> Unit, onAlbum: (AlbumRef) -> Unit) {
    val store = LocalStore.current
    val actions = LocalActions.current
    val prefs = AppPrefs.get(LocalContext.current)
    val albums = rememberMyAlbums()
    val types = rememberMediaTypes()
    val onTabs = album == null
    Column(Modifier.fillMaxSize()) {
        SidebarHeader("Photos") { AlbumsMoreButton() }
        SidebarList {
            item { SidebarRow("Library", IosIcons.Library, selected = onTabs && tab == PhotosTab.Library) { onTab(PhotosTab.Library) } }
            item { SidebarRow("Albums", IosIcons.Albums, selected = onTabs && tab == PhotosTab.Albums) { onTab(PhotosTab.Albums) } }
            item { SidebarRow("Search", IosIcons.Search, selected = onTabs && tab == PhotosTab.Search) { onTab(PhotosTab.Search) } }
            sidebarSection("albums", "My Albums") {
                albums.filter { it.first != AlbumRef.All }.forEach { (ref, list) ->
                    SidebarRow(
                        ref.title,
                        if (ref == AlbumRef.Favorites) Icons.Default.FavoriteBorder else IosIcons.Folder,
                        selected = album == ref,
                        detail = Fmt.num(list.size),
                    ) { onAlbum(ref) }
                }
            }
            if (types.isNotEmpty()) {
                sidebarSection("types", "Media Types") {
                    types.forEach { t ->
                        SidebarRow(t.ref.title, t.icon, selected = album == t.ref, detail = Fmt.num(t.count)) { onAlbum(t.ref) }
                    }
                }
            }
            sidebarSection("utils", "Utilities") {
                val dup = store.duplicates.size
                if (dup > 0) SidebarRow("Duplicates", IosIcons.Duplicate, selected = album == AlbumRef.Duplicates, detail = Fmt.num(dup)) { onAlbum(AlbumRef.Duplicates) }
                if (prefs.flag(PrefKeys.ShowHidden, true)) {
                    SidebarRow("Hidden", IosIcons.EyeSlash, selected = album == AlbumRef.Hidden) {
                        actions.unlock { onAlbum(AlbumRef.Hidden) }
                    }
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    SidebarRow("Recently Deleted", IosIcons.Trash, selected = album == AlbumRef.Trash) {
                        actions.unlock { onAlbum(AlbumRef.Trash) }
                    }
                }
            }
        }
    }
}

/** Screens that open inside the pane on a tablet; the viewer, editor and memories still fill the screen. */
fun Route.inPane(): Boolean = this is Route.Album || this is Route.AllAlbums || this is Route.Settings

package com.photos.gallery.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.photos.gallery.data.Media
import com.photos.gallery.data.PhotosStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TEMPORARY: bring favorites and settings over from Fossify Gallery. Remove this file and its one line in
// PhotosSettings.kt once you've moved over.

/** The part of a path after the storage root, e.g. "DCIM/Camera/IMG_1.jpg". */
private fun relative(path: String): String {
    val p = path.trim().replace('\\', '/')
    val m = Regex("^/storage/[^/]+/(?:\\d+/)?").find(p) ?: Regex("^/sdcard/").find(p)
    return (if (m != null) p.substring(m.range.last + 1) else p.trimStart('/')).lowercase()
}

private fun Media.relative(): String = (relPath + name).trimStart('/').lowercase()

private fun readLines(ctx: Context, uri: Uri): List<String> =
    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }.orEmpty()

/** Fossify's "Export favorites" file: one full path per line. */
private suspend fun importFavorites(ctx: Context, store: PhotosStore, uri: Uri): String = withContext(Dispatchers.IO) {
    val paths = readLines(ctx, uri).map { it.trim() }.filter { it.startsWith("/") }
    if (paths.isEmpty()) return@withContext "That file has no favorites in it. In Fossify Gallery, use Settings → Export favorites."
    val all = (store.library + store.itemsFor(com.photos.gallery.data.AlbumRef.Hidden))
    val byPath = all.associateBy { it.relative() }
    val found = paths.mapNotNull { byPath[relative(it)]?.id }.toSet()
    val added = found - store.favorites
    withContext(Dispatchers.Main) { if (added.isNotEmpty()) store.toggleFavorite(added) }
    val missing = paths.size - found.size
    buildString {
        append("Added ${added.size} favorite${if (added.size == 1) "" else "s"}.")
        if (found.size > added.size) append(" ${found.size - added.size} were already favorites.")
        if (missing > 0) append(" $missing couldn't be found on this phone.")
    }
}

/** Fossify's "Export settings" file: key=value lines. Only settings Photos has an equivalent for are used. */
private suspend fun importSettings(ctx: Context, store: PhotosStore, uri: Uri): String = withContext(Dispatchers.IO) {
    val values = readLines(ctx, uri).mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1] } }.toMap()
    if (values.isEmpty()) return@withContext "That doesn't look like a Fossify Gallery settings file. Use Settings → Export settings in Fossify."
    val prefs = AppPrefs.get(ctx)
    val done = mutableListOf<String>()
    values["media_column_cnt"]?.toIntOrNull()?.let { n ->
        val cols = ColumnLevels.minByOrNull { kotlin.math.abs(it - n) } ?: 3
        prefs.putInt("libColumns", cols)
        done += "grid size ($cols columns)"
    }
    values["crop_thumbnails"]?.toBooleanStrictOrNull()?.let { crop ->
        withContext(Dispatchers.Main) { prefs.setFlag(PrefKeys.AspectGrid, !crop) }
        done += if (crop) "square thumbnails" else "Aspect Ratio Grid"
    }
    val excluded = values["excluded_folders"].orEmpty().split(",").map { it.trim() }.filter { it.startsWith("/") }
    if (excluded.isNotEmpty()) {
        val folders = excluded.map { relative(it).trimEnd('/') + "/" }
        val hide = store.library.filter { m -> val r = m.relative(); folders.any { r.startsWith(it) } }.map { it.id }
        if (hide.isNotEmpty()) withContext(Dispatchers.Main) { store.setHidden(hide, true) }
        done += "${hide.size} photos from ${excluded.size} excluded folder${if (excluded.size == 1) "" else "s"} moved to Hidden"
    }
    if (done.isEmpty()) "Nothing in that file has a match in Photos."
    else "Imported: " + done.joinToString(", ") + ". Close and reopen Photos to see the grid size."
}

/** Settings group: Import Favorites… and Import Settings… from Fossify Gallery. */
@Composable
fun FossifyImportGroup() {
    val ctx = LocalContext.current
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    val favs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { result = runCatching { importFavorites(ctx, store, uri) }.getOrElse { "Couldn't read that file." } }
    }
    val sets = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { result = runCatching { importSettings(ctx, store, uri) }.getOrElse { "Couldn't read that file." } }
    }
    SettingsGroup(
        header = "Import from Fossify Gallery",
        footer = "In Fossify Gallery, open Settings and use Export favorites and Export settings, then pick those files here.",
    ) {
        SettingsRow("Import Favorites…") { favs.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }
        SettingsRow("Import Settings…", last = true) { sets.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }
    }
    result?.let { msg ->
        AlertDialog(
            onDismissRequest = { result = null },
            confirmButton = { TextButton(onClick = { result = null }) { Text("OK") } },
            title = { Text("Fossify Gallery") },
            text = { Text(msg) },
        )
    }
}

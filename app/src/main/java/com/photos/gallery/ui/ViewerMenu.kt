package com.photos.gallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photos.gallery.data.Media

/** The ••• button at the top of the photo viewer, like iOS: Copy, Duplicate, Hide, Copy / Move to Album, Use as. */
@Composable
fun ViewerMoreButton(m: Media, bg: Color, border: Color, fg: Color, onAlbum: (move: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val store = LocalStore.current
    val actions = LocalActions.current
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(46.dp).shadow(10.dp, CircleShape, clip = false).clip(CircleShape)
                .background(bg).border(0.5.dp, border, CircleShape).tap { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(IosIcons.More, null, tint = fg, modifier = Modifier.size(24.dp))
        }
        IosPopupMenu(
            open, { open = false },
            buildList {
                add(PopupItem("Copy", IosIcons.Duplicate) {
                    runCatching {
                        ctx.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newUri(ctx.contentResolver, if (m.isVideo) "Video" else "Photo", m.uri))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }
                })
                add(PopupItem("Duplicate", IosIcons.Albums) { actions.duplicate(listOf(m)) })
                add(PopupItem("Hide", IosIcons.EyeSlash) {
                    store.setHidden(listOf(m.id), true)
                    Toast.makeText(ctx, "Hidden. Find it in the Hidden album.", Toast.LENGTH_SHORT).show()
                })
                add(PopupItem("Copy to Album…", IosIcons.Albums, group = true) { onAlbum(false) })
                add(PopupItem("Move to Album…", IosIcons.Folder) { onAlbum(true) })
                add(PopupItem(if (m.isVideo) "Open With…" else "Use As…", IosIcons.Share, group = true) {
                    val i = if (m.isVideo) Intent(Intent.ACTION_VIEW) else Intent(Intent.ACTION_ATTACH_DATA)
                    i.setDataAndType(m.uri, m.mime.ifBlank { if (m.isVideo) "video/*" else "image/*" })
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { ctx.startActivity(Intent.createChooser(i, null)) }
                })
            },
        )
    }
}

/** Choose an album (folder) to copy or move into, or make a new one. */
@Composable
fun AlbumPicker(move: Boolean, items: List<Media>, onDone: () -> Unit) {
    val store = LocalStore.current
    val actions = LocalActions.current
    val c = LocalIos.current
    var naming by remember { mutableStateOf(false) }
    val here = items.map { it.relPath }.toSet()
    val albums = store.buckets.filter { b -> b.items.isNotEmpty() && b.items.first().relPath !in here || !move }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
        Text(
            if (move) "Move to Album" else "Copy to Album",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
        )
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            item {
                PickerRow(null, "New Album…", null) { naming = true }
            }
            items(albums, key = { it.id }) { b ->
                PickerRow(b.items.firstOrNull(), b.name, b.items.size) {
                    val rel = b.items.first().relPath
                    if (move) actions.moveTo(items, rel, b.name) else actions.copyTo(items, rel, b.name)
                    onDone()
                }
            }
        }
    }
    if (naming) {
        NewAlbumDialog(onCancel = { naming = false }) { name ->
            naming = false
            val rel = "Pictures/$name/"
            if (move) actions.moveTo(items, rel, name) else actions.copyTo(items, rel, name)
            onDone()
        }
    }
}

@Composable
private fun PickerRow(cover: Media?, title: String, count: Int?, onClick: () -> Unit) {
    val c = LocalIos.current
    Row(
        Modifier.fillMaxWidth().tap(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(c.fill), contentAlignment = Alignment.Center) {
            if (cover != null) Thumb(cover, Modifier.fillMaxSize(), 192)
            else Icon(IosIcons.Albums, null, tint = c.label, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (count != null) Text(Fmt.num(count), fontSize = 14.sp, color = c.secondary)
        }
    }
}

@Composable
private fun NewAlbumDialog(onCancel: () -> Unit, onCreate: (String) -> Unit) {
    val c = LocalIos.current
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.width(290.dp).clip(RoundedCornerShape(16.dp)).background(if (c.dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F2)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("New Album", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label, modifier = Modifier.padding(top = 18.dp))
            Text("Enter a name for this album.", fontSize = 13.sp, color = c.label, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            BasicTextField(
                name, { name = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = c.label),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(7.dp))
                    .background(if (c.dark) Color(0xFF1C1C1E) else Color.White).padding(10.dp).focusRequester(focus),
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
            Row(Modifier.height(46.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().tap(onClick = onCancel), contentAlignment = Alignment.Center) {
                    Text("Cancel", fontSize = 17.sp, color = c.label)
                }
                Box(Modifier.width(0.5.dp).fillMaxHeight().background(c.separator))
                Box(Modifier.weight(1f).fillMaxHeight().tap(enabled = clean.isNotEmpty()) { onCreate(clean) }, contentAlignment = Alignment.Center) {
                    Text("Save", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (clean.isNotEmpty()) c.label else c.secondary)
                }
            }
        }
    }
}

package com.photos.gallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.photos.gallery.data.ImageLoader
import com.photos.gallery.data.Media
import com.photos.gallery.data.rememberThumbnail

private object FullWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset.Zero
}

/**
 * The iPhone long-press menu for a photo: the photo lifts out large over a dimmed screen,
 * with Copy · Duplicate · Hide, Share, Favorite, Add to Album, Edit and Delete below it.
 */
@Composable
fun PhotoContextMenu(m: Media, onDismiss: () -> Unit, onAddTo: () -> Unit) {
    val ctx = LocalContext.current
    val store = LocalStore.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val c = LocalIos.current
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    var closing by remember { mutableStateOf(false) }
    fun close(then: () -> Unit = {}) {
        if (closing) return
        closing = true
        shown.targetState = false
        onDismiss()
        then()
    }
    val thumb = rememberThumbnail(m.uri, 1080)
    val cfg = LocalConfiguration.current
    val maxW = (cfg.screenWidthDp - 48).coerceAtMost(520).dp
    val maxH = (cfg.screenHeightDp * 0.42f).dp
    val ratio = when {
        thumb != null && thumb.height > 0 -> thumb.width.toFloat() / thumb.height
        m.width > 0 && m.height > 0 -> m.width.toFloat() / m.height
        else -> 1f
    }.coerceIn(0.4f, 2.5f)
    val fav = m.id in store.favorites

    Popup(popupPositionProvider = FullWindow, onDismissRequest = { close() }, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { close() } }) {
            AnimatedVisibility(shown, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (c.dark) 0.62f else 0.35f)))
            }
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 24.dp).statusBarsPadding().navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    shown,
                    enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f), initialScale = 0.86f) + fadeIn(tween(140)),
                    exit = scaleOut(tween(140), targetScale = 0.92f) + fadeOut(tween(140)),
                ) {
                    // The lifted photo.
                    Box(
                        Modifier.widthIn(max = maxW).heightIn(max = maxH).aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                            .shadow(30.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(c.placeholder)
                            .pointerInput(Unit) { detectTapGestures { close { nav.push(Route.Viewer(com.photos.gallery.data.AlbumRef.All, m.id)) } } },
                    ) {
                        if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Spacer(Modifier.height(14.dp))
                AnimatedVisibility(
                    shown,
                    enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 480f), initialScale = 0.8f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)) + fadeIn(tween(160)),
                    exit = scaleOut(tween(130), targetScale = 0.9f) + fadeOut(tween(130)),
                ) {
                    MenuCard {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            TopAction("Copy", CopyIcon, Modifier.weight(1f)) {
                                close {
                                    runCatching {
                                        ctx.getSystemService(ClipboardManager::class.java)
                                            .setPrimaryClip(ClipData.newUri(ctx.contentResolver, "Photo", m.uri))
                                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            TopAction("Duplicate", IosIcons.Duplicate, Modifier.weight(1f)) { close { actions.duplicate(listOf(m)) } }
                            TopAction("Hide", IosIcons.EyeSlash, Modifier.weight(1f)) {
                                close {
                                    store.setHidden(listOf(m.id), true)
                                    Toast.makeText(ctx, "Hidden. Find it in the Hidden album.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        Band()
                        MenuRow("Share", IosIcons.Share) { close { actions.share(listOf(m)) } }
                        Line()
                        MenuRow(if (fav) "Unfavorite" else "Favorite", if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder) {
                            close { store.toggleFavorite(listOf(m.id)) }
                        }
                        Band()
                        MenuRow("Add to Album", IosIcons.Albums, chevron = true) { close { onAddTo() } }
                        if (!m.isVideo && !m.isAnimated) {
                            Line()
                            MenuRow("Edit", IosIcons.Sliders) { close { nav.push(Route.Editor(m)) } }
                        }
                        Band()
                        MenuRow("Delete", IosIcons.Trash, color = c.red) { close { actions.trash(listOf(m)) } }
                    }
                }
            }
        }
    }
}

/** The album picker for "Add to Album", as a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToAlbumSheet(m: Media, onDone: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDone, containerColor = LocalIos.current.grouped) {
        AlbumPicker(false, listOf(m), onDone)
    }
}

@Composable
private fun MenuCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalIos.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.width(270.dp).shadow(24.dp, shape, clip = false).clip(shape)
            .background(if (c.dark) Color(0xF52A2A2D) else Color(0xFAFFFFFF))
            .border(0.5.dp, if (c.dark) Color(0x26FFFFFF) else Color(0x14000000), shape)
            .pointerInput(Unit) { detectTapGestures { } },
        content = content,
    )
}

@Composable
private fun TopAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalIos.current
    Column(modifier.tap(onClick = onClick).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c.label, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, color = c.label)
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, color: Color? = null, chevron: Boolean = false, onClick: () -> Unit) {
    val c = LocalIos.current
    val col = color ?: c.label
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).tap(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = col, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 17.sp, color = col, modifier = Modifier.weight(1f))
        if (chevron) Icon(IosIcons.ChevronRight, null, tint = c.secondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Band() {
    val c = LocalIos.current
    Box(Modifier.fillMaxWidth().height(7.dp).background(if (c.dark) Color(0x14FFFFFF) else Color(0x0F000000)))
}

@Composable
private fun Line() {
    val c = LocalIos.current
    Box(Modifier.padding(start = 55.dp).fillMaxWidth().height(0.5.dp).background(if (c.dark) Color(0x1FFFFFFF) else Color(0x14000000)))
}

/** A page with a folded corner and a plus: the Copy icon. */
private val CopyIcon: ImageVector by lazy {
    ImageVector.Builder("copy", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            androidx.compose.ui.graphics.vector.PathParser()
                .parsePathString("M14 3 H7 a2 2 0 0 0 -2 2 V19 a2 2 0 0 0 2 2 H17 a2 2 0 0 0 2 -2 V8 Z M14 3 V8 H19").toNodes(),
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White), strokeLineWidth = 1.7f,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
    }.build()
}

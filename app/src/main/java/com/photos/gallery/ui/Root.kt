package com.photos.gallery.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photos.gallery.data.*
import kotlinx.coroutines.launch

fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** 0 = no access, 1 = limited (user-selected), 2 = full. */
fun accessLevel(ctx: Context): Int {
    fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= 33) {
        when {
            granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO) -> 2
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> 1
            else -> 0
        }
    } else {
        if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) 2 else 0
    }
}

@Composable
fun PhotosRoot(store: PhotosStore, openId: Long? = null, openUri: android.net.Uri? = null, onOpenFailed: () -> Unit = {}, onOpenedClosed: () -> Unit = {}) {
    val ctx = LocalContext.current
    var access by remember { mutableIntStateOf(accessLevel(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = accessLevel(ctx)
        store.requestReload()
    }
    LaunchedEffect(store.resumeTick) { access = accessLevel(ctx) }
    LaunchedEffect(access, store.reloadTick, store.resumeTick) { if (access > 0) store.reload() }

    DisposableEffect(access) {
        if (access == 0) return@DisposableEffect onDispose { }
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ store.requestReload() }, 700)
            }
        }
        ctx.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        ctx.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose {
            ctx.contentResolver.unregisterContentObserver(observer)
            handler.removeCallbacksAndMessages(null)
        }
    }

    if (access == 0) {
        PermissionScreen { permLauncher.launch(mediaPermissions()) }
    } else {
        MainScaffold(store, limited = access == 1, openId, openUri, onOpenFailed, onOpenedClosed) { permLauncher.launch(mediaPermissions()) }
    }
}

@Composable
private fun MainScaffold(
    store: PhotosStore,
    limited: Boolean,
    openId: Long?,
    openUri: android.net.Uri?,
    onOpenFailed: () -> Unit,
    onOpenedClosed: () -> Unit,
    onManage: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val nav = remember { Nav(scope) }
    val actions = rememberMediaActions(store)
    var tab by rememberSaveable { mutableStateOf(PhotosTab.Library) }
    val ctx = LocalContext.current
    val lib = remember { LibraryUi(AppPrefs.get(ctx)) }
    val albumsState = rememberLazyListState()
    val c = LocalIos.current
    val updates = rememberUpdateCenter()
    LaunchedEffect(store.resumeTick) { updates.tick++; actions.resumed() }

    val tablet = isTablet()
    var paneAlbum by remember { mutableStateOf<AlbumRef?>(null) }
    BackHandler(enabled = nav.canPop) { nav.pop() }
    BackHandler(enabled = tablet && !nav.canPop && (paneAlbum != null || tab != PhotosTab.Library)) {
        paneAlbum = null
        tab = PhotosTab.Library
    }

    // Opened from another app (e.g. Files): show the photo inside its album so you can swipe through the folder.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(store.loaded, openId) {
        if (openId == null || opened || !store.loaded) return@LaunchedEffect
        val m = store.library.firstOrNull { it.id == openId }
        if (m == null) { onOpenFailed(); return@LaunchedEffect }
        opened = true
        nav.push(Route.Viewer(AlbumRef(Kind.Bucket, m.bucket, m.bucketId), m.id), instant = true)
    }
    if (openId != null && opened) {
        LaunchedEffect(nav.stack.isEmpty()) { if (nav.stack.isEmpty()) onOpenedClosed() }
    }

    CompositionLocalProvider(LocalNav provides nav, LocalActions provides actions, LocalUpdates provides updates) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
          if (openId != null) {
            // Opened from another app (the camera, Files…): show the photo at once instead of the
            // library, so nothing flashes while the library loads behind it.
            OpenCover(openUri)
            nav.stack.forEach { e -> key(e.id) { RouteHost(e, nav) } }
          } else if (tablet) {
            SplitLayout(
                sidebar = {
                    PhotosSidebar(
                        tab, paneAlbum,
                        onTab = { t ->
                            if (t == tab && paneAlbum == null) scope.launch {
                                when (t) {
                                    PhotosTab.Library -> lib.scrollToNewest()
                                    PhotosTab.Albums -> albumsState.animateScrollToItem(0)
                                    PhotosTab.Search -> Unit
                                }
                            }
                            lib.selection.clear()
                            nav.stack.filter { it.route.inPane() }.forEach { _ -> nav.pop() }
                            paneAlbum = null
                            tab = t
                        },
                        onAlbum = { ref ->
                            lib.selection.clear()
                            nav.stack.filter { it.route.inPane() }.forEach { _ -> nav.pop() }
                            paneAlbum = ref
                        },
                    )
                },
            ) {
                Box(Modifier.fillMaxSize()) {
                    androidx.compose.animation.Crossfade(paneAlbum to tab, animationSpec = tween(180), label = "pane") { (a, t) ->
                        CompositionLocalProvider(LocalPaneRoot provides true) {
                            if (a != null) AlbumScreen(a) { paneAlbum = null }
                            else when (t) {
                                PhotosTab.Library -> LibraryScreen(store, lib, limited, onManage)
                                PhotosTab.Albums -> AlbumsScreen(albumsState)
                                PhotosTab.Search -> SearchScreen()
                            }
                        }
                    }
                    if (!store.loaded) CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.secondary)
                    nav.stack.forEach { e -> if (e.route.inPane()) key(e.id) { RouteHost(e, nav) } }
                }
            }
            nav.stack.forEach { e -> if (!e.route.inPane()) key(e.id) { RouteHost(e, nav) } }
          } else {
            when (tab) {
                PhotosTab.Library -> LibraryScreen(store, lib, limited, onManage)
                PhotosTab.Albums -> AlbumsScreen(albumsState)
                PhotosTab.Search -> SearchScreen()
            }
            if (!(tab == PhotosTab.Library && lib.selection.active)) {
                FloatingTabBar(
                    current = tab,
                    onSelect = { t ->
                        if (t == tab) {
                            scope.launch {
                                when (t) {
                                    PhotosTab.Library -> lib.scrollToNewest()
                                    PhotosTab.Albums -> albumsState.animateScrollToItem(0)
                                    PhotosTab.Search -> Unit
                                }
                            }
                        }
                        lib.selection.clear()
                        tab = t
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            if (!store.loaded) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.secondary)
            }
            nav.stack.forEach { e -> key(e.id) { RouteHost(e, nav) } }
          }
            ActionCards()
            if (openId == null) AutoUpdatePrompt("Photos")
        }
    }
}

@Composable
private fun RouteHost(entry: NavEntry, nav: Nav) {
    val slide = entry.route is Route.Album || entry.route is Route.AllAlbums || entry.route is Route.Settings
    val progress = remember { Animatable(if (entry.instant) 1f else 0f) }
    LaunchedEffect(entry.closing) {
        if (entry.closing) progress.animateTo(0f, tween(220, easing = FastOutLinearInEasing))
        else progress.animateTo(1f, tween(if (slide) 420 else 280, easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)))
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val p = progress.value
            if (slide) {
                translationX = (1f - p) * size.width
            } else {
                alpha = p
                val s = 0.92f + 0.08f * p
                scaleX = s
                scaleY = s
            }
        }.blockTouches()
    ) {
        when (val r = entry.route) {
            is Route.Album -> AlbumScreen(r.ref) { nav.pop() }
            is Route.AllAlbums -> AllAlbumsScreen { nav.pop() }
            is Route.Viewer -> ViewerScreen(r.ref, r.startId) { nav.pop() }
            is Route.Story -> StoryScreen(r.memory) { nav.pop() }
            is Route.Editor -> EditorScreen(r.media) { nav.pop() }
            is Route.Settings -> PhotosSettingsScreen(r.about) { nav.pop() }
        }
    }
}

@Composable
private fun OpenCover(uri: android.net.Uri?) {
    val c = LocalIos.current
    Box(Modifier.fillMaxSize().background(c.bg)) {
        if (uri != null) {
            val img = com.photos.gallery.data.rememberThumbnail(uri, 512)
            if (img != null) {
                androidx.compose.foundation.Image(
                    img, null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppGlyph(96.dp)
        Spacer(Modifier.height(24.dp))
        Text("Welcome to Photos", style = IosType.largeTitle.copy(fontSize = 30.sp), color = c.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Allow access to your photos and videos to browse your library, relive memories and organize albums.",
            fontSize = 17.sp, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp),
        )
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(c.accent).tap(onClick = onRequest),
            contentAlignment = Alignment.Center,
        ) {
            Text("Continue", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Open Settings", color = c.label, fontSize = 15.sp,
            modifier = Modifier.tap {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                )
            }.padding(10.dp),
        )
    }
}

@Composable
fun AppGlyph(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.23f)).background(
            Brush.linearGradient(listOf(Color(0xFFFFB443), Color(0xFFFF5E7E), Color(0xFF8A5CFF)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(IosIcons.Logo, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

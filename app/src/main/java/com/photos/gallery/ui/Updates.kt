package com.photos.gallery.ui

// Shared between Photos and Files (copied into each app with its package name).

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// ---------- Preferences ----------

/** App-wide settings backed by SharedPreferences and observable from Compose. */
class AppPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = mutableStateOf(p.getBoolean(key, def))
    private fun put(key: String, v: Boolean) = p.edit().putBoolean(key, v).apply()

    /** 0 = follow system, 1 = light, 2 = dark */
    var appearance by mutableIntStateOf(p.getInt("appearance", 0))
        private set
    fun updateAppearance(v: Int) { appearance = v; p.edit().putInt("appearance", v).apply() }

    private val _autoUpdate = bool("autoUpdate", true)
    var autoUpdate: Boolean
        get() = _autoUpdate.value
        set(v) { _autoUpdate.value = v; put("autoUpdate", v) }

    var lastUpdateCheck: Long
        get() = p.getLong("lastUpdateCheck", 0)
        set(v) { p.edit().putLong("lastUpdateCheck", v).apply() }

    var skippedVersion: String
        get() = p.getString("skippedVersion", "") ?: ""
        set(v) { p.edit().putString("skippedVersion", v).apply() }

    fun int(key: String, def: Int) = p.getInt(key, def)
    fun putInt(key: String, v: Int) = p.edit().putInt(key, v).apply()

    private val flags = HashMap<String, MutableState<Boolean>>()
    fun flag(key: String, def: Boolean): Boolean = flags.getOrPut(key) { bool(key, def) }.value
    fun setFlag(key: String, v: Boolean) { flags.getOrPut(key) { bool(key, v) }.value = v; put(key, v) }

    companion object {
        @Volatile private var inst: AppPrefs? = null
        fun get(ctx: Context): AppPrefs = inst ?: synchronized(this) { inst ?: AppPrefs(ctx.applicationContext).also { inst = it } }
    }
}

// ---------- Updater ----------

object Updater {
    const val PHOTOS_PKG = "com.photos.gallery"
    const val FILES_PKG = "com.photos.files"

    /** Each app has its own GitHub repository, releases and website. */
    fun repo(pkg: String) = if (pkg == FILES_PKG) "shatheitguy/files-android" else "shatheitguy/photos-android"
    fun website(pkg: String) = if (pkg == FILES_PKG) "https://shatheitguy.github.io/files-android/" else "https://shatheitguy.github.io/photos-android/"
    fun source(pkg: String) = "https://github.com/${repo(pkg)}"

    fun assetName(pkg: String) = if (pkg == FILES_PKG) "Files.apk" else "Photos.apk"

    data class Asset(val url: String, val size: Long)
    data class Release(val version: String, val notes: String, val assets: Map<String, Asset>, val page: String)

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PhotosAndroid-Updater")
            setRequestProperty("Accept", "application/vnd.github+json")
        }

    suspend fun latest(pkg: String): Release = withContext(Dispatchers.IO) {
        val api = runCatching { latestFromApi(pkg) }
        api.getOrNull() ?: runCatching { latestFromWeb(pkg) }.getOrElse { throw friendly(api.exceptionOrNull() ?: it) }
    }

    private fun latestFromApi(pkg: String): Release {
        val c = open("https://api.github.com/repos/${repo(pkg)}/releases/latest")
        try {
            val code = c.responseCode
            if (code != 200) error("GitHub returned $code.")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val arr = o.optJSONArray("assets")
            val assets = HashMap<String, Asset>()
            if (arr != null) for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                assets[a.getString("name")] = Asset(a.getString("browser_download_url"), a.optLong("size"))
            }
            return Release(
                version = o.optString("tag_name").removePrefix("v").removePrefix("V"),
                notes = o.optString("body"),
                assets = assets,
                page = o.optString("html_url"),
            )
        } finally {
            c.disconnect()
        }
    }

    /** Fallback when api.github.com can't be reached (some networks and DNS filters block it). */
    private fun latestFromWeb(pkg: String): Release {
        val c = open("https://github.com/${repo(pkg)}/releases/latest")
        c.instanceFollowRedirects = false
        c.requestMethod = "HEAD"
        val location = try {
            c.responseCode
            c.getHeaderField("Location") ?: error("No release found.")
        } finally {
            c.disconnect()
        }
        val tag = location.substringAfterLast("/tag/", "").ifBlank { error("No release found.") }
        val name = assetName(pkg)
        val url = "https://github.com/${repo(pkg)}/releases/download/$tag/$name"
        val h = open(url)
        h.requestMethod = "HEAD"
        val size = try {
            if (h.responseCode !in 200..299) error("The update file is missing.")
            h.contentLengthLong
        } finally {
            h.disconnect()
        }
        return Release(
            version = tag.removePrefix("v").removePrefix("V"),
            notes = "",
            assets = mapOf(name to Asset(url, size.coerceAtLeast(0))),
            page = location,
        )
    }

    fun friendly(e: Throwable): Exception = Exception(
        when (e) {
            is java.net.UnknownHostException ->
                "Can't reach GitHub. Check that you're online. If you use Private DNS or an ad blocker, allow github.com."
            is java.net.SocketTimeoutException -> "GitHub took too long to answer. Try again."
            is javax.net.ssl.SSLException -> "A secure connection to GitHub couldn't be made. Check the phone's date and time."
            else -> e.message ?: "Couldn't check for updates."
        },
        e,
    )

    fun isNewer(remote: String, local: String?): Boolean {
        if (local == null) return true
        fun parts(v: String) = v.split('.', '-', ' ').mapNotNull { it.toIntOrNull() }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun installedVersion(ctx: Context, pkg: String): String? =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()

    suspend fun download(ctx: Context, asset: Asset, name: String, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.startsWith(name) }?.forEach { it.delete() }
        val part = File(dir, "$name.part")
        val c = open(asset.url)
        c.setRequestProperty("Accept", "application/octet-stream")
        try {
            if (c.responseCode !in 200..299) error("Download failed (${c.responseCode}).")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: asset.size
            c.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - last > 128 * 1024) {
                            last = done
                            val f = done.toFloat() / total
                            withContext(Dispatchers.Main) { progress(f.coerceIn(0f, 1f)) }
                        }
                    }
                }
            }
            if (asset.size > 0 && part.length() != asset.size) error("The download was incomplete. Try again.")
            val apk = File(dir, name)
            check(part.renameTo(apk)) { "Couldn't save the update." }
            apk
        } finally {
            c.disconnect()
        }
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun askInstallPermission(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
        }
    }

    fun install(ctx: Context, apk: File) {
        val auth = if (ctx.packageName == FILES_PKG) "com.photos.files.provider" else "${ctx.packageName}.updates"
        val uri = FileProvider.getUriForFile(ctx, auth, apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "Couldn't open the installer.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openUrl(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(ctx, "No browser found.", Toast.LENGTH_SHORT).show() }
    }

    fun launch(ctx: Context, pkg: String) {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { ctx.startActivity(it) } }
    }
}

/** Keeps the result of the last update check and any downloads in progress. */
class UpdateCenter(private val ctx: Context, private val scope: CoroutineScope) {
    /** Latest release per app package. */
    val releases = mutableStateMapOf<String, Updater.Release>()
    val release: Updater.Release? get() = releases[ctx.packageName]
    var checking by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val progress = mutableStateMapOf<String, Float>()
    /** Downloaded APKs waiting to be installed. */
    val ready = mutableStateMapOf<String, File>()
    /** The update card currently shown, if any. */
    var prompt by mutableStateOf<Updater.Release?>(null)
    /** Bumped on resume so installed versions are re-read. */
    var tick by mutableIntStateOf(0)

    val selfPkg: String = ctx.packageName
    fun installed(pkg: String): String? { tick; return Updater.installedVersion(ctx, pkg) }

    fun check(then: (Updater.Release) -> Unit = {}) {
        if (checking) return
        checking = true
        error = null
        scope.launch {
            runCatching { Updater.latest(selfPkg) }
                .onSuccess { releases[selfPkg] = it; AppPrefs.get(ctx).lastUpdateCheck = System.currentTimeMillis() }
                .onFailure { error = it.message ?: "Couldn't check for updates." }
            // The other app's release (for "Our Apps"); failures here are quiet.
            val other = if (selfPkg == Updater.FILES_PKG) Updater.PHOTOS_PKG else Updater.FILES_PKG
            runCatching { Updater.latest(other) }.onSuccess { releases[other] = it }
            checking = false
            releases[selfPkg]?.let(then)
        }
    }

    fun hasUpdate(pkg: String): Boolean {
        val r = releases[pkg] ?: return false
        val v = installed(pkg) ?: return false
        return Updater.assetName(pkg) in r.assets && Updater.isNewer(r.version, v)
    }

    fun available(pkg: String): Boolean = releases[pkg]?.assets?.containsKey(Updater.assetName(pkg)) == true

    /** Downloads the latest APK for [pkg] and opens the system installer. */
    fun getApp(pkg: String) {
        val r = releases[pkg] ?: return check { if (releases[pkg] != null) getApp(pkg) }
        val asset = r.assets[Updater.assetName(pkg)] ?: run {
            Toast.makeText(ctx, "This app isn't in the latest release.", Toast.LENGTH_SHORT).show(); return
        }
        if (pkg in progress) return
        if (!Updater.canInstall(ctx)) {
            Toast.makeText(ctx, "Allow installing apps, then tap again.", Toast.LENGTH_LONG).show()
            Updater.askInstallPermission(ctx)
            return
        }
        ready[pkg]?.let { f ->
            if (f.exists() && f.length() == asset.size) { Updater.install(ctx, f); return }
            ready.remove(pkg)
        }
        progress[pkg] = 0f
        scope.launch {
            runCatching { Updater.download(ctx, asset, Updater.assetName(pkg)) { progress[pkg] = it } }
                .onSuccess { ready[pkg] = it; progress[pkg] = 1f; kotlinx.coroutines.delay(350); Updater.install(ctx, it) }
                .onFailure { Toast.makeText(ctx, Updater.friendly(it).message ?: "Download failed.", Toast.LENGTH_LONG).show() }
            progress.remove(pkg)
        }
    }
}

val LocalUpdates = staticCompositionLocalOf<UpdateCenter> { error("No update center") }

@Composable
fun rememberUpdateCenter(): UpdateCenter {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { UpdateCenter(ctx, scope) }
}

/** Checks each time the app opens and offers the update for this app. */
@Composable
fun AutoUpdatePrompt(appName: String) {
    val ctx = LocalContext.current
    val updates = LocalUpdates.current
    val prefs = AppPrefs.get(ctx)
    LaunchedEffect(Unit) {
        // Every time the app opens: if a newer version is out, offer it.
        if (!prefs.autoUpdate) return@LaunchedEffect
        updates.check { r -> if (updates.hasUpdate(updates.selfPkg)) updates.prompt = r }
    }
    val r = updates.prompt ?: return
    UpdateCard(appName, r) { updates.prompt = null }
}

/** The update card: app icon with a progress ring, release notes, and a button that fills as it downloads. */
@Composable
fun UpdateCard(appName: String, r: Updater.Release, onDismiss: () -> Unit) {
    val c = LocalIos.current
    val updates = LocalUpdates.current
    val pkg = updates.selfPkg
    val raw = updates.progress[pkg]
    val ready = updates.ready[pkg] != null
    val downloading = raw != null
    val progress by animateFloatAsState(raw ?: if (ready) 1f else 0f, tween(260), label = "dl")
    val apkSize = r.assets[Updater.assetName(pkg)]?.size ?: 0L
    val pulse = rememberInfiniteTransition(label = "pulse")
    val breathe by pulse.animateFloat(1f, 1.06f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val spin by pulse.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "s")
    val gradient = listOf(Color(0xFFFFB443), Color(0xFFFF5E7E), Color(0xFF8A5CFF), Color(0xFFFFB443))
    val cardBg = if (c.dark) Color(0xFF1C1C1E) else Color.White

    Dialog(onDismissRequest = { if (!downloading) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 28.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(cardBg)
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                // Soft glow
                Box(
                    Modifier.size(120.dp).graphicsLayer { alpha = if (downloading) 0.55f else 0.3f; scaleX = breathe; scaleY = breathe }
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFFF5E7E).copy(alpha = 0.6f), Color.Transparent))),
                )
                // Progress ring
                Canvas(Modifier.size(124.dp)) {
                    val stroke = 6.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = if (c.dark) Color(0xFF2C2C2E) else Color(0xFFEDEDF0), startAngle = 0f, sweepAngle = 360f,
                        useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
                    )
                    if (downloading || ready) {
                        rotate(if (downloading && progress < 0.02f) spin else 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(gradient),
                                startAngle = -90f,
                                sweepAngle = if (downloading && progress < 0.02f) 60f else 360f * progress,
                                useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
                Box(Modifier.graphicsLayer { val k = if (downloading) breathe else 1f; scaleX = k; scaleY = k }) {
                    AppGlyphSmall(pkg, 84.dp)
                }
                if (ready) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(30.dp).clip(CircleShape).background(Color(0xFF34C759))
                            .border(3.dp, cardBg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(14.dp)) {
                            val w = size.width
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(w * 0.15f, w * 0.55f); lineTo(w * 0.42f, w * 0.8f); lineTo(w * 0.88f, w * 0.22f)
                            }
                            drawPath(path, Color.White, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    ready -> "Ready to Install"
                    downloading -> "Downloading…"
                    else -> "Update Available"
                },
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.label,
            )
            Spacer(Modifier.height(4.dp))
            val current = updates.installed(pkg) ?: "?"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$appName $current", fontSize = 14.sp, color = c.secondary)
                Text("  →  ", fontSize = 14.sp, color = c.secondary)
                Box(Modifier.clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 2.dp)) {
                    Text(r.version, fontSize = 14.sp, color = c.label, fontWeight = FontWeight.SemiBold)
                }
                if (apkSize > 0) Text("  ·  ${fmtBytes(apkSize)}", fontSize = 14.sp, color = c.secondary)
            }
            val notes = r.notes.lines().map { it.trim().removePrefix("-").removePrefix("*").trim().replace("**", "") }.filter { it.isNotBlank() }
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 150.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (c.dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                        .verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("What's New", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.label)
                    notes.take(12).forEach { n ->
                        Row {
                            Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(c.accent))
                            Spacer(Modifier.width(8.dp))
                            Text(n, fontSize = 13.sp, color = c.secondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            // The button fills up as the download progresses.
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(CircleShape)
                    .background(if (downloading) (if (c.dark) Color(0xFF2C2C2E) else Color(0xFFEDEDF0)) else Color.Transparent)
                    .tap(enabled = !downloading) { updates.getApp(pkg) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.align(Alignment.CenterStart).fillMaxHeight()
                        .fillMaxWidth(if (downloading) progress.coerceIn(0.04f, 1f) else 1f)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFF8A5B), Color(0xFFFF5E7E), Color(0xFF8A5CFF)))),
                )
                Text(
                    when {
                        downloading -> "${(progress * 100).toInt()}%" + if (apkSize > 0) "  ·  ${fmtBytes((apkSize * progress).toLong())} of ${fmtBytes(apkSize)}" else ""
                        ready -> "Install Now"
                        else -> "Update Now"
                    },
                    color = if (downloading && progress < 0.45f) c.label else Color.White,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (downloading) "You can keep using the app" else "Later",
                color = if (downloading) c.secondary else c.label, fontSize = 16.sp,
                modifier = Modifier.tap { onDismiss() }.padding(10.dp),
            )
        }
    }
}

private fun fmtBytes(b: Long): String = when {
    b >= 1_000_000 -> "%.1f MB".format(b / 1_000_000.0)
    b >= 1_000 -> "%.0f KB".format(b / 1_000.0)
    else -> "$b bytes"
}

// ---------- Settings building blocks ----------

@Composable
fun SettingsPage(title: String, onBack: () -> Unit, scrollToEnd: Boolean = false, content: LazyListScope.() -> Unit) {
    val c = LocalIos.current
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToEnd) {
        if (scrollToEnd) {
            kotlinx.coroutines.delay(380)
            listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }
    val bg = if (c.dark) Color.Black else Color(0xFFF2F2F7)
    Box(Modifier.fillMaxSize().background(bg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            state = listState,
            contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp),
        ) {
            item {
                Text(
                    title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.label, letterSpacing = (-0.5).sp,
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 8.dp),
                )
            }
            content()
        }
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.85f), bg.copy(alpha = 0f))))
                .statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp).height(52.dp),
        ) {
            val dark = c.dark
            Box(
                Modifier.align(Alignment.CenterStart).size(46.dp).clip(CircleShape)
                    .background(if (dark) Color(0xF22A2A2D) else Color(0xF8FFFFFF)).tap(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IosIcons.ChevronLeft, null, tint = c.label, modifier = Modifier.size(23.dp))
            }
        }
    }
}

@Composable
fun SettingsGroup(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalIos.current
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 22.dp)) {
        if (header != null) {
            Text(header.uppercase(), fontSize = 13.sp, color = c.secondary, modifier = Modifier.padding(start = 36.dp, bottom = 6.dp))
        }
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (c.dark) Color(0xFF1C1C1E) else Color.White),
            content = content,
        )
        if (footer != null) {
            Text(footer, fontSize = 13.sp, color = c.secondary, modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 6.dp))
        }
    }
}

@Composable
private fun RowDivider(inset: Dp = 16.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(LocalIos.current.separator))
}

@Composable
fun SettingsToggle(title: String, checked: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    val c = LocalIos.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).tap { onChange(!checked) }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 17.sp, color = c.label, modifier = Modifier.weight(1f))
            Switch(
                checked, onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759), checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White, uncheckedTrackColor = if (c.dark) Color(0xFF39393D) else Color(0xFFE9E9EA),
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
        if (!last) RowDivider()
    }
}

@Composable
fun SettingsRow(
    title: String,
    value: String? = null,
    titleColor: Color? = null,
    chevron: Boolean = true,
    last: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalIos.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).tap(enabled = onClick != null) { onClick?.invoke() }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 17.sp, color = titleColor ?: c.label, modifier = Modifier.weight(1f))
            if (value != null) Text(value, fontSize = 17.sp, color = c.secondary, maxLines = 1)
            trailing?.invoke()
            if (chevron && onClick != null) {
                Spacer(Modifier.width(8.dp))
                Icon(IosIcons.ChevronRight, null, tint = c.tertiary, modifier = Modifier.size(14.dp))
            }
        }
        if (!last) RowDivider()
    }
}

@Composable
fun AppearanceRow(prefs: AppPrefs) {
    val c = LocalIos.current
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Automatic", "Light", "Dark").forEachIndexed { i, label ->
            val sel = prefs.appearance == i
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) c.accent.copy(alpha = 0.14f) else Color.Transparent)
                    .tap { prefs.updateAppearance(i) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val (top, bottom) = when (i) {
                    1 -> Color.White to Color.White
                    2 -> Color(0xFF1C1C1E) to Color(0xFF1C1C1E)
                    else -> Color.White to Color(0xFF1C1C1E)
                }
                Box(
                    Modifier.size(width = 46.dp, height = 80.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.horizontalGradient(0.5f to top, 0.5f to bottom))
                        .then(if (sel) Modifier.background(Color.Transparent) else Modifier),
                ) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(width = 26.dp, height = 8.dp)
                            .clip(CircleShape).background(Color(0xFF8E8E93).copy(alpha = 0.5f)),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(label, fontSize = 14.sp, color = c.label, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun AppGlyphSmall(pkg: String, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.23f))
            .background(Brush.linearGradient(listOf(Color(0xFFFFB443), Color(0xFFFF5E7E), Color(0xFF8A5CFF)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (pkg == Updater.FILES_PKG) IosIcons.Folder else IosIcons.Logo, null,
            tint = Color.White, modifier = Modifier.size(size * 0.6f),
        )
    }
}

/** "Our Apps": shows each app with Open / Update / Get. */
@Composable
fun OurAppRow(name: String, pkg: String, blurb: String, last: Boolean = false) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val updates = LocalUpdates.current
    val installed = updates.installed(pkg)
    val update = updates.hasUpdate(pkg)
    val p = updates.progress[pkg]
    val isSelf = pkg == updates.selfPkg
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppGlyphSmall(pkg)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, color = c.label, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        installed == null -> blurb
                        update -> "Version ${updates.releases[pkg]?.version} available · you have $installed"
                        else -> "Version $installed"
                    },
                    fontSize = 13.sp, color = c.secondary, maxLines = 2,
                )
            }
            Spacer(Modifier.width(10.dp))
            if (p != null) {
                CircularProgressIndicator(progress = { p }, modifier = Modifier.size(28.dp), color = c.label, strokeWidth = 3.dp, trackColor = c.fill)
            } else {
                val (label, action) = when {
                    installed == null -> "GET" to { updates.getApp(pkg) }
                    update -> "UPDATE" to { updates.getApp(pkg) }
                    isSelf -> null to {}
                    else -> "OPEN" to { Updater.launch(ctx, pkg) }
                }
                if (label != null) {
                    Box(
                        Modifier.clip(CircleShape).background(if (c.dark) Color(0xFF2C2C2E) else Color(0xFFEEEEF0))
                            .tap(onClick = action).padding(horizontal = 18.dp, vertical = 6.dp),
                    ) {
                        Text(label, color = c.label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (!last) RowDivider(72.dp)
    }
}

/** Software Update section, shared by both apps' settings. */
fun LazyListScope.commonSettings(appName: String, prefs: AppPrefs) {
    item {
        val updates = LocalUpdates.current
        val c = LocalIos.current
        val r = updates.release
        val self = updates.selfPkg
        SettingsGroup(
            header = "Software Update",
            footer = "$appName ${updates.installed(self) ?: ""}. Updates download from GitHub and Android asks you to confirm the install.",
        ) {
            SettingsRow(
                title = when {
                    updates.checking -> "Checking for Updates…"
                    updates.hasUpdate(self) -> "Update to $appName ${r?.version}"
                    else -> "Check for Updates"
                },
                titleColor = c.label,
                value = when {
                    updates.checking -> null
                    updates.error != null -> "Failed"
                    r != null && !updates.hasUpdate(self) -> "Up to date"
                    else -> null
                },
                chevron = false,
                trailing = {
                    val p = updates.progress[self]
                    if (updates.checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.secondary)
                    else if (p != null) Text("${(p * 100).toInt()}%", fontSize = 15.sp, color = c.secondary)
                },
            ) {
                if (updates.hasUpdate(self)) updates.prompt = r else updates.check()
            }
            updates.error?.let { e ->
                Text(e, fontSize = 13.sp, color = c.red, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            SettingsToggle("Check Automatically", prefs.autoUpdate, last = true) { prefs.autoUpdate = it }
        }
        LaunchedEffect(Unit) { if (updates.release == null && !updates.checking) updates.check() }
    }
}

@Composable
fun IosDropdown(expanded: Boolean, onDismiss: () -> Unit, items: List<Triple<String, ImageVector, () -> Unit>>) {
    IosPopupMenu(expanded, onDismiss, items.map { (label, icon, run) -> PopupItem(label, icon, run = run) })
}

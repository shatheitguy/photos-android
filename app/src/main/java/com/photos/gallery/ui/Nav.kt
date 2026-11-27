package com.photos.gallery.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.photos.gallery.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalStore = staticCompositionLocalOf<PhotosStore> { error("No store") }
val LocalNav = staticCompositionLocalOf<Nav> { error("No nav") }
val LocalActions = staticCompositionLocalOf<MediaActions> { error("No actions") }

sealed interface Route {
    data class Album(val ref: AlbumRef) : Route
    data class Viewer(val ref: AlbumRef, val startId: Long) : Route
    data class Story(val memory: Memory) : Route
    data class Editor(val media: Media) : Route
    data object AllAlbums : Route
    data class Settings(val about: Boolean = false) : Route
}

class NavEntry(val id: Int, val route: Route) {
    var closing by mutableStateOf(false)
}

class Nav(private val scope: CoroutineScope) {
    val stack = mutableStateListOf<NavEntry>()
    private var nextId = 0
    val canPop: Boolean get() = stack.any { !it.closing }

    fun push(route: Route) {
        stack.add(NavEntry(nextId++, route))
    }

    fun pop() {
        val e = stack.lastOrNull { !it.closing } ?: return
        e.closing = true
        scope.launch {
            delay(240)
            stack.remove(e)
        }
    }
}

class MediaActions(
    private val ctx: Context,
    private val store: PhotosStore,
    private val launchSender: (IntentSender) -> Unit,
    private val launchUnlock: (Intent) -> Unit,
) {
    var onUnlocked: (() -> Unit)? = null
    var unlocked = false

    /** Runs when Android's permission prompt (write / move) comes back approved. */
    var afterSender: (() -> Unit)? = null
    private val io = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    private fun say(msg: String) = android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()

    fun duplicate(items: List<Media>) {
        if (items.isEmpty()) return
        io.launch {
            val n = items.groupBy { it.relPath }.entries.sumOf { (rel, list) -> store.copyTo(list, rel.ifBlank { "Pictures/" }) }
            store.requestReload()
            say(if (n > 0) "Duplicated" else "Couldn't duplicate")
        }
    }

    fun copyTo(items: List<Media>, relPath: String, album: String) {
        if (items.isEmpty()) return
        io.launch {
            val n = store.copyTo(items, relPath)
            store.requestReload()
            say(if (n > 0) "Copied to $album" else "Couldn't copy")
        }
    }

    fun moveTo(items: List<Media>, relPath: String, album: String) {
        if (items.isEmpty()) return
        val go = {
            io.launch {
                val n = store.moveNow(items, relPath)
                store.requestReload()
                say(if (n > 0) "Moved to $album" else "Couldn't move")
            }
            Unit
        }
        val sender = runCatching { store.writeRequest(items) }.getOrNull()
        if (sender == null) { go(); return }
        perform {
            afterSender = go
            launchSender(sender)
        }
    }

    /** An iOS-style confirmation card waiting for an answer. */
    class Confirm(val message: String, val button: String, val destructive: Boolean, val run: () -> Unit)
    var confirm by mutableStateOf<Confirm?>(null)

    /** Shown once: offers media management so Android stops asking "Allow" on every delete. */
    var askAccess by mutableStateOf<(() -> Unit)?>(null)
    private var waitingForAccess: (() -> Unit)? = null
    private var askedThisSession = false

    fun canManageMedia(): Boolean = Build.VERSION.SDK_INT >= 31 && android.provider.MediaStore.canManageMedia(ctx)

    fun openMediaAccess() {
        if (Build.VERSION.SDK_INT < 31) return
        runCatching {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_MANAGE_MEDIA, android.net.Uri.fromParts("package", ctx.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Runs a delete or recover; the first time, offers media management so there's no extra Android prompt. */
    private fun perform(run: () -> Unit) {
        if (Build.VERSION.SDK_INT < 31 || canManageMedia() || askedThisSession) return run()
        askedThisSession = true
        askAccess = run
    }

    fun accessTurnOn() {
        waitingForAccess = askAccess
        askAccess = null
        openMediaAccess()
    }

    fun accessNotNow() {
        val r = askAccess
        askAccess = null
        r?.invoke()
    }

    /** Back from Settings: finish the delete that was waiting. */
    fun resumed() {
        val r = waitingForAccess ?: return
        waitingForAccess = null
        r()
    }

    private fun noun(items: List<Media>): String = when {
        items.size > 1 -> "${items.size} Items"
        items[0].isVideo -> "Video"
        else -> "Photo"
    }

    fun share(items: List<Media>) {
        if (items.isEmpty()) return
        val intent = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items[0].mime.ifBlank { if (items[0].isVideo) "video/*" else "image/*" }
                putExtra(Intent.EXTRA_STREAM, items[0].uri)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = when {
                    items.all { it.isVideo } -> "video/*"
                    items.none { it.isVideo } -> "image/*"
                    else -> "*/*"
                }
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            ctx.startActivity(Intent.createChooser(intent, null))
        } catch (_: Exception) {
        }
    }

    fun trash(items: List<Media>, then: () -> Unit = {}) {
        if (items.isEmpty()) return
        val msg = if (items.size == 1) "This ${noun(items).lowercase()} will be deleted from the library. It will be in Recently Deleted for 30 days."
        else "These items will be deleted from the library. They will be in Recently Deleted for 30 days."
        confirm = Confirm(msg, "Delete ${noun(items)}", true) { then(); perform { trashNow(items) } }
    }

    private fun trashNow(items: List<Media>) {
        try {
            val sender = if (Build.VERSION.SDK_INT >= 30) store.trashRequest(items, true) else store.deleteRequest(items)
            if (sender != null) launchSender(sender) else store.requestReload()
        } catch (_: Exception) {
        }
    }

    fun restore(items: List<Media>, then: () -> Unit = {}) {
        if (items.isEmpty()) return
        if (items.size == 1) { then(); perform { restoreNow(items) }; return }
        confirm = Confirm("These items will be recovered to your library.", "Recover ${noun(items)}", false) { then(); perform { restoreNow(items) } }
    }

    private fun restoreNow(items: List<Media>) {
        try {
            store.trashRequest(items, false)?.let(launchSender)
        } catch (_: Exception) {
        }
    }

    fun deleteForever(items: List<Media>, then: () -> Unit = {}) {
        if (items.isEmpty()) return
        val msg = if (items.size == 1) "This ${noun(items).lowercase()} will be deleted. This action can't be undone."
        else "These ${items.size} items will be deleted. This action can't be undone."
        confirm = Confirm(msg, "Delete ${noun(items)}", true) { then(); perform { deleteNow(items) } }
    }

    private fun deleteNow(items: List<Media>) {
        try {
            val sender = store.deleteRequest(items)
            if (sender != null) launchSender(sender) else store.requestReload()
        } catch (_: Exception) {
        }
    }

    fun unlock(then: () -> Unit) {
        if (unlocked || !AppPrefs.get(ctx).flag(PrefKeys.Passcode, true)) return then()
        val km = ctx.getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isDeviceSecure) {
            unlocked = true
            then()
            return
        }
        @Suppress("DEPRECATION")
        val intent = km.createConfirmDeviceCredentialIntent("Photos", "Unlock to view this album")
        if (intent == null) {
            then()
            return
        }
        onUnlocked = then
        launchUnlock(intent)
    }
}

@Composable
fun rememberMediaActions(store: PhotosStore): MediaActions {
    val ctx = LocalContext.current
    val holder = remember { arrayOfNulls<MediaActions>(1) }
    val senderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val a = holder[0]
        val then = a?.afterSender
        a?.afterSender = null
        if (r.resultCode == Activity.RESULT_OK) then?.invoke()
        store.requestReload()
    }
    val unlockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val a = holder[0]
        if (r.resultCode == Activity.RESULT_OK && a != null) {
            a.unlocked = true
            a.onUnlocked?.invoke()
        }
        a?.onUnlocked = null
    }
    return remember {
        MediaActions(
            ctx, store,
            launchSender = { senderLauncher.launch(IntentSenderRequest.Builder(it).build()) },
            launchUnlock = { unlockLauncher.launch(it) },
        ).also { holder[0] = it }
    }
}

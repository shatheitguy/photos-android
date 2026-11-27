package com.photos.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object PrefKeys {
    const val Memories = "showMemories"
    const val Featured = "showFeatured"
    const val ShowHidden = "showHiddenAlbum"
    const val Passcode = "requirePasscode"
    const val AspectGrid = "aspectGrid"
}

/** The ••• button at the top right of Albums. */
@Composable
fun AlbumsMoreButton() {
    val nav = LocalNav.current
    val c = LocalIos.current
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(44.dp).shadow(8.dp, CircleShape, clip = false).clip(CircleShape)
                .background(if (c.dark) Color(0xF22A2A2D) else Color(0xF8FFFFFF))
                .border(0.5.dp, if (c.dark) Color(0x26FFFFFF) else Color(0x14000000), CircleShape)
                .tap { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(IosIcons.More, null, tint = c.label, modifier = Modifier.size(22.dp))
        }
        val updates = LocalUpdates.current
        IosDropdown(
            open, { open = false },
            listOf(
                Triple("Settings", IosIcons.Sliders) { nav.push(Route.Settings()) },
                Triple("Check for Updates", IosIcons.Download) { updates.checkNow("Photos") },
            ),
        )
    }
}

@Composable
fun PhotosSettingsScreen(about: Boolean, onBack: () -> Unit) {
    val prefs = AppPrefs.get(LocalContext.current)
    val actions = LocalActions.current
    val resume = LocalStore.current.resumeTick
    var mediaAccess by remember { mutableStateOf(actions.canManageMedia()) }
    LaunchedEffect(resume) { mediaAccess = actions.canManageMedia() }
    SettingsPage("Settings", onBack, scrollToEnd = about) {
        item {
            SettingsGroup(header = "Appearance") { AppearanceRow(prefs) }
        }
        item {
            SettingsGroup(header = "Albums & Grid", footer = "Aspect Ratio Grid shows whole photos instead of square crops.") {
                SettingsToggle("Show Memories", prefs.flag(PrefKeys.Memories, true)) { prefs.setFlag(PrefKeys.Memories, it) }
                SettingsToggle("Show Featured Photos", prefs.flag(PrefKeys.Featured, true)) { prefs.setFlag(PrefKeys.Featured, it) }
                SettingsToggle("Aspect Ratio Grid", prefs.flag(PrefKeys.AspectGrid, false), last = true) { prefs.setFlag(PrefKeys.AspectGrid, it) }
            }
        }
        item {
            SettingsGroup(
                header = "Privacy",
                footer = "When Use Passcode is on, opening Hidden and Recently Deleted asks for your phone's PIN, pattern or fingerprint.",
            ) {
                SettingsToggle("Show Hidden Album", prefs.flag(PrefKeys.ShowHidden, true)) { prefs.setFlag(PrefKeys.ShowHidden, it) }
                SettingsToggle("Use Passcode", prefs.flag(PrefKeys.Passcode, true), last = true) { prefs.setFlag(PrefKeys.Passcode, it) }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            item {
                SettingsGroup(
                    header = "Deleting",
                    footer = "Deletes and recovers straight away, without Android's extra “Allow” prompt. Photos still asks you to confirm first.",
                ) {
                    SettingsToggle("Delete Without Asking", mediaAccess, last = true) { actions.openMediaAccess() }
                }
            }
        }
        item { FossifyImportGroup() } // TEMPORARY: remove with FossifyImport.kt
        commonSettings("Photos", prefs)
    }
}

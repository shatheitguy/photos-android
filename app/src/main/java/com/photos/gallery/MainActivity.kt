package com.photos.gallery

import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.photos.gallery.ui.accessLevel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.photos.gallery.data.PhotosStore
import com.photos.gallery.ui.ExternalViewer
import com.photos.gallery.ui.LocalStore
import com.photos.gallery.ui.PhotosRoot
import com.photos.gallery.ui.PhotosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        val store = PhotosStore.get(this)
        val viewUri = if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        val viewType = intent?.type ?: viewUri?.let { contentResolver.getType(it) } ?: ""
        setContent {
            PhotosTheme {
                CompositionLocalProvider(LocalStore provides store) {
                    var fallback by remember { mutableStateOf(false) }
                    val mediaId = viewUri?.takeIf { it.authority == MediaStore.AUTHORITY }
                        ?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }?.takeIf { it >= 0 }
                    when {
                        viewUri == null -> PhotosRoot(store)
                        mediaId != null && !fallback && accessLevel(this) > 0 ->
                            PhotosRoot(store, mediaId, onOpenFailed = { fallback = true }, onOpenedClosed = { finish() })
                        else -> ExternalViewer(viewUri, viewType) { finish() }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PhotosStore.get(this).resumeTick++
    }
}

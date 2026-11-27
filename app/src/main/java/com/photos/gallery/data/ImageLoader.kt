package com.photos.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

object ImageLoader {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()
    private val cache = object : LruCache<String, ImageBitmap>(maxKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }
    private val sizes = listOf(1080, 512, 400, 256, 192)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val thumbDispatcher = Dispatchers.IO.limitedParallelism(6)

    private fun key(uri: Uri, size: Int) = "$uri#$size"

    fun cached(uri: Uri, size: Int): ImageBitmap? = cache.get(key(uri, size))

    fun cachedAny(uri: Uri, size: Int): ImageBitmap? =
        cached(uri, size) ?: sizes.firstNotNullOfOrNull { cache.get(key(uri, it)) }

    suspend fun thumbnail(ctx: Context, uri: Uri, size: Int): ImageBitmap? {
        cached(uri, size)?.let { return it }
        return withContext(thumbDispatcher) {
            try {
                ctx.contentResolver.loadThumbnail(uri, Size(size, size), null).asImageBitmap().also {
                    it.prepareToDraw()
                    cache.put(key(uri, size), it)
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    suspend fun full(ctx: Context, uri: Uri, maxDim: Int, software: Boolean = false): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val src = ImageDecoder.createSource(ctx.contentResolver, uri)
                ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = minOf(1f, maxDim.toFloat() / maxOf(w, h))
                    if (scale < 1f) {
                        decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    }
                    if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (_: Throwable) {
                null
            }
        }

    suspend fun animated(ctx: Context, uri: Uri): Drawable? = withContext(Dispatchers.IO) {
        try {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.contentResolver, uri))
        } catch (_: Throwable) {
            null
        }
    }
}

@Composable
fun rememberThumbnail(uri: Uri, size: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val state = produceState(ImageLoader.cachedAny(uri, size), uri, size) {
        if (ImageLoader.cached(uri, size) == null) {
            value = ImageLoader.thumbnail(ctx, uri, size) ?: value
        }
    }
    return state.value
}

package com.photos.gallery.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotosStore private constructor(private val app: Context) {
    companion object {
        @Volatile
        private var instance: PhotosStore? = null
        fun get(context: Context): PhotosStore = instance ?: synchronized(this) {
            instance ?: PhotosStore(context.applicationContext).also { instance = it }
        }
    }

    private val prefs = app.getSharedPreferences("photos_prefs", Context.MODE_PRIVATE)

    var all by mutableStateOf<List<Media>>(emptyList())
        private set
    var trash by mutableStateOf<List<Media>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var favorites by mutableStateOf(readSet("fav"))
        private set
    var hidden by mutableStateOf(readSet("hidden"))
        private set
    var resumeTick by mutableIntStateOf(0)
    var reloadTick by mutableIntStateOf(0)
        private set

    fun requestReload() {
        reloadTick++
    }

    val library: List<Media> by derivedStateOf {
        val h = hidden
        if (h.isEmpty()) all else all.filter { it.id !in h }
    }
    val buckets: List<Bucket> by derivedStateOf {
        library.groupBy { it.bucketId }
            .map { (id, l) -> Bucket(id, l.first().bucket.ifBlank { "Other" }, l) }
            .sortedWith(compareByDescending<Bucket> { it.name.equals("Camera", true) }.thenByDescending { it.items.size })
    }
    val memories: List<Memory> by derivedStateOf { Memories.build(library) }
    val featured: List<Media> by derivedStateOf { Memories.featured(library) }
    /** Sets of photos whose files are byte-for-byte the same. The first of each set is the one to keep. */
    private var dupSets by mutableStateOf<List<List<Media>>>(emptyList())
    val dupGroups: List<List<Media>> by derivedStateOf {
        val h = hidden
        dupSets.map { g -> g.filter { it.id !in h } }.filter { it.size > 1 }
    }
    val duplicates: List<Media> by derivedStateOf { dupGroups.flatten() }
    private val hashCache = HashMap<String, String>()

    /** Same size and dimensions first (cheap), then the file contents are compared so only true copies count. */
    private suspend fun findDuplicates(items: List<Media>) {
        val sets = withContext(Dispatchers.IO) {
            items.filter { !it.isVideo && it.size > 0 }
                .groupBy { Triple(it.size, it.width, it.height) }
                .values.filter { it.size > 1 }
                .flatMap { same -> same.groupBy { hashOf(it) }.filterKeys { it != null }.values.filter { it.size > 1 } }
                .map { g -> g.sortedWith(keepFirst) }
                .sortedByDescending { it.first().dateTaken }
        }
        dupSets = sets
    }

    /** The copy to keep: one in the camera folder, then the oldest, then the one without "(1)" / "copy" in its name. */
    private val keepFirst = compareByDescending<Media> { it.relPath.startsWith("DCIM/", true) }
        .thenBy { it.dateTaken }
        .thenBy { Regex("""\(\d+\)|copy""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
        .thenBy { it.name.length }
        .thenBy { it.id }

    private fun hashOf(m: Media): String? {
        val key = "${m.id}:${m.size}"
        synchronized(hashCache) { hashCache[key]?.let { return it } }
        val h = runCatching {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            app.contentResolver.openInputStream(m.uri)!!.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull() ?: return null
        synchronized(hashCache) { hashCache[key] = h }
        return h
    }

    fun itemsFor(ref: AlbumRef): List<Media> = when (ref.kind) {
        Kind.All -> library
        Kind.Bucket -> library.filter { it.bucketId == ref.key }
        Kind.Favorites -> favorites.let { f -> library.filter { it.id in f } }
        Kind.Photos -> library.filter { !it.isVideo }
        Kind.Videos -> library.filter { it.isVideo }
        Kind.Selfies -> library.filter { it.isSelfie }
        Kind.Screenshots -> library.filter { it.isScreenshot }
        Kind.Recordings -> library.filter { it.isScreenRecording }
        Kind.Panoramas -> library.filter { it.isPanorama }
        Kind.Animated -> library.filter { it.isAnimated }
        Kind.Duplicates -> duplicates
        Kind.Hidden -> hidden.let { h -> all.filter { it.id in h } }
        Kind.Trash -> trash
        Kind.Search -> search(ref.query)
        Kind.Day -> library.filter { it.epochDay == ref.key }
        Kind.Month -> library.filter { it.monthKey == ref.key }
        Kind.Year -> library.filter { it.date.year.toLong() == ref.key }
        Kind.Featured -> featured
    }

    fun search(q: String): List<Media> {
        val tokens = q.lowercase().split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val fav = favorites
        return library.filter { m -> tokens.all { t -> matches(m, t, fav) } }
    }

    private fun matches(m: Media, t: String, fav: Set<Long>): Boolean = when {
        t.startsWith("video") -> m.isVideo
        t.startsWith("photo") -> !m.isVideo
        t.startsWith("screenshot") -> m.isScreenshot
        t.startsWith("favo") -> m.id in fav
        t.startsWith("pano") -> m.isPanorama
        t.startsWith("selfie") -> m.isSelfie
        t == "gif" -> m.isAnimated
        else -> m.searchText.contains(t)
    }

    // ---------- Loading ----------

    suspend fun reload() {
        val (items, trashed) = withContext(Dispatchers.IO) {
            val img = query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, video = false, trashed = false)
            val vid = query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video = true, trashed = false)
            val t = if (Build.VERSION.SDK_INT >= 30) {
                query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, true) +
                    query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, true)
            } else emptyList()
            (img + vid).distinctBy { it.id }.sortedBy { it.dateTaken } to t.distinctBy { it.id }.sortedBy { it.dateExpires }
        }
        all = items
        trash = trashed
        loaded = true
        findDuplicates(items)
    }

    private fun query(collection: Uri, video: Boolean, trashed: Boolean): List<Media> {
        val proj = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        if (video) proj += MediaStore.MediaColumns.DURATION else proj += MediaStore.MediaColumns.ORIENTATION
        if (trashed) proj += MediaStore.MediaColumns.DATE_EXPIRES
        val args = Bundle()
        if (trashed && Build.VERSION.SDK_INT >= 30) {
            args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val out = ArrayList<Media>()
        try {
            app.contentResolver.query(collection, proj.toTypedArray(), args, null)?.use { cur ->
                val iId = cur.getColumnIndex(MediaStore.MediaColumns._ID)
                val iTaken = cur.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val iMod = cur.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val iAdded = cur.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val iName = cur.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val iBucket = cur.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iBucketId = cur.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                val iW = cur.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val iH = cur.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val iSize = cur.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val iMime = cur.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val iPath = cur.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val iDur = cur.getColumnIndex(MediaStore.MediaColumns.DURATION)
                val iOri = cur.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
                val iExp = cur.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
                while (cur.moveToNext()) {
                    val id = cur.long(iId)
                    var taken = cur.long(iTaken)
                    if (taken <= 0) taken = cur.long(iMod) * 1000
                    if (taken <= 0) taken = cur.long(iAdded) * 1000
                    var w = cur.int(iW)
                    var h = cur.int(iH)
                    val ori = cur.int(iOri)
                    if (ori == 90 || ori == 270) { val t = w; w = h; h = t }
                    out += Media(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        isVideo = video,
                        dateTaken = taken,
                        name = cur.str(iName),
                        bucket = cur.str(iBucket),
                        bucketId = cur.long(iBucketId),
                        width = w,
                        height = h,
                        size = cur.long(iSize),
                        duration = cur.long(iDur),
                        mime = cur.str(iMime),
                        relPath = cur.str(iPath),
                        dateExpires = cur.long(iExp),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun Cursor.long(i: Int): Long = if (i < 0 || isNull(i)) 0L else getLong(i)
    private fun Cursor.int(i: Int): Int = if (i < 0 || isNull(i)) 0 else getInt(i)
    private fun Cursor.str(i: Int): String = if (i < 0 || isNull(i)) "" else getString(i) ?: ""

    // ---------- Favorites / hidden ----------

    fun toggleFavorite(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val allFav = ids.all { it in favorites }
        favorites = if (allFav) favorites - ids.toSet() else favorites + ids
        saveSet("fav", favorites)
    }

    fun setHidden(ids: Collection<Long>, hide: Boolean) {
        hidden = if (hide) hidden + ids else hidden - ids.toSet()
        saveSet("hidden", hidden)
    }

    private fun readSet(key: String): Set<Long> =
        prefs.getStringSet(key, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    private fun saveSet(key: String, set: Set<Long>) {
        prefs.edit().putStringSet(key, set.map { it.toString() }.toSet()).apply()
    }

    // ---------- Delete / trash ----------

    fun trashRequest(items: List<Media>, trash: Boolean): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30) {
            MediaStore.createTrashRequest(app.contentResolver, items.map { it.uri }, trash).intentSender
        } else null

    fun deleteRequest(items: List<Media>): IntentSender? {
        if (Build.VERSION.SDK_INT >= 30) {
            return MediaStore.createDeleteRequest(app.contentResolver, items.map { it.uri }).intentSender
        }
        for (m in items) {
            try {
                app.contentResolver.delete(m.uri, null, null)
            } catch (e: RecoverableSecurityException) {
                return e.userAction.actionIntent.intentSender
            } catch (_: Exception) {
            }
        }
        return null
    }

    // ---------- Copy / move between albums ----------

    /** Copies each item into [relPath] (e.g. "Pictures/Trips/"); Android adds " (1)" to clashing names. */
    suspend fun copyTo(items: List<Media>, relPath: String): Int = withContext(Dispatchers.IO) {
        var n = 0
        val resolver = app.contentResolver
        for (m in items) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, m.name.ifBlank { "IMG_${System.currentTimeMillis()}" })
                    put(MediaStore.MediaColumns.MIME_TYPE, m.mime.ifBlank { if (m.isVideo) "video/mp4" else "image/jpeg" })
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.DATE_TAKEN, m.dateTaken)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = if (m.isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values) ?: error("insert failed")
                resolver.openInputStream(m.uri)!!.use { input -> resolver.openOutputStream(uri)!!.use { input.copyTo(it) } }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                n++
            }
        }
        n
    }

    fun writeRequest(items: List<Media>): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createWriteRequest(app.contentResolver, items.map { it.uri }).intentSender else null

    /** Moves items into [relPath]; call after write access was granted. */
    suspend fun moveNow(items: List<Media>, relPath: String): Int = withContext(Dispatchers.IO) {
        items.count { m ->
            runCatching {
                val v = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relPath) }
                app.contentResolver.update(m.uri, v, null, null) > 0
            }.getOrDefault(false)
        }
    }

    // ---------- Save edits ----------

    suspend fun saveEdited(original: Media, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            val base = original.name.substringBeforeLast('.').ifBlank { "IMG" }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${base}_edited_${System.currentTimeMillis() % 100000}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photos Edits")
                put(MediaStore.MediaColumns.DATE_TAKEN, original.dateTaken + 1)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = app.contentResolver
            val uri = resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
            ) ?: return@withContext false
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }
}

package com.photos.gallery.data

import android.net.Uri
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.random.Random

data class Media(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val dateTaken: Long,
    val name: String = "",
    val bucket: String = "",
    val bucketId: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0,
    val duration: Long = 0,
    val mime: String = "",
    val relPath: String = "",
    val dateExpires: Long = 0,
) {
    val date: LocalDate = Instant.ofEpochMilli(dateTaken).atZone(ZoneId.systemDefault()).toLocalDate()
    val epochDay: Long = date.toEpochDay()
    val yearMonth: YearMonth get() = YearMonth.from(date)
    val monthKey: Long get() = date.year * 12L + (date.monthValue - 1)

    val isScreenshot: Boolean
        get() = !isVideo && (bucket.contains("screenshot", true) || relPath.contains("screenshot", true))
    val isScreenRecording: Boolean
        get() = isVideo && (bucket.contains("screen", true) || relPath.contains("screen", true) || name.contains("screen", true))
    val isPanorama: Boolean
        get() = !isVideo && !isScreenshot && height > 0 && width > height &&
            width.toFloat() / height >= 2.0f
    val isAnimated: Boolean get() = mime == "image/gif"
    val isSelfie: Boolean
        get() = bucket.contains("selfie", true) || relPath.contains("selfie", true) || name.contains("selfie", true)

    val searchText: String by lazy {
        val loc = Locale.getDefault()
        buildString {
            append(name).append(' ').append(bucket).append(' ').append(relPath).append(' ')
            append(date.month.getDisplayName(TextStyle.FULL, loc)).append(' ')
            append(date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)).append(' ')
            append(date.year).append(' ')
            append(date.dayOfWeek.getDisplayName(TextStyle.FULL, loc)).append(' ')
            append(date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
        }.lowercase()
    }
}

enum class Kind {
    All, Bucket, Favorites, Photos, Videos, Selfies, Screenshots, Recordings, Panoramas, Animated,
    Duplicates, Hidden, Trash, Search, Day, Month, Year, Featured
}

data class AlbumRef(val kind: Kind, val title: String, val key: Long = 0, val query: String = "") {
    companion object {
        val All = AlbumRef(Kind.All, "Recents")
        val Favorites = AlbumRef(Kind.Favorites, "Favorites")
        val Photos = AlbumRef(Kind.Photos, "Photos")
        val Videos = AlbumRef(Kind.Videos, "Videos")
        val Selfies = AlbumRef(Kind.Selfies, "Selfies")
        val Screenshots = AlbumRef(Kind.Screenshots, "Screenshots")
        val Recordings = AlbumRef(Kind.Recordings, "Screen Recordings")
        val Panoramas = AlbumRef(Kind.Panoramas, "Panoramas")
        val Animated = AlbumRef(Kind.Animated, "Animated")
        val Duplicates = AlbumRef(Kind.Duplicates, "Duplicates")
        val Hidden = AlbumRef(Kind.Hidden, "Hidden")
        val Trash = AlbumRef(Kind.Trash, "Recently Deleted")
        val Featured = AlbumRef(Kind.Featured, "Featured Photos")
    }
}

data class Bucket(val id: Long, val name: String, val items: List<Media>)

data class Memory(val id: String, val title: String, val subtitle: String, val items: List<Media>) {
    val cover: Media get() = items[items.size / 2]
}

object Memories {
    private fun sample(list: List<Media>, n: Int = 30): List<Media> =
        if (list.size <= n) list else List(n) { list[it * list.size / n] }

    fun build(library: List<Media>): List<Memory> {
        val photos = library.filter { !it.isVideo && !it.isScreenshot }
        if (photos.isEmpty()) return emptyList()
        val loc = Locale.getDefault()
        val today = LocalDate.now()
        val first = mutableListOf<Memory>()
        val rest = mutableListOf<Pair<Long, Memory>>()

        val onThisDay = photos.filter {
            it.date.monthValue == today.monthValue && it.date.dayOfMonth == today.dayOfMonth && it.date.year < today.year
        }
        if (onThisDay.isNotEmpty()) {
            val years = onThisDay.map { it.date.year }.distinct()
            val sub = if (years.size == 1) {
                val n = today.year - years[0]
                if (n == 1) "1 year ago" else "$n years ago"
            } else today.format(DateTimeFormatter.ofPattern("d MMMM", loc))
            first += Memory("otd", "On This Day", sub, sample(onThisDay))
        }

        val dayFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", loc)
        photos.groupBy { it.epochDay }.filter { it.value.size >= 6 }.entries
            .sortedByDescending { it.key }.take(8).forEach { e ->
                val date = LocalDate.ofEpochDay(e.key)
                val title = when (date.dayOfWeek) {
                    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> "Weekend Moments"
                    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, loc)
                }
                rest += e.key to Memory("d${e.key}", title, date.format(dayFmt), sample(e.value))
            }

        photos.groupBy { it.yearMonth }.filter { it.value.size >= 12 }.entries
            .sortedByDescending { it.key }.take(6).forEach { e ->
                val ym = e.key
                rest += ym.atEndOfMonth().toEpochDay() to Memory(
                    "m$ym", "Best of ${ym.month.getDisplayName(TextStyle.FULL, loc)}", ym.year.toString(), sample(e.value)
                )
            }

        photos.groupBy { it.date.year }.filter { it.key < today.year && it.value.size >= 25 }.forEach { e ->
            rest += LocalDate.of(e.key, 12, 31).toEpochDay() to
                Memory("y${e.key}", "Year in Review", e.key.toString(), sample(e.value, 40))
        }

        return (first + rest.sortedByDescending { it.first }.map { it.second }).distinctBy { it.id }
    }

    fun featured(library: List<Media>): List<Media> {
        val photos = library.filter { !it.isVideo && !it.isScreenshot }
        return photos.shuffled(Random(LocalDate.now().toEpochDay())).take(12)
    }
}

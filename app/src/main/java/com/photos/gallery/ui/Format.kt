package com.photos.gallery.ui

import com.photos.gallery.data.Media
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Fmt {
    private fun p(s: String) = DateTimeFormatter.ofPattern(s, Locale.getDefault())
    private val dayMonth by lazy { p("d MMMM") }
    private val dayMonthYear by lazy { p("d MMMM yyyy") }
    private val header by lazy { p("EEEE, d MMM yyyy") }
    private val monthYearF by lazy { p("MMMM yyyy") }
    private val full by lazy { p("EEEE, d MMMM yyyy") }
    private val timeF by lazy { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()) }

    fun dayTitle(d: LocalDate): String {
        val today = LocalDate.now()
        return when {
            d == today -> "Today"
            d == today.minusDays(1) -> "Yesterday"
            d.year == today.year -> d.format(dayMonth)
            else -> d.format(dayMonthYear)
        }
    }

    fun dayHeader(d: LocalDate): String = d.format(header)
    fun monthYear(ym: YearMonth): String = ym.format(monthYearF)
    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeF)
    fun fullDate(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(full) + "  •  " + time(ms)

    fun duration(ms: Long): String {
        val t = ms / 1000
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun num(n: Int): String = NumberFormat.getIntegerInstance().format(n)

    fun counts(photos: Int, videos: Int): String = listOfNotNull(
        if (photos > 0) "${num(photos)} ${if (photos == 1) "Photo" else "Photos"}" else null,
        if (videos > 0) "${num(videos)} ${if (videos == 1) "Video" else "Videos"}" else null,
    ).joinToString(", ")

    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / 1073741824.0)
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        else -> String.format(Locale.US, "%d KB", bytes / 1024)
    }

    fun daysLeftValue(m: Media): Int =
        ((m.dateExpires * 1000 - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0).toInt()

    fun daysLeft(m: Media): String {
        val d = daysLeftValue(m)
        return if (d == 1) "1 day" else "$d days"
    }
}

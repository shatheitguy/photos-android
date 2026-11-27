package com.photos.gallery.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class IosColors(
    val dark: Boolean,
    val bg: Color,
    val grouped: Color,
    val card: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val accent: Color,
    val red: Color,
    val fill: Color,
    val bar: Color,
    val placeholder: Color,
    val inactive: Color,
)

val LightIos = IosColors(
    dark = false,
    bg = Color.White,
    grouped = Color(0xFFF2F2F7),
    card = Color.White,
    label = Color.Black,
    secondary = Color(0xFF8A8A8E),
    tertiary = Color(0xFFC4C4C7),
    separator = Color(0x4D3C3C43),
    accent = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    fill = Color(0xFFEEEEF0),
    bar = Color(0xFCF9F9F9),
    placeholder = Color(0xFFE9E9EC),
    inactive = Color(0xFF999999),
)

val DarkIos = IosColors(
    dark = true,
    bg = Color.Black,
    grouped = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    label = Color.White,
    secondary = Color(0xFF8D8D93),
    tertiary = Color(0xFF48484A),
    separator = Color(0x99545458),
    accent = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    fill = Color(0xFF1C1C1E),
    bar = Color(0xFC121212),
    placeholder = Color(0xFF1C1C1E),
    inactive = Color(0xFF8A8A8A),
)

val LocalIos = staticCompositionLocalOf { LightIos }

object IosType {
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    val title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp)
    val subhead = TextStyle(fontSize = 15.sp)
    val footnote = TextStyle(fontSize = 13.sp)
}

@Composable
fun PhotosTheme(content: @Composable () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val system = isSystemInDarkTheme()
    val dark = when (AppPrefs.get(ctx).appearance) { 1 -> false; 2 -> true; else -> system }
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val ctl = androidx.core.view.WindowCompat.getInsetsController(w, view)
            ctl.isAppearanceLightStatusBars = !dark
            ctl.isAppearanceLightNavigationBars = !dark
        }
    }
    val ios = if (dark) DarkIos else LightIos
    val scheme = if (dark) {
        darkColorScheme(
            primary = ios.accent, background = ios.bg, surface = ios.card,
            onSurface = ios.label, onBackground = ios.label, surfaceContainerLow = ios.card,
        )
    } else {
        lightColorScheme(
            primary = ios.accent, background = ios.bg, surface = ios.bg,
            onSurface = ios.label, onBackground = ios.label, surfaceContainerLow = ios.grouped,
        )
    }
    CompositionLocalProvider(LocalIos provides ios) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides ios.label, content = content)
        }
    }
}

/** The phone's own accent colour (Material You), used only for the tab bar highlight. iOS blue on older Android. */
@Composable
fun systemAccent(): Color {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = LocalIos.current
    if (android.os.Build.VERSION.SDK_INT < 31) return c.accent
    return Color(ctx.getColor(if (c.dark) android.R.color.system_accent1_500 else android.R.color.system_accent1_600))
}

package com.photos.gallery.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Wide screens (tablets, and big phones in landscape) get the iPad layout: a sidebar beside the content. */
@Composable
fun isTablet(): Boolean = LocalConfiguration.current.screenWidthDp >= 700

/** True while drawing inside the right-hand pane of the iPad layout, where the root screen needs no back button. */
val LocalInPane = staticCompositionLocalOf { false }

/** True for the screen that a sidebar item shows; it is the root of the pane, so it has no back button. */
val LocalPaneRoot = staticCompositionLocalOf { false }

/** The back action, or none when this screen is the root of the iPad pane. */
@Composable
fun backUnlessRoot(onBack: () -> Unit): (() -> Unit)? = if (LocalPaneRoot.current) null else onBack

@Composable
fun sidebarWidth(): Dp = if (LocalConfiguration.current.screenWidthDp >= 1000) 320.dp else 290.dp

/** Sidebar on the left, content on the right, like iPadOS. */
@Composable
fun SplitLayout(sidebar: @Composable () -> Unit, content: @Composable () -> Unit) {
    val c = LocalIos.current
    val sw = sidebarWidth()
    Row(Modifier.fillMaxSize().background(c.bg)) {
        Box(Modifier.width(sw).fillMaxHeight().background(sidebarColor())) { sidebar() }
        Box(Modifier.width(0.5.dp).fillMaxHeight().background(c.separator))
        Pane(Modifier.weight(1f).fillMaxHeight(), content)
    }
}

@Composable
fun sidebarColor(): Color {
    val c = LocalIos.current
    return if (c.dark) Color(0xFF161618) else Color(0xFFF2F2F7)
}

/**
 * The content pane. Screens size their grids from the screen width, so inside the pane
 * they are told the pane's width instead and lay out exactly as they would on a screen that size.
 */
@Composable
fun Pane(modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val base = LocalConfiguration.current
        val w = with(LocalDensity.current) { constraints.maxWidth.toDp().value.toInt() }
        val cfg = remember(base, w) { Configuration(base).apply { screenWidthDp = w } }
        CompositionLocalProvider(LocalConfiguration provides cfg, LocalInPane provides true) { content() }
    }
}

/** Large bold title at the top of the sidebar, with an optional button on the right. */
@Composable
fun SidebarHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalIos.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.label, letterSpacing = (-0.4).sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** One sidebar row: icon, title, optional count. The chosen row gets a filled rounded highlight. */
@Composable
fun SidebarRow(
    title: String,
    icon: ImageVector? = null,
    selected: Boolean = false,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LocalIos.current
    val bg by animateColorAsState(if (selected) c.accent else Color.Transparent, label = "sideBg")
    val fg = if (selected) Color.White else c.label
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
            .background(bg).tap(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
            when {
                leading != null -> leading()
                icon != null -> Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 17.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, fontSize = 15.sp, color = if (selected) Color.White.copy(alpha = 0.8f) else c.secondary, maxLines = 1)
    }
}

/** A sidebar section with a title that folds it away, like the sections in the iPad sidebar. */
fun LazyListScope.sidebarSection(key: String, title: String, rows: @Composable ColumnScope.() -> Unit) {
    item(key = "h-$key") {
        var open by rememberSaveable(key) { mutableStateOf(true) }
        val c = LocalIos.current
        val turn by animateFloatAsState(if (open) 90f else 0f, spring(stiffness = 600f), label = "chev")
        Column {
            Row(
                Modifier.fillMaxWidth().tap { open = !open }.padding(start = 22.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label, modifier = Modifier.weight(1f))
                Icon(IosIcons.ChevronRight, null, tint = c.secondary, modifier = Modifier.size(16.dp).rotate(turn))
            }
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(content = rows)
            }
        }
    }
}

/** Scrolling sidebar body. */
@Composable
fun SidebarList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues().let { PaddingValues(bottom = it.calculateBottomPadding() + 24.dp) },
        content = content,
    )
}

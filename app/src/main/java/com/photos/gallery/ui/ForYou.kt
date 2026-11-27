@file:OptIn(ExperimentalFoundationApi::class)

package com.photos.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photos.gallery.data.*

/** Memories + Featured Photos rows, shown at the top of the Albums tab (like Collections on iPhone). */
fun LazyListScope.memorySections(
    memories: List<Memory>,
    featured: List<Media>,
    cardWidth: Dp,
    onMemory: (Memory) -> Unit,
    onFeatured: (Media) -> Unit,
) {
    if (memories.isNotEmpty()) {
        item(key = "mem-h") { SectionHeader("Memories", divider = false) }
        item(key = "mem-row") {
            val rs = rememberLazyListState()
            LazyRow(
                state = rs,
                flingBehavior = rememberSnapFlingBehavior(rs),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(memories, key = { it.id }) { m ->
                    MemoryCard(m, Modifier.width(cardWidth).aspectRatio(0.8f)) { onMemory(m) }
                }
            }
        }
    }
    if (featured.isNotEmpty()) {
        item(key = "feat-h") { SectionHeader("Featured Photos") }
        item(key = "feat-row") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(featured, key = { it.id }) { m ->
                    Thumb(
                        m,
                        Modifier.width(150.dp).height(196.dp).clip(RoundedCornerShape(12.dp)).tap { onFeatured(m) },
                        512,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryCard(m: Memory, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).tap(onClick = onClick)) {
        Thumb(m.cover, Modifier.fillMaxSize(), 1080)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(m.title, color = Color.White, style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp))
            Text(m.subtitle.uppercase(), color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
    }
}

// ---------------- Memory player ----------------

@Composable
fun StoryScreen(memory: Memory, onClose: () -> Unit) {
    val items = memory.items
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var restart by remember { mutableIntStateOf(0) }
    var resetNext by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val per = 4500
    val close by rememberUpdatedState(onClose)

    LaunchedEffect(index, paused, restart) {
        if (resetNext) {
            progress.snapTo(0f)
            resetNext = false
        }
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * per).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        if (index < items.lastIndex) {
            resetNext = true
            index++
        } else close()
    }

    fun go(delta: Int) {
        val n = index + delta
        if (n > items.lastIndex) {
            close(); return
        }
        resetNext = true
        index = n.coerceAtLeast(0)
        restart++
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    paused = true
                    tryAwaitRelease()
                    paused = false
                },
                onTap = { o -> if (o.x < size.width / 3f) go(-1) else go(1) },
            )
        }
    ) {
        Crossfade(targetState = index, animationSpec = tween(700), label = "story") { i ->
            KenBurns(items[i], i)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f), 0.2f to Color.Transparent,
                    0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f),
                )
            )
        )
        Column(Modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            StoryBars(items.size, index, progress.value)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(memory.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(memory.subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).tap { close() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = index == 0, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(Modifier.navigationBarsPadding().padding(24.dp)) {
                Text(memory.title, color = Color.White, style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
                Text(memory.subtitle.uppercase(), color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
            }
        }
    }
}

@Composable
private fun StoryBars(count: Int, index: Int, progress: Float) {
    Row(Modifier.fillMaxWidth().height(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (count <= 25) {
            repeat(count) { i ->
                val f = when {
                    i < index -> 1f
                    i == index -> progress
                    else -> 0f
                }
                StoryBar(Modifier.weight(1f), f)
            }
        } else {
            StoryBar(Modifier.weight(1f), (index + progress) / count)
        }
    }
}

@Composable
private fun StoryBar(modifier: Modifier, fraction: Float) {
    Box(modifier.fillMaxHeight().clip(CircleShape).background(Color.White.copy(alpha = 0.35f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Color.White))
    }
}

@Composable
private fun KenBurns(m: Media, i: Int) {
    val ctx = LocalContext.current
    val thumb = rememberThumbnail(m.uri, 512)
    var full by remember(m.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(m.id) { full = ImageLoader.full(ctx, m.uri, 1600)?.asImageBitmap() }
    val z = remember { Animatable(1f) }
    LaunchedEffect(Unit) { z.animateTo(1.18f, tween(6000, easing = LinearEasing)) }
    val img = full ?: thumb
    if (img != null) {
        val dir = if (i % 2 == 0) 1f else -1f
        Image(
            img, null,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = z.value
                scaleY = z.value
                translationX = dir * (z.value - 1f) * size.width * 0.25f
            },
            contentScale = ContentScale.Crop,
        )
    }
}

package com.photos.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The confirmation cards for deleting and recovering, drawn over everything. */
@Composable
fun ActionCards() {
    val actions = LocalActions.current
    val confirm = actions.confirm
    var lastConfirm by remember { mutableStateOf<MediaActions.Confirm?>(null) }
    if (confirm != null) lastConfirm = confirm
    GlassCardHost(visible = confirm != null, onDismiss = { actions.confirm = null }) {
        val cf = lastConfirm ?: return@GlassCardHost
        Text(cf.message, fontSize = 17.sp, color = LocalIos.current.label, lineHeight = 23.sp, modifier = Modifier.padding(horizontal = 6.dp))
        Spacer(Modifier.height(18.dp))
        CardButton(cf.button, if (cf.destructive) Color(0xFFFF6961) else LocalIos.current.label) {
            actions.confirm = null
            cf.run()
        }
    }

    val ask = actions.askAccess
    GlassCardHost(visible = ask != null, onDismiss = { actions.accessNotNow() }) {
        val c = LocalIos.current
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(c.red), contentAlignment = Alignment.Center) {
                Icon(IosIcons.Trash, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Delete Without Asking", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Turn on “Allow media management” for Photos so deleting and recovering happen straight away, without Android's extra “Allow” prompt.",
                fontSize = 15.sp, color = c.secondary, textAlign = TextAlign.Center, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            CardButton("Turn On", Color.White, fill = c.accent) { actions.accessTurnOn() }
            Spacer(Modifier.height(8.dp))
            CardButton("Not Now", c.label) { actions.accessNotNow() }
        }
    }
}

@Composable
private fun GlassCardHost(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalIos.current
    if (visible) BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(Color(0x59000000)).tap(onClick = onDismiss))
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), initialScale = 0.82f) + fadeIn(tween(140)),
            exit = scaleOut(tween(150), targetScale = 0.9f) + fadeOut(tween(150)),
        ) {
            val bg = if (c.dark) Color(0xF02C2C2E) else Color(0xF7F7F7FA)
            val border = if (c.dark) Color(0x33FFFFFF) else Color(0x14000000)
            Column(
                Modifier.padding(horizontal = 40.dp).widthIn(max = 340.dp)
                    .shadow(40.dp, RoundedCornerShape(34.dp), clip = false)
                    .clip(RoundedCornerShape(34.dp)).background(bg).border(0.5.dp, border, RoundedCornerShape(34.dp))
                    .blockTouches().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun CardButton(text: String, color: Color, fill: Color? = null, onClick: () -> Unit) {
    val c = LocalIos.current
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(CircleShape)
            .background(fill ?: if (c.dark) Color(0x24FFFFFF) else Color(0x12000000))
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

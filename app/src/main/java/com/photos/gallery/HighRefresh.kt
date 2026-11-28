package com.photos.gallery

import android.app.Activity
import android.os.Build
import android.view.View

/**
 * Runs the screen at its fastest refresh rate (90/120 Hz or more) at the current resolution,
 * so scrolling and animations feel smoother. Called on start and every time the screen comes back,
 * because some phones drop to 60 Hz after the app was in the background.
 */
fun Activity.useHighRefreshRate() {
    runCatching {
        val d = if (Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        d ?: return
        val cur = d.mode
        val best = d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = best.modeId
            preferredRefreshRate = best.refreshRate
        }
    }
    // Android 15+: ask for the high frame rate even when the phone adapts the rate on its own.
    if (Build.VERSION.SDK_INT >= 35) runCatching {
        window.decorView.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
    }
}

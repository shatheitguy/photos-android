package com.photos.gallery.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Very soft taps, like the iPhone's: the motor's lightest "low tick" where the phone has one,
 * otherwise a tiny, low-power pulse. Much gentler than Android's standard haptic feedback.
 */
object Haptics {
    private var vib: Vibrator? = null

    private fun vibrator(ctx: Context): Vibrator? {
        vib?.let { return it }
        val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        vib = v
        return v
    }

    private var lastTick = 0L

    /**
     * The same tick Files uses when a menu opens (Android's built-in "tick" effect): scrubbing, swiping
     * photos, the editor dial, menus. At most one every 45 ms, so it never buzzes.
     */
    fun tick(ctx: Context) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTick < 45) return
        lastTick = now
        effect(ctx, VibrationEffect.EFFECT_TICK)
    }

    /** Zero or the end of the dial: the same tick. */
    fun detent(ctx: Context) = effect(ctx, VibrationEffect.EFFECT_TICK)

    /** Long-pressing a photo: Android's built-in "click". */
    fun press(ctx: Context) = effect(ctx, VibrationEffect.EFFECT_CLICK)

    private fun effect(ctx: Context, id: Int) {
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createPredefined(id)) }
    }
}

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

    /** The lightest tick: scrubbing photos, turning the editor dial. At most one every 45 ms, so it never buzzes. */
    fun tick(ctx: Context) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTick < 45) return
        lastTick = now
        play(ctx, 0.5f, 4, 30)
    }

    /** Long-pressing a photo: light, but clearly felt. */
    fun press(ctx: Context) {
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                v.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.45f).compose())
            } else if (v.hasAmplitudeControl()) {
                v.vibrate(VibrationEffect.createOneShot(10, 70))
            } else {
                v.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    /** A little firmer: reaching zero or the end of the dial, or opening a menu. */
    fun detent(ctx: Context) = play(ctx, 0.7f, 6, 55)

    private fun play(ctx: Context, scale: Float, ms: Long, amplitude: Int) {
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                v.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale).compose())
            } else if (v.hasAmplitudeControl()) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            } else {
                // No strength control: keep the pulse extremely short so it still feels light.
                v.vibrate(VibrationEffect.createOneShot(if (scale < 0.6f) 3 else 5, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}

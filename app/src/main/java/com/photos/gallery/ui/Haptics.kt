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

    /** The lightest tick: scrubbing photos, turning the editor dial. */
    fun tick(ctx: Context) = play(ctx, 0.3f, 5, 18)

    /** A little firmer: reaching zero or the end of the dial. */
    fun detent(ctx: Context) = play(ctx, 0.6f, 8, 45)

    private fun play(ctx: Context, scale: Float, ms: Long, amplitude: Int) {
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                v.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale).compose())
            } else if (v.hasAmplitudeControl()) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            } else {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
        }
    }
}

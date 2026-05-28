package com.example.caraka.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.caraka.ui.prefs.LocalUiPrefs

/**
 * Thin wrapper around the system Vibrator with semantic helpers and a
 * runtime "haptics enabled" check sourced from [LocalUiPrefs].
 *
 * Use the [rememberHaptics] composable inside any screen to get a stable instance.
 */
class Haptics(private val context: Context, private val enabled: () -> Boolean) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Subtle 20ms tick — for tap confirmations, toggles, selections. */
    fun tick() = vibrate(20L, amplitude = 40)

    /** Medium 50ms — for sending a message, opening a screen. */
    fun light() = vibrate(50L, amplitude = 80)

    /** Heavy 100ms — for SOS broadcast, peer connect, alerts. */
    fun heavy() = vibrate(120L, amplitude = 180)

    /** SOS pattern — three escalating pulses. */
    fun emergency() {
        val v = vibrator ?: return
        if (!enabled()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 80, 60, 80, 60, 200)
                val amps    = intArrayOf(0, 120, 0, 160, 0, 220)
                v.vibrate(VibrationEffect.createWaveform(pattern, amps, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 80, 60, 80, 60, 200), -1)
            }
        } catch (_: Throwable) {}
    }

    private fun vibrate(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!enabled()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (_: Throwable) {}
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val ctx = LocalContext.current
    val prefs = LocalUiPrefs.current
    return remember(ctx) { Haptics(ctx) { prefs.haptics } }
}

package com.equipo.pocketguard.actuation

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.equipo.pocketguard.decision.VibrationPattern
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PocketGuard"

/** Vibración con los patrones de [VibrationPatterns]. Usa el uso "alarma" para vibrar también en modo silencio. */
@Singleton
class VibrationActuator @Inject constructor(
    private val vibrator: Vibrator,
) : Actuator {

    fun vibrate(pattern: VibrationPattern) {
        if (!vibrator.hasVibrator()) return
        val spec = VibrationPatterns.specFor(pattern)
        val effect = VibrationEffect.createWaveform(spec.timings.toLongArray(), spec.repeatIndex)
        try {
            vibrateAsAlarm(effect)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso para vibrar", e)
        }
    }

    /** Vibración de alarma continua. */
    override fun start() = vibrate(VibrationPattern.ALARM)

    override fun stop() {
        vibrator.cancel()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun vibrateAsAlarm(effect: VibrationEffect) {
        // API 33+ tiene VibrationAttributes; antes se usa AudioAttributes con uso de alarma.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }
}

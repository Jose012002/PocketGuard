package com.equipo.pocketguard.actuation

import android.util.Log
import com.equipo.pocketguard.decision.Effect
import com.equipo.pocketguard.decision.GuardState
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PocketGuard"

/**
 * Traduce los [Effect] de la capa de decisión en llamadas a los actuadores (sección 7.1). Es el único
 * punto que los invoca. Un actuador que falla no impide que los demás actúen (RNF-16).
 *
 * Los efectos de monitoreo, wake lock, muestreo y registro los atiende el servicio, no este controlador.
 */
@Singleton
class AlarmController @Inject constructor(
    private val siren: SirenActuator,
    private val torch: TorchActuator,
    private val vibration: VibrationActuator,
    private val notifier: AlarmNotifier,
) {
    /** Ejecuta los efectos en orden. [state] es el estado ya resultante de la transición. */
    fun execute(effects: List<Effect>, state: GuardState) {
        effects.forEach { execute(it, state) }
    }

    private fun execute(effect: Effect, state: GuardState) {
        when (effect) {
            Effect.StartSiren -> safely("sirena") { siren.start() }
            Effect.StartStrobe -> safely("flash") { torch.start() }
            is Effect.Vibrate -> safely("vibración") { vibration.vibrate(effect.pattern) }
            Effect.StopAll -> {
                safely("sirena") { siren.stop() }
                safely("flash") { torch.stop() }
                safely("vibración") { vibration.stop() }
            }
            Effect.ShowPreAlarmUi -> safely("notificación") { notifier.showPreAlarm() }
            Effect.ShowAlarmUi -> safely("notificación") { notifier.showAlarm() }
            Effect.ClearAlarmUi -> safely("notificación") { notifier.clearAlarmUi() }
            Effect.UpdateStatusNotification -> safely("notificación") { notifier.updateStatus(state) }
            is Effect.ShowMessage -> safely("notificación") { notifier.showMessage(effect.code) }
            // Los atiende GuardService.
            Effect.StartMonitoring,
            Effect.StopMonitoring,
            Effect.AcquireWakeLock,
            Effect.ReleaseWakeLock,
            is Effect.SetSampling,
            is Effect.Log,
            -> Unit
        }
    }

    private inline fun safely(what: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            Log.e(TAG, "Falló el actuador de $what; se continúa con los demás", e)
        }
    }
}

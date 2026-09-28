package com.equipo.pocketguard.decision

import com.equipo.pocketguard.processing.SensorSnapshot

data class Transition(val state: GuardState, val effects: List<Effect>)

/**
 * Reducer puro de la máquina de estados (sección 6.4): `reduce(estado, entrada) → (estado', efectos)`.
 * No tiene estado propio ni ejecuta acciones; solo describe qué hay que hacer.
 */
class TheftDetectionEngine(private val config: DetectionConfig) {

    fun reduce(state: GuardState, input: GuardInput): Transition {
        if (input is GuardInput.Disarm) return onDisarm(state, input)
        return when (state) {
            GuardState.Disarmed -> onDisarmed(state, input)
            is GuardState.Arming -> onArming(state, input)
            is GuardState.Stored -> onStored(state, input)
            is GuardState.Suspicion -> onSuspicion(state, input)
            is GuardState.PreAlarm -> onPreAlarm(state, input)
            is GuardState.Alarm -> stay(state)
        }
    }

    private fun onDisarmed(state: GuardState, input: GuardInput): Transition {
        if (input !is GuardInput.Arm) return stay(state)
        if (!input.sensors.meetsMinimum) {
            return Transition(state, listOf(Effect.ShowMessage(MessageCode.INSUFFICIENT_SENSORS)))
        }
        return Transition(
            GuardState.Arming(startedAtNs = input.nowNs, pocketSinceNs = null, sensors = input.sensors),
            listOf(
                Effect.StartMonitoring,
                Effect.AcquireWakeLock,
                Effect.SetSampling(SamplingMode.NORMAL),
                Effect.UpdateStatusNotification,
                Effect.Log(EventType.ARMED),
            ),
        )
    }

    private fun onArming(state: GuardState.Arming, input: GuardInput): Transition = when (input) {
        is GuardInput.Snapshot -> onArmingSnapshot(state, input.snapshot)
        is GuardInput.Tick ->
            if (input.nowNs - state.startedAtNs >= config.armingTimeoutMs.msToNs()) {
                Transition(
                    GuardState.Disarmed,
                    listOf(
                        Effect.StopMonitoring,
                        Effect.ReleaseWakeLock,
                        Effect.ShowMessage(MessageCode.ARMING_TIMEOUT),
                        Effect.Log(EventType.TIMEOUT),
                    ),
                )
            } else {
                stay(state)
            }
        else -> stay(state)
    }

    private fun onArmingSnapshot(state: GuardState.Arming, s: SensorSnapshot): Transition {
        if (!isPocketCondition(s, state.sensors)) {
            return stay(if (state.pocketSinceNs == null) state else state.copy(pocketSinceNs = null))
        }
        val since = state.pocketSinceNs
            ?: return stay(state.copy(pocketSinceNs = s.timestampNs))
        if (s.timestampNs - since < config.armingStableMs.msToNs()) return stay(state)
        return Transition(
            GuardState.Stored(baselineLux = s.lux ?: 0f, sensors = state.sensors),
            listOf(
                Effect.Vibrate(VibrationPattern.CONFIRM),
                Effect.UpdateStatusNotification,
                Effect.Log(EventType.STORED),
            ),
        )
    }

    private fun onStored(state: GuardState.Stored, input: GuardInput): Transition {
        if (input !is GuardInput.Snapshot) return stay(state)
        val flags = Indicators.evaluate(input.snapshot, state.baselineLux, state.sensors, config)
        if (!flags.anyActive) return stay(state)
        return Transition(
            GuardState.Suspicion(
                openedAtNs = input.snapshot.timestampNs,
                baselineLux = state.baselineLux,
                flags = flags,
                sensors = state.sensors,
            ),
            listOf(Effect.SetSampling(SamplingMode.HIGH), Effect.Log(EventType.SUSPICION)),
        )
    }

    private fun onSuspicion(state: GuardState.Suspicion, input: GuardInput): Transition = when (input) {
        is GuardInput.Tick ->
            if (isWindowExpired(state, input.nowNs)) backToStored(state) else stay(state)
        is GuardInput.Snapshot -> onSuspicionSnapshot(state, input)
        else -> stay(state)
    }

    private fun onSuspicionSnapshot(state: GuardState.Suspicion, input: GuardInput.Snapshot): Transition {
        val s = input.snapshot
        if (isWindowExpired(state, s.timestampNs)) {
            // Un snapshot puede llegar antes que el siguiente Tick: la ventana ya venció, así que este
            // snapshot no puede completar la detección. Se evalúa como si el estado fuera Stored, lo que
            // reabre la sospecha por nivel (sección 6.3) si algún indicador sigue activo.
            val stored = backToStored(state)
            val reopened = onStored(stored.state as GuardState.Stored, input)
            return Transition(reopened.state, stored.effects + reopened.effects)
        }
        val flags = state.flags or Indicators.evaluate(s, state.baselineLux, state.sensors, config)
        if (!flags.isCompleteFor(state.sensors)) {
            return stay(if (flags == state.flags) state else state.copy(flags = flags))
        }
        return if (config.preAlarmGraceMs > 0) {
            Transition(
                GuardState.PreAlarm(startedAtNs = s.timestampNs),
                listOf(
                    Effect.Vibrate(VibrationPattern.SOFT),
                    Effect.ShowPreAlarmUi,
                    Effect.Log(EventType.PRE_ALARM),
                ),
            )
        } else {
            alarm(s.timestampNs)
        }
    }

    private fun onPreAlarm(state: GuardState.PreAlarm, input: GuardInput): Transition {
        if (input !is GuardInput.Tick) return stay(state)
        return if (input.nowNs - state.startedAtNs >= config.preAlarmGraceMs.msToNs()) {
            alarm(input.nowNs)
        } else {
            stay(state)
        }
    }

    private fun onDisarm(state: GuardState, input: GuardInput.Disarm): Transition {
        if (state == GuardState.Disarmed) return stay(state)
        if (!input.authOk) return Transition(state, listOf(Effect.Log(EventType.AUTH_FAILED)))
        return Transition(
            GuardState.Disarmed,
            listOf(
                Effect.StopAll,
                Effect.StopMonitoring,
                Effect.ReleaseWakeLock,
                Effect.ClearAlarmUi,
                Effect.Log(EventType.DISARMED),
            ),
        )
    }

    private fun backToStored(state: GuardState.Suspicion) = Transition(
        GuardState.Stored(state.baselineLux, state.sensors),
        listOf(Effect.SetSampling(SamplingMode.NORMAL)),
    )

    private fun alarm(nowNs: Long) = Transition(
        GuardState.Alarm(startedAtNs = nowNs),
        listOf(
            Effect.StartSiren,
            Effect.StartStrobe,
            Effect.Vibrate(VibrationPattern.ALARM),
            Effect.ShowAlarmUi,
            Effect.Log(EventType.ALARM),
        ),
    )

    private fun isWindowExpired(state: GuardState.Suspicion, nowNs: Long) =
        nowNs - state.openedAtNs >= config.coincidenceWindowMs.msToNs()

    /** Condición de bolsillo: cerca y oscuro. Un sensor ausente cuenta como satisfecho. */
    private fun isPocketCondition(s: SensorSnapshot, sensors: AvailableSensors) =
        (!sensors.proximity || s.isNear) && (!sensors.light || s.isDark)

    private fun stay(state: GuardState) = Transition(state, emptyList())

    private fun Long.msToNs() = this * NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

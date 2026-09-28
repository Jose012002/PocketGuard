package com.equipo.pocketguard.decision

/** Órdenes que la decisión emite y la actuación ejecuta. La decisión nunca actúa por sí misma. */
sealed interface Effect {
    data object StartMonitoring : Effect
    data object StopMonitoring : Effect
    data object AcquireWakeLock : Effect
    data object ReleaseWakeLock : Effect
    data class SetSampling(val mode: SamplingMode) : Effect
    data object StartSiren : Effect
    data object StartStrobe : Effect
    data class Vibrate(val pattern: VibrationPattern) : Effect
    data object StopAll : Effect
    data object ShowPreAlarmUi : Effect
    data object ShowAlarmUi : Effect
    data object ClearAlarmUi : Effect
    data object UpdateStatusNotification : Effect
    data class ShowMessage(val code: MessageCode) : Effect
    data class Log(val type: EventType, val detail: String = "") : Effect
}

enum class SamplingMode { NORMAL, HIGH }

enum class VibrationPattern { CONFIRM, SOFT, ALARM }

enum class MessageCode { INSUFFICIENT_SENSORS, ARMING_TIMEOUT }

/** Tipos de evento del historial (RF-30). */
enum class EventType { ARMED, STORED, SUSPICION, PRE_ALARM, ALARM, DISARMED, TIMEOUT, AUTH_FAILED }

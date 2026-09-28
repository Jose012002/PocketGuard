package com.equipo.pocketguard.decision

/**
 * Estados de la máquina (sección 6.2). Los [AvailableSensors] viajan dentro de los estados que los
 * necesitan, así el motor es un reducer puro (ver docs/DECISIONES.md, D-06).
 */
sealed interface GuardState {
    data object Disarmed : GuardState

    data class Arming(
        val startedAtNs: Long,
        val pocketSinceNs: Long?,
        val sensors: AvailableSensors,
    ) : GuardState

    data class Stored(val baselineLux: Float, val sensors: AvailableSensors) : GuardState

    data class Suspicion(
        val openedAtNs: Long,
        val baselineLux: Float,
        val flags: Indicators,
        val sensors: AvailableSensors,
    ) : GuardState

    data class PreAlarm(val startedAtNs: Long) : GuardState

    data class Alarm(val startedAtNs: Long) : GuardState
}

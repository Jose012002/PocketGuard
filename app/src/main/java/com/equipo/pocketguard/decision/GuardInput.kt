package com.equipo.pocketguard.decision

import com.equipo.pocketguard.processing.SensorSnapshot

/** Entradas del reducer. Todas las marcas de tiempo usan el reloj de `elapsedRealtimeNanos`. */
sealed interface GuardInput {
    data class Arm(val sensors: AvailableSensors, val nowNs: Long) : GuardInput

    data class Disarm(val authOk: Boolean) : GuardInput

    data class Snapshot(val snapshot: SensorSnapshot) : GuardInput

    data class Tick(val nowNs: Long) : GuardInput
}

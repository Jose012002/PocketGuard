package com.equipo.pocketguard.actuation

import com.equipo.pocketguard.decision.VibrationPattern

/** Forma de una vibración: tiempos apagado/encendido alternados (el primero es la espera) y desde dónde se repite (-1 = no repite). */
data class VibrationSpec(val timings: List<Long>, val repeatIndex: Int)

/** Patrones de la sección 8.3. Kotlin puro para poder probarlos sin un `Vibrator`. */
object VibrationPatterns {
    fun specFor(pattern: VibrationPattern): VibrationSpec = when (pattern) {
        VibrationPattern.CONFIRM -> VibrationSpec(listOf(0, 150), repeatIndex = -1)
        VibrationPattern.SOFT -> VibrationSpec(listOf(0, 200, 800), repeatIndex = 0)
        VibrationPattern.ALARM -> VibrationSpec(listOf(0, 800, 200), repeatIndex = 0)
    }
}

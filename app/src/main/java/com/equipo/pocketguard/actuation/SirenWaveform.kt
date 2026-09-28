package com.equipo.pocketguard.actuation

import kotlin.math.PI
import kotlin.math.sin

/**
 * Sintetiza la sirena: onda senoidal cuya frecuencia sube de [minHz] a [maxHz] y vuelve a bajar
 * en ciclos de [cycleSeconds] (sección 8.3). Mantiene la fase entre llamadas, así que el audio es
 * continuo sin importar el tamaño de cada bloque. Kotlin puro.
 */
class SirenWaveform(
    val sampleRate: Int = 44_100,
    private val minHz: Double = 600.0,
    private val maxHz: Double = 1_200.0,
    cycleSeconds: Double = 1.0,
    amplitude: Double = 0.9,
) {
    private val samplesPerCycle = (sampleRate * cycleSeconds).toInt()
    private val peak = amplitude * Short.MAX_VALUE
    private var phase = 0.0
    private var indexInCycle = 0

    /** Rellena `buffer[0 until count]` con los siguientes `count` muestras PCM de 16 bits. */
    fun fill(buffer: ShortArray, count: Int = buffer.size) {
        for (i in 0 until count) {
            val hz = frequencyAt(indexInCycle.toDouble() / samplesPerCycle)
            phase += TWO_PI * hz / sampleRate
            if (phase >= TWO_PI) phase -= TWO_PI
            buffer[i] = (sin(phase) * peak).toInt().toShort()
            indexInCycle = (indexInCycle + 1) % samplesPerCycle
        }
    }

    /** Frecuencia en la posición `cyclePosition` (0 a 1) del ciclo: triangular, mínima al inicio y máxima a la mitad. */
    fun frequencyAt(cyclePosition: Double): Double {
        val sweep = if (cyclePosition < 0.5) cyclePosition * 2 else (1 - cyclePosition) * 2
        return minHz + (maxHz - minHz) * sweep
    }

    private companion object {
        const val TWO_PI = 2 * PI
    }
}

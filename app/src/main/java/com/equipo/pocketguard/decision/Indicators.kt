package com.equipo.pocketguard.decision

import com.equipo.pocketguard.processing.SensorSnapshot
import kotlin.math.max

/** Qué sensores tiene el dispositivo. */
data class AvailableSensors(val proximity: Boolean, val light: Boolean, val accelerometer: Boolean) {
    /** RF-07: el acelerómetro es obligatorio y se necesita al menos proximidad o luz. */
    val meetsMinimum: Boolean get() = accelerometer && (proximity || light)
}

data class Indicators(val proximityFar: Boolean, val lightJump: Boolean, val motion: Boolean) {
    infix fun or(o: Indicators) = Indicators(proximityFar || o.proximityFar, lightJump || o.lightJump, motion || o.motion)

    val anyActive: Boolean get() = proximityFar || lightJump || motion

    /**
     * Todos los indicadores disponibles están activos. Un indicador cuyo sensor no existe
     * se considera satisfecho (modo degradado, sección 6.3).
     */
    fun isCompleteFor(sensors: AvailableSensors): Boolean =
        (!sensors.proximity || proximityFar) &&
            (!sensors.light || lightJump) &&
            (!sensors.accelerometer || motion)

    companion object {
        val None = Indicators(proximityFar = false, lightJump = false, motion = false)

        /** Indicadores del snapshot `s` con luz base `baselineLux`. Un sensor ausente nunca activa su indicador. */
        fun evaluate(
            s: SensorSnapshot,
            baselineLux: Float,
            sensors: AvailableSensors,
            config: DetectionConfig,
        ): Indicators {
            val lux = s.lux
            return Indicators(
                proximityFar = sensors.proximity && !s.isNear,
                lightJump = sensors.light && lux != null &&
                    lux >= max(baselineLux * config.lightJumpFactor, config.lightJumpMinLux),
                motion = sensors.accelerometer && s.motion >= config.motionThreshold,
            )
        }
    }
}

package com.equipo.pocketguard.processing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Parámetros de filtrado. El servicio los toma de `DetectionConfig`. */
data class ProcessingConfig(
    val darkEnterLux: Float,
    val darkExitLux: Float,
    val luxSmoothingAlpha: Float,
)

/** Qué sensores tiene el dispositivo y cómo hay que interpretar sus lecturas. */
data class SensorHardware(
    /** Rango máximo del sensor de proximidad en cm; `null` si no hay sensor. */
    val proximityMaxRangeCm: Float?,
    val hasLightSensor: Boolean,
    /** `true` si se usa `TYPE_LINEAR_ACCELERATION`; `false` si hay que quitar la gravedad. */
    val hasLinearAcceleration: Boolean,
)

/**
 * Guarda la última lectura de cada sensor y emite un [SensorSnapshot] cada vez que llega cualquiera
 * (sección 8.2). Es Kotlin puro y no es seguro para uso concurrente: una sola colección a la vez.
 */
class SignalProcessor(
    config: ProcessingConfig,
    private val hardware: SensorHardware,
) {
    private val luxSmoother = ExponentialSmoother(config.luxSmoothingAlpha)
    private val darkDetector = Hysteresis(config.darkEnterLux, config.darkExitLux)
    private val gravityFilter = GravityFilter()

    // Con sensor presente pero sin lectura todavía, se asume "lejos" y "claro": nunca se adelanta el bolsillo.
    private var distanceCm: Float? = null
    private var isNear = !hardware.hasProximity
    private var lux: Float? = null
    private var isDark = !hardware.hasLightSensor
    private var motion = 0f

    private val hardwareNearLimitCm: Float =
        minOf(hardware.proximityMaxRangeCm ?: NEAR_LIMIT_CM, NEAR_LIMIT_CM)

    /** Procesa una lectura y devuelve el estado fusionado. Las lecturas de sensores ausentes no afectan al snapshot. */
    fun onReading(reading: SensorReading): SensorSnapshot {
        when (reading) {
            is SensorReading.Proximity -> if (hardware.hasProximity) {
                distanceCm = reading.distanceCm
                isNear = reading.distanceCm < hardwareNearLimitCm
            }
            is SensorReading.Light -> if (hardware.hasLightSensor) {
                val smoothed = luxSmoother.update(reading.lux)
                lux = smoothed
                isDark = darkDetector.update(smoothed)
            }
            is SensorReading.Acceleration -> {
                motion = if (hardware.hasLinearAcceleration) {
                    magnitude(reading.x, reading.y, reading.z)
                } else {
                    gravityFilter.update(reading.x, reading.y, reading.z)
                }
            }
        }
        return SensorSnapshot(
            isNear = isNear,
            distanceCm = distanceCm,
            lux = lux,
            isDark = isDark,
            motion = motion,
            timestampNs = reading.timestampNs,
        )
    }

    /** Descarta todo el estado interno. */
    fun reset() {
        luxSmoother.reset()
        darkDetector.reset()
        gravityFilter.reset()
        distanceCm = null
        isNear = !hardware.hasProximity
        lux = null
        isDark = !hardware.hasLightSensor
        motion = 0f
    }

    /** Convierte el flujo de lecturas en snapshots. Cada colección empieza con el estado limpio. */
    fun process(readings: Flow<SensorReading>): Flow<SensorSnapshot> =
        readings.onStart { reset() }.map(::onReading)

    private val SensorHardware.hasProximity: Boolean get() = proximityMaxRangeCm != null

    private companion object {
        /** Umbral máximo de "cerca" (sección 8.2). Con `min` con el rango del sensor cubre los binarios. */
        const val NEAR_LIMIT_CM = 5f
    }
}

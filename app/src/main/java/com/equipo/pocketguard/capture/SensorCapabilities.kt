package com.equipo.pocketguard.capture

import com.equipo.pocketguard.decision.AvailableSensors
import com.equipo.pocketguard.processing.SensorHardware

enum class SensorKind { PROXIMITY, LIGHT, ACCELEROMETER }

/** Cuánto puede hacer el dispositivo con los sensores que tiene (RF-07 y RF-08). */
enum class SensorSupport {
    /** Tiene los tres sensores. */
    FULL,

    /** Falta proximidad o luz: funciona con los sensores disponibles (modo degradado). */
    DEGRADED,

    /** Falta el acelerómetro, o faltan proximidad y luz a la vez: no se puede armar. */
    UNSUPPORTED,
}

/** Sensores y flash que tiene el dispositivo. Es un tipo de datos puro para poder probarlo sin Android. */
data class SensorCapabilities(
    /** Rango máximo del sensor de proximidad en cm; `null` si el dispositivo no tiene sensor. */
    val proximityMaxRangeCm: Float?,
    val hasLight: Boolean,
    val hasAccelerometer: Boolean,
    val hasLinearAcceleration: Boolean,
    val hasFlash: Boolean,
) {
    val hasProximity: Boolean get() = proximityMaxRangeCm != null

    /** Hay alguna fuente de movimiento: la aceleración lineal se prefiere, pero el acelerómetro también sirve. */
    val hasMotionSensor: Boolean get() = hasAccelerometer || hasLinearAcceleration

    val missing: Set<SensorKind>
        get() = buildSet {
            if (!hasProximity) add(SensorKind.PROXIMITY)
            if (!hasLight) add(SensorKind.LIGHT)
            if (!hasMotionSensor) add(SensorKind.ACCELEROMETER)
        }

    val support: SensorSupport
        get() = when {
            !toAvailableSensors().meetsMinimum -> SensorSupport.UNSUPPORTED
            missing.isEmpty() -> SensorSupport.FULL
            else -> SensorSupport.DEGRADED
        }

    /** RF-08: solo se puede armar si se cumple el mínimo de RF-07. */
    val canArm: Boolean get() = support != SensorSupport.UNSUPPORTED

    fun toAvailableSensors() = AvailableSensors(
        proximity = hasProximity,
        light = hasLight,
        accelerometer = hasMotionSensor,
    )

    fun toHardware() = SensorHardware(
        proximityMaxRangeCm = proximityMaxRangeCm,
        hasLightSensor = hasLight,
        hasLinearAcceleration = hasLinearAcceleration,
    )
}

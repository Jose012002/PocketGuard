package com.equipo.pocketguard.processing

/**
 * Lectura cruda de un sensor, ya desacoplada de `android.hardware.SensorEvent`. La capa de captura
 * las produce y `SignalProcessor` las consume (RNF-11: se comunican por tipos de datos, no por clases concretas).
 */
sealed interface SensorReading {
    /** `SystemClock.elapsedRealtimeNanos()` en el momento de recibir el evento. */
    val timestampNs: Long

    data class Proximity(val distanceCm: Float, override val timestampNs: Long) : SensorReading

    data class Light(val lux: Float, override val timestampNs: Long) : SensorReading

    /** Aceleración en m/s². Es lineal (sin gravedad) o cruda según [SensorHardware.hasLinearAcceleration]. */
    data class Acceleration(
        val x: Float,
        val y: Float,
        val z: Float,
        override val timestampNs: Long,
    ) : SensorReading
}

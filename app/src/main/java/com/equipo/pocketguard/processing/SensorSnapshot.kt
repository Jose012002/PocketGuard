package com.equipo.pocketguard.processing

/** Estado fusionado de todos los sensores en un instante. Es Kotlin puro: lo consume la capa de decisión. */
data class SensorSnapshot(
    /** Proximidad; `true` si el sensor no existe. */
    val isNear: Boolean,
    /** Valor crudo de proximidad, para el monitor. */
    val distanceCm: Float?,
    /** Lux suavizado con EMA; `null` si no hay sensor de luz. */
    val lux: Float?,
    /** Salida del detector con histéresis; `true` si no hay sensor de luz. */
    val isDark: Boolean,
    /** Magnitud de la aceleración lineal en m/s². */
    val motion: Float,
    /** `SystemClock.elapsedRealtimeNanos()` al recibir el evento. */
    val timestampNs: Long,
)

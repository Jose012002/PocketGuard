package com.equipo.pocketguard.processing

import kotlin.math.sqrt

/**
 * Filtro pasa-altos para el dispositivo sin aceleración lineal: estima la gravedad con un pasa-bajos
 * (`gravity = alpha·gravity + (1 − alpha)·raw`) y la resta (`linear = raw − gravity`).
 * La gravedad arranca con la primera muestra para no producir un falso movimiento al iniciar.
 */
class GravityFilter(private val alpha: Float = DEFAULT_ALPHA) {

    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var initialized = false

    /** Devuelve la magnitud de la aceleración lineal (sin gravedad) en m/s². */
    fun update(x: Float, y: Float, z: Float): Float {
        if (!initialized) {
            gx = x
            gy = y
            gz = z
            initialized = true
        } else {
            gx = alpha * gx + (1f - alpha) * x
            gy = alpha * gy + (1f - alpha) * y
            gz = alpha * gz + (1f - alpha) * z
        }
        return magnitude(x - gx, y - gy, z - gz)
    }

    fun reset() {
        initialized = false
        gx = 0f
        gy = 0f
        gz = 0f
    }

    companion object {
        /** Valor fijado por la sección 8.2. */
        const val DEFAULT_ALPHA = 0.8f
    }
}

internal fun magnitude(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)

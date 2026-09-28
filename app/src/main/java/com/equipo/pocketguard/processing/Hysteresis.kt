package com.equipo.pocketguard.processing

/**
 * Detector oscuro/claro con dos umbrales para evitar rebotes: pasa a oscuro si `lux < enterBelow`
 * y a claro si `lux > exitAbove`. Entre ambos umbrales conserva el estado anterior.
 */
class Hysteresis(
    private val enterBelow: Float,
    private val exitAbove: Float,
    private val initial: Boolean = false,
) {

    init {
        require(exitAbove > enterBelow) { "exitAbove ($exitAbove) debe ser mayor que enterBelow ($enterBelow)" }
    }

    /** `true` mientras se considera oscuro. */
    var isActive: Boolean = initial
        private set

    fun update(lux: Float): Boolean {
        if (isActive) {
            if (lux > exitAbove) isActive = false
        } else {
            if (lux < enterBelow) isActive = true
        }
        return isActive
    }

    fun reset() {
        isActive = initial
    }
}

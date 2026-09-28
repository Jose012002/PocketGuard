package com.equipo.pocketguard.processing

/**
 * Filtro de media móvil exponencial: `y = alpha·x + (1 − alpha)·y_anterior`.
 * Con `alpha = 1` no suaviza nada; con valores bajos suaviza más. La primera muestra pasa sin filtrar.
 */
class ExponentialSmoother(private val alpha: Float) {

    init {
        require(alpha > 0f && alpha <= 1f) { "alpha debe estar en (0, 1], se recibió $alpha" }
    }

    /** Último valor suavizado, o `null` si aún no llegó ninguna muestra. */
    var value: Float? = null
        private set

    fun update(sample: Float): Float {
        val previous = value
        val next = if (previous == null) sample else alpha * sample + (1f - alpha) * previous
        value = next
        return next
    }

    fun reset() {
        value = null
    }
}

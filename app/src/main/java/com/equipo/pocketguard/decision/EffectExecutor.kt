package com.equipo.pocketguard.decision

/**
 * Quien ejecuta los [Effect] de actuación. La capa de actuación lo implementa y el servicio lo usa,
 * así ninguna de las dos depende de la clase concreta de la otra (RNF-11).
 */
fun interface EffectExecutor {
    /** Ejecuta los efectos en orden. [state] es el estado resultante de la transición que los produjo. */
    fun execute(effects: List<Effect>, state: GuardState)
}

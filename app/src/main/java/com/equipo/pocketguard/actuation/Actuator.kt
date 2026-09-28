package com.equipo.pocketguard.actuation

/** Un actuador físico. `start()` y `stop()` son idempotentes y no lanzan excepciones al llamador. */
interface Actuator {
    fun start()

    fun stop()
}

package com.equipo.pocketguard.decision

/**
 * Da acceso a la configuración vigente sin que las capas dependan de cómo se guarda (DataStore, en la fase 7).
 * Se lee en el momento de usarla, así un cambio en Ajustes se aplica en la siguiente armada.
 */
fun interface DetectionConfigProvider {
    fun current(): DetectionConfig
}

package com.equipo.pocketguard.capture

import com.equipo.pocketguard.decision.SamplingMode
import com.equipo.pocketguard.processing.SensorReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge

/**
 * Origen de lecturas de sensores. Cada `Flow` registra su listener al empezar a colectarse y lo
 * desregistra al cancelarse, así que sin colectores no queda ningún listener (RNF-05).
 * Un sensor que no existe produce un `Flow` vacío.
 */
interface SensorDataSource {
    fun proximity(): Flow<SensorReading.Proximity>

    fun light(): Flow<SensorReading.Light>

    /** Aceleración lineal si existe; si no, acelerómetro crudo. La tasa de muestreo depende de [mode] (RNF-06). */
    fun acceleration(mode: SamplingMode): Flow<SensorReading.Acceleration>

    /**
     * Las tres fuentes fusionadas. Cuando cambia [samplingMode] solo se vuelve a registrar el
     * acelerómetro; proximidad y luz siguen registradas, así no se pierde su último valor (RF-17).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun readings(samplingMode: Flow<SamplingMode>): Flow<SensorReading> = merge(
        proximity(),
        light(),
        samplingMode.distinctUntilChanged().flatMapLatest { acceleration(it) },
    )
}

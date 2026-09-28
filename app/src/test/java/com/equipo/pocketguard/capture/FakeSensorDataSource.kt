package com.equipo.pocketguard.capture

import com.equipo.pocketguard.decision.SamplingMode
import com.equipo.pocketguard.processing.SensorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart

/** Origen de sensores falso: las pruebas emiten lecturas a mano y comprueban cuántos colectores hay. */
class FakeSensorDataSource : SensorDataSource {
    val prox = MutableSharedFlow<SensorReading.Proximity>(extraBufferCapacity = 64)
    val light = MutableSharedFlow<SensorReading.Light>(extraBufferCapacity = 64)
    val accel = MutableSharedFlow<SensorReading.Acceleration>(extraBufferCapacity = 64)
    val accelModes = mutableListOf<SamplingMode>()

    override fun proximity(): Flow<SensorReading.Proximity> = prox

    override fun light(): Flow<SensorReading.Light> = light

    override fun acceleration(mode: SamplingMode): Flow<SensorReading.Acceleration> =
        accel.onStart { accelModes += mode }

    /** Cuántos listeners hay activos en total. */
    val activeListeners: Int
        get() = prox.subscriptionCount.value + light.subscriptionCount.value + accel.subscriptionCount.value
}

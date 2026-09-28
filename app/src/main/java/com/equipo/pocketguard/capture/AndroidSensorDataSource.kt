package com.equipo.pocketguard.capture

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.equipo.pocketguard.decision.SamplingMode
import com.equipo.pocketguard.processing.SensorReading
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

private const val TAG = "PocketGuard"

/** Implementación de [SensorDataSource] sobre `SensorManager` (sección 8.1). */
@Singleton
class AndroidSensorDataSource @Inject constructor(
    private val sensorManager: SensorManager,
) : SensorDataSource {

    override fun proximity(): Flow<SensorReading.Proximity> {
        val sensor = sensorManager.defaultProximity() ?: return emptyFlow()
        return listen(sensor, "proximity", SensorManager.SENSOR_DELAY_NORMAL) { event, nowNs ->
            SensorReading.Proximity(distanceCm = event.values[0], timestampNs = nowNs)
        }
    }

    override fun light(): Flow<SensorReading.Light> {
        val sensor = sensorManager.defaultLight() ?: return emptyFlow()
        return listen(sensor, "light", SensorManager.SENSOR_DELAY_NORMAL) { event, nowNs ->
            SensorReading.Light(lux = event.values[0], timestampNs = nowNs)
        }
    }

    override fun acceleration(mode: SamplingMode): Flow<SensorReading.Acceleration> {
        val sensor = sensorManager.defaultLinearAcceleration() ?: sensorManager.defaultAccelerometer()
            ?: return emptyFlow()
        val delay = when (mode) {
            SamplingMode.NORMAL -> SensorManager.SENSOR_DELAY_UI
            SamplingMode.HIGH -> SensorManager.SENSOR_DELAY_GAME
        }
        return listen(sensor, "motion", delay) { event, nowNs ->
            SensorReading.Acceleration(
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
                timestampNs = nowNs,
            )
        }
    }

    /**
     * Registra el listener al colectar y lo desregistra en `awaitClose`. Los eventos llegan en un hilo
     * propio para no cargar el hilo principal. Sin batching (`maxReportLatencyUs = 0`).
     * La marca de tiempo es `elapsedRealtimeNanos()` al recibir el evento, el mismo reloj que usan los ticks.
     */
    private fun <T : SensorReading> listen(
        sensor: Sensor,
        name: String,
        samplingPeriodUs: Int,
        toReading: (SensorEvent, Long) -> T,
    ): Flow<T> = callbackFlow {
        val thread = HandlerThread("PocketGuard-$name").apply { start() }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(toReading(event, SystemClock.elapsedRealtimeNanos()))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        val registered = sensorManager.registerListener(
            listener,
            sensor,
            samplingPeriodUs,
            MAX_REPORT_LATENCY_US,
            Handler(thread.looper),
        )
        if (!registered) {
            thread.quitSafely()
            close(IllegalStateException("No se pudo registrar el sensor $name"))
            return@callbackFlow
        }
        Log.d(TAG, "Listener registrado: $name (${sensor.name}, delay=$samplingPeriodUs)")
        awaitClose {
            sensorManager.unregisterListener(listener)
            thread.quitSafely()
            Log.d(TAG, "Listener liberado: $name")
        }
    }.buffer(BUFFER_SIZE, BufferOverflow.DROP_OLDEST)

    private companion object {
        const val MAX_REPORT_LATENCY_US = 0
        const val BUFFER_SIZE = 64
    }
}

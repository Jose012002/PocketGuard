package com.equipo.pocketguard.capture

import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PocketGuard"

/** Proximidad: se prefiere la variante wake-up para que siga entregando eventos con el CPU dormido. */
internal fun SensorManager.defaultProximity(): Sensor? =
    getDefaultSensor(Sensor.TYPE_PROXIMITY, true) ?: getDefaultSensor(Sensor.TYPE_PROXIMITY)

internal fun SensorManager.defaultLight(): Sensor? = getDefaultSensor(Sensor.TYPE_LIGHT)

internal fun SensorManager.defaultLinearAcceleration(): Sensor? =
    getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

internal fun SensorManager.defaultAccelerometer(): Sensor? = getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

/** RF-06: detecta qué sensores existen y si hay flash. */
@Singleton
class SensorCapabilityChecker @Inject constructor(
    private val sensorManager: SensorManager,
    private val packageManager: PackageManager,
) {
    fun check(): SensorCapabilities {
        val capabilities = SensorCapabilities(
            proximityMaxRangeCm = sensorManager.defaultProximity()?.maximumRange,
            hasLight = sensorManager.defaultLight() != null,
            hasAccelerometer = sensorManager.defaultAccelerometer() != null,
            hasLinearAcceleration = sensorManager.defaultLinearAcceleration() != null,
            hasFlash = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
        )
        Log.d(TAG, "Capacidades: $capabilities (${capabilities.support})")
        return capabilities
    }
}

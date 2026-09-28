package com.equipo.pocketguard.actuation

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PocketGuard"

/** Acceso de bajo nivel a la linterna. Separado de [TorchActuator] para poder probar el estroboscopio sin cámara. */
interface TorchDriver {
    /** `true` si el dispositivo tiene una cámara con flash. */
    val isAvailable: Boolean

    /** Enciende o apaga la linterna. Puede lanzar una excepción si la cámara está ocupada. */
    fun setTorch(on: Boolean)
}

/** Usa `CameraManager.setTorchMode`, que no requiere el permiso `CAMERA` (sección 8.3). */
@Singleton
class AndroidTorchDriver @Inject constructor(
    private val cameraManager: CameraManager,
) : TorchDriver {

    private val cameraId: String? by lazy { findFlashCamera() }

    override val isAvailable: Boolean get() = cameraId != null

    override fun setTorch(on: Boolean) {
        val id = cameraId ?: return
        cameraManager.setTorchMode(id, on)
    }

    /** Busca una cámara con flash, prefiriendo la trasera. */
    private fun findFlashCamera(): String? = try {
        val withFlash = cameraManager.cameraIdList.filter { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    } catch (e: CameraAccessException) {
        Log.w(TAG, "No se pudo consultar las cámaras", e)
        null
    }
}

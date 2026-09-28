package com.equipo.pocketguard.actuation

import android.util.Log
import com.equipo.pocketguard.decision.DetectionConfigProvider
import com.equipo.pocketguard.di.ActuationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PocketGuard"

/**
 * Flash estroboscópico: alterna la linterna cada `strobeIntervalMs`. Si no hay flash o la cámara está
 * ocupada, la alarma sigue con los demás actuadores (RNF-16): nunca lanza excepciones al llamador.
 */
@Singleton
class TorchActuator @Inject constructor(
    private val driver: TorchDriver,
    private val configProvider: DetectionConfigProvider,
    @param:ActuationScope private val scope: CoroutineScope,
) : Actuator {

    private var job: Job? = null

    @Synchronized
    override fun start() {
        if (job?.isActive == true) return
        if (!driver.isAvailable) {
            Log.i(TAG, "Sin flash disponible: la alarma continúa sin estroboscopio")
            return
        }
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin() // evita que el apagado de una corrida anterior pise a esta
            strobe()
        }
    }

    @Synchronized
    override fun stop() {
        // Se conserva la referencia: un start() inmediato espera a que termine el apagado de esta corrida.
        job?.cancel()
    }

    private suspend fun strobe() {
        var failing = false
        fun set(on: Boolean) {
            try {
                driver.setTorch(on)
                failing = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Cámara en uso u otro fallo: se reintenta en el siguiente ciclo; se registra una vez por racha.
                if (!failing) Log.w(TAG, "No se pudo cambiar la linterna", e)
                failing = true
            }
        }

        try {
            var on = false
            while (true) {
                on = !on
                set(on)
                delay(configProvider.current().strobeIntervalMs)
            }
        } finally {
            withContext(NonCancellable) { set(false) }
        }
    }
}

package com.equipo.pocketguard.service

import android.os.SystemClock
import android.util.Log
import com.equipo.pocketguard.capture.SensorCapabilities
import com.equipo.pocketguard.capture.SensorDataSource
import com.equipo.pocketguard.data.ArmedStateStore
import com.equipo.pocketguard.data.GuardStateRepository
import com.equipo.pocketguard.data.eventlog.EventRecorder
import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.DetectionConfigProvider
import com.equipo.pocketguard.decision.Effect
import com.equipo.pocketguard.decision.EffectExecutor
import com.equipo.pocketguard.decision.EventType
import com.equipo.pocketguard.decision.GuardInput
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.SamplingMode
import com.equipo.pocketguard.decision.TheftDetectionEngine
import com.equipo.pocketguard.processing.ProcessingConfig
import com.equipo.pocketguard.processing.SensorHardware
import com.equipo.pocketguard.processing.SignalProcessor
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "PocketGuard"

/**
 * Orquesta el pipeline entrada → procesamiento → decisión → actuación (sección 7.5).
 *
 * Todas las entradas de la máquina de estados (snapshots, ticks y comandos) pasan por un único
 * [Channel] que consume una sola corrutina, así el estado se toca en serie y no hay carreras.
 * No depende de `Service`: `GuardService` es solo su envoltorio con el ciclo de vida de Android.
 */
class GuardPipeline(
    private val scope: CoroutineScope,
    private val sensors: SensorDataSource,
    private val capabilities: () -> SensorCapabilities,
    private val configProvider: DetectionConfigProvider,
    private val executor: EffectExecutor,
    private val wakeLock: WakeLockController,
    private val repository: GuardStateRepository,
    private val recorder: EventRecorder,
    private val armedStore: ArmedStateStore,
    /** Se invoca cuando el pipeline queda desarmado y el servicio debe cerrarse. Puede llamarse varias veces. */
    private val onStopped: () -> Unit,
    private val nowNs: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val tickIntervalMs: Long = TICK_INTERVAL_MS,
) {
    private val inputs = Channel<GuardInput>(Channel.UNLIMITED)
    private val sampling = MutableStateFlow(SamplingMode.NORMAL)

    // Solo los toca la corrutina del bucle.
    private var state: GuardState = GuardState.Disarmed
    private var engine: TheftDetectionEngine? = null
    private var config: DetectionConfig = DetectionConfig.Default
    private var monitorJob: Job? = null
    private var tickerJob: Job? = null
    private var loopJob: Job? = null

    // Los escriben quienes envían comandos y los lee el bucle: la escritura precede al envío por el canal.
    @Volatile
    private var hardware: SensorHardware = SensorHardware(null, false, false)

    @Volatile
    private var needsTicks = false

    /** Empieza a consumir entradas. */
    fun start() {
        if (loopJob != null) return
        loopJob = scope.launch {
            for (input in inputs) handle(input)
        }
    }

    /** Envía `Arm` con los sensores que tiene el dispositivo ahora. */
    fun arm() {
        val caps = capabilities()
        hardware = caps.toHardware()
        inputs.trySend(GuardInput.Arm(caps.toAvailableSensors(), nowNs()))
    }

    fun disarm(authOk: Boolean) {
        inputs.trySend(GuardInput.Disarm(authOk))
    }

    /** Tras un reinicio del servicio por el sistema: vuelve a armar si estaba armado; si no, se cierra. */
    fun restoreOrStop() {
        scope.launch {
            if (armedStore.isArmed()) arm() else onStopped()
        }
    }

    /** Libera todo sin pasar por la máquina de estados (el servicio se está destruyendo). */
    fun shutdown() {
        monitorJob?.cancel()
        tickerJob?.cancel()
        loopJob?.cancel()
        inputs.close()
        wakeLock.release()
    }

    private suspend fun handle(input: GuardInput) {
        val previous = state
        if (input is GuardInput.Arm && previous == GuardState.Disarmed) {
            // La configuración se congela al armar: los ajustes están bloqueados mientras está armado.
            config = configProvider.current().coerced()
            engine = TheftDetectionEngine(config)
        }
        val current = engine
        if (current == null) {
            // Un comando llegó sin que nada estuviera armado (por ejemplo, un desarme a un servicio recién creado).
            if (input is GuardInput.Disarm) onStopped()
            return
        }

        val transition = current.reduce(previous, input)
        state = transition.state
        needsTicks = state is GuardState.Arming || state is GuardState.Suspicion || state is GuardState.PreAlarm

        if (state != previous) {
            Log.d(TAG, "Transición ${label(previous)} → ${label(state)} (${describe(input)})") // RNF-15
            repository.updateState(state)
        }
        applyEffects(transition.effects, input)

        if (previous == GuardState.Disarmed && state != GuardState.Disarmed) armedStore.setArmed(true)
        val disarmedByThisInput = state == GuardState.Disarmed &&
            (previous != GuardState.Disarmed || input is GuardInput.Arm || input is GuardInput.Disarm)
        if (disarmedByThisInput) {
            armedStore.setArmed(false)
            onStopped()
        }
    }

    private fun applyEffects(effects: List<Effect>, input: GuardInput) {
        for (effect in effects) {
            when (effect) {
                Effect.StartMonitoring -> startMonitoring()
                Effect.StopMonitoring -> stopMonitoring()
                Effect.AcquireWakeLock -> wakeLock.acquire()
                Effect.ReleaseWakeLock -> wakeLock.release()
                is Effect.SetSampling -> sampling.value = effect.mode
                is Effect.Log -> recordEvent(effect, input)
                is Effect.ShowMessage -> {
                    repository.emitMessage(effect.code)
                    executor.execute(listOf(effect), state)
                }
                else -> executor.execute(listOf(effect), state)
            }
        }
    }

    private fun startMonitoring() {
        val processor = SignalProcessor(
            ProcessingConfig(config.darkEnterLux, config.darkExitLux, config.luxSmoothingAlpha),
            hardware,
        )
        sampling.value = SamplingMode.NORMAL
        monitorJob?.cancel()
        monitorJob = scope.launch {
            try {
                sensors.readings(sampling).map(processor::onReading).collect { snapshot ->
                    repository.publishSnapshot(snapshot)
                    inputs.trySend(GuardInput.Snapshot(snapshot))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin sensores no se puede vigilar: es mejor desarmar que aparentar que se vigila.
                Log.e(TAG, "Falló la captura de sensores; se desarma", e)
                inputs.trySend(GuardInput.Disarm(authOk = true))
            }
        }
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(tickIntervalMs)
                if (needsTicks) inputs.trySend(GuardInput.Tick(nowNs()))
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        tickerJob?.cancel()
        monitorJob = null
        tickerJob = null
        sampling.value = SamplingMode.NORMAL
    }

    /** Registra el evento; en la alarma directa desde un snapshot añade la latencia (RNF-01). */
    private fun recordEvent(effect: Effect.Log, input: GuardInput) {
        val detail = if (effect.type == EventType.ALARM && input is GuardInput.Snapshot) {
            val latencyMs = (nowNs() - input.snapshot.timestampNs) / NANOS_PER_MILLI
            "${effect.detail} latencyMs=${String.format(Locale.ROOT, "%.1f", latencyMs)}".trim()
        } else {
            effect.detail
        }
        recorder.record(effect.type, detail)
    }

    private fun label(state: GuardState) = state::class.simpleName

    private fun describe(input: GuardInput) = when (input) {
        is GuardInput.Arm -> "Arm"
        is GuardInput.Disarm -> "Disarm(authOk=${input.authOk})"
        is GuardInput.Snapshot -> "Snapshot"
        is GuardInput.Tick -> "Tick"
    }

    companion object {
        /** Frecuencia del ticker (sección 7.5). */
        const val TICK_INTERVAL_MS = 100L
        private const val NANOS_PER_MILLI = 1_000_000.0
    }
}

package com.equipo.pocketguard.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipo.pocketguard.capture.SensorCapabilities
import com.equipo.pocketguard.capture.SensorCapabilityChecker
import com.equipo.pocketguard.capture.SensorDataSource
import com.equipo.pocketguard.capture.TraceRecorder
import com.equipo.pocketguard.data.GuardStateRepository
import com.equipo.pocketguard.data.SettingsRepository
import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.Indicators
import com.equipo.pocketguard.decision.SamplingMode
import com.equipo.pocketguard.processing.ProcessingConfig
import com.equipo.pocketguard.processing.SensorSnapshot
import com.equipo.pocketguard.processing.SignalProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn

/** De dónde vienen los valores que muestra el monitor. */
enum class MonitorSource {
    /** La alarma está desarmada: el monitor registra los sensores mientras la pantalla está visible. */
    LIVE,

    /** La alarma vigila: se muestran las lecturas del servicio, sin registrar listeners duplicados. */
    SERVICE,
}

data class MonitorUiState(
    val guardState: GuardState = GuardState.Disarmed,
    val snapshot: SensorSnapshot? = null,
    val indicators: Indicators = Indicators.None,
    val config: DetectionConfig = DetectionConfig.Default,
    val capabilities: SensorCapabilities? = null,
    val source: MonitorSource = MonitorSource.LIVE,
    val recording: Boolean = false,
    /** Última traza grabada, lista para compartir. */
    val lastTrace: File? = null,
)

/**
 * Indicadores que ve el monitor: en sospecha, los acumulados por la ventana; en cualquier otro estado, los
 * que se cumplen en este instante. Con la alarma no guardada no hay luz base, así que el salto de luz se
 * mide contra 0 (equivale a "supera el mínimo").
 */
fun monitorIndicators(
    state: GuardState,
    snapshot: SensorSnapshot?,
    config: DetectionConfig,
    capabilities: SensorCapabilities?,
): Indicators {
    if (snapshot == null || capabilities == null) return Indicators.None
    return when (state) {
        is GuardState.Suspicion -> state.flags
        is GuardState.Stored -> Indicators.evaluate(snapshot, state.baselineLux, state.sensors, config)
        else -> Indicators.evaluate(snapshot, 0f, capabilities.toAvailableSensors(), config)
    }
}

/** CU-08 y CU-11: valores en vivo (al menos 10 veces por segundo) y grabación de trazas. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val sensors: SensorDataSource,
    checker: SensorCapabilityChecker,
    private val repository: GuardStateRepository,
    private val settings: SettingsRepository,
    private val recorder: TraceRecorder,
) : ViewModel() {

    private val capabilities = checker.check()
    private val lastTrace = MutableStateFlow<File?>(null)

    private val guardState: Flow<GuardState> = repository.state

    /** Lecturas del servicio si está vigilando; si no, las del propio monitor mientras la pantalla se vea. */
    private val snapshots: Flow<SensorSnapshot?> = guardState
        .map { it == GuardState.Disarmed }
        .flatMapLatest { disarmed -> if (disarmed) liveSnapshots() else repository.uiState.map { it.snapshot } }

    val state: StateFlow<MonitorUiState> = combine(
        guardState,
        snapshots
            .onEach { snapshot -> if (snapshot != null) recorder.record(snapshot, repository.uiState.value.state.name()) }
            .sample(REFRESH_MS)
            // Emite ya, sin esperar la primera lectura: así el estado y el botón de grabar responden de inmediato.
            .onStart { emit(null) },
        recorder.isRecording,
        lastTrace,
    ) { guard, snapshot, recording, trace ->
        val config = settings.current()
        MonitorUiState(
            guardState = guard,
            snapshot = snapshot,
            indicators = monitorIndicators(guard, snapshot, config, capabilities),
            config = config,
            capabilities = capabilities,
            source = if (guard == GuardState.Disarmed) MonitorSource.LIVE else MonitorSource.SERVICE,
            recording = recording,
            lastTrace = trace,
        )
    }.stateIn(
        viewModelScope,
        // Sin tiempo de gracia: al salir de la pantalla se sueltan los listeners (CU-08).
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
        MonitorUiState(guardState = repository.uiState.value.state, capabilities = capabilities),
    )

    fun startRecording() {
        recorder.start()
        lastTrace.value = null
    }

    fun stopRecording() {
        recorder.stop()?.let { lastTrace.value = it }
    }

    override fun onCleared() {
        if (recorder.isRecording.value) recorder.stop()
    }

    private fun liveSnapshots(): Flow<SensorSnapshot?> {
        val config = settings.current()
        val processor = SignalProcessor(
            ProcessingConfig(config.darkEnterLux, config.darkExitLux, config.luxSmoothingAlpha),
            capabilities.toHardware(),
        )
        return sensors.readings(flowOf(SamplingMode.NORMAL)).map(processor::onReading)
    }

    private fun GuardState.name(): String = this::class.simpleName ?: "?"

    private companion object {
        /** Máximo 20 actualizaciones por segundo: sobrado para el mínimo de 10 y sin recomponer en cada lectura. */
        const val REFRESH_MS = 50L
    }
}

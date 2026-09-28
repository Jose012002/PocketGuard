package com.equipo.pocketguard.capture

import android.content.Context
import com.equipo.pocketguard.processing.SensorSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Graba las lecturas fusionadas en un CSV para calibrar los umbrales con el teléfono real (CU-11, RF-31).
 * Los archivos van a `filesDir/traces/trace_<fecha>.csv`. La interfaz solo lo ofrece en compilaciones debug.
 */
@Singleton
class TraceRecorder(
    private val tracesDir: File,
    private val clockMs: () -> Long,
) {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(File(context.filesDir, DIRECTORY), System::currentTimeMillis)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var writer: BufferedWriter? = null
    private var file: File? = null
    private var firstTimestampNs: Long? = null

    /** Empieza a grabar y devuelve el archivo. Si ya se estaba grabando, devuelve el mismo. */
    @Synchronized
    fun start(): File {
        file?.takeIf { writer != null }?.let { return it }
        tracesDir.mkdirs()
        val target = uniqueFile()
        writer = target.bufferedWriter().also { it.appendLine(HEADER) }
        file = target
        firstTimestampNs = null
        _isRecording.value = true
        return target
    }

    /** Añade una fila. Sin grabación activa no hace nada. [stateName] es el estado de la alarma en ese instante. */
    @Synchronized
    fun record(snapshot: SensorSnapshot, stateName: String) {
        val out = writer ?: return
        val first = firstTimestampNs ?: snapshot.timestampNs.also { firstTimestampNs = it }
        val elapsedMs = (snapshot.timestampNs - first) / NANOS_PER_MILLI
        out.appendLine(
            listOf(
                elapsedMs,
                snapshot.timestampNs,
                snapshot.distanceCm ?: "",
                snapshot.isNear,
                snapshot.rawLux ?: snapshot.lux ?: "",
                snapshot.isDark,
                snapshot.motion,
                stateName,
            ).joinToString(","),
        )
    }

    /** Termina la grabación y devuelve el archivo completo, o `null` si no se estaba grabando. */
    @Synchronized
    fun stop(): File? {
        val out = writer ?: return null
        out.close()
        writer = null
        _isRecording.value = false
        return file
    }

    /** Trazas guardadas, la más reciente primero. */
    fun listTraces(): List<File> =
        tracesDir.listFiles { f -> f.isFile && f.extension == "csv" }?.sortedByDescending { it.name } ?: emptyList()

    private fun uniqueFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date(clockMs()))
        var candidate = File(tracesDir, "trace_$stamp.csv")
        var n = 1
        while (candidate.exists()) candidate = File(tracesDir, "trace_${stamp}_${n++}.csv")
        return candidate
    }

    companion object {
        const val DIRECTORY = "traces"
        const val HEADER = "elapsed_ms,timestamp_ns,distance_cm,is_near,lux,is_dark,motion,state"
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

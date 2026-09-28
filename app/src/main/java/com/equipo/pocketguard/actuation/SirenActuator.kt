package com.equipo.pocketguard.actuation

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.equipo.pocketguard.di.ActuationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PocketGuard"

/**
 * Sirena sintetizada con `AudioTrack` por el canal de alarma, así suena aunque el teléfono esté en
 * silencio o vibración (RF-23). Sube el volumen de alarma al máximo y lo restaura al detenerse (RF-25).
 */
@Singleton
class SirenActuator @Inject constructor(
    private val audioManager: AudioManager,
    @param:ActuationScope private val scope: CoroutineScope,
) : Actuator {

    private var job: Job? = null
    private var previousVolume: Int? = null

    @Synchronized
    override fun start() {
        if (job?.isActive == true) return
        maximizeVolume()
        job = scope.launch(Dispatchers.IO) { play() }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
        restoreVolume()
    }

    private fun maximizeVolume() {
        try {
            // Solo se guarda el volumen original una vez: un start() repetido no debe guardar el máximo.
            if (previousVolume == null) previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo subir el volumen de alarma", e)
        }
    }

    private fun restoreVolume() {
        val volume = previousVolume ?: return
        previousVolume = null
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo restaurar el volumen de alarma", e)
        }
    }

    private suspend fun play() {
        val waveform = SirenWaveform()
        val track = buildTrack(waveform.sampleRate)
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack no se pudo inicializar")
            track.release()
            return
        }
        try {
            track.play()
            val chunk = ShortArray(waveform.sampleRate / CHUNKS_PER_SECOND)
            while (currentCoroutineContext().isActive) {
                waveform.fill(chunk)
                track.write(chunk, 0, chunk.size)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Fallo al escribir la sirena", e)
        } finally {
            // pause + flush corta el sonido de inmediato; stop() dejaría sonar lo ya escrito.
            runCatching {
                track.pause()
                track.flush()
            }
            track.release()
        }
    }

    private fun buildTrack(sampleRate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferBytes = maxOf(minBuffer, sampleRate * BYTES_PER_SAMPLE / BUFFERS_PER_SECOND)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) // RNF-01
            .build()
    }

    private companion object {
        const val CHUNKS_PER_SECOND = 50 // bloques de 20 ms
        const val BUFFERS_PER_SECOND = 10 // búfer de 100 ms
        const val BYTES_PER_SAMPLE = 2
    }
}

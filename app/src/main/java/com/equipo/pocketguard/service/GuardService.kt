package com.equipo.pocketguard.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.equipo.pocketguard.actuation.AlarmNotifier
import com.equipo.pocketguard.capture.SensorCapabilityChecker
import com.equipo.pocketguard.capture.SensorDataSource
import com.equipo.pocketguard.data.ArmedStateStore
import com.equipo.pocketguard.data.GuardStateRepository
import com.equipo.pocketguard.data.eventlog.EventRecorder
import com.equipo.pocketguard.decision.DetectionConfigProvider
import com.equipo.pocketguard.decision.EffectExecutor
import com.equipo.pocketguard.decision.GuardState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val TAG = "PocketGuard"

/**
 * Servicio en primer plano que mantiene viva la vigilancia con la pantalla apagada o la app cerrada
 * (RF-18, RNF-08). Solo aporta el ciclo de vida de Android; la lógica está en [GuardPipeline].
 */
@AndroidEntryPoint
class GuardService : Service() {

    @Inject lateinit var sensors: SensorDataSource

    @Inject lateinit var capabilityChecker: SensorCapabilityChecker

    @Inject lateinit var configProvider: DetectionConfigProvider

    @Inject lateinit var executor: EffectExecutor

    @Inject lateinit var wakeLock: WakeLockController

    @Inject lateinit var repository: GuardStateRepository

    @Inject lateinit var recorder: EventRecorder

    @Inject lateinit var armedStore: ArmedStateStore

    @Inject lateinit var notifier: AlarmNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var pipeline: GuardPipeline

    override fun onCreate() {
        super.onCreate()
        pipeline = GuardPipeline(
            scope = scope,
            sensors = sensors,
            capabilities = capabilityChecker::check,
            configProvider = configProvider,
            executor = executor,
            wakeLock = wakeLock,
            repository = repository,
            recorder = recorder,
            armedStore = armedStore,
            onStopped = ::stopFromPipeline,
        )
        pipeline.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            GuardCommands.ACTION_ARM -> {
                if (!enterForeground()) return START_NOT_STICKY
                pipeline.arm()
            }
            GuardCommands.ACTION_DISARM ->
                pipeline.disarm(authOk = intent.getBooleanExtra(GuardCommands.EXTRA_AUTH_OK, false))
            // Reinicio por el sistema tras matar el proceso (Intent nulo): se rearma si estaba armado.
            null -> {
                if (!enterForeground()) return START_NOT_STICKY
                pipeline.restoreOrStop()
            }
        }
        // La tarea de la app puede cerrarse desde recientes sin que el servicio se detenga (RNF-08).
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pipeline.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Debe llamarse pronto tras `startForegroundService` o Android aborta la app. Falla si el sistema no
     * permite iniciar servicios en primer plano desde segundo plano (Android 12+).
     */
    private fun enterForeground(): Boolean {
        val current = repository.uiState.value.state
        val shown = if (current == GuardState.Disarmed) {
            GuardState.Arming(startedAtNs = 0, pocketSinceNs = null, sensors = capabilityChecker.check().toAvailableSensors())
        } else {
            current
        }
        return try {
            // El tipo se toma del manifiesto (specialUse); en Android 8 a 9 se ignora.
            ServiceCompat.startForeground(
                this,
                AlarmNotifier.STATUS_ID,
                notifier.buildStatusNotification(shown),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
            )
            true
        } catch (e: IllegalStateException) {
            Log.e(TAG, "El sistema no permitió iniciar el servicio en primer plano", e)
            stopSelf()
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Falta un permiso para el servicio en primer plano", e)
            stopSelf()
            false
        }
    }

    /** El pipeline terminó (desarmado, tiempo agotado o sensores insuficientes): se quita la notificación y se cierra. */
    private fun stopFromPipeline() {
        ContextCompat.getMainExecutor(this).execute {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
}

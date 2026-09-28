package com.equipo.pocketguard.actuation

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.equipo.pocketguard.R
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.MessageCode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Contrato entre las notificaciones y la pantalla de alarma (fase 8), sin que `actuation` dependa de `ui`. */
object AlarmScreenContract {
    const val ACTIVITY_CLASS_NAME = "com.equipo.pocketguard.ui.AlarmActivity"
    const val EXTRA_MODE = "com.equipo.pocketguard.extra.MODE"

    /** Desbloqueo durante la pre-alarma (CU-05). */
    const val MODE_PRE_ALARM = "pre_alarm"

    /** Desbloqueo para detener la alarma (CU-06). */
    const val MODE_ALARM = "alarm"

    /** Desbloqueo para desarmar manualmente desde la notificación de estado (CU-07). */
    const val MODE_DISARM = "disarm"
}

/** Notificaciones de estado, pre-alarma y alarma, con sus tres canales (sección 8.3). */
@Singleton
class AlarmNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_STATUS,
                    context.getString(R.string.channel_status_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_status_description)
                },
                NotificationChannel(
                    CHANNEL_PRE_ALARM,
                    context.getString(R.string.channel_pre_alarm_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_pre_alarm_description)
                    // El sonido y la vibración los aportan los actuadores, no el canal.
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(
                    CHANNEL_ALARM,
                    context.getString(R.string.channel_alarm_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_alarm_description)
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            ),
        )
    }

    /** Notificación persistente del servicio (`startForeground`); su id es [STATUS_ID]. */
    fun buildStatusNotification(state: GuardState): Notification =
        NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(statusTextRes(state)))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(alarmScreenIntent(AlarmScreenContract.MODE_DISARM, REQUEST_STATUS_OPEN))
            .addAction(
                0,
                context.getString(R.string.notif_action_disarm),
                alarmScreenIntent(AlarmScreenContract.MODE_DISARM, REQUEST_STATUS_DISARM),
            )
            .build()

    fun updateStatus(state: GuardState) = notify(STATUS_ID, buildStatusNotification(state))

    fun showPreAlarm() = notify(
        PRE_ALARM_ID,
        alertBuilder(CHANNEL_PRE_ALARM, AlarmScreenContract.MODE_PRE_ALARM, REQUEST_PRE_ALARM)
            .setContentTitle(context.getString(R.string.notif_pre_alarm_title))
            .setContentText(context.getString(R.string.notif_pre_alarm_text))
            .setAutoCancel(false)
            .build(),
    )

    /** Alarma: no descartable y con pantalla completa si el sistema lo permite. */
    fun showAlarm() {
        notificationManager.cancel(PRE_ALARM_ID)
        notify(
            ALARM_ID,
            alertBuilder(CHANNEL_ALARM, AlarmScreenContract.MODE_ALARM, REQUEST_ALARM)
                .setContentTitle(context.getString(R.string.notif_alarm_title))
                .setContentText(context.getString(R.string.notif_alarm_text))
                .setOngoing(true)
                .setAutoCancel(false)
                .build(),
        )
    }

    /** Retira las notificaciones de pre-alarma y alarma. */
    fun clearAlarmUi() {
        notificationManager.cancel(PRE_ALARM_ID)
        notificationManager.cancel(ALARM_ID)
    }

    /** Avisos puntuales al dueño (por ejemplo, el tiempo de armado agotado). */
    fun showMessage(code: MessageCode) {
        val text = context.getString(
            when (code) {
                MessageCode.INSUFFICIENT_SENSORS -> R.string.message_insufficient_sensors
                MessageCode.ARMING_TIMEOUT -> R.string.message_arming_timeout
            },
        )
        notify(
            MESSAGE_ID,
            NotificationCompat.Builder(context, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun alertBuilder(channel: String, mode: String, requestCode: Int): NotificationCompat.Builder {
        val contentIntent = alarmScreenIntent(mode, requestCode)
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setColor(ContextCompat.getColor(context, R.color.alert_red))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .apply { if (canUseFullScreenIntent()) setFullScreenIntent(contentIntent, true) }
    }

    /** Desde Android 14 el usuario puede revocar el permiso de pantalla completa; entonces basta el heads-up. */
    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notificationManager.canUseFullScreenIntent()

    private fun alarmScreenIntent(mode: String, requestCode: Int): PendingIntent {
        val intent = Intent()
            .setClassName(context.packageName, AlarmScreenContract.ACTIVITY_CLASS_NAME)
            .putExtra(AlarmScreenContract.EXTRA_MODE, mode)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Sin el permiso de notificaciones (Android 13+) no se publica nada, pero el servicio sigue funcionando. */
    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: Notification) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            notificationManager.notify(id, notification)
        }
    }

    private fun statusTextRes(state: GuardState): Int = when (state) {
        GuardState.Disarmed -> R.string.notif_status_disarmed
        is GuardState.Arming -> R.string.notif_status_arming
        is GuardState.Stored -> R.string.notif_status_stored
        is GuardState.Suspicion -> R.string.notif_status_suspicion
        is GuardState.PreAlarm -> R.string.notif_status_pre_alarm
        is GuardState.Alarm -> R.string.notif_status_alarm
    }

    companion object {
        const val CHANNEL_STATUS = "guard_status"
        const val CHANNEL_PRE_ALARM = "pre_alarm"
        const val CHANNEL_ALARM = "alarm"

        const val STATUS_ID = 1
        const val PRE_ALARM_ID = 2
        const val ALARM_ID = 3
        const val MESSAGE_ID = 4

        private const val REQUEST_STATUS_OPEN = 10
        private const val REQUEST_STATUS_DISARM = 11
        private const val REQUEST_PRE_ALARM = 12
        private const val REQUEST_ALARM = 13
    }
}

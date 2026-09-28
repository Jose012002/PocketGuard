package com.equipo.pocketguard.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.equipo.pocketguard.R
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.ui.theme.GuardColors

/** Cómo se muestra cada estado (sección 9). El color siempre va acompañado de texto e ícono. */
data class GuardStateUi(
    @param:StringRes val label: Int,
    @param:StringRes val hint: Int,
    val color: Color,
    val icon: ImageVector,
)

fun GuardState.toUi(): GuardStateUi = when (this) {
    GuardState.Disarmed -> GuardStateUi(
        R.string.state_disarmed, R.string.state_disarmed_hint, GuardColors.Disarmed, Icons.Filled.LockOpen,
    )
    is GuardState.Arming -> GuardStateUi(
        R.string.state_arming, R.string.state_arming_hint, GuardColors.Arming, Icons.Filled.HourglassTop,
    )
    is GuardState.Stored -> GuardStateUi(
        R.string.state_stored, R.string.state_stored_hint, GuardColors.Stored, Icons.Filled.Shield,
    )
    is GuardState.Suspicion -> GuardStateUi(
        R.string.state_suspicion, R.string.state_suspicion_hint, GuardColors.Suspicion, Icons.Filled.Visibility,
    )
    is GuardState.PreAlarm -> GuardStateUi(
        R.string.state_pre_alarm, R.string.state_pre_alarm_hint, GuardColors.PreAlarm, Icons.Filled.NotificationsActive,
    )
    is GuardState.Alarm -> GuardStateUi(
        R.string.state_alarm, R.string.state_alarm_hint, GuardColors.Alarm, Icons.Filled.NotificationsActive,
    )
}

/** Milisegundos que quedan del periodo de gracia; nunca negativo. Los tiempos usan `elapsedRealtimeNanos`. */
fun graceRemainingMs(startedAtNs: Long, nowNs: Long, graceMs: Long): Long =
    (graceMs - (nowNs - startedAtNs) / NANOS_PER_MILLI).coerceIn(0L, graceMs)

private const val NANOS_PER_MILLI = 1_000_000L

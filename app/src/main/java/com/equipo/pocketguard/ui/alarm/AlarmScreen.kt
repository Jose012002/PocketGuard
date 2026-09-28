package com.equipo.pocketguard.ui.alarm

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipo.pocketguard.BuildConfig
import com.equipo.pocketguard.R
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.ui.graceRemainingMs
import com.equipo.pocketguard.ui.theme.GuardColors
import kotlin.math.ceil
import kotlinx.coroutines.delay

private const val COUNTDOWN_STEP_MS = 100L

/**
 * Pantalla completa de pre-alarma y alarma (sección 9): fondo rojo, pad de PIN, biometría y, solo en
 * compilaciones debug, un botón para detener sin PIN. Con otro estado (modo "desarmar" desde la
 * notificación de estado) usa colores neutros. Se cierra sola al llegar a `Desarmado`.
 */
@Composable
fun AlarmScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state == GuardState.Disarmed) onFinished()
    }

    val alerting = state is GuardState.PreAlarm || state is GuardState.Alarm
    val background = when (state) {
        is GuardState.Alarm -> GuardColors.Alarm
        is GuardState.PreAlarm -> GuardColors.PreAlarm
        else -> MaterialTheme.colorScheme.background
    }
    val contentColor = if (alerting) Color.White else MaterialTheme.colorScheme.onBackground

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        if (alerting) {
            Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.padding(4.dp),
            )
        }
        AlarmHeader(state, viewModel.graceMs, contentColor)

        UnlockSection(
            autoBiometric = alerting,
            errorColor = if (alerting) Color.White else MaterialTheme.colorScheme.error,
        )

        if (BuildConfig.DEBUG) {
            OutlinedButton(onClick = viewModel::debugStop) {
                Text(stringResource(R.string.alarm_debug_stop))
            }
        }
    }
}

@Composable
private fun AlarmHeader(state: GuardState, graceMs: Long, color: Color) {
    val title: String
    val subtitle: String
    when (state) {
        is GuardState.PreAlarm -> {
            title = stringResource(R.string.alarm_pre_alarm_title)
            subtitle = stringResource(R.string.alarm_pre_alarm_countdown, rememberGraceSeconds(state, graceMs))
        }
        is GuardState.Alarm -> {
            title = stringResource(R.string.alarm_alarm_title)
            subtitle = stringResource(R.string.alarm_alarm_subtitle)
        }
        else -> {
            title = stringResource(R.string.alarm_disarm_mode_title)
            subtitle = stringResource(R.string.unlock_title)
        }
    }
    Text(title, style = MaterialTheme.typography.headlineLarge, color = color, textAlign = TextAlign.Center)
    Text(subtitle, style = MaterialTheme.typography.titleMedium, color = color, textAlign = TextAlign.Center)
}

/** Segundos (redondeados hacia arriba) que faltan para que suene la alarma; se actualiza cada 100 ms. */
@Composable
private fun rememberGraceSeconds(state: GuardState.PreAlarm, graceMs: Long): Int {
    var remainingMs by remember(state) {
        mutableLongStateOf(graceRemainingMs(state.startedAtNs, SystemClock.elapsedRealtimeNanos(), graceMs))
    }
    LaunchedEffect(state, graceMs) {
        while (remainingMs > 0) {
            delay(COUNTDOWN_STEP_MS)
            remainingMs = graceRemainingMs(state.startedAtNs, SystemClock.elapsedRealtimeNanos(), graceMs)
        }
    }
    return ceil(remainingMs / 1000.0).toInt()
}

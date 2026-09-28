package com.equipo.pocketguard.ui.monitor

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipo.pocketguard.BuildConfig
import com.equipo.pocketguard.R
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.processing.SensorSnapshot
import com.equipo.pocketguard.ui.toUi
import java.io.File
import java.util.Locale

/** CU-08: proximidad, luz, movimiento, estado e indicadores de la ventana de sospecha, en vivo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val caps = ui.capabilities
    val snapshot = ui.snapshot

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.monitor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val stateUi = ui.guardState.toUi()
            Text(
                stringResource(R.string.monitor_state, stringResource(stateUi.label)),
                style = MaterialTheme.typography.titleLarge,
                color = stateUi.color,
            )
            Text(
                stringResource(
                    if (ui.source == MonitorSource.LIVE) R.string.monitor_source_live else R.string.monitor_source_service,
                ),
                style = MaterialTheme.typography.bodySmall,
            )

            ProximityCard(snapshot, available = caps?.hasProximity != false)
            LightCard(snapshot, available = caps?.hasLight != false)
            MotionCard(snapshot, threshold = ui.config.motionThreshold)

            val baseline = (ui.guardState as? GuardState.Stored)?.baselineLux
                ?: (ui.guardState as? GuardState.Suspicion)?.baselineLux
            if (baseline != null) {
                Text(stringResource(R.string.monitor_baseline, format(baseline, 1)), style = MaterialTheme.typography.bodyMedium)
            }

            Text(stringResource(R.string.monitor_indicators_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                IndicatorChip(stringResource(R.string.indicator_proximity_far), ui.indicators.proximityFar, Modifier.weight(1f))
                IndicatorChip(stringResource(R.string.indicator_light_jump), ui.indicators.lightJump, Modifier.weight(1f))
                IndicatorChip(stringResource(R.string.indicator_motion), ui.indicators.motion, Modifier.weight(1f))
            }

            if (BuildConfig.DEBUG) {
                TraceControls(
                    recording = ui.recording,
                    lastTrace = ui.lastTrace,
                    onStart = viewModel::startRecording,
                    onStop = viewModel::stopRecording,
                )
            }
        }
    }
}

@Composable
private fun ProximityCard(snapshot: SensorSnapshot?, available: Boolean) {
    ValueCard(stringResource(R.string.monitor_proximity)) {
        when {
            !available -> Text(stringResource(R.string.sensor_missing))
            snapshot?.distanceCm == null -> Text(stringResource(R.string.monitor_waiting))
            else -> {
                Text(stringResource(R.string.monitor_cm, format(snapshot.distanceCm, 1)), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (snapshot.isNear) R.string.monitor_near else R.string.monitor_far))
            }
        }
    }
}

@Composable
private fun LightCard(snapshot: SensorSnapshot?, available: Boolean) {
    ValueCard(stringResource(R.string.monitor_light)) {
        when {
            !available -> Text(stringResource(R.string.sensor_missing))
            snapshot?.lux == null -> Text(stringResource(R.string.monitor_waiting))
            else -> {
                Text(
                    stringResource(R.string.monitor_lux_smoothed, format(snapshot.lux, 1)),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(R.string.monitor_lux_raw, format(snapshot.rawLux ?: snapshot.lux, 1)))
                Text(stringResource(if (snapshot.isDark) R.string.monitor_dark else R.string.monitor_bright))
            }
        }
    }
}

@Composable
private fun MotionCard(snapshot: SensorSnapshot?, threshold: Float) {
    val motion = snapshot?.motion ?: 0f
    // La barra llega al doble del umbral: el umbral queda a la mitad.
    val fraction = (motion / (threshold * 2f)).coerceIn(0f, 1f)
    val overThreshold = motion >= threshold
    val description = stringResource(R.string.monitor_motion_description, format(motion, 2), format(threshold, 1))
    ValueCard(stringResource(R.string.monitor_motion)) {
        Text(stringResource(R.string.monitor_ms2, format(motion, 2)), style = MaterialTheme.typography.headlineSmall)
        LinearProgressIndicator(
            progress = { fraction },
            color = if (overThreshold) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        )
        Text(stringResource(R.string.monitor_motion_threshold, format(threshold, 1)), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ValueCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Indicador de la ventana: ícono y texto de estado, no solo color. */
@Composable
private fun IndicatorChip(label: String, active: Boolean, modifier: Modifier = Modifier) {
    val status = stringResource(if (active) R.string.indicator_active else R.string.indicator_inactive)
    Card(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $status" }) {
        Column(
            Modifier
                .padding(8.dp)
                .heightIn(min = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (active) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            )
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(status, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TraceControls(recording: Boolean, lastTrace: File?, onStart: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.monitor_traces_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.monitor_traces_hint), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = if (recording) onStop else onStart,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(if (recording) R.string.monitor_stop_recording else R.string.monitor_record)) }
            if (lastTrace != null && !recording) {
                OutlinedButton(
                    onClick = { shareTrace(context, lastTrace) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.monitor_share_trace, lastTrace.name)) }
            }
        }
    }
}

/** Comparte el CSV con un intent de envío a través del `FileProvider` de la compilación debug. */
private fun shareTrace(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun format(value: Float, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", value)

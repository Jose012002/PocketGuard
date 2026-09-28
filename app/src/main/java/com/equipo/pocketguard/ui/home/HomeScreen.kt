package com.equipo.pocketguard.ui.home

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipo.pocketguard.R
import com.equipo.pocketguard.capture.SensorCapabilities
import com.equipo.pocketguard.capture.SensorSupport
import com.equipo.pocketguard.decision.MessageCode
import com.equipo.pocketguard.ui.alarm.UnlockSection
import com.equipo.pocketguard.ui.toUi

/** CU-02 y CU-07: indicador de estado, botón de armar/desarmar y sensores disponibles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenMonitor: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showUnlock by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var notificationsEnabled by remember { mutableStateOf(areNotificationsEnabled(context)) }
    var batteryOptimized by remember { mutableStateOf(isBatteryOptimized(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = areNotificationsEnabled(context)
        batteryOptimized = isBatteryOptimized(context)
    }

    val timeoutMessage = stringResource(R.string.home_message_arming_timeout)
    val sensorsMessage = stringResource(R.string.home_message_insufficient_sensors)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { code ->
            snackbarHost.showSnackbar(
                when (code) {
                    MessageCode.ARMING_TIMEOUT -> timeoutMessage
                    MessageCode.INSUFFICIENT_SENSORS -> sensorsMessage
                },
            )
        }
    }
    // Al desarmarse el diálogo ya no tiene sentido.
    LaunchedEffect(ui.isArmed) { if (!ui.isArmed) showUnlock = false }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StateIndicator(ui.guardState)

            if (ui.isArmed) {
                Button(
                    onClick = { showUnlock = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.action_disarm)) }
            } else {
                Button(
                    onClick = viewModel::arm,
                    enabled = ui.canArm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.action_arm)) }
                if (!ui.capabilities.canArm) {
                    Text(
                        stringResource(R.string.home_unsupported_reason),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ShortcutButton(stringResource(R.string.nav_monitor), onOpenMonitor, Modifier.weight(1f))
                ShortcutButton(stringResource(R.string.nav_settings), onOpenSettings, Modifier.weight(1f))
                ShortcutButton(stringResource(R.string.nav_history), onOpenHistory, Modifier.weight(1f))
            }

            if (!notificationsEnabled) {
                NotificationsWarning(onOpenSettings = { openNotificationSettings(context) })
            }
            if (batteryOptimized) {
                BatteryHint(onOpenSettings = { openBatterySettings(context) })
            }
            SensorsCard(ui.capabilities)
        }
    }

    if (showUnlock) {
        Dialog(onDismissRequest = { showUnlock = false }) {
            Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.unlock_disarm_title), style = MaterialTheme.typography.titleLarge)
                    UnlockSection(onUnlocked = { showUnlock = false })
                    OutlinedButton(onClick = { showUnlock = false }) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}

/** Círculo con el color del estado y, debajo, su nombre y una pista. Nunca solo color (sección 9). */
@Composable
private fun StateIndicator(state: com.equipo.pocketguard.decision.GuardState) {
    val ui = state.toUi()
    val label = stringResource(ui.label)
    val hint = stringResource(ui.hint)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label. $hint" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(ui.color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ui.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
        }
        Text(label, style = MaterialTheme.typography.headlineLarge)
        Text(hint, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ShortcutButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, maxLines = 1)
    }
}

@Composable
private fun BatteryHint(onOpenSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_battery_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.home_battery_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_open_settings))
            }
        }
    }
}

@Composable
private fun SensorsCard(capabilities: SensorCapabilities) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_sensors_title), style = MaterialTheme.typography.titleMedium)
            SensorRow(stringResource(R.string.sensor_proximity), capabilities.hasProximity)
            SensorRow(stringResource(R.string.sensor_light), capabilities.hasLight)
            SensorRow(stringResource(R.string.sensor_accelerometer), capabilities.hasMotionSensor)
            SensorRow(stringResource(R.string.sensor_flash), capabilities.hasFlash)
            if (capabilities.support == SensorSupport.DEGRADED) {
                WarningText(stringResource(R.string.home_degraded_warning))
            }
            if (!capabilities.hasFlash) {
                Text(stringResource(R.string.home_no_flash_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SensorRow(name: String, available: Boolean) {
    val status = stringResource(if (available) R.string.sensor_available else R.string.sensor_missing)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$name: $status" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (available) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(name, modifier = Modifier.weight(1f))
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WarningText(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NotificationsWarning(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.NotificationsOff, contentDescription = null)
                Text(stringResource(R.string.home_notifications_off_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.home_notifications_off_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) }
        }
    }
}

private fun isBatteryOptimized(context: Context): Boolean =
    !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/** Abre la lista de optimización de batería del sistema; algunos fabricantes cierran servicios de forma agresiva. */
private fun openBatterySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun areNotificationsEnabled(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

/** Abre la pantalla de notificaciones de esta app en los ajustes del sistema. */
internal fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

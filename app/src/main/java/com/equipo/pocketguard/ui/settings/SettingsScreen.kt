package com.equipo.pocketguard.ui.settings

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipo.pocketguard.R
import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.SensitivityProfile
import java.util.Locale

/** CU-09: perfiles de sensibilidad, un deslizador por parámetro, biometría y restablecer valores. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (ui.locked) LockedBanner()

            ProfileSelector(ui.profile, enabled = !ui.locked, onSelect = viewModel::selectProfile)

            SettingFields.all.forEach { field ->
                SettingSlider(field, ui.config, enabled = !ui.locked) { value -> viewModel.onFieldChanged(field, value) }
            }

            BiometricRow(
                enabled = ui.biometricEnabled,
                supported = ui.biometricSupported,
                onChange = viewModel::setBiometricEnabled,
            )

            OutlinedButton(
                onClick = viewModel::reset,
                enabled = !ui.locked,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.settings_reset)) }
        }
    }
}

@Composable
private fun LockedBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null)
            Text(stringResource(R.string.settings_locked), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ProfileSelector(selected: SensitivityProfile?, enabled: Boolean, onSelect: (SensitivityProfile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_profile_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SensitivityProfile.entries.forEach { profile ->
                FilterChip(
                    selected = profile == selected,
                    onClick = { onSelect(profile) },
                    enabled = enabled,
                    label = { Text(stringResource(profile.label())) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Text(
            stringResource(if (selected == null) R.string.settings_profile_custom else R.string.settings_profile_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun SensitivityProfile.label(): Int = when (this) {
    SensitivityProfile.LOW -> R.string.profile_low
    SensitivityProfile.MEDIUM -> R.string.profile_medium
    SensitivityProfile.HIGH -> R.string.profile_high
}

/**
 * Deslizador de un parámetro. Mientras se arrastra solo cambia el valor local; se guarda al soltar,
 * para no escribir en disco en cada pixel.
 */
@Composable
private fun SettingSlider(field: SettingField, config: DetectionConfig, enabled: Boolean, onFinished: (Float) -> Unit) {
    val stored = field.read(config)
    var draft by remember(stored) { mutableFloatStateOf(stored) }
    val range = field.range(config)
    val unit = stringResource(field.unit)
    val valueText = formatValue(field.displayValue(draft), field.decimals, unit)
    val label = stringResource(field.label)
    val rangeText = stringResource(
        R.string.settings_range,
        formatValue(field.displayValue(range.start), field.decimals, unit),
        formatValue(field.displayValue(range.endInclusive), field.decimals, unit),
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleSmall)
        }
        Text(stringResource(field.description), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = draft.coerceIn(range.start, range.endInclusive),
            onValueChange = { draft = it },
            onValueChangeFinished = { onFinished(draft) },
            valueRange = range,
            steps = field.sliderSteps(config),
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueText
            },
        )
        Text(rangeText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BiometricRow(enabled: Boolean, supported: Boolean, onChange: (Boolean) -> Unit) {
    val title = stringResource(R.string.settings_biometric)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (supported) R.string.settings_biometric_desc else R.string.settings_biometric_unavailable),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = enabled && supported,
            onCheckedChange = onChange,
            enabled = supported,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

private fun formatValue(value: Float, decimals: Int, unit: String): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value) + if (unit.isBlank()) "" else " $unit"

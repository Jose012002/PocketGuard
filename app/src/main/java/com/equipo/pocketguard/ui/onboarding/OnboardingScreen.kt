package com.equipo.pocketguard.ui.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipo.pocketguard.R
import com.equipo.pocketguard.ui.components.PinDots
import com.equipo.pocketguard.ui.components.PinPad
import com.equipo.pocketguard.ui.home.openNotificationSettings
import com.equipo.pocketguard.security.PinPolicy

/** CU-01: introducción de 3 pasos, creación del PIN con confirmación y solicitud del permiso de notificaciones. */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state.step) {
            OnboardingStep.INTRO -> IntroStep(
                page = state.introPage,
                onNext = viewModel::nextIntroPage,
                onBack = viewModel::previousIntroPage,
            )
            OnboardingStep.CREATE_PIN -> PinStep(
                title = stringResource(R.string.onboarding_create_pin_title),
                subtitle = stringResource(R.string.onboarding_create_pin_subtitle),
                state = state,
                viewModel = viewModel,
            )
            OnboardingStep.CONFIRM_PIN -> PinStep(
                title = stringResource(R.string.onboarding_confirm_pin_title),
                subtitle = stringResource(R.string.onboarding_confirm_pin_subtitle),
                state = state,
                viewModel = viewModel,
            )
            OnboardingStep.PERMISSIONS -> PermissionsStep(
                granted = state.notificationsGranted,
                onResult = viewModel::onPermissionResult,
                onFinish = viewModel::finish,
            )
        }
    }
}

@Composable
private fun IntroStep(page: Int, onNext: () -> Unit, onBack: () -> Unit) {
    val (title, body) = when (page) {
        0 -> R.string.onboarding_intro_1_title to R.string.onboarding_intro_1_body
        1 -> R.string.onboarding_intro_2_title to R.string.onboarding_intro_2_body
        else -> R.string.onboarding_intro_3_title to R.string.onboarding_intro_3_body
    }
    Icon(
        Icons.Filled.Shield,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 32.dp),
    )
    Text(
        stringResource(R.string.onboarding_step_indicator, page + 1, OnboardingViewModel.INTRO_PAGES),
        style = MaterialTheme.typography.labelLarge,
    )
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
        if (page > 0) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_back))
            }
        }
        Button(onClick = onNext, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_next))
        }
    }
}

@Composable
private fun PinStep(title: String, subtitle: String, state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    PinDots(length = state.pin.length)
    Text(
        text = when (state.error) {
            OnboardingError.PIN_TOO_SHORT -> stringResource(R.string.onboarding_error_too_short)
            OnboardingError.PIN_MISMATCH -> stringResource(R.string.onboarding_error_mismatch)
            OnboardingError.SAVE_FAILED -> stringResource(R.string.onboarding_error_save_failed)
            null -> ""
        },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.heightIn(min = 40.dp),
    )
    PinPad(
        onDigit = viewModel::onDigit,
        onDelete = viewModel::onDelete,
        onSubmit = viewModel::submitPin,
        submitEnabled = state.pin.length >= PinPolicy.MIN_LENGTH,
        enabled = !state.saving,
    )
}

@Composable
private fun PermissionsStep(granted: Boolean?, onResult: (Boolean) -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    val needsRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsEnabled(context)

    Icon(
        Icons.Filled.Notifications,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 32.dp),
    )
    Text(
        stringResource(R.string.onboarding_permissions_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        stringResource(R.string.onboarding_permissions_body),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )

    if (granted == false) {
        // Flujo alternativo 5a: se avisa y se ofrece abrir los ajustes, pero no se bloquea el avance.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.NotificationsOff, contentDescription = null)
                Text(stringResource(R.string.onboarding_permissions_denied), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { openNotificationSettings(context) }) {
                    Text(stringResource(R.string.action_open_settings))
                }
            }
        }
        TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.onboarding_permissions_continue_anyway))
        }
    } else if (needsRequest) {
        Button(
            onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.onboarding_permissions_allow)) }
    } else {
        Button(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_continue))
        }
    }

    // Tras conceder el permiso se avanza sin más pasos.
    LaunchedEffect(granted) { if (granted == true) onFinish() }
}

private fun notificationsEnabled(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

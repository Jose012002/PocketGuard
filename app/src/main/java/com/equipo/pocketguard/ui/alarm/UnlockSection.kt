package com.equipo.pocketguard.ui.alarm

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipo.pocketguard.R

/**
 * Pad de PIN conectado a [UnlockViewModel]: PIN, límite de intentos y biometría. [onUnlocked] se llama
 * cuando el dueño se autenticó y ya se pidió el desarme. Con [autoBiometric] abre la biometría al mostrarse.
 */
@Composable
fun UnlockSection(
    modifier: Modifier = Modifier,
    autoBiometric: Boolean = false,
    errorColor: Color = MaterialTheme.colorScheme.error,
    onUnlocked: () -> Unit = {},
    viewModel: UnlockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val biometricAvailable by viewModel.biometricAvailable.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val biometricTitle = stringResource(R.string.unlock_biometric_title)
    val biometricNegative = stringResource(R.string.unlock_biometric_negative)

    val launchBiometric: (() -> Unit)? = if (biometricAvailable && activity != null) {
        { viewModel.authenticateWithBiometric(activity, biometricTitle, biometricNegative) }
    } else {
        null
    }

    DisposableEffect(viewModel) {
        viewModel.reset()
        onDispose { }
    }
    LaunchedEffect(viewModel) { viewModel.unlocked.collect { onUnlocked() } }
    LaunchedEffect(autoBiometric, biometricAvailable) {
        if (autoBiometric && biometricAvailable) launchBiometric?.invoke()
    }

    UnlockPanel(
        state = state,
        onDigit = viewModel::onDigit,
        onDelete = viewModel::onDelete,
        onSubmit = viewModel::submit,
        modifier = modifier,
        onBiometric = launchBiometric,
        errorColor = errorColor,
    )
}

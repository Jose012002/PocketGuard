package com.equipo.pocketguard.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.equipo.pocketguard.R
import com.equipo.pocketguard.ui.components.PinDots
import com.equipo.pocketguard.ui.components.PinPad
import kotlin.math.ceil

/** Puntos, mensaje de error y teclado. Lo usan el diálogo de inicio y la pantalla de alarma. */
@Composable
fun UnlockPanel(
    state: UnlockUiState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
    errorColor: Color = MaterialTheme.colorScheme.error,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PinDots(length = state.pin.length)

        // Reserva el alto del mensaje para que el teclado no salte al aparecer un error.
        Column(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.locked -> UnlockMessage(
                    stringResource(R.string.unlock_locked, ceil(state.lockedMs / 1000.0).toInt()),
                    errorColor,
                )
                state.wrongPinAttemptsLeft != null -> {
                    UnlockMessage(stringResource(R.string.unlock_wrong_pin), errorColor)
                    UnlockMessage(
                        pluralStringResource(
                            R.plurals.unlock_attempts_left,
                            state.wrongPinAttemptsLeft,
                            state.wrongPinAttemptsLeft,
                        ),
                        errorColor,
                    )
                }
            }
        }

        PinPad(
            onDigit = onDigit,
            onDelete = onDelete,
            onSubmit = onSubmit,
            submitEnabled = state.canSubmit,
            enabled = !state.locked && !state.submitting,
            onBiometric = onBiometric.takeUnless { state.locked },
        )
    }
}

@Composable
private fun UnlockMessage(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

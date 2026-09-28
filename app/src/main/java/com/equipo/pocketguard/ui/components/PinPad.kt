package com.equipo.pocketguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.equipo.pocketguard.R
import com.equipo.pocketguard.security.PinPolicy

private val KeySize = 72.dp

/** Puntos que muestran cuántos dígitos lleva el PIN, sin revelarlos. */
@Composable
fun PinDots(length: Int, modifier: Modifier = Modifier, maxLength: Int = PinPolicy.MAX_LENGTH) {
    val description = pluralStringResource(R.plurals.pin_digits_entered, length, length)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(maxLength) { index ->
            val filled = index < length
            Box(
                Modifier
                    .size(16.dp)
                    .background(if (filled) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/**
 * Teclado numérico con botón de borrar y de confirmar. Si [onBiometric] no es nulo, ocupa la esquina
 * izquierda de la última fila. Todas las teclas miden al menos 48 dp (RNF-14).
 */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    submitLabel: String = stringResource(R.string.action_confirm),
    onBiometric: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onBiometric != null) {
                FilledTonalIconButton(onClick = onBiometric, enabled = enabled, modifier = Modifier.size(KeySize)) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = stringResource(R.string.cd_use_biometric))
                }
            } else {
                Spacer(Modifier.size(KeySize))
            }
            DigitKey('0', enabled) { onDigit('0') }
            FilledTonalIconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(KeySize)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.cd_delete_digit))
            }
        }
        Button(
            onClick = onSubmit,
            enabled = enabled && submitEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .size(width = KeySize * 3 + 32.dp, height = 56.dp),
        ) {
            Text(submitLabel)
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(KeySize),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

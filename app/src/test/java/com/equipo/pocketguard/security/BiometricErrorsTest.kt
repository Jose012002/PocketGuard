package com.equipo.pocketguard.security

import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricErrorsTest {

    @Test
    fun `cancelar o elegir el PIN no es un error`() {
        assertTrue(BiometricErrors.isUserDismissal(BiometricPrompt.ERROR_USER_CANCELED))
        assertTrue(BiometricErrors.isUserDismissal(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertTrue(BiometricErrors.isUserDismissal(BiometricPrompt.ERROR_CANCELED))
    }

    @Test
    fun `los fallos reales si son errores`() {
        assertFalse(BiometricErrors.isUserDismissal(BiometricPrompt.ERROR_LOCKOUT))
        assertFalse(BiometricErrors.isUserDismissal(BiometricPrompt.ERROR_LOCKOUT_PERMANENT))
        assertFalse(BiometricErrors.isUserDismissal(BiometricPrompt.ERROR_HW_UNAVAILABLE))
        assertFalse(BiometricErrors.isUserDismissal(BiometricPrompt.ERROR_NO_BIOMETRICS))
        assertFalse(BiometricErrors.isUserDismissal(BiometricPrompt.ERROR_TIMEOUT))
    }
}

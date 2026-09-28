package com.equipo.pocketguard.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BiometricResult {
    data object Success : BiometricResult

    /** El usuario cerró el diálogo o eligió usar el PIN. No es un fallo. */
    data object Dismissed : BiometricResult

    /** Error real (sensor bloqueado, demasiados intentos, sin huellas registradas...). */
    data class Error(val code: Int, val message: String) : BiometricResult
}

/** Qué códigos de `BiometricPrompt` significan que el usuario simplemente no quiso continuar. */
object BiometricErrors {
    fun isUserDismissal(code: Int): Boolean =
        code == BiometricPrompt.ERROR_USER_CANCELED ||
            code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
            code == BiometricPrompt.ERROR_CANCELED
}

/**
 * Desarme con biometría (RF-03). El PIN siempre queda como respaldo: el diálogo trae un botón para usarlo.
 * La biometría solo confirma la identidad del dueño; no protege ninguna clave criptográfica.
 */
@Singleton
class BiometricAuthenticator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** `true` si el dispositivo tiene sensor y al menos una biometría registrada. */
    fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Muestra el diálogo del sistema. Devuelve una función para cancelarlo (por ejemplo, si la pantalla se cierra).
     * El resultado se entrega en el hilo principal. Los intentos fallidos no se reportan: el diálogo sigue abierto.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        negativeButtonText: String,
        onResult: (BiometricResult) -> Unit,
    ): () -> Unit {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(
                    if (BiometricErrors.isUserDismissal(errorCode)) {
                        BiometricResult.Dismissed
                    } else {
                        BiometricResult.Error(errorCode, errString.toString())
                    },
                )
            }
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
        return prompt::cancelAuthentication
    }

    private companion object {
        // Con botón negativo no se puede combinar con DEVICE_CREDENTIAL; el respaldo es el PIN de la app.
        const val AUTHENTICATORS = BIOMETRIC_STRONG or BIOMETRIC_WEAK
    }
}

package com.equipo.pocketguard.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.equipo.pocketguard.ui.alarm.AlarmScreen
import com.equipo.pocketguard.ui.theme.PocketGuardTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Pantalla de pre-alarma, alarma y desarme. Se muestra sobre la pantalla de bloqueo y enciende la pantalla
 * (RF-26). Hereda de `FragmentActivity` porque `BiometricPrompt` lo exige.
 */
@AndroidEntryPoint
class AlarmActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            PocketGuardTheme {
                AlarmScreen(onFinished = { finish() })
            }
        }
    }

    /** API 27+ tiene métodos propios; en API 26 se usan los flags de ventana equivalentes. */
    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}

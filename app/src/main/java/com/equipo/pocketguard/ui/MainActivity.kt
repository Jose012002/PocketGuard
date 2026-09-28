package com.equipo.pocketguard.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipo.pocketguard.ui.navigation.AppNavHost
import com.equipo.pocketguard.ui.theme.PocketGuardTheme
import dagger.hilt.android.AndroidEntryPoint

/** Actividad principal. Hereda de `FragmentActivity` porque `BiometricPrompt` lo exige. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketGuardTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val hasPin by viewModel.hasPin.collectAsStateWithLifecycle()
                    when (val known = hasPin) {
                        // Mientras se lee el almacenamiento no se muestra ninguna pantalla (evita parpadeos).
                        null -> Box(Modifier.fillMaxSize())
                        else -> AppNavHost(hasPin = known)
                    }
                }
            }
        }
    }
}

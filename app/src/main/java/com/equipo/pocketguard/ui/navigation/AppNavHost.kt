package com.equipo.pocketguard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.equipo.pocketguard.ui.home.HomeScreen
import com.equipo.pocketguard.ui.onboarding.OnboardingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
}

/** Navegación de la app. El destino inicial depende de si ya existe un PIN (CU-01). */
@Composable
fun AppNavHost(hasPin: Boolean, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (hasPin) Routes.HOME else Routes.ONBOARDING,
        modifier = modifier,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}

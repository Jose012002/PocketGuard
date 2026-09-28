package com.equipo.pocketguard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.equipo.pocketguard.ui.history.HistoryScreen
import com.equipo.pocketguard.ui.home.HomeScreen
import com.equipo.pocketguard.ui.monitor.MonitorScreen
import com.equipo.pocketguard.ui.onboarding.OnboardingScreen
import com.equipo.pocketguard.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val MONITOR = "monitor"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
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
            HomeScreen(
                onOpenMonitor = { navController.navigate(Routes.MONITOR) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.MONITOR) { MonitorScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.HISTORY) { HistoryScreen(onBack = { navController.popBackStack() }) }
    }
}

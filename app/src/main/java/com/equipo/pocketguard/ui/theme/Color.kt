package com.equipo.pocketguard.ui.theme

import androidx.compose.ui.graphics.Color

val PrimaryLight = Color(0xFF1B5E8C)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFCFE5FF)
val OnPrimaryContainerLight = Color(0xFF001D33)
val SecondaryLight = Color(0xFF526070)
val BackgroundLight = Color(0xFFF8F9FF)
val SurfaceLight = Color(0xFFF8F9FF)
val OnSurfaceLight = Color(0xFF191C20)
val ErrorLight = Color(0xFFBA1A1A)

val PrimaryDark = Color(0xFF9DCAFD)
val OnPrimaryDark = Color(0xFF00325A)
val PrimaryContainerDark = Color(0xFF00497B)
val OnPrimaryContainerDark = Color(0xFFCFE5FF)
val SecondaryDark = Color(0xFFBAC8DB)
val BackgroundDark = Color(0xFF111418)
val SurfaceDark = Color(0xFF111418)
val OnSurfaceDark = Color(0xFFE1E2E8)
val ErrorDark = Color(0xFFFFB4AB)

/** Colores de estado (sección 9 de la especificación). Siempre van acompañados de texto. */
object GuardColors {
    val Disarmed = Color(0xFF757575)
    val Arming = Color(0xFFFFB300)
    val Stored = Color(0xFF2E7D32)
    val Suspicion = Color(0xFFEF6C00)
    val PreAlarm = Color(0xFFC62828)
    val Alarm = Color(0xFFB71C1C)
}

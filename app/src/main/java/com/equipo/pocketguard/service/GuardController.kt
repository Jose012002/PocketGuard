package com.equipo.pocketguard.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que la interfaz puede pedirle al servicio. Es una interfaz para poder probar las pantallas sin Android. */
interface GuardController {
    /** Arma la alarma. Debe llamarse desde una acción del usuario con la app en primer plano. */
    fun arm()

    /** Desarma. [authOk] es `false` cuando el PIN o la biometría fallaron: solo se registra el intento. */
    fun disarm(authOk: Boolean)
}

@Singleton
class ServiceGuardController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : GuardController {
    override fun arm() = GuardCommands.arm(context)

    override fun disarm(authOk: Boolean) = GuardCommands.disarm(context, authOk)
}

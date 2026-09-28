package com.equipo.pocketguard.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Órdenes que la interfaz envía al servicio mediante Intents. El servicio no se exporta: solo esta app puede enviarlas. */
object GuardCommands {
    const val ACTION_ARM = "com.equipo.pocketguard.action.ARM"
    const val ACTION_DISARM = "com.equipo.pocketguard.action.DISARM"

    /** Resultado de la autenticación que hizo la interfaz. Si falta se asume `false`. */
    const val EXTRA_AUTH_OK = "com.equipo.pocketguard.extra.AUTH_OK"

    /** Arma la alarma. Debe llamarse desde una acción del usuario con la app en primer plano. */
    fun arm(context: Context) {
        ContextCompat.startForegroundService(context, intent(context, ACTION_ARM))
    }

    /** Desarma la alarma. [authOk] es `false` cuando el PIN o la biometría fallaron (queda registrado). */
    fun disarm(context: Context, authOk: Boolean) {
        context.startService(intent(context, ACTION_DISARM).putExtra(EXTRA_AUTH_OK, authOk))
    }

    private fun intent(context: Context, action: String) =
        Intent(context, GuardService::class.java).setAction(action)
}

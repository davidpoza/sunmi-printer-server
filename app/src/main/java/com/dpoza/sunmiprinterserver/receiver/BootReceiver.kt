package com.dpoza.sunmiprinterserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dpoza.sunmiprinterserver.config.ServerConfig
import com.dpoza.sunmiprinterserver.service.PrinterServerService

/**
 * Arranca el servidor automáticamente tras el arranque del dispositivo cuando el
 * autostart está habilitado en la configuración.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }

        val config = ServerConfig(context)
        if (!config.autostart) {
            Log.i(TAG, "Autostart deshabilitado; no se inicia el servidor")
            return
        }

        Log.i(TAG, "Autostart habilitado; iniciando servidor en el puerto ${config.port}")
        PrinterServerService.start(context, config.port)
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}

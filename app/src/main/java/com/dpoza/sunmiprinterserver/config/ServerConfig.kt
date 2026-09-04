package com.dpoza.sunmiprinterserver.config

import android.content.Context

/**
 * Configuración persistente del servidor (puerto y autostart), respaldada en
 * SharedPreferences para sobrevivir a reinicios de la app y del dispositivo.
 */
class ServerConfig(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var autostart: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTOSTART, value).apply()

    companion object {
        const val DEFAULT_PORT = 8080
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535

        private const val PREFS_NAME = "printer_server_config"
        private const val KEY_PORT = "port"
        private const val KEY_AUTOSTART = "autostart"

        /** Valida que un puerto esté en el rango permitido. */
        fun isValidPort(port: Int): Boolean = port in MIN_PORT..MAX_PORT
    }
}

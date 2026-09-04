package com.dpoza.sunmiprinterserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.dpoza.sunmiprinterserver.MainActivity
import com.dpoza.sunmiprinterserver.R
import com.dpoza.sunmiprinterserver.config.ServerConfig
import com.dpoza.sunmiprinterserver.printer.PrinterManager
import com.dpoza.sunmiprinterserver.server.PrinterServer
import com.dpoza.sunmiprinterserver.util.NetworkUtils

/**
 * Servicio en primer plano que hospeda el [PrinterServer] y lo mantiene vivo con una
 * notificación persistente. Gestiona también el binding a la impresora durante su vida.
 */
class PrinterServerService : Service() {

    private var server: PrinterServer? = null

    override fun onCreate() {
        super.onCreate()
        PrinterManager.connect(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = ServerConfig(this)
        val port = intent?.getIntExtra(EXTRA_PORT, config.port) ?: config.port

        startForegroundCompat(buildNotification(port))
        startServer(port)
        return START_STICKY
    }

    private fun startServer(port: Int) {
        // Reinicia si ya había uno corriendo.
        stopServer()
        val srv = PrinterServer(port)
        try {
            srv.start(SOCKET_READ_TIMEOUT, false)
            server = srv
            runningPort = port
            isRunning = true
            Log.i(TAG, "Servidor iniciado en ${NetworkUtils.baseUrl(port)}")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar el servidor en el puerto $port", e)
            isRunning = false
            server = null
            stopSelf()
        }
    }

    private fun stopServer() {
        server?.let { runCatching { it.stop() } }
        server = null
    }

    override fun onDestroy() {
        stopServer()
        PrinterManager.disconnect()
        isRunning = false
        runningPort = 0
        super.onDestroy()
    }

    // --- Notificación --------------------------------------------------------

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(port: Int): Notification {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val url = NetworkUtils.baseUrl(port)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text, url))
            .setSmallIcon(R.drawable.ic_stat_print)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
                channel.description = getString(R.string.notif_channel_desc)
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val TAG = "PrinterServerService"
        private const val CHANNEL_ID = "printer_server"
        private const val NOTIF_ID = 1001
        private const val SOCKET_READ_TIMEOUT = 10_000
        const val EXTRA_PORT = "extra_port"

        /** Estado observable por la UI (proceso único). */
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var runningPort: Int = 0
            private set

        /** Arranca el servicio en primer plano en [port]. */
        fun start(context: Context, port: Int) {
            val intent = Intent(context, PrinterServerService::class.java).apply {
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Detiene el servicio (y con él el servidor). */
        fun stop(context: Context) {
            context.stopService(Intent(context, PrinterServerService::class.java))
        }
    }
}

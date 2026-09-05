package com.dpoza.sunmiprinterserver

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.dpoza.sunmiprinterserver.config.ServerConfig
import com.dpoza.sunmiprinterserver.databinding.ActivityMainBinding
import com.dpoza.sunmiprinterserver.service.PrinterServerService
import com.dpoza.sunmiprinterserver.util.NetworkUtils

/**
 * Pantalla de configuración y estado: iniciar/detener el servidor, configurar puerto y
 * autostart, y ver el estado del servidor y su URL.
 */
class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: ServerConfig

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = ServerConfig(this)
        binding.editPort.setText(config.port.toString())
        binding.switchAutostart.isChecked = config.autostart

        binding.switchAutostart.setOnCheckedChangeListener { _, checked ->
            config.autostart = checked
        }
        binding.btnStart.setOnClickListener { onStartClicked() }
        binding.btnStop.setOnClickListener { onStopClicked() }
        binding.btnRefresh.setOnClickListener { refresh() }

        maybeRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun onStartClicked() {
        val port = binding.editPort.text.toString().toIntOrNull()
        if (port == null || !ServerConfig.isValidPort(port)) {
            Toast.makeText(this, R.string.invalid_port, Toast.LENGTH_SHORT).show()
            return
        }
        config.port = port
        PrinterServerService.start(this, port)
        handler.postDelayed({ refresh() }, 400)
    }

    private fun onStopClicked() {
        PrinterServerService.stop(this)
        handler.postDelayed({ refresh() }, 400)
    }

    private fun refresh() {
        val running = PrinterServerService.isRunning
        val port = if (running && PrinterServerService.runningPort > 0) {
            PrinterServerService.runningPort
        } else {
            config.port
        }

        binding.txtServerStatus.text = getString(
            R.string.label_status,
        ) + " " + getString(if (running) R.string.state_running else R.string.state_stopped)

        binding.txtUrl.text = if (running) NetworkUtils.baseUrl(port) else getString(R.string.url_placeholder)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            }
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 1_500L
        const val REQ_NOTIF = 100
    }
}

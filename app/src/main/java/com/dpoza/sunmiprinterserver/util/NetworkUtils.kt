package com.dpoza.sunmiprinterserver.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Devuelve la primera dirección IPv4 de la LAN (no loopback) del dispositivo, o null.
     * Prioriza interfaces activas y no virtuales. No requiere permisos.
     */
    fun getLocalIpAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    /** URL base de acceso, o un placeholder si aún no hay IP. */
    fun baseUrl(port: Int): String {
        val ip = getLocalIpAddress() ?: "0.0.0.0"
        return "http://$ip:$port"
    }
}

package com.dpoza.sunmiprinterserver.server

import android.util.Base64
import android.util.Log
import com.dpoza.sunmiprinterserver.printer.PrinterManager
import com.dpoza.sunmiprinterserver.printer.PrintResult
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * Servidor HTTP embebido que expone la impresora interna Sunmi como endpoint POS.
 *
 * Rutas:
 *  - `GET  /`        healthcheck (nombre + versión)
 *  - `GET  /status`  estado del servidor y de la impresora
 *  - `POST /print`   imprime ESC/POS crudo (octet-stream) o `{ "escpos_base64": "..." }`
 */
class PrinterServer(port: Int) : NanoHTTPD("0.0.0.0", port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            route(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error no controlado atendiendo ${session.method} ${session.uri}", e)
            json(Http.INTERNAL_ERROR, ok = false, extra = mapOf("error" to "Error interno del servidor"))
        }
    }

    private fun route(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        val method = session.method
        return when (uri) {
            "/" -> if (method == Method.GET) health() else methodNotAllowed()
            "/status" -> if (method == Method.GET) status() else methodNotAllowed()
            "/print" -> if (method == Method.POST) print(session) else methodNotAllowed()
            else -> json(Http.NOT_FOUND, ok = false, extra = mapOf("error" to "Ruta no encontrada"))
        }
    }

    // --- Endpoints -----------------------------------------------------------

    private fun health(): Response = json(
        Http.OK,
        ok = true,
        extra = mapOf(
            "service" to SERVICE_NAME,
            "version" to VERSION,
        ),
    )

    private fun status(): Response {
        val printer = PrinterManager.getStatus()
        val body = JSONObject().apply {
            put("ok", true)
            put("server", JSONObject().apply {
                put("running", isAlive)
                put("port", listeningPort)
            })
            put("printer", JSONObject().apply {
                put("connected", printer.connected)
                put("status", printer.state.name)
                put("canPrint", printer.canPrint)
                put("detail", printer.detail)
            })
        }
        return newFixedLengthResponse(Http.OK, MIME_JSON, body.toString())
    }

    private fun print(session: IHTTPSession): Response {
        val contentType = (session.headers["content-type"] ?: "").substringBefore(';').trim().lowercase()

        val data: ByteArray = when (contentType) {
            MIME_JSON -> {
                val raw = readBody(session)
                    ?: return json(Http.BAD_REQUEST, ok = false, extra = mapOf("error" to "Cuerpo vacío"))
                val base64 = runCatching { JSONObject(String(raw, Charsets.UTF_8)).optString("escpos_base64", "") }
                    .getOrDefault("")
                if (base64.isEmpty()) {
                    return json(Http.BAD_REQUEST, ok = false, extra = mapOf("error" to "Falta el campo 'escpos_base64'"))
                }
                runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
                    ?: return json(Http.BAD_REQUEST, ok = false, extra = mapOf("error" to "El campo 'escpos_base64' no es base64 válido"))
            }

            else -> readBody(session)
                ?: return json(Http.BAD_REQUEST, ok = false, extra = mapOf("error" to "Cuerpo vacío"))
        }

        if (data.isEmpty()) {
            return json(Http.BAD_REQUEST, ok = false, extra = mapOf("error" to "Cuerpo vacío"))
        }

        return when (val result = PrinterManager.printRaw(data)) {
            is PrintResult.Success -> json(Http.OK, ok = true, extra = mapOf("bytes" to data.size))
            is PrintResult.Unavailable -> json(
                Http.SERVICE_UNAVAILABLE, ok = false,
                extra = mapOf("error" to "Impresora no disponible"),
            )
            is PrintResult.Failure -> json(
                Http.SERVICE_UNAVAILABLE, ok = false,
                extra = mapOf("error" to result.reason),
            )
        }
    }

    // --- Helpers -------------------------------------------------------------

    /** Lee el cuerpo binario según Content-Length. Devuelve null si no hay cuerpo. */
    private fun readBody(session: IHTTPSession): ByteArray? {
        val length = session.headers["content-length"]?.toIntOrNull() ?: return null
        if (length <= 0) return null
        val buffer = ByteArray(length)
        var offset = 0
        val input = session.inputStream
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        if (offset == 0) return null
        return if (offset == length) buffer else buffer.copyOf(offset)
    }

    private fun methodNotAllowed(): Response =
        json(Http.METHOD_NOT_ALLOWED, ok = false, extra = mapOf("error" to "Método no permitido"))

    private fun json(status: Response.IStatus, ok: Boolean, extra: Map<String, Any?> = emptyMap()): Response {
        val body = JSONObject().apply {
            put("ok", ok)
            extra.forEach { (k, v) -> put(k, v) }
        }
        return newFixedLengthResponse(status, MIME_JSON, body.toString())
    }

    companion object {
        private const val TAG = "PrinterServer"
        const val SERVICE_NAME = "Sunmi HTTP Printer Server"
        const val VERSION = "1.0.0"
        private const val MIME_JSON = "application/json"
    }

    /** Estados HTTP como [Response.IStatus] propios (garantiza 503, no siempre en el enum de NanoHTTPD). */
    private object Http {
        val OK = status(200, "OK")
        val BAD_REQUEST = status(400, "Bad Request")
        val NOT_FOUND = status(404, "Not Found")
        val METHOD_NOT_ALLOWED = status(405, "Method Not Allowed")
        val SERVICE_UNAVAILABLE = status(503, "Service Unavailable")
        val INTERNAL_ERROR = status(500, "Internal Server Error")

        private fun status(code: Int, reason: String) = object : Response.IStatus {
            override fun getRequestStatus(): Int = code
            override fun getDescription(): String = "$code $reason"
        }
    }
}

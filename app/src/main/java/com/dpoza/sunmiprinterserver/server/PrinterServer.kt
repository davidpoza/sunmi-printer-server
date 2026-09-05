package com.dpoza.sunmiprinterserver.server

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.dpoza.sunmiprinterserver.printer.PrinterManager
import com.dpoza.sunmiprinterserver.printer.PrintResult
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

/**
 * Servidor HTTP embebido que expone la impresora interna Sunmi como endpoint POS.
 *
 * Rutas:
 *  - `GET  /`        healthcheck (nombre + versión)
 *  - `GET  /status`  estado del servidor y de la impresora
 *  - `POST /print`   imprime una imagen vía `printBitmap` (PNG/JPEG binario o `image_base64`),
 *                    o ESC/POS crudo vía `sendRAWData` (octet-stream o `escpos_base64`)
 *  - `GET  /diagnostics`  lectura cruda del servicio para depurar alineación del AIDL
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
            "/diagnostics" -> if (method == Method.GET) diagnostics() else methodNotAllowed()
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

    private fun diagnostics(): Response {
        val d = PrinterManager.getDiagnostics()
        val body = JSONObject().apply {
            put("ok", true)
            put("bound", d.bound)
            put("rawStateCode", d.rawStateCode ?: JSONObject.NULL)
            put("serialNo", d.serialNo ?: JSONObject.NULL)
            put("firmwareVersion", d.firmwareVersion ?: JSONObject.NULL)
            put("model", d.model ?: JSONObject.NULL)
            put("errors", JSONArray(d.errors))
        }
        return newFixedLengthResponse(Http.OK, MIME_JSON, body.toString())
    }

    private fun print(session: IHTTPSession): Response {
        val contentType = (session.headers["content-type"] ?: "").substringBefore(';').trim().lowercase()

        // Payload + intención: imagen -> printBitmap; ESC/POS crudo -> sendRAWData.
        val data: ByteArray
        val asImage: Boolean

        when (contentType) {
            MIME_JSON -> {
                val raw = readBody(session) ?: return badRequest("Cuerpo vacío")
                val body = runCatching { JSONObject(String(raw, Charsets.UTF_8)) }.getOrNull()
                    ?: return badRequest("JSON inválido")
                val imageB64 = body.optString("image_base64", "")
                val escposB64 = body.optString("escpos_base64", "")
                when {
                    imageB64.isNotEmpty() -> {
                        data = runCatching { Base64.decode(imageB64, Base64.DEFAULT) }.getOrNull()
                            ?: return badRequest("El campo 'image_base64' no es base64 válido")
                        asImage = true
                    }
                    escposB64.isNotEmpty() -> {
                        data = runCatching { Base64.decode(escposB64, Base64.DEFAULT) }.getOrNull()
                            ?: return badRequest("El campo 'escpos_base64' no es base64 válido")
                        asImage = false
                    }
                    else -> return badRequest("Falta el campo 'image_base64' o 'escpos_base64'")
                }
            }

            else -> {
                data = readBody(session) ?: return badRequest("Cuerpo vacío")
                // Imagen si el Content-Type es image/* o si los bytes traen cabecera de imagen.
                asImage = contentType.startsWith("image/") || looksLikeImage(data)
            }
        }

        if (data.isEmpty()) return badRequest("Cuerpo vacío")

        val result = if (asImage) {
            val bitmap = runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()
                ?: return badRequest("No se pudo decodificar la imagen (¿formato soportado?)")
            PrinterManager.printBitmap(bitmap)
        } else {
            PrinterManager.printRaw(data)
        }

        return when (result) {
            is PrintResult.Success -> json(
                Http.OK, ok = true,
                extra = mapOf("bytes" to data.size, "mode" to if (asImage) "bitmap" else "escpos"),
            )
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

    /** Detecta por cabecera mágica los formatos que `BitmapFactory` sabe decodificar. */
    private fun looksLikeImage(b: ByteArray): Boolean {
        if (b.size < 4) return false
        fun u(i: Int) = b[i].toInt() and 0xFF
        return when {
            u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47 -> true // PNG
            u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF -> true                  // JPEG
            u(0) == 0x42 && u(1) == 0x4D -> true                                  // BMP
            u(0) == 0x47 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x38 -> true  // GIF
            else -> false
        }
    }

    private fun badRequest(msg: String): Response =
        json(Http.BAD_REQUEST, ok = false, extra = mapOf("error" to msg))

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

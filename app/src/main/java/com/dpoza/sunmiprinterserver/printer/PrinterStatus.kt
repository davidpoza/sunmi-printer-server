package com.dpoza.sunmiprinterserver.printer

/**
 * Estado de la impresora interna, derivado de la conexión con el servicio Sunmi
 * y del código devuelto por `updatePrinterState()`.
 */
enum class PrinterState {
    READY,
    PREPARING,
    OUT_OF_PAPER,
    OVERHEATED,
    COVER_OPEN,
    CUTTER_ABNORMAL,
    COMM_ABNORMAL,
    NO_PRINTER,
    SERVICE_UNAVAILABLE,
    UNKNOWN,
    ;

    companion object {
        /** Traduce el código entero del SDK Sunmi a un [PrinterState]. */
        fun fromSunmiCode(code: Int): PrinterState = when (code) {
            1 -> READY
            2 -> PREPARING
            3 -> COMM_ABNORMAL
            4 -> OUT_OF_PAPER
            5 -> OVERHEATED
            6 -> COVER_OPEN
            7 -> CUTTER_ABNORMAL
            8 -> READY // cortador recuperado
            505 -> NO_PRINTER
            else -> UNKNOWN
        }
    }
}

/**
 * Instantánea del estado de la impresora expuesta a la API y a la UI.
 *
 * @param connected true si el servicio de impresión Sunmi está vinculado.
 * @param state estado concreto de la impresora.
 * @param detail texto legible para logs/UI.
 */
data class PrinterStatus(
    val connected: Boolean,
    val state: PrinterState,
    val detail: String,
) {
    /** La impresora puede imprimir ahora mismo. */
    val canPrint: Boolean
        get() = connected && (state == PrinterState.READY || state == PrinterState.UNKNOWN || state == PrinterState.PREPARING)
}

/**
 * Lectura cruda del servicio para verificar que el AIDL vendorizado coincide con el firmware.
 *
 * Si [serialNo]/[firmwareVersion]/[model] salen vacíos, con excepción o con basura, o el
 * [rawStateCode] no tiene sentido, el ORDEN de métodos del AIDL no cuadra con el del terminal
 * (IDs de transacción Binder desalineados) y por eso las llamadas caen en el método equivocado.
 */
data class PrinterDiagnostics(
    val bound: Boolean,
    val rawStateCode: Int?,
    val serialNo: String?,
    val firmwareVersion: String?,
    val model: String?,
    val errors: List<String>,
)

/** Resultado de una operación de impresión. */
sealed class PrintResult {
    /** Los bytes se entregaron a la impresora correctamente. */
    object Success : PrintResult()

    /** El servicio de impresión no está disponible (no vinculado / sin impresora). */
    object Unavailable : PrintResult()

    /** La impresora rechazó el trabajo o el SDK lanzó un error. */
    data class Failure(val reason: String) : PrintResult()
}

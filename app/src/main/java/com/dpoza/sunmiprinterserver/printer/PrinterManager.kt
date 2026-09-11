package com.dpoza.sunmiprinterserver.printer

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Punto único de integración con el servicio de impresión interno de Sunmi.
 *
 * Usa el **SDK oficial de Sunmi** (`com.sunmi:printerlibrary`): se vincula con
 * [InnerPrinterManager.bindService] y recibe un [SunmiPrinterService] en [InnerPrinterCallback].
 * Esto evita el frágil AIDL vendorizado (cuyos IDs de transacción no coincidían con el firmware
 * y hacían que las impresiones "no salieran"); el SDK trae el AIDL correcto para cada terminal.
 *
 * Responsabilidades:
 *  - Vincular/reconectar el servicio de impresión.
 *  - Reenviar bytes ESC/POS crudos ([printRaw]) o imágenes ([printBitmap]), serializando el acceso.
 *  - Exponer estado ([getStatus]) y diagnóstico crudo ([getDiagnostics]).
 *
 * Cualquier error del binder se traduce a un [PrintResult]/[PrinterStatus] sin propagar excepciones.
 */
object PrinterManager {

    private const val TAG = "PrinterManager"
    private const val PRINT_TIMEOUT_MS = 15_000L
    private const val REBIND_DELAY_MS = 3_000L

    private val service = AtomicReference<SunmiPrinterService?>(null)
    private val printLock = ReentrantLock(true)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var bindRequested = false

    /** True si el binding falló porque el servicio no existe en el terminal. */
    @Volatile
    private var serviceMissing = false

    private val innerCallback = object : InnerPrinterCallback() {
        override fun onConnected(svc: SunmiPrinterService) {
            Log.i(TAG, "SunmiPrinterService conectado")
            serviceMissing = false
            service.set(svc)
        }

        override fun onDisconnected() {
            Log.w(TAG, "SunmiPrinterService desconectado; se reintentará el binding")
            service.set(null)
            scheduleRebind()
        }
    }

    /** Vincula el servicio de impresión. Idempotente. */
    @Synchronized
    fun connect(context: Context) {
        appContext = context.applicationContext
        bindRequested = true
        doBind()
    }

    /** Desvincula el servicio y detiene los reintentos. */
    @Synchronized
    fun disconnect() {
        bindRequested = false
        mainHandler.removeCallbacksAndMessages(null)
        val ctx = appContext
        if (ctx != null) {
            runCatching { InnerPrinterManager.getInstance().unBindService(ctx, innerCallback) }
        }
        service.set(null)
    }

    private fun doBind() {
        val ctx = appContext ?: return
        val ok = runCatching {
            InnerPrinterManager.getInstance().bindService(ctx, innerCallback)
        }.getOrDefault(false)
        if (!ok) {
            serviceMissing = true
            Log.e(TAG, "No se pudo vincular el servicio Sunmi (¿no instalado en este terminal?)")
            scheduleRebind()
        }
    }

    private fun scheduleRebind() {
        if (!bindRequested) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (bindRequested && service.get() == null) doBind()
        }, REBIND_DELAY_MS)
    }

    /** Reenvía [data] tal cual (passthrough ESC/POS) a la impresora vía `sendRAWData`. */
    fun printRaw(data: ByteArray): PrintResult =
        runPrintJob("sendRAWData") { svc, cb -> svc.sendRAWData(data, cb) }

    /**
     * Imprime [bitmap] con `printBitmap`: el servicio lo convierte a monocromo y lo rasteriza.
     * Es la vía de alto nivel equivalente a la que usan las apps de Sunmi y no depende de que el
     * firmware soporte ESC/POS crudo, por lo que es más robusta para etiquetas/imágenes.
     *
     * Se envuelve en una transacción de buffer **autocontenida** para evitar el desfase "una
     * etiqueta por detrás": sin confirmar el buffer, el ráster queda retenido en el buffer del
     * *servicio* de impresión Sunmi (otro proceso, que sobrevive a reiniciar este servidor) y solo
     * lo vuelca el siguiente trabajo. Aquí:
     *  - `enterPrinterBuffer(true)` abre el buffer **limpiando restos** de trabajos previos.
     *  - `exitPrinterBufferWithCallback(true, …)` hace commit **en el mismo trabajo**, así el
     *    contenido se imprime ya y nada se rezaga a la siguiente impresión.
     * No añade avance de papel en blanco: solo se imprime la propia imagen.
     */
    fun printBitmap(bitmap: Bitmap): PrintResult =
        runPrintJob("printBitmap") { svc, cb ->
            svc.enterPrinterBuffer(true)
            svc.printBitmap(bitmap, null)
            svc.exitPrinterBufferWithCallback(true, cb)
        }

    /**
     * Ejecuta un trabajo de impresión serializando el acceso a la impresora y esperando la
     * confirmación con timeout. [dispatch] invoca el método concreto del servicio (raw o bitmap),
     * de modo que la gestión de callback/latch/errores se comparte entre todos los modos.
     */
    private fun runPrintJob(
        op: String,
        dispatch: (SunmiPrinterService, InnerResultCallback) -> Unit,
    ): PrintResult {
        val svc = service.get() ?: return PrintResult.Unavailable

        return printLock.withLock {
            val latch = CountDownLatch(1)
            val outcome = AtomicReference<PrintResult>(null)

            val callback = object : InnerResultCallback() {
                override fun onRunResult(isSuccess: Boolean) {
                    outcome.compareAndSet(null, if (isSuccess) PrintResult.Success else PrintResult.Failure("La impresora reportó fallo"))
                    latch.countDown()
                }

                override fun onReturnString(result: String?) {
                    // No usado para impresión.
                }

                override fun onRaiseException(code: Int, msg: String?) {
                    outcome.compareAndSet(null, PrintResult.Failure("Excepción de impresora ($code): ${msg ?: "sin detalle"}"))
                    latch.countDown()
                }

                override fun onPrintResult(code: Int, msg: String?) {
                    val res = if (code == 0) PrintResult.Success else PrintResult.Failure("Resultado de impresión ($code): ${msg ?: "sin detalle"}")
                    outcome.compareAndSet(null, res)
                    latch.countDown()
                }
            }

            try {
                dispatch(svc, callback)
            } catch (e: RemoteException) {
                Log.e(TAG, "RemoteException en $op; el servicio pudo morir", e)
                service.set(null)
                scheduleRebind()
                return@withLock PrintResult.Failure("Fallo de comunicación con el servicio de impresión")
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado en $op", e)
                return@withLock PrintResult.Failure("Error de impresión: ${e.message}")
            }

            val signaled = runCatching { latch.await(PRINT_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)
            when {
                !signaled -> PrintResult.Failure("Timeout esperando confirmación de la impresora")
                else -> outcome.get() ?: PrintResult.Success
            }
        }
    }

    /**
     * Lee campos crudos del servicio (serial, versión, modelo, código de estado) para verificar
     * que el servicio responde. Cada llamada va aislada: si una lanza, se registra en
     * [PrinterDiagnostics.errors] sin abortar el resto.
     */
    fun getDiagnostics(): PrinterDiagnostics {
        val svc = service.get()
            ?: return PrinterDiagnostics(
                bound = false,
                rawStateCode = null,
                serialNo = null,
                firmwareVersion = null,
                model = null,
                errors = listOf("Servicio de impresión no vinculado"),
            )

        val errors = mutableListOf<String>()
        fun <T> probe(name: String, block: () -> T): T? =
            runCatching(block)
                .onFailure { errors += "$name: ${it.message ?: it.javaClass.simpleName}" }
                .getOrNull()

        return PrinterDiagnostics(
            bound = true,
            rawStateCode = probe("updatePrinterState") { svc.updatePrinterState() },
            serialNo = probe("getPrinterSerialNo") { svc.printerSerialNo },
            firmwareVersion = probe("getPrinterVersion") { svc.printerVersion },
            model = probe("getPrinterModal") { svc.printerModal },
            errors = errors,
        )
    }

    /** Devuelve el estado actual de la impresora sin lanzar excepciones. */
    fun getStatus(): PrinterStatus {
        val svc = service.get()
            ?: return PrinterStatus(
                connected = false,
                state = PrinterState.SERVICE_UNAVAILABLE,
                detail = if (serviceMissing) "Servicio de impresión Sunmi no disponible" else "Conectando con el servicio de impresión…",
            )

        return try {
            val code = svc.updatePrinterState()
            val state = PrinterState.fromSunmiCode(code)
            PrinterStatus(connected = true, state = state, detail = "Código de estado Sunmi: $code")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener el estado de la impresora", e)
            PrinterStatus(connected = true, state = PrinterState.UNKNOWN, detail = "Impresora vinculada; estado no disponible")
        }
    }
}

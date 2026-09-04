package com.dpoza.sunmiprinterserver.printer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import woyou.aidlservice.jiuiv5.ICallback
import woyou.aidlservice.jiuiv5.IWoyouService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Punto único de integración con el servicio de impresión interno de Sunmi.
 *
 * Responsabilidades:
 *  - Vincularse (bindService) al servicio `woyou.aidlservice.jiuiv5` y reconectar si cae.
 *  - Reenviar bytes ESC/POS crudos con [printRaw], serializando el acceso a la impresora.
 *  - Exponer el estado con [getStatus].
 *
 * Toda la fragilidad del SDK Sunmi queda encapsulada aquí: cualquier error del binder se
 * traduce a un [PrintResult]/[PrinterStatus] sin propagar excepciones al servidor HTTP.
 */
object PrinterManager {

    private const val TAG = "PrinterManager"
    private const val SUNMI_PACKAGE = "woyou.aidlservice.jiuiv5"
    private const val SUNMI_ACTION = "woyou.aidlservice.jiuiv5.IWoyouService"
    private const val PRINT_TIMEOUT_MS = 15_000L
    private const val REBIND_DELAY_MS = 3_000L

    private val service = AtomicReference<IWoyouService?>(null)
    private val printLock = ReentrantLock(true)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var bindRequested = false

    /** True si el binding falló porque el servicio no existe en el terminal. */
    @Volatile
    private var serviceMissing = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "Servicio de impresión conectado")
            serviceMissing = false
            service.set(IWoyouService.Stub.asInterface(binder))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Servicio de impresión desconectado; se reintentará el binding")
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
        if (ctx != null && service.get() != null) {
            runCatching { ctx.unbindService(connection) }
        }
        service.set(null)
    }

    private fun doBind() {
        val ctx = appContext ?: return
        val intent = Intent().apply {
            setPackage(SUNMI_PACKAGE)
            action = SUNMI_ACTION
        }
        val ok = runCatching {
            ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
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

    /**
     * Reenvía [data] tal cual (passthrough) a la impresora. Serializa el acceso para no
     * intercalar comandos de trabajos concurrentes y espera la confirmación con timeout.
     */
    fun printRaw(data: ByteArray): PrintResult {
        val svc = service.get() ?: return PrintResult.Unavailable

        return printLock.withLock {
            val latch = CountDownLatch(1)
            val outcome = AtomicReference<PrintResult>(null)

            val callback = object : ICallback.Stub() {
                override fun onRunResult(isSuccess: Boolean) {
                    outcome.compareAndSet(null, if (isSuccess) PrintResult.Success else PrintResult.Failure("La impresora reportó fallo"))
                    latch.countDown()
                }

                override fun onReturnString(result: String?) {
                    // No usado para impresión cruda.
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
                svc.sendRAWData(data, callback)
            } catch (e: RemoteException) {
                Log.e(TAG, "RemoteException en sendRAWData; el servicio pudo morir", e)
                service.set(null)
                scheduleRebind()
                return@withLock PrintResult.Failure("Fallo de comunicación con el servicio de impresión")
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado en sendRAWData", e)
                return@withLock PrintResult.Failure("Error de impresión: ${e.message}")
            }

            val signaled = runCatching { latch.await(PRINT_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)
            when {
                !signaled -> PrintResult.Failure("Timeout esperando confirmación de la impresora")
                else -> outcome.get() ?: PrintResult.Success
            }
        }
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
            // Vinculado pero no se pudo consultar el estado (p.ej. firmware con firma distinta).
            Log.w(TAG, "No se pudo obtener el estado de la impresora", e)
            PrinterStatus(connected = true, state = PrinterState.UNKNOWN, detail = "Impresora vinculada; estado no disponible")
        }
    }
}

// Copia de la interfaz oficial de callback del servicio de impresión Sunmi.
// No renombrar el paquete ni cambiar las firmas: debe coincidir con el servicio del dispositivo.
package woyou.aidlservice.jiuiv5;

interface ICallback {
    void onRunResult(boolean isSuccess);

    void onReturnString(String result);

    void onRaiseException(int code, String msg);

    void onPrintResult(int code, String msg);
}

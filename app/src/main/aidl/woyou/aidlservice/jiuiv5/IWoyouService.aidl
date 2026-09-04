// Copia vendorizada de la interfaz AIDL oficial del servicio de impresión Sunmi
// (woyou.aidlservice.jiuiv5.IWoyouService, expuesto por la app "Sunmi Printer Service").
//
// IMPORTANTE (frágil): el ORDEN de declaración de los métodos define los IDs de
// transacción Binder. Debe coincidir EXACTAMENTE con el servicio instalado en el
// terminal. Si Sunmi publica una versión de SDK distinta, sustituye este fichero por
// el AIDL/AAR oficial. Este servidor sólo invoca `sendRAWData` y `updatePrinterState`,
// pero se mantiene la interfaz completa para preservar los IDs de transacción.
package woyou.aidlservice.jiuiv5;

import woyou.aidlservice.jiuiv5.ICallback;
import android.graphics.Bitmap;

interface IWoyouService {

    void printerInit(in ICallback callback);

    void printerSelfChecking(in ICallback callback);

    String getPrinterSerialNo();

    String getPrinterVersion();

    String getPrinterModal();

    void getPrintedLength(in ICallback callback);

    // Devuelve el código de estado de la impresora (1=OK, 4=sin papel, 5=sobrecalentada,
    // 6=tapa abierta, 3=comunicación anómala, 505=sin impresora, ...).
    int updatePrinterState();

    void sendRAWData(in byte[] data, in ICallback callback);

    void setAlignment(int alignment, in ICallback callback);

    void setFontName(String typeface, in ICallback callback);

    void setFontSize(float fontsize, in ICallback callback);

    void printText(String text, in ICallback callback);

    void printTextWithFont(String text, String typeface, float fontsize, in ICallback callback);

    void printOriginalText(String text, in ICallback callback);

    void printColumnsText(in String[] colsTextArr, in int[] colsWidthArr, in int[] colsAlign, in ICallback callback);

    void printColumnsString(in String[] colsTextArr, in int[] colsWidthArr, in int[] colsAlign, in ICallback callback);

    void printBitmap(in Bitmap bitmap, in ICallback callback);

    void printBitmapCustom(in Bitmap bitmap, int type, in ICallback callback);

    void printBarCode(String data, int symbology, int height, int width, int textposition, in ICallback callback);

    void printQRCode(String data, int modulesize, int errorlevel, in ICallback callback);

    void print2DCode(String data, int symbology, int modulesize, int errorlevel, in ICallback callback);

    void lineWrap(int n, in ICallback callback);

    void cutPaper(in ICallback callback);

    void getCutPaperTimes(in ICallback callback);

    void openDrawer(in ICallback callback);

    void getOpenDrawerTimes(in ICallback callback);

    void enterPrinterBuffer(in boolean clean);

    void commitPrinterBuffer();

    void exitPrinterBuffer(in boolean commit);
}

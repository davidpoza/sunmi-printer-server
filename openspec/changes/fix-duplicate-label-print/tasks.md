## 1. Implementación del fix

- [x] 1.1 En `PrinterManager.kt`, añadir la constante `LABEL_FEED_LINES` (valor inicial 2–3) con comentario que explique el compromiso de papel en blanco.
- [x] 1.2 Reescribir `printBitmap` para dejar de usar el modo buffer: dentro de `runPrintJob`, llamar `svc.printBitmap(bitmap, null)` seguido de `svc.lineWrap(LABEL_FEED_LINES, cb)`, pasando el callback al `lineWrap` (último comando).
- [x] 1.3 Eliminar las llamadas `enterPrinterBuffer(true)` y `exitPrinterBufferWithCallback(true, cb)` de ese método.
- [x] 1.4 Actualizar el comentario KDoc de `printBitmap` para reflejar la nueva estrategia (sin buffer; avance de papel para expulsar) y retirar la mención "No añade avance de papel en blanco".

## 2. Robustez

- [x] 2.1 Verificar que el manejo de excepciones/timeout de `runPrintJob` sigue cubriendo la nueva secuencia (el callback llega solo desde `lineWrap`); comprobar que un fallo en `printBitmap` o `lineWrap` se traduce a `PrintResult.Failure` sin tumbar el servidor.
- [x] 2.2 (Fallback) Si `lineWrap(n, callback)` no estuviera disponible en el SDK/firmware, sustituir el avance por `svc.sendRAWData(byteArrayOf(0x0a, 0x0a), cb)` (line feeds crudos, coherentes con `RASTER_JOB_TRAILER`). — No aplica: `lineWrap(Int, InnerResultCallback)` compila correctamente (`assembleDebug` OK), no se necesita el fallback.

## 3. Compilación

- [x] 3.1 Compilar el APK release/debug (`./gradlew assembleDebug`) y resolver errores de compilación.

## 4. Verificación en dispositivo (obligatoria)

- [ ] 4.1 Instalar el APK en la terminal Sunmi y confirmar que el servidor arranca y `GET /status` reporta la impresora conectada.
- [ ] 4.2 Imprimir una etiqueta individual y comprobar que sale **exactamente una copia**.
- [ ] 4.3 Imprimir la secuencia A, B, A (tres peticiones) y comprobar que sale **A, B, A** (sin la A duplicada del principio, sin AABA).
- [ ] 4.4 Confirmar que la última etiqueta se expulsa completa sin necesidad de un trabajo posterior (sin desfase).
- [ ] 4.5 Ajustar `LABEL_FEED_LINES` al mínimo que expulse la etiqueta sin invadir la siguiente y repetir 4.2–4.4 si se cambió el valor.

## 5. Cierre

- [ ] 5.1 Dejar registrado en el commit/PR el valor final de `LABEL_FEED_LINES` verificado en la etiqueta de producción.

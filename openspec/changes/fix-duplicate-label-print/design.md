## Context

El servidor Sunmi expone `POST /print`. Cuando el cuerpo es una imagen, `PrinterServer` la decodifica y llama a `PrinterManager.printBitmap`, que hoy hace:

```kotlin
svc.enterPrinterBuffer(true)
svc.printBitmap(bitmap, null)
svc.exitPrinterBufferWithCallback(true, cb)
```

Historia del problema:
- **Versión inicial**: `svc.printBitmap(bitmap, cb)` directo. El ráster quedaba en el buffer del *servicio* de impresión Sunmi (proceso aparte) y no se expulsaba hasta el **siguiente** trabajo → desfase "una etiqueta por detrás" (commit anterior a `4fd283f`, descrito como *"prints cached previous job"*).
- **Arreglo `4fd283f`**: se envolvió en una transacción de buffer autocontenida para forzar el commit en el mismo trabajo. En este firmware ese patrón produce el conocido **double-print** del modo buffer: el trabajo se confirma (el callback llega bien) pero el contenido se imprime una vez de más → secuencia A, B, A observada como **A A B A**.

Restricción previa registrada en el código: *"No añade avance de papel en blanco"* — se evitaba a propósito gastar papel de etiqueta. Este diseño relaja esa restricción de forma acotada porque es la vía fiable para expulsar la etiqueta sin usar el buffer.

El acceso a la impresora ya está serializado por `printLock` y confirmado con `CountDownLatch` + timeout en `runPrintJob`; ese andamiaje se conserva.

## Goals / Non-Goals

**Goals:**
- Imprimir cada etiqueta **exactamente una vez** (sin duplicado).
- Expulsar la etiqueta en la misma petición (sin desfase "una por detrás").
- Cambio mínimo y contenido en `PrinterManager.printBitmap`, conservando `runPrintJob`, el lock y el manejo de errores/timeout.

**Non-Goals:**
- No se cambia la API HTTP (`/print`, `/status`, `/diagnostics`) ni el backend ni el frontend.
- No se toca la ruta ESC/POS crudo (`sendRAWData`), que ya funciona por passthrough.
- No se introduce configuración nueva de red ni de autenticación.

## Decisions

### Decisión: Imprimir sin modo buffer y avanzar papel para expulsar

Reemplazar la transacción de buffer por impresión directa seguida de un avance de papel que confirma la operación:

```kotlin
fun printBitmap(bitmap: Bitmap): PrintResult =
    runPrintJob("printBitmap") { svc, cb ->
        svc.printBitmap(bitmap, null)
        svc.lineWrap(LABEL_FEED_LINES, cb)   // expulsa la etiqueta; el callback confirma el fin
    }
```

- El callback se traslada al **último** comando de la secuencia (`lineWrap`), para que `runPrintJob` confirme cuando toda la operación (imagen + avance) ha terminado, no antes.
- `LABEL_FEED_LINES` es una constante acotada (p. ej. `2`–`3`) para minimizar el papel en blanco; queda como único punto a ajustar según la etiqueta física.

**Por qué esta opción y no las alternativas:**
- *Mantener buffer con `commitPrinterBufferWithCallback` + `exitPrinterBuffer(false)`*: menor cambio y sin avance de papel, pero sigue dependiendo del frágil modo buffer del firmware (que ya nos ha dado desfase y duplicado). Descartada por fiabilidad.
- *`printBitmap(bitmap, cb)` directo sin avance*: es exactamente la versión que causaba el desfase "una por detrás". Descartada.
- *Añadir corte de papel (`cutPaper`)*: las etiquetas suelen ser die-cut/gap, no rollo continuo; cortar no aplica. El avance por líneas es suficiente para expulsar.

### Decisión: Conservar `runPrintJob`, el lock y el timeout

No se modifica el andamiaje de serialización/confirmación. Solo cambia el bloque `dispatch` que se pasa a `runPrintJob`. Esto mantiene el aislamiento de errores (una excepción del SDK se traduce a `PrintResult.Failure` sin tumbar el servidor HTTP).

## Risks / Trade-offs

- **[Dependencia del firmware]** El comportamiento exacto del buffer/avance depende del servicio de impresión Sunmi de la terminal → **Mitigación**: verificación obligatoria en el dispositivo físico con la secuencia A, B, A antes de dar por cerrado el cambio; `LABEL_FEED_LINES` ajustable si la etiqueta no se expulsa del todo o avanza de más.
- **[Gasto de papel]** El avance introduce un pequeño margen en blanco tras cada etiqueta → **Mitigación**: mantener el número de líneas al mínimo que expulse la etiqueta; documentarlo junto a la constante.
- **[Avance excesivo en etiquetas cortas]** Un avance fijo podría empujar hacia la siguiente etiqueta → **Mitigación**: elegir el valor mínimo verificado; si en el futuro se soporta modo etiqueta/gap del SDK, sustituir el avance fijo por el reposicionamiento a hueco.
- **[`lineWrap` no disponible/edge cases]** Si el SDK/firmware no expusiera `lineWrap` con callback → **Mitigación**: alternativa equivalente `sendRAWData(byteArrayOf(0x0a, 0x0a), cb)` (line feeds crudos), coherente con el `RASTER_JOB_TRAILER` (`\n\n`) que ya usa el frontend en su ruta ESC/POS.

## Migration Plan

1. Aplicar el cambio en `PrinterManager.printBitmap` y compilar el APK.
2. Instalar en la terminal Sunmi y verificar físicamente: imprimir A, B, A y comprobar que sale **A, B, A** (una sola A al principio) y que la última etiqueta se expulsa sin necesidad de otro trabajo.
3. Ajustar `LABEL_FEED_LINES` si hiciera falta y repetir la verificación.
4. **Rollback**: revertir a la transacción de buffer (`enterPrinterBuffer`/`exitPrinterBufferWithCallback`) si la verificación fallara; el cambio está aislado en un único método.

## Open Questions

- Valor definitivo de `LABEL_FEED_LINES` para la etiqueta de producción (50×30 mm): se fija tras la verificación en dispositivo.
- ¿Interesa exponer el número de líneas de avance como configuración del servidor, o basta con la constante? (Por defecto: constante, sin nueva configuración.)

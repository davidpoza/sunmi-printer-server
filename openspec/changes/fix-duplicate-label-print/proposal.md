## Why

Al imprimir etiquetas a través del servidor Sunmi (`POST /print` con imagen → `printBitmap`), cada trabajo deja una copia extra: imprimiendo A, luego B, luego A se obtiene **A A B A** en lugar de **A B A**. El intento previo de arreglar el desfase "una etiqueta por detrás" (commit `4fd283f`) envolvió la impresión en una transacción de buffer de Sunmi (`enterPrinterBuffer(true)` → `printBitmap` → `exitPrinterBufferWithCallback(true, …)`), y ese patrón provoca el conocido *double-print* del modo buffer en este firmware: el ráster se confirma pero queda retenido/re-commiteado y sale una vez más. Se cambió "imprime tarde" por "imprime de más", y el problema sigue afectando a cada etiqueta impresa.

## What Changes

- Reescribir el camino de impresión de imagen en `PrinterManager.printBitmap` para **no usar el modo buffer de Sunmi**: imprimir directamente con `printBitmap(bitmap, null)` y a continuación **avanzar el papel** (`lineWrap`) para expulsar la etiqueta, confirmando la operación con el callback del avance.
- Con ello cada etiqueta se imprime **exactamente una vez**, sin el desfase "una por detrás" (el avance expulsa el ráster de inmediato) y sin la copia duplicada (no hay commit de buffer que se repita).
- Aceptar como contrapartida un pequeño avance de papel en blanco tras cada etiqueta (necesario para expulsarla); la cantidad de líneas de avance queda acotada y ajustable.
- Sin cambios en la API HTTP (`/print`, `/status`, `/diagnostics`), ni en el backend (`dps-stock-backend`) ni en el frontend (`dps-stock-web`). La ruta ESC/POS crudo (`sendRAWData`) no se toca.

## Capabilities

### New Capabilities
- `bitmap-label-printing`: impresión de una imagen de etiqueta vía el servicio de impresión Sunmi (`printBitmap`), garantizando que cada petición imprime la etiqueta **exactamente una vez**, sin retención en buffer que la retrase (desfase) ni que la duplique.

### Modified Capabilities
<!-- Sin cambios de requisitos en capabilities existentes: la API HTTP y el ciclo de vida no cambian. -->

## Impact

- **Código**: `app/src/main/java/com/dpoza/sunmiprinterserver/printer/PrinterManager.kt` (método `printBitmap`; deja de usar `enterPrinterBuffer`/`exitPrinterBufferWithCallback`).
- **Comportamiento**: la ruta de imagen de `POST /print` en `PrinterServer.kt` (misma API, ahora imprime una sola copia + avance de papel).
- **Dispositivo/firmware**: depende del comportamiento del servicio de impresión Sunmi; requiere verificación en la terminal física (imprimir A, B, A y comprobar que sale A B A).
- **Sin impacto** en `dps-stock-backend`, `dps-stock-web`, ni en la ruta ESC/POS crudo.

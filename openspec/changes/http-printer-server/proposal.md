## Why

La impresora interna de la Sunmi V2 solo es accesible desde código Android nativo (SDK/AIDL de Sunmi), lo que obliga a que cualquier sistema que quiera imprimir (TPV web, backend, apps en otros dispositivos de la LAN) tenga que integrarse a bajo nivel en el propio terminal. Exponer la impresora como un servidor HTTP que hable "protocolo POS" (ESC/POS) permite que cualquier cliente de la red local imprima con una simple petición HTTP, sin acoplarse al SDK de Sunmi.

## What Changes

- Nueva app Android (Kotlin) que se instala en la Sunmi V2 y actúa como **servidor HTTP** embebido (NanoHTTPD) escuchando en la LAN.
- Endpoint de impresión que acepta **comandos ESC/POS crudos** (passthrough): binario (`application/octet-stream`) o `escpos_base64` en JSON, reenviados tal cual a la impresora interna vía el SDK de Sunmi (`sendRAWData`).
- Endpoints auxiliares de **estado/salud** para consultar disponibilidad del servidor y de la impresora (papel, conexión con el servicio de impresión).
- **Servicio en primer plano** que mantiene vivo el servidor con notificación persistente, y **autostart** al arrancar el dispositivo (`BOOT_COMPLETED`).
- Pantalla de configuración/estado mínima (puerto, IP, on/off, estado de la impresora).
- Sin autenticación en esta versión: el acceso se confía a la red local (**Non-goal**: TLS/auth, se abordará más adelante).

## Capabilities

### New Capabilities
- `http-print-api`: Servidor HTTP embebido y su contrato de API (endpoint de impresión ESC/POS crudo, endpoint de estado/salud, códigos de respuesta y manejo de errores).
- `escpos-printing`: Integración con la impresora interna Sunmi para reenviar bytes ESC/POS crudos y reportar el estado de la impresora.
- `printer-server-lifecycle`: Ciclo de vida del servidor en el dispositivo: servicio en primer plano, autostart en `BOOT_COMPLETED` y pantalla de configuración/estado.

### Modified Capabilities
<!-- Proyecto nuevo: no hay capacidades existentes que modificar. -->

## Impact

- **Nuevo proyecto Android** (Gradle, Kotlin) desde cero: `AndroidManifest.xml`, `MainActivity`, servicio en primer plano, `BroadcastReceiver` de boot y clase servidor NanoHTTPD.
- **Dependencias**: NanoHTTPD, SDK/AIDL de impresora Sunmi (`woyou.aidlservice.jiuiv5` / librería `sunmiprinter`).
- **Permisos**: `INTERNET`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS` (Android 13+), `RECEIVE_BOOT_COMPLETED`.
- **Runtime**: requiere terminal Sunmi con servicio de impresión instalado; abre un puerto TCP en la LAN.
- **Seguridad**: superficie de red abierta en la LAN (sin auth) — riesgo aceptado y documentado para esta fase.

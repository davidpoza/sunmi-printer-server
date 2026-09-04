## 1. Andamiaje del proyecto Android

- [x] 1.1 Crear proyecto Android (Kotlin) con Gradle: `settings.gradle(.kts)`, `build.gradle` de proyecto y de módulo `app`, `gradle.properties` y wrapper.
- [x] 1.2 Definir `applicationId`, `minSdk`/`targetSdk` y `compileSdk`, y habilitar Kotlin + ViewBinding.
- [x] 1.3 Añadir dependencia de NanoHTTPD y la del SDK de impresora Sunmi (`sunmiprinter`); si no hay artefacto Maven, vendorizar los AIDL (`IWoyouService`, `ICallback`, `WoyouConsts`) en `app/src/main/aidl/woyou/aidlservice/jiuiv5/`.
- [x] 1.4 Crear `AndroidManifest.xml` con permisos `INTERNET`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, y declarar `MainActivity`, `PrinterServerService` y `BootReceiver`.

## 2. Integración con la impresora (escpos-printing)

- [x] 2.1 Implementar `PrinterManager` (singleton) que haga `bindService` al servicio `woyou.aidlservice.jiuiv5` mediante un `ServiceConnection` y mantenga el estado de conexión.
- [x] 2.2 Implementar reconexión automática en `onServiceDisconnected` (reintento del binding).
- [x] 2.3 Implementar `printRaw(bytes): Result` usando `sendRAWData` con `ICallback`, serializando el acceso (lock) y con timeout.
- [x] 2.4 Implementar `getStatus(): PrinterStatus` (conexión con el servicio + estado de papel cuando el SDK lo exponga).
- [x] 2.5 Capturar excepciones del SDK y traducirlas a `Result` de fallo con motivo, sin propagar el crash.

## 3. Servidor HTTP y API (http-print-api)

- [x] 3.1 Implementar `PrinterServer : NanoHTTPD` con enrutado por método y ruta, escuchando en `0.0.0.0:<puerto>`.
- [x] 3.2 Implementar `GET /` (healthcheck): `200 OK` con nombre y versión del servicio.
- [x] 3.3 Implementar `POST /print` con `application/octet-stream`: leer bytes del body, validar no-vacío y llamar a `PrinterManager.printRaw`.
- [x] 3.4 Implementar `POST /print` con `application/json` (`escpos_base64`): decodificar base64, validar y llamar a `printRaw`.
- [x] 3.5 Implementar `GET /status`: JSON con `server.running`, puerto y `printer.status`.
- [x] 3.6 Implementar el mapeo de errores a códigos HTTP: `400` (body vacío/base64 inválido), `404` (ruta desconocida), `405` (método no permitido), `503` (impresora no disponible), y respuestas JSON uniformes `{ ok, error? }`.

## 4. Ciclo de vida del servidor (printer-server-lifecycle)

- [x] 4.1 Implementar `PrinterServerService : Service` en primer plano: crear canal de notificación, `startForeground` con notificación persistente (IP + puerto) y arrancar/parar `PrinterServer`.
- [x] 4.2 Manejar intents `START`/`STOP` del servicio y liberar recursos (cerrar servidor, unbind impresora) en `onDestroy`.
- [x] 4.3 Implementar `BootReceiver` para `BOOT_COMPLETED` que arranque el servicio si el autostart está habilitado (`startForegroundService`).
- [x] 4.4 Implementar persistencia de configuración (puerto + autostart) con `SharedPreferences`/DataStore.
- [x] 4.5 Implementar utilidad para obtener la IP de la LAN del dispositivo (para notificación y UI).

## 5. UI de configuración y estado (printer-server-lifecycle)

- [x] 5.1 Implementar `MainActivity` con estado del servidor (activo/inactivo, URL base `http://<ip>:<puerto>`) y estado de la impresora.
- [x] 5.2 Añadir controles de Iniciar/Detener que envíen intents al `PrinterServerService`.
- [x] 5.3 Añadir campos de configuración de puerto y toggle de autostart, persistidos y aplicados al reiniciar el servidor.
- [x] 5.4 Solicitar permiso `POST_NOTIFICATIONS` en Android 13+ y reflejar el estado observando el servicio.

## 6. Verificación

- [x] 6.1 Compilar el APK (`assembleDebug`) y resolver dependencias/AIDL.
- [ ] 6.2 Instalar en la Sunmi V2 y verificar impresión binaria: `curl --data-binary @ticket.bin -H "Content-Type: application/octet-stream" http://<ip>:8080/print`.
- [ ] 6.3 Verificar impresión base64 (`POST /print` con `{ "escpos_base64": "..." }`) y corte de papel.
- [ ] 6.4 Verificar `GET /status` con impresora lista, sin papel y servicio no disponible (`503` en `/print`).
- [ ] 6.5 Verificar foreground service (sobrevive en segundo plano) y autostart tras reinicio del dispositivo.
- [ ] 6.6 Verificar códigos de error: body vacío (`400`), base64 inválido (`400`), ruta desconocida (`404`), método no permitido (`405`).

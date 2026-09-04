## Context

La Sunmi V2 es un terminal Android con impresora térmica interna accesible únicamente mediante el servicio de impresión de Sunmi (AIDL `woyou.aidlservice.jiuiv5.IWoyouService`, expuesto por la librería `sunmiprinter`). Hoy, imprimir requiere código nativo en el propio terminal. Este cambio introduce una app Android nueva, desde cero, que convierte al terminal en un **servidor de impresión HTTP** para la red local: cualquier cliente envía comandos ESC/POS por HTTP y la app los reenvía a la impresora interna.

Decisiones de producto ya fijadas: API de **ESC/POS crudo (passthrough)**, stack **Kotlin + NanoHTTPD**, ejecución mediante **foreground service con autostart en boot**, y acceso **abierto en la LAN** (sin autenticación en esta fase).

## Goals / Non-Goals

**Goals:**
- App Android instalable en la Sunmi V2 que levanta un servidor HTTP en la LAN.
- Endpoint `POST /print` que acepte ESC/POS crudo (binario y base64) y lo reenvíe sin transformar a `sendRAWData`.
- Endpoint `GET /status` con estado de servidor e impresora; healthcheck en `/`.
- Servidor robusto: foreground service + autostart, resiliente a desconexiones del servicio de impresión.
- UI mínima de estado/configuración (puerto, autostart, iniciar/detener, IP de acceso).

**Non-Goals:**
- Autenticación, TLS/HTTPS, rate limiting o control de acceso (fase posterior).
- API JSON estructurada de alto nivel (texto/QR/imagen) — solo passthrough ESC/POS.
- Descubrimiento de red (mDNS/Bonjour), colas de impresión persistentes o multi-impresora.
- Compatibilidad con modelos Sunmi distintos de los que exponen el servicio de impresión interno.

## Decisions

### D1: NanoHTTPD como servidor embebido
Se usa **NanoHTTPD** por ser ligero, single-jar, sin dependencias pesadas y suficiente para un puñado de endpoints. 
- *Alternativa considerada*: **Ktor embedded (CIO/Netty)** — más potente (routing, coroutines) pero añade peso y complejidad innecesaria para 3 endpoints. Descartado por simplicidad.
- *Alternativa*: `ServerSocket` a mano — demasiado bajo nivel; reinventar parsing HTTP. Descartado.

### D2: Passthrough ESC/POS vía `sendRAWData`
El cuerpo de `POST /print` se trata como bytes opacos y se entrega a `IWoyouService.sendRAWData(byte[], ICallback)`. Dos formas de entrada:
- Binario: `Content-Type: application/octet-stream` → bytes del body directos.
- JSON: `{ "escpos_base64": "..." }` → `Base64.decode` → bytes.

Esto mantiene la fidelidad total al "protocolo POS": el cliente controla fuentes, alineación, códigos de barras, corte, etc. La app no interpreta comandos.

### D3: Integración con el servicio de impresión Sunmi mediante binding AIDL
Se vincula (`bindService`) al servicio `woyou.aidlservice.jiuiv5` mediante un `ServiceConnection` gestionado por un `PrinterManager` singleton. El manager:
- Mantiene la referencia `IWoyouService` y su estado de conexión.
- Reintenta el binding si `onServiceDisconnected`.
- Expone `printRaw(bytes): Result` y `getStatus(): PrinterStatus`.

Se preferirá integrar la librería oficial `sunmiprinter` (AIDL incluido). Si no estuviera disponible como dependencia, se incluirá el `.aidl` (`IWoyouService`, `ICallback`, `WoyouConsts`) en el proyecto.

### D4: Foreground service como host del servidor
El servidor NanoHTTPD vive dentro de un `PrinterServerService : Service` en primer plano con notificación persistente (canal de notificaciones dedicado). Motivos: evitar que Android mate el proceso, permitir ejecución 24/7 y desacoplar el ciclo de vida del servidor del de la `MainActivity`.
- La `MainActivity` sólo envía intents de start/stop y observa el estado.
- El binding a la impresora se hace desde el servicio (contexto de larga vida).

### D5: Autostart en BOOT_COMPLETED
Un `BootReceiver : BroadcastReceiver` escucha `RECEIVE_BOOT_COMPLETED`. Si el flag de autostart está activo (persistido en `SharedPreferences`/DataStore), lanza el `PrinterServerService` con `startForegroundService`.

### D6: Modelo de respuestas HTTP
Respuestas JSON uniformes: `{ "ok": bool, "error"?: string, ... }`. Códigos: `200` éxito, `400` entrada inválida (body vacío/base64 malo), `404` ruta desconocida, `405` método no permitido, `503` impresora no disponible. Los errores del SDK se capturan y se mapean a `503` con motivo, sin tumbar el servidor (aislamiento de fallos).

### D7: Concurrencia
NanoHTTPD atiende peticiones en hilos propios. El acceso a `sendRAWData` se **serializa** (lock/monitor en `PrinterManager`) porque la impresora es un recurso único; así se evita intercalar comandos de dos trabajos simultáneos.

## Risks / Trade-offs

- **Superficie de red abierta (sin auth)** → cualquiera en la LAN puede imprimir o agotar papel. *Mitigación*: documentar el riesgo, recomendar red aislada; dejar el punto de extensión para token/HTTPS en fase posterior (Non-goal actual).
- **Bloqueo/latencia por impresión serializada** → trabajos grandes retrasan a los siguientes. *Mitigación*: serialización con timeout y respuesta de error si excede; documentar que es una impresora única.
- **Servicio de impresión ausente o de otro modelo** → binding falla. *Mitigación*: `getStatus` reporta "impresora no disponible" y `/print` responde `503` en lugar de crashear.
- **Restricciones de foreground service / batería (Android 8+/13+)** → el SO puede limitar autostart o requerir `POST_NOTIFICATIONS`. *Mitigación*: tipo de foreground service adecuado, canal de notificación, solicitar permiso de notificaciones en Android 13+; documentar exclusión de optimización de batería si hace falta.
- **IP dinámica de la LAN** → la URL de acceso cambia. *Mitigación*: mostrar la IP actual en la UI y en la notificación; recomendar IP fija/reserva DHCP.
- **NanoHTTPD es mantenimiento comunitario** → riesgo de dependencia. *Trade-off* aceptado por simplicidad; el `serve()` está encapsulado para poder migrar a Ktor si hiciera falta.

## Migration Plan

Proyecto nuevo, sin datos ni sistema previo que migrar. Despliegue:
1. Compilar el APK (`assembleRelease`).
2. Instalar en la Sunmi V2 (`adb install` o gestor de apps del terminal).
3. Conceder permiso de notificaciones (Android 13+) y habilitar autostart en la UI.
4. Verificar impresión con `curl --data-binary @ticket.bin http://<ip>:8080/print`.

*Rollback*: desinstalar el APK; no deja estado en otros sistemas.

## Open Questions

- ¿Puerto por defecto definitivo (`8080` vs `9100` estilo RAW/JetDirect)? Se asume `8080` configurable.
- ¿Se necesita también un endpoint para consultar/gestionar la cola o basta con impresión síncrona? Se asume síncrono por ahora.
- ¿Versión mínima de Android/target concreta del parque de Sunmi V2 a soportar? A confirmar con el hardware real.
- ¿Disponibilidad de `sunmiprinter` en un repositorio Maven accesible, o hay que vendorizar los AIDL? A confirmar al implementar.

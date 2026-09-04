# Sunmi HTTP Printer Server

App Android que corre en la **Sunmi V2** y expone la impresora térmica interna como un
**servidor HTTP** en la red local. Acepta comandos **ESC/POS crudos** (passthrough) y los
reenvía a la impresora vía el servicio de impresión de Sunmi (`sendRAWData`).

## Requisitos

- Terminal Sunmi con el servicio de impresión instalado (`woyou.aidlservice.jiuiv5`).
- Android 7.0+ (minSdk 24). Compilado con SDK 34, Kotlin 1.9, AGP 8.5.

## Compilar

```bash
./gradlew :app:assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

## Compilar con Docker (sin SDK en el host)

```bash
# Compila y extrae el APK a ./dist/app-debug.apk (requiere BuildKit)
DOCKER_BUILDKIT=1 docker build --target export --output type=local,dest=./dist .
```

La imagen instala JDK 17 + Android SDK (platforms 34, build-tools 34.0.0) y compila con el
wrapper de Gradle. Alternativa sin BuildKit:

```bash
docker build --target build -t sunmi-printer-server:build .
id=$(docker create sunmi-printer-server:build)
docker cp "$id":/workspace/app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk
docker rm "$id"
```

## Instalar

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Abre la app, ajusta el puerto (por defecto **8080**), activa **autostart** si quieres que
arranque al encender el terminal, y pulsa **Iniciar**. La pantalla y la notificación
muestran la URL base `http://<ip-sunmi>:<puerto>`.

## API HTTP

Sin autenticación: pensado para una **red local de confianza**.

| Método | Ruta      | Descripción |
|--------|-----------|-------------|
| GET    | `/`       | Healthcheck (`service`, `version`). |
| GET    | `/status` | Estado del servidor y de la impresora. |
| POST   | `/print`  | Imprime ESC/POS crudo. |

### Imprimir ESC/POS binario

```bash
curl --data-binary @ticket.bin \
  -H "Content-Type: application/octet-stream" \
  http://<ip-sunmi>:8080/print
```

### Imprimir ESC/POS en base64

```bash
curl -H "Content-Type: application/json" \
  -d '{"escpos_base64":"G0AbYQE..."}' \
  http://<ip-sunmi>:8080/print
```

### Códigos de respuesta

- `200` impresión aceptada / consulta correcta
- `400` cuerpo vacío o `escpos_base64` inválido
- `404` ruta desconocida · `405` método no permitido
- `503` impresora no disponible

## Nota sobre el AIDL de Sunmi

`app/src/main/aidl/woyou/aidlservice/jiuiv5/` contiene una **copia vendorizada** de la
interfaz AIDL oficial de Sunmi. El **orden de los métodos** define los IDs de transacción
Binder y debe coincidir con el servicio del terminal. Si tu firmware usa una versión de SDK
distinta, sustituye estos `.aidl` por los oficiales de Sunmi. El acoplamiento con el SDK
está aislado en `printer/PrinterManager.kt`.

## Verificación en dispositivo (pendiente de hardware)

Las pruebas 6.2–6.6 del cambio OpenSpec requieren un terminal Sunmi V2 físico:
impresión binaria y base64, corte de papel, `/status` con impresora lista / sin papel /
servicio no disponible, supervivencia del foreground service y autostart tras reinicio,
y verificación de los códigos de error.

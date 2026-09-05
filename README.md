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

| Método | Ruta           | Descripción |
|--------|----------------|-------------|
| GET    | `/`            | Healthcheck (`service`, `version`). |
| GET    | `/status`      | Estado del servidor y de la impresora. |
| GET    | `/diagnostics` | Lectura cruda del servicio (serial/versión/modelo/estado) para depurar el AIDL. |
| POST   | `/print`       | Imprime una **imagen** (`printBitmap`) o **ESC/POS crudo** (`sendRAWData`). |

`/print` decide el modo automáticamente:

- **Imagen → `printBitmap`** (recomendado para etiquetas): `Content-Type: image/png` (o
  `image/jpeg`), o JSON `{ "image_base64": "..." }`. También se autodetecta por la cabecera
  del fichero si mandas la imagen como `application/octet-stream`.
- **ESC/POS crudo → `sendRAWData`**: `application/octet-stream` con bytes que no son imagen, o
  JSON `{ "escpos_base64": "..." }`.

La respuesta incluye `"mode": "bitmap"` o `"mode": "escpos"` según la vía usada.

### Imprimir una imagen (printBitmap)

```bash
# Binario PNG/JPEG
curl --data-binary @etiqueta.png \
  -H "Content-Type: image/png" \
  http://<ip-sunmi>:8080/print

# En base64 dentro de JSON
curl -H "Content-Type: application/json" \
  -d '{"image_base64":"iVBORw0KGgo..."}' \
  http://<ip-sunmi>:8080/print
```

> Para una etiqueta de 50×30 mm a 203 dpi genera la imagen a **400×240 px** (ancho útil del
> cabezal interno ≈ 384 px / 48 mm; si te pasas, recórtalo a 384).

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

## Integración con la impresora Sunmi

La impresión usa el **SDK oficial de Sunmi** (`com.sunmi:printerlibrary`, Maven Central): el
binding se hace con `InnerPrinterManager.bindService(...)` y se obtiene un `SunmiPrinterService`
en `InnerPrinterCallback`. Todo el acoplamiento con el SDK está aislado en
`printer/PrinterManager.kt`.

Se abandonó el AIDL vendorizado (`woyou.aidlservice.jiuiv5`) porque su orden de métodos —que
define los IDs de transacción Binder— no coincidía con algunos firmwares, y las impresiones
"no salían" aunque el servicio estuviera vinculado. El SDK oficial trae el AIDL correcto para
cada terminal.

## Verificación en dispositivo (pendiente de hardware)

Las pruebas 6.2–6.6 del cambio OpenSpec requieren un terminal Sunmi V2 físico:
impresión binaria y base64, corte de papel, `/status` con impresora lista / sin papel /
servicio no disponible, supervivencia del foreground service y autostart tras reinicio,
y verificación de los códigos de error.

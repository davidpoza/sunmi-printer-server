## ADDED Requirements

### Requirement: Servidor HTTP embebido en la LAN

El sistema SHALL exponer un servidor HTTP embebido, escuchando en todas las interfaces de red del dispositivo (`0.0.0.0`) en un puerto TCP configurable (por defecto `8080`), accesible desde otros equipos de la red local.

#### Scenario: Servidor accesible en la LAN

- **WHEN** el servidor está iniciado y un cliente de la misma red local envía una petición HTTP a `http://<ip-sunmi>:<puerto>/`
- **THEN** el servidor responde con `200 OK` y un cuerpo que identifica el servicio y su versión

#### Scenario: Puerto configurable

- **WHEN** el usuario configura un puerto distinto al por defecto y reinicia el servidor
- **THEN** el servidor pasa a escuchar en el nuevo puerto y deja de escuchar en el anterior

### Requirement: Impresión de ESC/POS crudo en binario

El sistema SHALL aceptar peticiones `POST /print` con cuerpo binario (`Content-Type: application/octet-stream`) y reenviar los bytes recibidos como comandos ESC/POS crudos a la impresora interna sin transformarlos.

#### Scenario: Impresión binaria correcta

- **WHEN** un cliente hace `POST /print` con `Content-Type: application/octet-stream` y un cuerpo de bytes ESC/POS válidos
- **THEN** el servidor entrega esos bytes a la impresora y responde `200 OK` con un cuerpo JSON que indica éxito

#### Scenario: Cuerpo vacío

- **WHEN** un cliente hace `POST /print` sin cuerpo o con cuerpo de longitud cero
- **THEN** el servidor responde `400 Bad Request` y no envía nada a la impresora

### Requirement: Impresión de ESC/POS crudo en base64

El sistema SHALL aceptar peticiones `POST /print` con cuerpo JSON (`Content-Type: application/json`) que contenga un campo `escpos_base64`, decodificar ese valor y reenviar los bytes resultantes como comandos ESC/POS crudos a la impresora.

#### Scenario: Impresión base64 correcta

- **WHEN** un cliente hace `POST /print` con JSON `{ "escpos_base64": "<base64 válido>" }`
- **THEN** el servidor decodifica el base64, envía los bytes a la impresora y responde `200 OK` con éxito

#### Scenario: Base64 inválido

- **WHEN** el campo `escpos_base64` no es un base64 válido
- **THEN** el servidor responde `400 Bad Request` con un mensaje de error y no envía nada a la impresora

### Requirement: Endpoint de estado y salud

El sistema SHALL exponer `GET /status` que devuelva en JSON el estado del servidor (activo, puerto) y de la impresora (conexión con el servicio de impresión y estado del papel cuando esté disponible).

#### Scenario: Consulta de estado con impresora lista

- **WHEN** un cliente hace `GET /status` y la impresora está disponible y con papel
- **THEN** el servidor responde `200 OK` con un JSON que incluye `server.running=true`, el puerto y `printer.status` indicando disponibilidad

#### Scenario: Consulta de estado con impresora no disponible

- **WHEN** un cliente hace `GET /status` y el servicio de impresión no está disponible
- **THEN** el servidor responde `200 OK` con un JSON en el que `printer.status` refleja la indisponibilidad, sin fallar la petición

### Requirement: Validación de método y ruta

El sistema SHALL rechazar rutas no reconocidas con `404 Not Found` y métodos no permitidos sobre rutas válidas con `405 Method Not Allowed`.

#### Scenario: Ruta desconocida

- **WHEN** un cliente solicita una ruta no definida por la API
- **THEN** el servidor responde `404 Not Found`

#### Scenario: Método no permitido

- **WHEN** un cliente hace `GET /print` (método no permitido en esa ruta)
- **THEN** el servidor responde `405 Method Not Allowed`

### Requirement: Manejo de errores de impresión

El sistema SHALL devolver `503 Service Unavailable` cuando reciba una petición de impresión válida pero la impresora no esté disponible, e informar del motivo en el cuerpo de la respuesta.

#### Scenario: Impresora no disponible al imprimir

- **WHEN** un cliente hace `POST /print` con datos válidos pero el servicio de impresión no está conectado
- **THEN** el servidor responde `503 Service Unavailable` con un cuerpo que describe el error

## ADDED Requirements

### Requirement: Cada etiqueta se imprime exactamente una vez

El sistema SHALL imprimir la imagen recibida en `POST /print` (ruta de imagen → `printBitmap`) **una sola vez** por petición, sin producir copias adicionales por contenido retenido o re-confirmado en el buffer del servicio de impresión Sunmi.

#### Scenario: Petición individual produce una copia

- **WHEN** un cliente envía una imagen de etiqueta válida a `POST /print`
- **THEN** la impresora imprime exactamente una copia de esa etiqueta y el servidor responde `200 OK`

#### Scenario: Secuencia A, B, A no duplica

- **WHEN** se envían tres peticiones consecutivas con las etiquetas A, B y A (una por petición)
- **THEN** la impresora produce exactamente la secuencia A, B, A (y no A, A, B, A ni ninguna copia extra)

#### Scenario: No queda contenido retenido para la siguiente petición

- **WHEN** se completa una petición de impresión de imagen
- **THEN** ningún resto del ráster impreso queda retenido de forma que se vuelva a imprimir en una petición posterior

### Requirement: La etiqueta se expulsa sin desfase

El sistema SHALL expulsar físicamente la etiqueta impresa dentro de la misma petición, avanzando el papel lo necesario, de modo que no dependa de la siguiente petición para salir (sin el desfase "una etiqueta por detrás").

#### Scenario: Expulsión inmediata

- **WHEN** un cliente imprime una etiqueta y no se envía ninguna petición posterior
- **THEN** la etiqueta sale completa del cabezal sin necesidad de un trabajo de impresión adicional

#### Scenario: Confirmación tras completar la impresión

- **WHEN** la impresión y el avance de papel han terminado
- **THEN** el servidor confirma el resultado (éxito o fallo con motivo) basándose en el callback del servicio de impresión, y responde en consecuencia

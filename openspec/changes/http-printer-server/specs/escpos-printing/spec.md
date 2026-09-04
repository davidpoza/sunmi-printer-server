## ADDED Requirements

### Requirement: Reenvío de bytes ESC/POS crudos a la impresora interna

El sistema SHALL integrarse con el servicio de impresión de Sunmi y enviar los bytes ESC/POS recibidos tal cual (passthrough), sin reinterpretarlos ni reordenarlos, usando el mecanismo de datos crudos del SDK (`sendRAWData`).

#### Scenario: Passthrough sin modificación

- **WHEN** el sistema recibe una secuencia de bytes ESC/POS para imprimir
- **THEN** entrega exactamente esos bytes a la impresora en el mismo orden, sin añadir ni quitar bytes

#### Scenario: Secuencia con corte de papel

- **WHEN** los bytes ESC/POS incluyen un comando de corte de papel al final
- **THEN** la impresora imprime el contenido y ejecuta el corte tal como indican los comandos

### Requirement: Conexión y reconexión con el servicio de impresión

El sistema SHALL establecer la conexión con el servicio de impresión de Sunmi al iniciarse y SHALL reintentar la conexión si el servicio se desconecta, de forma que las peticiones posteriores puedan volver a imprimir sin reiniciar la app.

#### Scenario: Servicio disponible al arrancar

- **WHEN** el sistema arranca y el servicio de impresión de Sunmi está instalado y disponible
- **THEN** el sistema queda vinculado (bound) al servicio y listo para imprimir

#### Scenario: Recuperación tras desconexión

- **WHEN** el servicio de impresión se desconecta y posteriormente vuelve a estar disponible
- **THEN** el sistema restablece la conexión y las siguientes peticiones de impresión se procesan correctamente

### Requirement: Reporte de estado de la impresora

El sistema SHALL exponer el estado de la impresora (conexión con el servicio y estado del papel cuando el SDK lo proporcione) para que la API y la UI puedan consultarlo.

#### Scenario: Sin papel

- **WHEN** la impresora reporta falta de papel
- **THEN** el estado expuesto por el sistema refleja la condición de falta de papel

#### Scenario: Impresora operativa

- **WHEN** la impresora está conectada y con papel
- **THEN** el estado expuesto indica que la impresora está lista para imprimir

### Requirement: Aislamiento de errores del dispositivo

El sistema SHALL capturar los errores y excepciones provenientes del SDK/servicio de impresión y traducirlos a un resultado de operación (éxito o fallo con motivo), sin que un fallo de la impresora provoque el cierre del servidor HTTP.

#### Scenario: Excepción del SDK durante la impresión

- **WHEN** el SDK de impresión lanza una excepción al procesar una petición
- **THEN** el sistema captura el error, devuelve un resultado de fallo con el motivo y el servidor HTTP sigue operativo para atender nuevas peticiones

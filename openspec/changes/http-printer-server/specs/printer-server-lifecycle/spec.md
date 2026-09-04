## ADDED Requirements

### Requirement: Servicio en primer plano

El sistema SHALL ejecutar el servidor HTTP dentro de un servicio en primer plano (foreground service) con una notificación persistente, de modo que el sistema operativo no lo detenga mientras esté activo.

#### Scenario: Notificación persistente mientras el servidor corre

- **WHEN** el servidor está iniciado
- **THEN** el sistema muestra una notificación persistente que indica que el servidor está activo, con su IP y puerto

#### Scenario: Servidor sobrevive en segundo plano

- **WHEN** el usuario sale de la app o la pantalla se apaga
- **THEN** el servicio en primer plano sigue ejecutándose y el servidor continúa atendiendo peticiones

### Requirement: Arranque automático al encender el dispositivo

El sistema SHALL arrancar el servidor automáticamente tras el arranque del dispositivo (`BOOT_COMPLETED`) cuando el autostart esté habilitado.

#### Scenario: Autostart habilitado

- **WHEN** el dispositivo termina de arrancar y el autostart está habilitado
- **THEN** el sistema inicia el servicio en primer plano y el servidor queda escuchando sin intervención del usuario

#### Scenario: Autostart deshabilitado

- **WHEN** el dispositivo termina de arrancar y el autostart está deshabilitado
- **THEN** el sistema no inicia el servidor automáticamente

### Requirement: Pantalla de configuración y estado

El sistema SHALL ofrecer una pantalla que muestre el estado del servidor (activo/inactivo, IP y puerto), el estado de la impresora, y permita iniciar/detener el servidor y configurar el puerto y el autostart.

#### Scenario: Iniciar el servidor desde la UI

- **WHEN** el usuario pulsa "Iniciar" con el servidor detenido
- **THEN** el sistema arranca el servicio en primer plano y la pantalla muestra el servidor como activo con su IP y puerto

#### Scenario: Detener el servidor desde la UI

- **WHEN** el usuario pulsa "Detener" con el servidor activo
- **THEN** el sistema detiene el servidor y el servicio en primer plano, y la pantalla muestra el servidor como inactivo

#### Scenario: Mostrar dirección de acceso

- **WHEN** el servidor está activo y el dispositivo tiene una IP de red local
- **THEN** la pantalla muestra la URL base (`http://<ip>:<puerto>`) para que el usuario sepa cómo acceder

### Requirement: Persistencia de la configuración

El sistema SHALL persistir la configuración del usuario (puerto y autostart) de forma que se conserve entre reinicios de la app y del dispositivo.

#### Scenario: Configuración conservada tras reinicio

- **WHEN** el usuario cambia el puerto o el autostart y posteriormente se reinicia la app o el dispositivo
- **THEN** el sistema recupera los últimos valores configurados

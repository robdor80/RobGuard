# RobGuard 0.1.0 — Primera versión oficial

Primera publicación de RobGuard para Android, distribuida por RobPublisher y el motor RS-05, con instalación desde RobStore.

## Funciones incluidas
- Bóveda de contraseñas cifrada localmente con Android Keystore y AES-256-GCM.
- Creación, consulta, edición, búsqueda, eliminación y generación de contraseñas.
- Desbloqueo mediante autenticación del dispositivo y bloqueo de la bóveda.
- Vinculación de la única cuenta Google autorizada.
- Copia, recuperación mediante código y sincronización cifrada con Firebase (RG-02).

## Seguridad y límites de esta versión
- La nueva gestión de dispositivos, revocación y borrado diferido (RG-03) está incorporada al código, pero **DESACTIVADA** en esta primera publicación. Se habilitará tras la migración de Firebase y pruebas físicas controladas.
- La primera instalación se dedica a comprobaciones reales en el Realme GT Neo 2. No utilizar RobGuard como única copia de contraseñas reales hasta certificar la restauración y sincronización entre dispositivos.
- Las reglas de Firebase en producción no se modifican al publicar esta versión.
- La instalación puede requerir confirmación de Android. No se promete instalación silenciosa.

Paquete oficial: `es.robertodorado.robguard`. Versión `0.1.0`, código `1`. Se requiere conservar el certificado Release original para todas las actualizaciones.

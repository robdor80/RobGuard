# RobGuard 0.2.2 — Corrección urgente de arranque tras cambiar el icono

## Corrección
- Solucionado un cierre inmediato al abrir RobGuard 0.2.1 provocado por utilizar `painterResource()` de Jetpack Compose sobre un recurso XML Android `<inset>` no compatible.
- Las dos imágenes internas de la app cargan ahora directamente el archivo PNG `R.drawable.robguard_shield`, compatible con Compose.
- **Se conserva el escudo RobStyle azul/cian con candado dorado**, ahora con recurso PNG RGBA de 512 × 512 píxeles sin pérdida ni fondo negro en el lanzador, las pantallas internas y RobStore.

## Compatibilidad y seguridad
- Se mantienen intactos el cifrado, Android Keystore, Firebase, autorización de dispositivos y esquema de la bóveda.
- Mismo identificador `es.robertodorado.robguard` y firma Release histórica.
- `versionCode=4`, `versionName=0.2.2`.
- Publicar solo mediante RobPublisher y RS-05. Instalar como actualización desde RobStore, **sin desinstalar ni borrar datos**.
- Se corrige un fallo introducido en la 0.2.1; la compilación automática no prueba el arranque físico de una app Android.
- Verificar arranque en pantalla bloqueada, apertura con huella, entradas persistentes y sincronización en Realme tras actualizar.

La recuperación y revocación entre Realme y Galaxy Tab S9+ continúa pendiente de certificación física.

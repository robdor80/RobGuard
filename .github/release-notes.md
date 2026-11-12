# RobGuard 0.2.4 — Barra de estado Android respetada en tablets

## Corrección UX
- Corregido el solapamiento superior de RobGuard con la zona de fecha, hora, batería y demás iconos Android de la Samsung Galaxy Tab S9+.
- La cabecera de RobGuard añade `statusBarsPadding()` **solo en tablets** (`smallestScreenWidthDp >= 600`) en vertical y horizontal.
- El ajuste se aplica con la bóveda tanto bloqueada como desbloqueada; la barra superior y el botón «Bloquear» quedan por debajo del área del sistema.
- Se conserva íntegramente la corrección inferior `navigationBarsPadding()` de la versión 0.2.3.
- El Realme GT Neo 2 y demás teléfonos no reciben cambios visuales.

## Distribución y seguridad
- Sin cambios en secretos, firma, cifrado, Firebase, datos de la bóveda, autorizaciones de dispositivos ni permisos.
- Identidad Android invariable `es.robertodorado.robguard`; `versionCode=6`, `versionName=0.2.4`.
- Publicar únicamente mediante RobPublisher → GitHub Actions RS-05 → RobStore, usando el certificado histórico.
- Instalar sobre la versión anterior; **no desinstalar ni borrar datos**.

## Validación
- CI: `testReleaseUnitTest`, `lintRelease`, `assembleRelease`, contrato RS-05.
- Validación física pendiente en Galaxy Tab S9+: barras de sistema y cabecera sin solapamiento, en vertical y horizontal, bloqueada y desbloqueada.
- Confirmación visual de que en teléfono no ha cambiado el diseño.

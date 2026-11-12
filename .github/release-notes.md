# RobGuard 0.2.3 — Ajuste UX exclusivo para tablets

## Corrección
- En tablets Android (ancho mínimo de pantalla de 600 dp), la barra inferior **Bóveda · Nueva · Ajustes** respeta ahora los márgenes reales de navegación del sistema Android.
- Soluciona el solapamiento que impedía tocar «Ajustes» en la **Samsung Galaxy Tab S9+**, tanto en vertical como en horizontal.
- Utiliza `LocalConfiguration.current.smallestScreenWidthDp` como condición responsive equivalente a una media query `sw600dp`.
- Aplica `navigationBarsPadding()` exclusivamente a la barra inferior de tablets; admite navegación por gestos, tres botones y barra lateral en horizontal.
- En teléfonos (incluido Realme GT Neo 2) se conserva sin modificaciones el diseño anterior.

## Seguridad y actualización oficial
- Sin modificaciones en cifrado, Android Keystore, persistencia, Firebase, sincronización, cuentas o autorización de dispositivos.
- Misma identidad Android `es.robertodorado.robguard` y mismo certificado Release histórico.
- `versionName=0.2.3`, `versionCode=5`.
- Publicar **solo desde RobPublisher** mediante RS-05 y actualizar desde RobStore **sin desinstalar ni borrar datos**.

## Pruebas
- CI: `testReleaseUnitTest`, `lintRelease`, `assembleRelease`, comprobación RS-05.
- Verificación física pendiente: acceso a Ajustes en Galaxy Tab S9+ vertical/horizontal, comprobar que queda por encima de la navegación Android y que el Realme no cambia.
- La compilación verde no equivale a validación de interacción en tablet real.

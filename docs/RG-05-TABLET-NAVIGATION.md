# RG-05 — Corrección rápida de insets de navegación para tablets (0.2.3)

**Incidencia comunicada:** el pie de navegación de RobGuard invade la zona de botones/gestos de Android en Samsung Galaxy Tab S9+, tanto en vertical como en horizontal. «Ajustes» deja de poder pulsarse.

## Alcance mínimo
- `RobGuardApp.kt`, componente `UnlockedApp`: activar `Modifier.navigationBarsPadding()` **únicamente** cuando `LocalConfiguration.current.smallestScreenWidthDp >= 600`.
- Se aplica al contenedor de `bottomBar` dentro de `Scaffold`, no a todo el contenido ni a la aplicación Android completa.
- Respeta insets reales del sistema (inferior y lateral si procede), evitando un margen fijo no adaptable.
- En el Realme y demás teléfonos `sw<600dp`, la composición original de la barra inferior no cambia.
- No añadir pantallas, permisos, preferencias, ni modificar autenticación/seguridad.

## Criterios de aceptación
1. Pruebas unitarias para el umbral de 600dp y estabilidad ante cambios de orientación.
2. CI Release pasa tests, lint, ensamblado y RS-05.
3. Publicación exclusivamente por RobPublisher con la firma oficial preservada; incrementar `versionCode` a 5.
4. Confirmación **manual en Galaxy Tab S9+**: pestaña «Ajustes» pulsable en portrait y landscape, sin solaparse con la barra de navegación Android.
5. Confirmación básica en Realme de que su pie sigue igual. Mantener las entradas existentes: no desinstalar ni borrar datos.

**Limitación:** las pruebas unitarias comprueban la activación de la variante, pero no sustituyen una verificación instrumentada de los insets en un dispositivo físico.

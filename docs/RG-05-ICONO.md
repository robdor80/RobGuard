# RG-05 — Cambio de icono RobStyle

## Entrega visual 0.2.1
- Emblema aprobado: escudo azul/cian con borde plateado y candado dorado.
- Transparencia alfa sin fondo negro ni baldosa rectangular.
- Imagen optimizada para el lanzador (recurso `drawable-nodpi/robguard_shield.png`) y el catálogo (`robstore/icon.png`) a partir del diseño original.
- Foreground adaptativo `ic_robguard_foreground.xml` con margen de seguridad (18 dp por lado).
- `ic_launcher.xml` e `ic_launcher_round.xml` reutilizan el mismo foreground y el fondo transparente existente.
- Solo cambio de recursos visuales + versionado; sin cambios en lógica RG-01..RG-04.
- Release candidata `0.2.1` / código `3`, compatible con el protocolo RS-05 y la firma Release original.

## Verificaciones antes de publicar
1. CI GitHub Actions: `testReleaseUnitTest`, `lintRelease`, `assembleRelease`, `tools/check_rg00.py`.
2. Publicación exclusiva desde RobPublisher, sin APK Debug ni distribución por CI normal.
3. Actualización RobStore sobre 0.2.0 sin borrar datos, comprobación de icono en lanzador y catálogo.
4. Validación básica de apertura de bóveda, huella, persistencia y sincronización en Realme.
5. RG-04 mantiene pendiente la certificación física del segundo dispositivo.

No se pretende cerrar P01-P05 de RG-05 por este cambio visual puntual.

# RG-05 — Incidencia de arranque en 0.2.1 y hotfix 0.2.2

## Síntoma
Tras actualizar desde RobStore a 0.2.1 en el Realme GT Neo 2, la app abre y se cierra inmediatamente. Incidencia comunicada por el propietario.

## Causa estática identificada
El cambio visual de RG-05 convirtió `@drawable/ic_robguard_foreground` de vector drawable en `<inset>` envolviendo el PNG del escudo. Pero `RobGuardApp.kt` sigue invocando `painterResource(R.drawable.ic_robguard_foreground)` en dos componentes Compose (cabecera y pantalla de bóveda bloqueada); `painterResource()` soporta imágenes de mapa de bits y VectorDrawable, no un `InsetDrawable` arbitrario. Esa incompatibilidad es la causa más probable del cierre en composición. No se dispone de logcat del equipo para confirmar traza exacta.

## Corrección y prevención
- Sustituir ambas referencias Compose por `painterResource(R.drawable.robguard_shield)` (PNG disponible en `drawable-nodpi`).
- Mantener `<inset>` solo en la capa de icono adaptativo que carga el lanzador de Android.
- Conservar sin cambios paquetes, firma, base de datos y cifrado.
- Versionar como `0.2.2` / `versionCode=4`; publicar únicamente con el motor central RS-05/RobPublisher.
- Antes de publicar, verificar tests, lint, ensamblado y contrato.
- Actualizar sobre la instalación 0.2.1 SIN borrar datos; confirmar arranque, huella y persistencia en Realme.
- **Lección:** los tests de JVM y la compilación no sustituyen una prueba de arranque en dispositivo físico cuando se cambian recursos Compose.

La actualización resuelve el origen estático, pero la corrección en dispositivo no queda certificada hasta prueba física.

## Mejora adicional solicitada: calidad del icono
La 0.2.1 utilizaba PNG de solo 128 × 128 píxeles, demasiado pequeño para el renderizado de Compose a densidades altas. La 0.2.2 sustituye ambas copias del PNG por el diseño aprobado renderizado a 512 × 512, formato RGBA con transparencia y sin pérdida de color. Se añade una prueba estática de resolución, identidad de recursos y referencia Compose en `tools/check_rg00.py`.

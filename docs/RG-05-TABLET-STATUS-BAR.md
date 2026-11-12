# RG-05 — Hotfix tablet: barra de estado superior (0.2.4)

## Incidencia
En Samsung Galaxy Tab S9+, RobGuard aún invadía la zona superior con la fecha y hora de Android en vertical y horizontal, después de solucionarse la navegación inferior en 0.2.3.

## Cambio mínimo y seguro
- Solo `RobGuardApp.kt`: en `Header()` reutilizar la media query ya probada `smallestScreenWidthDp >= 600` y aplicar `Modifier.statusBarsPadding()` antes del padding interno.
- Afecta tanto a `LockedApp` como `UnlockedApp`, puesto que ambos usan la misma cabecera.
- Respeta los insets reales de Android, sin introducir offsets fijos o modificar el tema/ventana.
- El móvil permanece exactamente igual y la barra inferior de 0.2.3 no se altera.
- Sin cambios en datos, almacenamiento, descifrado, cuenta, credenciales o claves.

## Criterios de cierre
- CI Release PASS y RS-05 PASS.
- Publicación desde RobPublisher, con la misma firma original y `versionCode=6`.
- Verificación real Galaxy Tab S9+ en ambas orientaciones: encabezado íntegramente bajo la barra Android, acceso correcto a «Bloquear» y «Ajustes».
- Verificar también pantalla bloqueada y desbloqueada y que la instalación conserva la bóveda.
- No afirmar validación física hasta confirmación del propietario.

Nota: compilar y probar el punto de ruptura no sustituye inspección visual de insets en hardware real.

# RobGuard 0.2.0 — Autorización individual de dispositivos (RG-03)

**Versión candidata, NO publicar hasta finalizar la preparación de Firebase indicada en `docs/RG-03-MIGRACION.md`.**

## Novedades
- Identidad de instalación separada de la cuenta Google del propietario.
- Inventario de dispositivos registrados y estados pendientes/autorizados.
- Aprobación de dispositivos adicionales desde un dispositivo ya autorizado.
- Revocación del acceso a la bóveda de Firebase por instalación.
- Solicitud de borrado local diferido cuando una instalación revocada vuelva a ejecutar RobGuard con conexión.

## Migración segura desde 0.1.0
- Esta versión activa el modo RG-03. **Antes de publicarla**, el propietario debe habilitar Firebase Auth Anonymous y desplegar temporalmente `firestore.rg03.preparation.rules`.
- El Realme debe aparecer inicialmente como `pending` y ser autorizado una sola vez mediante Firebase Console.
- Después de verificar que la instalación autorizada puede sincronizar, sustituir las reglas temporales por **`firestore.rg03.rules` finales**.
- No borrar ni reinstalar RobGuard: la actualización conserva la bóveda local y sus datos cuando Android reconoce el mismo paquete y certificado.
- Durante las reglas temporales la sesión Google antigua sigue permitida y la revocación aún no está garantizada.
- El borrado remoto requiere que la aplicación revocada vuelva a ejecutarse online; no se garantiza eliminación física ni acceso a un dispositivo apagado.
- La autorización utiliza credenciales Firebase por instalación, no una prueba hardware intransferible.

## Publicación oficial
Solo mediante **RobPublisher → RS-05 → GitHub Release → RobStore**, conservando `es.robertodorado.robguard` y el certificado Release original. `versionCode=2`, `versionName=0.2.0`.

No utilizar contraseñas reales como única copia hasta certificar recuperación y revocación física en la tablet.

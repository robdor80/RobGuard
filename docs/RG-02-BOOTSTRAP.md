# RG-02 — Registro temporal de Firebase Authentication (cerrado)

La cuenta propietaria fue autenticada correctamente con el proveedor Google,
y se obtuvo el UID real de Firebase. El UID está incorporado a
`app/src/main/res/values/rg02_config.xml` y a `firestore.rules`.

El registro se hizo mediante una página web temporal en GitHub Pages sin
acceder a Firestore ni a los datos de la bóveda.

## Operaciones de cierre obligatorias

1. Firebase → Firestore → Reglas: pegar y publicar la versión final
   de `firestore.rules` en esta rama. Nunca conservar las reglas públicas de test.
2. Firebase → Authentication → Usuarios: confirmar que el UID se corresponde
   con la cuenta Google propietaria elegida.
3. Firebase → Authentication → Configuración → Dominios autorizados:
   retirar `robdor80.github.io` después del registro web.
4. GitHub → repositorio RobGuard → Settings → Pages: deshabilitar Pages
   si no se necesita para otra finalidad.
5. Validar que otro UID, el borrado y las rutas fuera de `/vaults/{uid}`
   son rechazados por Firestore, y que el UID propietario puede realizar
   únicamente las operaciones autorizadas.

Las páginas HTML/JS temporales se han eliminado del árbol de la rama; no
dejar la herramienta de aprovisionamiento publicada de forma permanente.
No se guardaron contraseñas, claves privadas ni archivos JSON de credenciales
Firebase Admin en el repositorio.

**El UID no es un secreto.** La seguridad reside en la verificación
criptográfica de la identidad por Firebase Authentication y en las reglas de
Firestore, no en ocultar el UID.

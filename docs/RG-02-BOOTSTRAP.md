# RG-02 · Alta inicial de la identidad Google sin APK Debug

**Estado:** utilidad web temporal de un solo propósito. No es la bóveda ni permite acceder a Firestore.

1. Mantener `firestore.rules` con `CONFIGURE_OWNER_UID` y todos los accesos denegados.
2. En GitHub → `robdor80/RobGuard` → Settings → Pages → Deploy from a branch,
   seleccionar `feature/rg02-google-sync-20261111` y `/docs` y guardar.
3. En Firebase → Authentication → Settings → Authorized domains, añadir
   exclusivamente `robdor80.github.io`. No utilizar dominios desconocidos.
4. Abrir `https://robdor80.github.io/RobGuard/rg02-bootstrap-google.html`
   desde Chrome. Pulsar el botón y seleccionar la cuenta Google habitual.
5. La página muestra únicamente el UID y el correo que devuelve Firebase Auth,
   cierra la sesión web inmediatamente, no usa Firestore y no guarda tokens.
6. En Firebase → Authentication → Usuarios comprobar correo y UID registrado.
   **No subir una captura que incluya datos de recuperación ni tokens.**
7. Incorporar ese UID a la configuración Android y las dos condiciones de
   `firestore.rules`. Publicar las reglas y probar primero con usuario distinto
   para confirmar que está denegado.
8. Eliminar `docs/rg02-bootstrap-google.html` y su enlace al terminar el
   aprovisionamiento, desautorizar `robdor80.github.io` en Firebase Auth
   y desactivar GitHub Pages si no se necesita.

El archivo `google-services.json` queda fuera de Git. Solo se incorporan al
código los identificadores públicos de cliente: API key, ID de proyecto,
ID de la app Android e ID del cliente OAuth web. No incluyen la clave privada
para firmar Android ni credenciales de cuenta de servicio.

**Nota:** Firebase Authentication puede registrar otras cuentas Google si
utilizan la API pública; el acceso exclusivo a los datos se impone por UID en
Firestore y por el chequeo de UID en RobGuard, no por el mero hecho de
habilitar Google como proveedor.

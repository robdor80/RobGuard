# RG-02 — Configuración privada de Google/Firebase

No se ha realizado ningún aprovisionamiento de cuentas ni desplegado reglas. Se prepara el
cliente Android **sin modificar el motor RS-05** y sin guardar secretos de servidor.

1. En Firebase console crear/elegir proyecto, registrar Android
   `es.robertodorado.robguard`, habilitar Authentication → Google.
2. Asociar al cliente Android las huellas **SHA-1 y SHA-256 del certificado
   RELEASE oficial** generado para RS-05. No registrar la de una build Debug.
3. Localizar los identificadores públicos de la configuración Android:
   `api_key.current_key`, `mobilesdk_app_id`, `project_id` y el
   identificador OAuth **web** `client_id` (no el client ID Android).
4. Abrir `app/src/main/res/values/rg02_config.xml` y sustituir sus marcadores.
   Estos identificadores de cliente van incorporados en cualquier APK Android;
   **NO** son claves privadas de Firebase Admin, de firma Android ni contraseñas.
   Restringir API key en Google Cloud a paquete + SHA-1 y limitar APIs permitidas.
5. Registrar en la consola Firebase Auth el usuario propietario, obtener su **UID**
   desde Authentication → Users y poner el mismo UID en `rg02_config.xml` y
   ambas comparaciones de `firestore.rules`. No usar el correo como control
   principal. Mantener marcadores hasta disponer de UID real.
6. En Firestore crear base de datos Native y desplegar `firestore.rules`.
   No elegir reglas de test público. Verificar que otros UID, operaciones delete
   y rutas ajenas quedan denegados.
7. Comprobar en Release oficial en RG-04. El flujo actual de vinculación
   exige que el UID esté preconfigurado. Si aún no existe en Authentication,
   puede crearse mediante una primera autenticación en un entorno de
   aprovisionamiento controlado sin acceso Firestore y luego fijar el UID.

Mientras queden marcadores, **Google queda desactivado pero la bóveda offline no
se altera**. La compilación Release sigue pasando por la misma infraestructura
RobPublisher → RS-05 → GitHub Release → RobStore.

No subir al repositorio: `google-services.json`, cuentas de servicio,
tokens OAuth, contraseñas, p12, pfx, claves privadas ni datos reales.

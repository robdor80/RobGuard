# RG-03 — Migración controlada desde RG-02

**Estado:** documentación de procedimiento. No ejecutar todavía; RG-03 no se ha desplegado.
**Equipos:** Realme GT Neo 2 disponible ahora; Galaxy Tab S9+ se incorpora después.
**Distribución:** ÚNICAMENTE RobPublisher → RS-05 → GitHub Release → RobStore. Sin Debug.

## Decisión de arquitectura (sin Functions / Blaze)

Mantener la identidad de propietario de RG-02 con Google/Firebase Auth, pero usar **una segunda sesión de Firebase Auth anónima, independiente por instalación**, para los accesos a Firestore de la bóveda. Las reglas RG-03 requieren que ese UID anónimo tenga un documento `/devices/{deviceUid}` con `ownerUid` exacto y estado `authorized`, verificados en cada petición al servidor. La sesión Google, aunque pertenezca al propietario, **ya no puede acceder a /vaults/** con las reglas RG-03 finales.

El identificador de instalación y la clave P-256 en Android Keystore son independientes y ayudan a detectar reinstalaciones; **Firestore Rules no comprueba firmas de esa clave**. La autorización efectiva en esta variante es el **token de autenticación anónima por instalación**, que se guarda privadamente en la app pero es una credencial tipo bearer (puede ser copiada si un atacante extrae los datos de la app). Revocar el documento bloquea todas las copias de ese token. No se afirma una vinculación criptográfica de cada petición al hardware ni protección frente a un dispositivo desbloqueado y comprometido mientras siga autorizado. Se puede estudiar posteriormente un backend de firmas con Cloud Functions si se necesita esa garantía adicional.

## Dependencias de consola

- Firebase Authentication → Proveedores de acceso: Google sigue habilitado; **habilitar Anónimo**.
- Si el proyecto usa Firebase Authentication con Identity Platform, **NO activar la limpieza automática de cuentas anónimas** de 30 días: podría eliminar el UID de una instalación autorizada y obligar a vincularla de nuevo.
- Mantener el mismo Firebase Project, el UID propietario y el código de recuperación de RG-02, sin subir secretos al repo.
- La aprobación del primer dispositivo exige actuación manual del propietario en Firebase Console; un cliente sin dispositivos autorizados **no puede aprobarse a sí mismo**.
- Las consultas `get()/exists()` de Firestore Rules para dispositivos cuentan como lecturas de documentos según Firebase. Monitorizar cuota/uso en el plan vigente, sin contratar nada adicional desde el asistente.

## Archivos preparados (NO desplegados)

- `firestore.rules`: regla RG-02 actual que permanece intacta.
- `firestore.rg03.preparation.rules`: ventana de migración **TEMPORAL** con acceso heredado del Google UID; **la revocación todavía no es efectiva** mientras estas reglas estén activas.
- `firestore.rg03.rules`: reglas finales que **eliminan** el acceso Google UID heredado y requieren identidad independiente autorizada.
- `firebase.rg03-emulator.json`: solo para pruebas de emulador, no para desplegar.
- `app/src/main/res/values/rg03_mode.xml`: `false` por defecto; **no cambiar durante desarrollo**.

## Secuencia obligatoria (RG-04, con Release oficial)

1. Verificar que RG-02 está sincronizada correctamente en una Release firmada y que existe una copia externa segura del **código de recuperación**. No depender únicamente de un dispositivo.
2. Validar en CI las pruebas adversariales de `firestore.rg03.rules`, las pruebas Android Release y las comprobaciones RS-05. Confirmar que la ruta Google UID heredada es denegada en las reglas finales. No publicar si alguna prueba falla.
3. Habilitar el proveedor **Anónimo** en Firebase Auth y comprobar que la limpieza automática está desactivada.
4. Tras preparar un plan de mantenimiento/retroceso, el propietario despliega temporalmente `firestore.rg03.preparation.rules` (no el fichero final). Esto permite a RG-02 continuar accediendo y a las nuevas instalaciones registrarse como `pending`. Este intervalo **no está protegido ante un equipo revocado**.
5. Cambiar `rg03_device_access_enabled` a `true` para la Release inicial RG-03/RG-04 y distribuir mediante **RobPublisher → RS-05 → GitHub Release → RobStore**. Instalar y abrir en el Realme; vincular Google y actualizar «Mis dispositivos». Se registra el UID independiente como `pending`. La bóveda local RG-01 debe conservarse intacta.
6. Firebase Console → Firestore → `devices/{uid-anónimo-del-Realme}` creado como pending: el propietario cambia SOLO `status` a `authorized` y añade `approvedAt` con fecha de aprobación. **No modificar installationId, publicKey, ownerUid, token, clave de bóveda ni documento /vaults**.
7. Comprobar en el Realme que el inventario se carga y la sincronización/recuperación siguen funcionando en la Release firmada. Si no, detener migración sin borrar datos locales.
8. Desplegar **`firestore.rg03.rules` FINAL**. Comprobar con el emulador y prueba de cliente real que la sesión Google UID heredada recibe `permission-denied`, que la instalación autorizada sigue pudiendo sincronizar y que los dispositivos no autorizados no pueden leer ni escribir. Desde este paso la revocación **sí se aplica en el servidor**.
9. Cuando esté disponible la tablet, instalar desde RobStore, iniciar Google y solicitar alta `pending`. Desde el Realme aprobado, autorizar la tablet. Restaurar la bóveda con el código externo de recuperación. Comprobar sincronización bidireccional y revocación de prueba en **instalaciones sin contraseñas reales únicas**.
10. No dejar implantadas las reglas de preparación. Si hubiera que volver a ellas durante una incidencia, registrar explícitamente que la revocación deja de estar garantizada; no afirmar operación segura hasta recuperar las reglas finales.

**NO borrar tokens, reinstalar o desinstalar una aplicación para resolver fallos en el despliegue** antes de comprobar que existe recuperación y que las dos copias cifradas están preservadas.

## Borrado remoto y limitaciones

Cuando un dispositivo revocado vuelve a ejecutar RobGuard con conexión, su propio UID anónimo puede leer **únicamente su ficha de estado**. Si contiene `status=revoked` y `wipeRequested=true`, la app bloquea la sesión local, persiste una barrera local de revocación, destruye la KEK de Android Keystore, elimina archivos cifrados y finalmente comunica `wipe_confirmed`. La barrera evita recrear una bóveda vacía tras un cierre inesperado. Si no se puede mandar el acuse, reintenta al recuperar conexión.

No es equivalente a un borrado remoto garantizado: un móvil apagado, offline, con app desinstalada, modificada o forzada a detenerse puede conservar una copia local. La confirmación demuestra lo que comunicó el cliente, no la destrucción física de la memoria flash. Para pérdida/robo, complementar con las herramientas de localización y borrado del fabricante/Google y cambio de contraseñas críticas.

## Puertas de aceptación

- CI Android y contrato RS-05 verdes, CI Firestore emulator verde.
- Documento `/vaults/{ownerUid}` no puede leerse con Google UID ni con instalación `pending/revoked`, incluso mediante peticiones Firestore directas.
- Alta de nueva instalación no implica autorización; aprobación necesita un equipo previamente autorizado.
- Nunca se permite revocación de la propia instalación desde el cliente ni recuperar una revocada con el mismo UID.
- Una instalación puede quedar pendiente si el Realme no está a mano; el primer UID siempre requiere consola. Dejar tablets pendientes hasta su prueba física oficial.
- Sin publicación, cambios de reglas en producción, fusión de PR o pruebas Debug durante RG-03.

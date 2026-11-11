# Plan reducido RobGuard

Estado actualizado (11/11/2026): **RG-00, RG-01, RG-02 y RG-03 integradas en `main`**. RG-03 se fusionó mediante [PR #4](https://github.com/robdor80/RobGuard/pull/4) tras superar Android CI (Release sin firma) y pruebas adversariales de reglas Firestore. **El modo RG-03 continúa desactivado y sus reglas todavía no se han desplegado en Firebase.** RG-04 prepara y certifica la distribución oficial. Sin Codex por defecto.

| Fase | Entrega |
| --- | --- |
| RG-00 ✅ | Proyecto Android, interfaz, identidad, icono y contrato RS-05 |
| RG-01 ✅ (código) | Bóveda cifrada offline, CRUD, búsqueda, generación y biometría; prueba física pendiente |
| RG-02 ✅ (código) | Cuenta Google única, recuperación y sincronización cifrada Firebase; prueba física pendiente |
| RG-03 ✅ (código) | Identidad por instalación, inventario, revocación y borrado diferido; modo desactivado y reglas no desplegadas |
| RG-04 ▶ | Primera Release firmada vía RobPublisher/RobStore, validación Realme y migración RG-03; Galaxy Tab S9+ después |

Consultar **[RG-04: procedimiento completo](RG-04.md)** y [RG-03: migración](RG-03-MIGRACION.md).

## Condiciones que no se negocian

1. `es.robertodorado.robguard` es la identidad oficial y no debe cambiar al actualizar.
2. `versionCode` aumenta en cada Release; siempre se utiliza el mismo certificado Release.
3. Solo RobPublisher activa la distribución mediante el motor RS-05 central; compilar y fusionar no publica.
4. Topic GitHub: `robstore-app`. Verificarlo antes de la primera publicación.
5. Ninguna clave privada de firma, contraseña ni bóveda real se sube al repositorio público.
6. RG-01 implementó bóveda local; RG-02 añadió recuperación cifrada y sincronización; RG-03 añade control por instalación, que NO es eficaz hasta desplegar reglas finales.
7. El proyecto se prueba mediante GitHub Actions y finalmente en Realme y tablet; verde en CI no sustituye pruebas físicas.
8. RobStore es el instalador objetivo; instalar sin confirmación depende de Android y de RS-05-P05 en RobStore, no de RobGuard.

## Riesgos y salida

No instalar APK Debug ni usar contraseñas reales como única copia antes de demostrar recuperación en dispositivos oficiales. No desplegar `firestore.rg03.rules` antes de autorizar el primer equipo mediante la migración de RG-04. No declarar RG-04 totalmente certificada hasta probar el segundo dispositivo.

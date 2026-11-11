# Plan reducido RobGuard

Estado actualizado (11/11/2026): **RG-00 a RG-03 integradas en main y RG-04 validada en el Realme GT Neo 2**, con Releases oficiales 0.1.0 y 0.2.0. El modo RG-03 está activado en 0.2.0; el propietario confirmó publicación de reglas definitivas en Firebase y sincronización del Realme autorizado. Android CI y pruebas adversariales del emulador superadas. **Falta certificación física en Galaxy Tab S9+, recuperación y revocación real entre dos instalaciones.** Sin Codex por defecto.

| Fase | Entrega |
| --- | --- |
| RG-00 ✅ | Proyecto Android, interfaz, identidad, icono y contrato RS-05 |
| RG-01 ✅ (Realme) | Bóveda cifrada offline, CRUD básico, generación, huella, persistencia y bloqueo por inactividad comprobados |
| RG-02 ✅ (Realme) | Cuenta Google única, copia cifrada y sincronización verificada; recuperación en segunda instalación pendiente |
| RG-03 ✅ (Realme autorizado) | Identidad por instalación activada en 0.2.0; reglas finales publicadas según propietario; pruebas negativas y revocación física pendientes |
| RG-04 ▶ (Realme PASS) | Releases 0.1.0 y 0.2.0 instaladas/actualizadas vía RobPublisher/RobStore y probadas en Realme; tablet y denegaciones reales pendientes |

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

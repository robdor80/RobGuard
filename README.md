# RobGuard

Gestor **personal** de contraseñas para Android, con bóveda local cifrada y sincronización cifrada con Firebase.

**Estado (11/11/2026):** RG-00 a RG-03 implementadas e integradas en `main`. Android CI y pruebas adversariales de Firestore PASS. **Aún no hay Release firmada instalada y verificada físicamente en Android.** El modo por instalación RG-03 permanece DESACTIVADO, y las reglas RG-03 no se han desplegado en Firebase. La primera publicación oficial y las pruebas con Realme/Tab S9+ corresponden a RG-04. No usar RobGuard como único depósito de contraseñas reales hasta certificar recuperación y sincronización.

## Decisiones

- Kotlin + Jetpack Compose + Material 3, módulo `app`.
- Identidad oficial: `es.robertodorado.robguard` (Debug: `es.robertodorado.robguard.debug`).
- SDK mínimo 26, SDK destino y compilación 36; Java 17.
- Bóveda local AES-256-GCM; clave protegida con Android Keystore.
- Google Firebase Auth: una cuenta propietaria; RG-02 incluye recuperación mediante código y sincronización cifrada.
- RG-03 separa las identidades anónimas por instalación y prepara autorización, inventario, revocación y borrado diferido. **No funciona hasta activar el modo y desplegar reglas finales siguiendo la migración.** No equivale a una credencial hardware intransferible ni garantiza borrado instantáneo offline.
- Datos locales excluidos de las copias automáticas de Android.
- GitHub público: jamás incluir contraseñas, claves privadas ni credenciales de servicio.
- Distribución oficial únicamente **RobPublisher → RS-05 → GitHub Release → RobStore**, nunca APK manuales.

## Compilación de control sin publicación

Se requieren JDK 17, Android SDK 36 y Build Tools 35.0.0.

```sh
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
python3 tools/check_rg00.py
```

Este CI no firma con la identidad privada Release ni publica. El wrapper Gradle se verifica mediante su SHA-256 en `gradle-wrapper.properties`.

## Publicación RS-05

Contratos oficiales: `.github/android-distribution.json`, `.github/workflows/android-distribute.yml`, icono `robstore/icon.png`, solo canal `release`. La app conserva el mismo certificado de firma; una actualización exige incrementar `versionCode`.

El alta privada del propietario se realiza una sola vez desde la copia de RobStore con:

```sh
python3 tools/android_owner_setup.py --repository robdor80/RobGuard --catalog --release-only
```

No repetir el alta si ya está efectuada: verificar los secretos y certificado existentes en el momento de la primera publicación. La copia de seguridad privada de la clave se mantiene fuera de GitHub.

Consulta [plan](docs/PLAN.md), [RG-04: Release y pruebas](docs/RG-04.md), [migración RG-03](docs/RG-03-MIGRACION.md) y [contrato de distribución](docs/RS-05.md).

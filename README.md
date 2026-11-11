# RobGuard

Gestor **personal** de contraseñas para Android, con acceso local sin conexión y sincronización cifrada prevista con Firebase.

**Estado:** RG-01 implementada y comprobada en CI; RG-02 es la siguiente fase. Incluye bóveda cifrada local, CRUD, búsqueda, generador y desbloqueo del dispositivo. **Todavía no hay recuperación ni sincronización Firebase** ni una Release publicada/verificada en Android real. No usar como única copia de contraseñas reales hasta completar RG-02 y las pruebas oficiales de RG-04.

## Decisiones

- Kotlin + Jetpack Compose + Material 3, un módulo `app`.
- Identidad oficial: `es.robertodorado.robguard` (Debug: `es.robertodorado.robguard.debug`).
- SDK mínimo 26, SDK destino y compilación 36; Java 17.
- La bóveda local usa AES-256-GCM y la clave se protege mediante Android Keystore. Firebase, cuando llegue RG-02, recibirá solamente información cifrada.
- Acceso habitual por biometría, alta con la única cuenta Google autorizada y opción de revocar un dispositivo (en fases posteriores).
- Los datos locales de RobGuard no se incluyen en las copias de seguridad automáticas de Android.
- GitHub es público: jamás incluir contraseñas, claves privadas ni credenciales Firebase de servicio.
- Toda distribución oficial debe pasar por **RobPublisher → RS-05 → GitHub Release → RobStore**; no se publicarán APK manualmente.

## Compilación

Requisitos: JDK 17, Android SDK 36 y Build Tools 35.0.0.

```sh
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

El wrapper descarga y verifica en el primer uso el JAR oficial usado por RobStore, si todavía no está presente. Gradle 8.13 se verifica mediante su SHA-256 en `gradle-wrapper.properties`.

## Publicación RS-05

Los archivos oficiales están en `.github/android-distribution.json` y `.github/workflows/android-distribute.yml`. El canal habilitado es `release`.

Alta del **propietario**, una sola vez y en un dispositivo propio con acceso administrador a GitHub, JDK, Python y gh:

```sh
python3 tools/android_owner_setup.py --repository robdor80/RobGuard --catalog --release-only
```

El comando anterior pertenece al repositorio **RobStore**, no a este proyecto: ejecutarlo desde una copia de RobStore (o invocar su script por ruta). El asistente crea y protege la clave de firma en el dispositivo del propietario; no entregar keystores a una IA ni versionarlos. Añade el topic exacto `robstore-app`. Después de completar RG-04, RobPublisher podrá publicar una Release estable con un botón.

Consulta [plan breve](docs/PLAN.md), [contrato de distribución](docs/RS-05.md) y [RG-01: seguridad y pruebas](docs/RG-01.md).

La primera publicación oficial no debe hacerse hasta que estén terminadas y comprobadas la bóveda cifrada, la autenticación y la sincronización.

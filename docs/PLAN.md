# Plan reducido RobGuard

Estado actualizado (11/11/2026): **RG-00 y RG-01 cerradas en código; RG-02 fusionada en main; RG-03 en PR Draft #4 con reglas de acceso por dispositivo y tests de emulador**. La validación física de la primera versión Release firmada y la migración controlada de las reglas Firebase se realizarán en RG-04. Sin Codex por defecto. No introducir credenciales reales en builds de demostración.

| Fase | Entrega |
| --- | --- |
| RG-00 ✅ | Proyecto Android, interfaz de entrada, icono, identidad y RS-05; integrada en `main` |
| RG-01 ✅ (código) | Bóveda cifrada offline, CRUD, búsqueda, copia, generación, biometría; CI PASS, prueba física aplazada a RG-04 |
| RG-02 ✅ (código) | Google UID único y sincronización cifrada Firebase; integrado en main, pruebas físicas pendientes RG-04 |
| RG-03 ▶ | Identidad independiente por instalación, autorización en Firestore, inventario, revocación y borrado diferido; PR #4 Draft sin desplegar |
| RG-04 | Alta RS-05, firma oficial, Release y prueba de instalación/actualización RobStore |

## Condiciones que no se negocian

1. `es.robertodorado.robguard` es la identidad oficial y no debe cambiar al actualizar.
2. `versionCode` aumenta en cada Release; siempre se utiliza el mismo certificado Release.
3. Solo RobPublisher activa la distribución mediante el motor RS-05 central.
4. Topic GitHub: `robstore-app`. Se añade en el alta privada con `--catalog`.
5. Ninguna clave privada de firma, contraseña ni bóveda real se sube al repositorio público.
6. La entrega RG-00 no contenía gestor de contraseñas. RG-01 sí implementa almacenamiento cifrado, pero no recuperación ni sincronización; ambas llegan en RG-02.
7. El proyecto se prueba mediante GitHub Actions y finalmente en el móvil y la tablet.
8. RobStore es el instalador objetivo; instalar sin confirmación depende de la capacidad de Android y de RS-05-P05 en RobStore, no de RobGuard.

## Estado de aceptación de RG-01

RG-01 se cierra **como código integrado** tras superar pruebas unitarias, Android Lint, compilación Release sin firmar y comprobación del contrato RS-05. No se declara validación de biometría/Keystore en dispositivo físico: la prueba de la primera Release firmada y publicada mediante RobPublisher/RobStore queda reservada para RG-04. No instalar builds Debug/diagnóstico ni usar credenciales reales como única copia mientras no exista recuperación cifrada.

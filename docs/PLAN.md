# Plan reducido RobGuard

Estado inicial: **RG-00 en preparación**. Sin Codex por defecto. No introducir credenciales reales en builds de demostración.

| Fase | Entrega |
| --- | --- |
| RG-00 | Proyecto Android, interfaz de entrada, icono, identidad y RS-05 |
| RG-01 | Bóveda cifrada offline, CRUD, búsqueda, copia, generación, biometría |
| RG-02 | Inicio Google de cuenta única y sincronización cifrada Firebase con clave recuperable en otro dispositivo |
| RG-03 | Inventario móvil/tablet, revocación y borrado remoto al conectarse |
| RG-04 | Alta RS-05, firma oficial, Release y prueba de instalación/actualización RobStore |

## Condiciones que no se negocian

1. `es.robertodorado.robguard` es la identidad oficial y no debe cambiar al actualizar.
2. `versionCode` aumenta en cada Release; siempre se utiliza el mismo certificado Release.
3. Solo RobPublisher activa la distribución mediante el motor RS-05 central.
4. Topic GitHub: `robstore-app`. Se añade en el alta privada con `--catalog`.
5. Ninguna clave privada de firma, contraseña ni bóveda real se sube al repositorio público.
6. La fase RG-00 **no contiene gestor de contraseñas**; su interfaz explica que aún no puede guardar datos.
7. El proyecto se prueba mediante GitHub Actions y finalmente en el móvil y la tablet.
8. RobStore es el instalador objetivo; instalar sin confirmación depende de la capacidad de Android y de RS-05-P05 en RobStore, no de RobGuard.

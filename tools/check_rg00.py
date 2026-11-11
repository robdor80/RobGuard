#!/usr/bin/env python3
"""Comprobaciones mínimas RS-05 de RobGuard, sin claves ni red.

Se acepta cualquier versionCode entero positivo: RS-05 exige incrementarlo
en cada nueva Release; fijarlo a 1 impediría las actualizaciones legítimas.
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
config = json.loads((ROOT / ".github/android-distribution.json").read_text("utf-8"))
assert config["schemaVersion"] == 1
assert config["repository"] == "robdor80/RobGuard"
assert config["packages"] == {
    "release": "es.robertodorado.robguard",
    "dev": "es.robertodorado.robguard.debug",
}
assert config["robstorePolicy"] is False
assert config["iconSource"] == "robstore/icon.png"
assert (ROOT / config["iconSource"]).read_bytes().startswith(b"\x89PNG\r\n\x1a\n")
workflow = (ROOT / ".github/workflows/android-distribute.yml").read_text("utf-8")
assert "android-distribution-engine.yml@main" in workflow
assert "secrets: inherit" in workflow
gradle = (ROOT / "app/build.gradle.kts").read_text("utf-8")
assert 'applicationId = "es.robertodorado.robguard"' in gradle
assert 'applicationIdSuffix = ".debug"' in gradle
match = re.search(r"(?m)^\s*versionCode\s*=\s*(\d+)\s*$", gradle)
assert match is not None and int(match.group(1)) > 0, "versionCode inválido"
assert re.search(r'(?m)^\s*versionName\s*=\s*"[^"]+"\s*$', gradle), "versionName ausente"
print(f"RobGuard: identidad Android y RS-05 válidos (versionCode={match.group(1)}).")

# Regresión RG-05: la imagen del catálogo debe ser idéntica al PNG usado en
# la aplicación y tener resolución suficiente (sin fondo ni escalado borroso).
import struct
icon = (ROOT / config["iconSource"]).read_bytes()
inapp = (ROOT / "app/src/main/res/drawable-nodpi/robguard_shield.png").read_bytes()
assert icon == inapp, "Iconos de RobStore y Android diferentes"
width, height = struct.unpack(">II", icon[16:24])
assert width >= 512 and height >= 512, "Icono de resolución insuficiente"
assert icon[25] == 6, "Se requiere PNG RGBA con transparencia"
# Compose painterResource() no admite el drawable XML InsetDrawable del launcher.
compose = (ROOT / "app/src/main/java/es/robertodorado/robguard/RobGuardApp.kt").read_text("utf-8")
assert "painterResource(R.drawable.ic_robguard_foreground)" not in compose, (
    "InsetDrawable del launcher no es compatible con painterResource de Compose"
)
print(f"RobGuard RG-05: icono RGBA {width}x{height} y referencias Compose válidas.")

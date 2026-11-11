#!/usr/bin/env python3
"""Comprobaciones mínimas RS-05, sin claves ni red."""
import json
import pathlib

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
assert "versionCode = 1" in gradle
print("RG-00: identidad Android y contrato RS-05 presentes.")

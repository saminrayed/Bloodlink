#!/usr/bin/env python3
"""Static consistency checks for the BloodLink source tree."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java"
RESOURCE_ROOT = ROOT / "src/main/resources"
FXML_ROOT = RESOURCE_ROOT / "com/bloodlink/view"
FX = "{http://javafx.com/fxml/1}"

errors: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


# XML well-formedness
for xml_file in [ROOT / "pom.xml", *sorted(FXML_ROOT.glob("*.fxml"))]:
    try:
        ET.parse(xml_file)
    except Exception as exc:  # noqa: BLE001 - verification should report every parser failure
        fail(f"XML parse failed: {xml_file.relative_to(ROOT)}: {exc}")

# FXML controller, field, action, and stylesheet bindings
for fxml in sorted(FXML_ROOT.glob("*.fxml")):
    root = ET.parse(fxml).getroot()
    controller = root.attrib.get(FX + "controller")
    if not controller:
        fail(f"Missing fx:controller: {fxml.relative_to(ROOT)}")
        continue

    controller_file = JAVA_ROOT / Path(controller.replace(".", "/") + ".java")
    if not controller_file.exists():
        fail(f"Missing controller {controller} for {fxml.name}")
        continue

    source = controller_file.read_text(encoding="utf-8")
    for element in root.iter():
        fx_id = element.attrib.get(FX + "id")
        if fx_id and not re.search(rf"\b{re.escape(fx_id)}\b", source):
            fail(f"FXML id #{fx_id} in {fxml.name} is absent from {controller_file.name}")
        for name, value in element.attrib.items():
            if name.startswith("on") and value.startswith("#"):
                method = value[1:]
                if not re.search(rf"\b{re.escape(method)}\s*\(", source):
                    fail(f"FXML action #{method} in {fxml.name} is absent from {controller_file.name}")

    stylesheet = root.attrib.get("stylesheets")
    if stylesheet and stylesheet.startswith("@"):
        target = (fxml.parent / stylesheet[1:]).resolve()
        if not target.exists():
            fail(f"Missing stylesheet referenced by {fxml.name}: {stylesheet}")

# Package declarations must match source paths
for java_file in sorted(JAVA_ROOT.rglob("*.java")):
    source = java_file.read_text(encoding="utf-8")
    match = re.search(r"^package\s+([\w.]+);", source, re.MULTILINE)
    if not match:
        fail(f"Missing package declaration: {java_file.relative_to(ROOT)}")
        continue
    expected = JAVA_ROOT / Path(match.group(1).replace(".", "/")) / java_file.name
    if expected.resolve() != java_file.resolve():
        fail(f"Package/path mismatch: {java_file.relative_to(ROOT)}")

# SQL copies must remain identical
runtime_schema = RESOURCE_ROOT / "com/bloodlink/sql/schema.sql"
convenience_schema = ROOT / "database/schema.sql"
if runtime_schema.read_bytes() != convenience_schema.read_bytes():
    fail("database/schema.sql differs from the runtime schema resource")

# No unfinished implementation markers outside preserved reference material
unfinished = re.compile(r"\bTODO\b|implement later|UnsupportedOperationException")
for path in sorted(ROOT.rglob("*")):
    if (not path.is_file() or "docs/reference" in path.as_posix()
            or path.resolve() == Path(__file__).resolve()
            or path.suffix in {".pptx", ".png", ".zip"}):
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    if unfinished.search(text):
        fail(f"Unfinished marker found in {path.relative_to(ROOT)}")

if errors:
    print("BloodLink verification FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("BloodLink verification PASSED")
print(f" - {len(list(JAVA_ROOT.rglob('*.java')))} production Java files")
print(f" - {len(list((ROOT / 'src/test/java').rglob('*.java')))} test Java files")
print(f" - {len(list(FXML_ROOT.glob('*.fxml')))} FXML screens")

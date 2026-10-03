"""Verify current source artifacts and every preserved inventory evidence hash.

This does not grant legal clearance, resolve incomplete correspondence or prove
that a compiled APK contains the same assets. It catches stale/missing evidence.
"""

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / "docs/open-source"


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as source:
        for data in iter(lambda: source.read(1024 * 1024), b""):
            value.update(data)
    return value.hexdigest()


def verify(path, expected):
    if not path.is_file() or digest(path) != expected:
        raise ValueError("Missing or changed inventory evidence: " + path.relative_to(ROOT).as_posix())


def main():
    report = json.loads((DIRECTORY / "inventory/components.json").read_text(encoding="utf-8"))
    bom = json.loads((DIRECTORY / "inventory/bom.cdx.json").read_text(encoding="utf-8"))
    evidence = []
    for row in [report["runtimeArchive"]] + report["standaloneRuntimeArtifacts"] + report["fonts"]:
        evidence.append((ROOT / row["file"], row["sha256"]))
    for row in report["runtime"]["licenseRecords"] + report["runtime"]["embeddedBundleNoticeRecords"]:
        if row.get("file"):
            evidence.append((DIRECTORY / row["file"], row["sha256"]))
    for row in report["fontLicenseRecords"]:
        evidence.append((ROOT / row["sourceFile"], row["sha256"]))
        evidence.append((DIRECTORY / row["file"], row["sha256"]))
    for artifact in report["gradle"]:
        for row in artifact["pomEvidence"] + artifact["artifactNoticeEvidence"]:
            evidence.append((DIRECTORY / row["file"], row["sha256"]))
    research = json.loads((DIRECTORY / "inventory/research-license-evidence.json").read_text(encoding="utf-8"))
    for row in research:
        if row.get("file"):
            evidence.append((DIRECTORY / row["file"], row["sha256"]))
    checked = set()
    for path, expected in evidence:
        if (path, expected) not in checked:
            verify(path, expected)
            checked.add((path, expected))
    assert bom["bomFormat"] == "CycloneDX" and bom["specVersion"] == "1.6"
    references = [component["bom-ref"] for component in bom["components"]]
    assert len(references) == len(set(references)), "Duplicate BOM references"
    assert report["gradle"], "Resolved Gradle inventory missing"
    assert {row["configuration"] for row in report["gradle"]} == {"releaseRuntimeClasspath", "debugRuntimeClasspath"}
    assert all(row["copyrightRecords"] for row in report["runtime"]["ubuntuPackages"]), "Missing Ubuntu copyright association"
    observed = (DIRECTORY / "INVENTORY.md").read_text(encoding="utf-8")
    assert report["runtimeArchive"]["sha256"] in observed, "Human inventory uses a different runtime hash"
    shizuku = [row for row in report["gradle"] if row["group"] == "dev.rikka.shizuku"]
    assert shizuku and all(any("MIT" in declaration["name"] for declaration in row["declaredLicenses"]) for row in shizuku), "Shizuku POM license differs from documentation"
    for name in ("docs/TECHNICAL_ARCHITECTURE.md", "THIRD_PARTY_NOTICES.md", "docs/open-source/RELEASE_CHECKLIST.md", "docs/open-source/INVENTORY.md"):
        assert (ROOT / name).is_file() and (ROOT / name).stat().st_size > 100, "Canonical document missing"
    print(json.dumps({"verifiedEvidenceFiles": len(checked), "bomComponents": len(references),
                      "ubuntuCopyrightAssociations": len(report["runtime"]["ubuntuPackages"]),
                      "gradleArtifacts": len(report["gradle"]), "legalClearance": False}, ensure_ascii=False))


if __name__ == "__main__":
    main()

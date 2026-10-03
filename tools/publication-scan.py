"""Find credential-shaped literals without printing their values.

Scans source/publication candidates only; private local configs and build caches
are not read. Finding count zero is not proof screenshots/history are safe.
"""

import json
import os
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PATTERNS = {
    "provider-key-shaped": re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b"),
    "github-token-shaped": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b"),
    "private-key-header": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "aws-key-shaped": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
}
TEXT_SUFFIXES = {".kt", ".kts", ".gradle", ".md", ".txt", ".json", ".toml", ".xml", ".js", ".mjs", ".cjs", ".py", ".sh", ".properties", ".yaml", ".yml", ".ps1"}
SKIP_PARTS = {".git", ".gradle", ".idea", ".kotlin", "build", "node_modules"}
PRIVATE_NAMES = {"local.properties", ".env", ".env.local", "keystore.properties", "signing.properties"}


def main():
    findings, private_candidates, image_candidates = [], [], []
    scanned = 0
    for directory, names, files in os.walk(ROOT):
        # Prune before descending: the build tree can contain transient,
        # symlinked or concurrently removed paths that rglob would follow.
        names[:] = [name for name in names if name not in SKIP_PARTS]
        for name in files:
            path = Path(directory) / name
            relative = path.relative_to(ROOT)
            if path.name in PRIVATE_NAMES or path.suffix.lower() in {".jks", ".keystore", ".p12", ".pem"}:
                private_candidates.append(relative.as_posix())
                continue
            if path.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".mp4"}:
                image_candidates.append(relative.as_posix())
                continue
            if path.suffix.lower() not in TEXT_SUFFIXES:
                continue
            try:
                if path.stat().st_size > 8 * 1024 * 1024:
                    continue
                text = path.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            scanned += 1
            counts = {name: len(pattern.findall(text)) for name, pattern in PATTERNS.items()}
            if any(counts.values()):
                findings.append({"file": relative.as_posix(), "counts": {name: count for name, count in counts.items() if count}})
    report = {"format": "xuanji-publication-scan-v1", "scannedTextFiles": scanned,
              "findings": findings, "privateCandidatesNotRead": private_candidates,
              "mediaRequiresManualReview": image_candidates,
              "limitations": ["No values are printed. False positives (synthetic test keys) need review.",
                              "Git history, ignored caches, APK contents, EXIF/OCR and all credential formats are not covered.",
                              "Design reference screenshots and generated artwork require rights and privacy review before publishing."]}
    output = ROOT / "docs/open-source/inventory/publication-scan.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"scannedTextFiles": scanned, "filesWithCredentialShapes": len(findings),
                      "findings": findings, "privateCandidateCount": len(private_candidates),
                      "mediaCandidateCount": len(image_candidates)}, ensure_ascii=False))


if __name__ == "__main__":
    main()

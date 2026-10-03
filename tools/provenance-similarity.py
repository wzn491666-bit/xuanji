#!/usr/bin/env python3
"""Find long identical normalized line runs between a target tree and reference trees.

Used to audit early code against GPL / third-party reference projects before
publication. Line matching catches verbatim and lightly reformatted copies; it
does not catch cross-language rewrites, renamed identifiers or conceptual reuse.
It never prints file contents to the console unless a match is found; full
matched text goes to the optional JSON report for manual review.
"""

import argparse
import hashlib
import json
import os
import sys
import tempfile
from pathlib import Path


TEXT_SUFFIXES = {
    ".kt", ".kts", ".java", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".py",
    ".sh", ".bash", ".xml", ".gradle", ".toml", ".yml", ".yaml", ".md",
}
SKIP_DIRS = {
    ".git", ".gradle", ".idea", ".kotlin", ".vscode", "build", "node_modules",
    "__pycache__",
}
EXCLUDED_PREFIXES = (
    "output/",
    "docs/open-source/license-texts/",
    "docs/design/references/",
    "app/src/main/assets/runtime/",
)
MAX_BYTES = 2 * 1024 * 1024


def collect_files(root, reference_roots=()):
    root = Path(root).resolve()
    references = tuple(Path(path).resolve() for path in reference_roots)
    for directory, names, files in os.walk(root):
        directory = Path(directory)
        relative = directory.relative_to(root).as_posix()
        def excluded(value):
            return any(value == prefix.rstrip("/") or value.startswith(prefix) for prefix in EXCLUDED_PREFIXES)
        if relative != "." and excluded(relative):
            names[:] = []
            continue
        names[:] = [name for name in names if name not in SKIP_DIRS]
        for name in files:
            path = directory / name
            try:
                resolved = path.resolve()
            except OSError:
                continue
            if any(resolved == ref or ref in resolved.parents for ref in references):
                continue
            try:
                size = path.stat().st_size
            except OSError:
                continue
            if path.suffix.lower() not in TEXT_SUFFIXES or size > MAX_BYTES:
                continue
            yield path


def normalize(path):
    lines = []
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return lines
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith(("//", "/*", "*", "#!")) or line in {"{", "}", "});", "};", "*/", "#"}:
            continue
        line = " ".join(line.split())
        if len(line) < 3:
            continue
        lines.append(line)
    return lines


def windows(lines, size):
    for start in range(len(lines) - size + 1):
        block = lines[start:start + size]
        imports = sum(1 for line in block if line.startswith(("import ", "package ")))
        if imports >= size - 1:
            continue
        text = "\n".join(block)
        yield hashlib.sha256(text.encode("utf-8", "replace")).hexdigest(), start, text


def index_references(references, minimum_lines):
    index = {}
    for reference in references:
        root = Path(reference).resolve()
        for path in collect_files(root):
            relative = path.relative_to(root).as_posix()
            lines = normalize(path)
            for digest, start, text in windows(lines, minimum_lines):
                index.setdefault(digest, []).append((relative, start + 1, text))
    return index


def merge_runs(hits):
    merged = []
    for target_file, target_start, reference_file, reference_start, text in sorted(hits):
        if merged and merged[-1]["targetFile"] == target_file and \
                merged[-1]["referenceFile"] == reference_file and \
                target_start == merged[-1]["targetStart"] + merged[-1]["windowCount"] and \
                reference_start == merged[-1]["referenceStart"] + merged[-1]["windowCount"]:
            merged[-1]["windowCount"] += 1
            merged[-1]["text"] += "\n" + text.split("\n")[-1]
        else:
            merged.append({"targetFile": target_file, "targetStart": target_start,
                           "referenceFile": reference_file, "referenceStart": reference_start,
                           "windowCount": 1, "text": text})
    for item in merged:
        item["lines"] = len(item["text"].splitlines())
        del item["windowCount"]
    return merged


def scan(target, references, minimum_lines):
    index = index_references(references, minimum_lines)
    hits = []
    for path in collect_files(target, references):
        relative = path.relative_to(Path(target).resolve()).as_posix()
        lines = normalize(path)
        for digest, start, _ in windows(lines, minimum_lines):
            for reference_file, reference_start, reference_text in index.get(digest, []):
                hits.append((relative, start + 1, reference_file, reference_start, reference_text))
    findings = merge_runs(hits)
    findings.sort(key=lambda item: (-item["lines"], item["targetFile"], item["targetStart"]))
    return findings


def self_test():
    with tempfile.TemporaryDirectory(prefix="xuanji-provenance-") as directory:
        base = Path(directory)
        target = base / "target"
        reference = base / "reference"
        target.mkdir()
        reference.mkdir()
        body = "\n".join(f"val sample{i} = computeStep{i}(input{i})" for i in range(12))
        (target / "Sample.kt").write_text(body, encoding="utf-8")
        (reference / "Other.kt").write_text(body, encoding="utf-8")
        (target / "Unrelated.kt").write_text("\n".join(f"fun unique{i}() = {i}" for i in range(12)), encoding="utf-8")
        findings = scan(target, [reference], minimum_lines=8)
        assert len(findings) == 1, findings
        assert findings[0]["lines"] >= 9, findings
    print('{"selfTest":"passed"}')
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", action="append", default=[], help="Reference source tree (repeatable)")
    parser.add_argument("--target", default=".", help="Target tree to inspect")
    parser.add_argument("--min-lines", type=int, default=8, help="Minimum identical normalized lines")
    parser.add_argument("--json", dest="json_path", help="Write the full report to this path")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--top", type=int, default=20, help="Console findings to print")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if not args.reference:
        print('{"status":"error","category":"missing-reference"}')
        return 2
    findings = scan(Path(args.target), args.reference, max(4, args.min_lines))
    report = {
        "format": "xuanji-provenance-similarity-v1",
        "target": str(Path(args.target).resolve()),
        "references": [str(Path(item).resolve()) for item in args.reference],
        "minimumLines": max(4, args.min_lines),
        "findingCount": len(findings),
        "findings": [dict(item, text=item["text"][:4000]) for item in findings],
        "limitations": [
            "Line matching only; cross-language rewrites and renamed identifiers are not detected.",
            "Standard boilerplate and framework patterns can produce false positives; every hit needs manual review.",
            "A clean result is evidence of absence for this heuristic, not legal clearance.",
        ],
    }
    if args.json_path:
        path = Path(args.json_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "scanned", "findingCount": len(findings),
                      "minimumLines": report["minimumLines"]}, ensure_ascii=False))
    for item in findings[:max(0, args.top)]:
        print(f'  {item["targetFile"]}:{item["targetStart"]} <- '
              f'{item["referenceFile"]}:{item["referenceStart"]} ({item["lines"]} lines)')
    return 0


if __name__ == "__main__":
    sys.exit(main())

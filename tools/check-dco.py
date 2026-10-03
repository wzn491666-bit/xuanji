#!/usr/bin/env python3
"""Check Developer Certificate of Origin sign-offs in a commit range.

Usage:
    python tools/check-dco.py --range BASE..HEAD
    python tools/check-dco.py --self-test

Every non-merge commit must contain a ``Signed-off-by: Name <email>`` line whose
email matches the commit author email (case-insensitive). Merge commits are
skipped because they do not introduce a contribution of their own. Values are
never printed; only commit ids and failure categories are reported.
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SIGNOFF = re.compile(r"^Signed-off-by:\s*(.+?)\s*<([^>\s]+)>\s*$", re.IGNORECASE | re.MULTILINE)


def git(*args, cwd=None, check=True):
    result = subprocess.run(
        ["git", "-C", str(cwd or ROOT), *args],
        capture_output=True,
        check=False,
    )
    if check and result.returncode:
        raise RuntimeError("git-operation-failed")
    return result.stdout.decode("utf-8", "replace")


def commit_ids(range_spec, cwd=None):
    output = git("rev-list", "--no-merges", range_spec, cwd=cwd)
    return [line.strip() for line in output.splitlines() if line.strip()]


def check_commit(commit, cwd=None):
    parents = git("rev-list", "--parents", "-n", "1", commit, cwd=cwd).split()
    if len(parents) > 2:
        return None
    author = git("show", "-s", "--format=%ae", commit, cwd=cwd).strip().lower()
    message = git("show", "-s", "--format=%B", commit, cwd=cwd)
    signoffs = [(name.strip(), email.strip().lower()) for name, email in SIGNOFF.findall(message)]
    if not signoffs:
        return "missing-signoff"
    if author and not any(email == author for _, email in signoffs):
        return "signoff-author-mismatch"
    return None


def run_range(range_spec, cwd=None):
    commits = commit_ids(range_spec, cwd=cwd)
    if not commits:
        print('{"status":"no-commits","range-checked":true}')
        return 0
    failures = []
    for commit in commits:
        category = check_commit(commit, cwd=cwd)
        if category:
            failures.append({"commit": commit[:12], "category": category})
    if failures:
        print(json.dumps({"status": "dco-findings", "failures": len(failures), "items": failures}))
        return 1
    print(json.dumps({"status": "dco-passed", "commits": len(commits)}))
    return 0


def run(cwd, *args):
    result = subprocess.run(["git", "-C", str(cwd), *args], capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError("self-test git command failed")
    return result.stdout


def commit_file(repo, name, message, sign_off, author_email="author@example.com"):
    path = Path(repo) / name
    path.write_text(name, encoding="utf-8")
    run(repo, "add", name)
    env = os.environ.copy()
    env.update({
        "GIT_AUTHOR_NAME": "Test Author",
        "GIT_AUTHOR_EMAIL": author_email,
        "GIT_COMMITTER_NAME": "Test Committer",
        "GIT_COMMITTER_EMAIL": "committer@example.com",
    })
    body = message + ("\n\nSigned-off-by: Test Author <author@example.com>" if sign_off else "")
    result = subprocess.run(["git", "-C", str(repo), "commit", "-q", "-m", body], env=env, capture_output=True)
    if result.returncode:
        raise RuntimeError("self-test commit failed")


def self_test():
    with tempfile.TemporaryDirectory(prefix="xuanji-dco-") as repo:
        run(repo, "init", "-q")
        commit_file(repo, "a.txt", "first", sign_off=False)
        assert check_commit("HEAD", cwd=repo) == "missing-signoff"
        commit_file(repo, "b.txt", "second", sign_off=True)
        assert check_commit("HEAD", cwd=repo) is None
        commit_file(repo, "c.txt", "third", sign_off=True, author_email="other@example.com")
        assert check_commit("HEAD", cwd=repo) == "signoff-author-mismatch"
    print('{"selfTest":"passed","valuesPrinted":false}')
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--range", dest="range_spec", default="HEAD",
                        help="Git revision range, for example origin/main..HEAD")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    try:
        if args.self_test:
            return self_test()
        return run_range(args.range_spec)
    except RuntimeError:
        print('{"status":"check-failed","category":"git-operation-failed"}')
        return 2


if __name__ == "__main__":
    sys.exit(main())

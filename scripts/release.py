#!/usr/bin/env python3

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPOSITORY = os.environ.get("IDLESKULL_REPOSITORY", "rekjorekjo/IdleSkull")
RELEASE_BASE = f"https://github.com/{REPOSITORY}/releases/download"


def main() -> None:
    parser = argparse.ArgumentParser(description="Package an IdleSkull beta/release APK")
    parser.add_argument("--apk", required=True, help="Path to a signed release APK")
    parser.add_argument("--notes", default="release-notes.md", help="Release notes markdown")
    args = parser.parse_args()

    root = Path(__file__).resolve().parent.parent
    version_code, version_name = read_version(root / "version.properties")
    tag_name = f"v{version_name}"
    apk = Path(args.apk).resolve()
    notes = (root / args.notes).resolve() if not Path(args.notes).is_absolute() else Path(args.notes)

    validate_git_state(root)
    validate_apk(apk)
    release_notes = read_release_notes(notes)
    dist = root / "dist"
    dist.mkdir(parents=True, exist_ok=True)

    target_name = f"IdleSkull-v{version_name}.apk"
    target_apk = dist / target_name
    shutil.copy2(apk, target_apk)
    size = target_apk.stat().st_size
    sha256 = hash_file(target_apk)

    manifest = {
        "schemaVersion": 1,
        "tagName": tag_name,
        "versionName": version_name,
        "versionCode": version_code,
        "releaseNotes": release_notes,
        "apk": {
            "name": target_name,
            "url": f"{RELEASE_BASE}/{tag_name}/{target_name}",
            "size": size,
            "sha256": sha256,
        },
    }
    manifest_text = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    manifest_path = dist / "latest.json"
    manifest_path.write_text(manifest_text, encoding="utf-8")
    print(f"Generated: {target_apk}")
    print(f"Generated: {manifest_path}")
    print(f"Version: {version_name} ({version_code})")
    print("Release assets: upload both the APK and dist/latest.json")
    print("The app reads latest.json directly from the newest published GitHub Release.")


def read_version(path: Path) -> tuple[int, str]:
    content = path.read_text(encoding="utf-8")
    code_match = re.search(r"^versionCode\s*=\s*(\d+)\s*$", content, re.MULTILINE)
    name_match = re.search(r"^versionName\s*=\s*(\S+)\s*$", content, re.MULTILINE)
    if not code_match or not name_match:
        sys.exit("Invalid version.properties")
    version_code = int(code_match.group(1))
    version_name = name_match.group(1)
    if version_code <= 0:
        sys.exit("versionCode must be a positive integer")
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-beta)?", version_name):
        sys.exit("versionName must use X.Y.Z or X.Y.Z-beta")
    return version_code, version_name


def validate_git_state(root: Path) -> None:
    git_dir = root / ".git"
    if not git_dir.exists():
        print("Warning: .git not found; skipping release branch/push checks")
        return

    def git(*args: str) -> str:
        result = subprocess.run(
            ["git", *args],
            cwd=root,
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        return result.stdout.strip()

    branch = git("rev-parse", "--abbrev-ref", "HEAD")
    if branch != "main":
        sys.exit(f"Release packaging must run from main, current branch: {branch}")

    tracked_changes = git("status", "--porcelain", "--untracked-files=no")
    if tracked_changes:
        sys.exit("Commit tracked source/version/release-note changes before packaging a release")

    try:
        upstream = git("rev-parse", "--abbrev-ref", "@{upstream}")
        ahead = int(git("rev-list", "--count", "@{upstream}..HEAD"))
    except (subprocess.CalledProcessError, ValueError):
        print("Warning: no usable upstream branch; cannot verify whether HEAD was pushed")
        return

    if ahead > 0:
        sys.exit(f"HEAD has {ahead} unpushed commit(s) relative to {upstream}; push source before packaging")


def validate_apk(path: Path) -> None:
    if not path.is_file() or path.stat().st_size <= 0:
        sys.exit(f"Invalid APK: {path}")
    if path.stat().st_size > 200 * 1024 * 1024:
        sys.exit("APK exceeds 200 MiB")
    lowered = path.name.lower()
    if "debug" in lowered or "unsigned" in lowered:
        sys.exit("Refusing to package a debug/unsigned APK")


def read_release_notes(path: Path) -> str:
    if not path.is_file():
        sys.exit(f"Release notes not found: {path}")
    content = path.read_text(encoding="utf-8")
    if len(content) > 20_000:
        sys.exit("Release notes exceed 20,000 characters")
    return content


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


if __name__ == "__main__":
    main()

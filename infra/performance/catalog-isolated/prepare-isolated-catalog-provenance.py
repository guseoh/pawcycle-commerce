#!/usr/bin/env python3
"""Create immutable provenance for an isolated Catalog dataset from exact source."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
from pathlib import Path


EXPECTED_DATASETS = {
    "catalog-core-control-v1": 32,
    "catalog-core-10k-v1": 10000,
}
EXPECTED_SEED = 20260826
SHA40 = re.compile(r"^[0-9a-f]{40}$")


class ProvenanceError(ValueError):
    pass


def absolute_path(path: Path) -> Path:
    return Path(os.path.abspath(os.path.expanduser(str(path))))


def require_secure_parent_chain(path: Path) -> None:
    target = absolute_path(path)
    current = Path(target.anchor)
    for part in target.parts[1:-1]:
        current = current / part
        details = os.lstat(current)
        if stat.S_ISLNK(details.st_mode):
            raise ProvenanceError(f"path component must not be a symlink: {current}")
        if not stat.S_ISDIR(details.st_mode):
            raise ProvenanceError(f"path component must be a directory: {current}")
        if details.st_uid != 0:
            raise ProvenanceError(f"path component must be root-owned: {current}")
        writable = stat.S_IMODE(details.st_mode) & 0o022
        sticky_root = bool(details.st_mode & stat.S_ISVTX) and details.st_uid == 0
        if writable and not sticky_root:
            raise ProvenanceError(
                f"path component must not be writable by group/other: {current}"
            )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_regular_file(path: Path) -> Path:
    target = path.resolve(strict=True)
    details = os.lstat(path)
    if stat.S_ISLNK(details.st_mode) or not stat.S_ISREG(details.st_mode):
        raise ProvenanceError(f"expected regular non-symlink file: {path}")
    return target


def canonical_source_file(source_root: Path, path: Path) -> Path:
    root = source_root.resolve(strict=True)
    lexical = absolute_path(path)
    require_secure_parent_chain(lexical)
    target = lexical.resolve(strict=True)
    try:
        relative = target.relative_to(root)
    except ValueError as exc:
        raise ProvenanceError(
            f"source file must remain inside canonical source root: {path}"
        ) from exc
    if not relative.parts:
        raise ProvenanceError(f"source file must be below source root: {path}")
    try:
        lexical_relative = lexical.relative_to(root)
    except ValueError as exc:
        raise ProvenanceError(
            f"source path must remain below canonical source root: {path}"
        ) from exc

    current = root
    for part in lexical_relative.parts:
        current /= part
        details = os.lstat(current)
        if stat.S_ISLNK(details.st_mode):
            raise ProvenanceError(f"source path component must not be a symlink: {current}")
        if current != target and not stat.S_ISDIR(details.st_mode):
            raise ProvenanceError(f"source path component must be a directory: {current}")
    return target


def require_source_file(source_root: Path, path: Path) -> Path:
    target = canonical_source_file(source_root, path)
    details = os.lstat(target)
    if not stat.S_ISREG(details.st_mode):
        raise ProvenanceError(f"expected source regular file: {target}")
    if details.st_uid != 0:
        raise ProvenanceError(f"source file must be root-owned: {target}")
    if stat.S_IMODE(details.st_mode) != 0o444:
        raise ProvenanceError(f"source file mode must be 0444: {target}")
    return target


def approved_source_sha(source_root: Path) -> str:
    original = Path(os.path.abspath(str(source_root)))
    source_root = original.resolve(strict=True)
    if original != source_root:
        raise ProvenanceError("source root must not contain symlinked path components")
    original_details = os.lstat(original)
    if stat.S_ISLNK(original_details.st_mode):
        raise ProvenanceError("source root must not be a symlink")

    details = os.lstat(source_root)
    if not stat.S_ISDIR(details.st_mode):
        raise ProvenanceError("source root must be a regular directory")
    if details.st_uid != 0:
        raise ProvenanceError("source root must be root-owned")
    if stat.S_IMODE(details.st_mode) != 0o555:
        raise ProvenanceError("source root mode must be 0555")
    if (source_root / ".git").exists():
        raise ProvenanceError("materialized source must not contain .git")

    marker = require_source_file(source_root, source_root / ".approved-sha")
    approved_sha = marker.read_text(encoding="utf-8").strip()
    if not SHA40.fullmatch(approved_sha):
        raise ProvenanceError("source marker must contain a 40-character lowercase SHA")
    if source_root.name != approved_sha:
        raise ProvenanceError("source directory name must match the approved SHA marker")
    return approved_sha


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--dataset-dir", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    if os.geteuid() != 0:
        raise ProvenanceError("provenance creation must run as root")

    args = parse_args()
    source_root = args.source_root.resolve(strict=True)
    dataset_dir = args.dataset_dir.resolve(strict=True)
    approved_sha = approved_source_sha(args.source_root)

    dataset_id = dataset_dir.name
    target_products = EXPECTED_DATASETS.get(dataset_id)
    if target_products is None:
        raise ProvenanceError(f"unsupported performance dataset: {dataset_id}")

    manifest_path = require_regular_file(dataset_dir / "manifest.json")
    report_path = require_regular_file(dataset_dir / "report.json")
    provenance_path = dataset_dir / "provenance.json"
    if provenance_path.exists() or provenance_path.is_symlink():
        raise ProvenanceError("provenance.json already exists; do not overwrite immutable provenance")

    prepare_wrapper = require_source_file(
        source_root,
        source_root / "scripts" / "prepare-product-scale-data.py"
    )
    data_generator = require_source_file(
        source_root,
        source_root / "scripts" / "generate-product-data-v2.py"
    )
    base_manifest = require_source_file(
        source_root,
        source_root
        / "backend"
        / "src"
        / "main"
        / "resources"
        / "catalog"
        / "demo-catalog.json"
    )

    report = json.loads(report_path.read_text(encoding="utf-8"))
    if report.get("datasetId") != dataset_id:
        raise ProvenanceError("dataset report ID does not match dataset directory")
    if report.get("seed") != EXPECTED_SEED:
        raise ProvenanceError(f"dataset report seed must be {EXPECTED_SEED}")
    if report.get("baseManifestSha256") != sha256(base_manifest):
        raise ProvenanceError("dataset base manifest digest does not match approved source")

    with tempfile.TemporaryDirectory(prefix="pawcycle-dataset-provenance-") as temp:
        temp_root = Path(temp)
        expected_manifest = temp_root / "manifest.json"
        expected_report = temp_root / "report.json"
        completed = subprocess.run(
            [
                sys.executable,
                str(prepare_wrapper),
                "--base-manifest",
                str(base_manifest),
                "--target-products",
                str(target_products),
                "--seed",
                str(EXPECTED_SEED),
                "--dataset-id",
                dataset_id,
                "--output",
                str(expected_manifest),
                "--report",
                str(expected_report),
            ],
            cwd=source_root,
            text=True,
            capture_output=True,
            check=False,
        )
        if completed.returncode != 0:
            raise ProvenanceError(
                "approved source failed to reproduce the dataset contract: "
                + completed.stderr.strip()
            )
        if expected_manifest.read_bytes() != manifest_path.read_bytes():
            raise ProvenanceError("installed manifest differs from exact-source reproduction")
        if expected_report.read_bytes() != report_path.read_bytes():
            raise ProvenanceError("installed report differs from exact-source reproduction")

    provenance = {
        "schemaVersion": 1,
        "datasetId": dataset_id,
        "approvedSourceSha": approved_sha,
        "prepareWrapperSha256": sha256(prepare_wrapper),
        "dataGeneratorSha256": sha256(data_generator),
        "baseManifestSha256": sha256(base_manifest),
        "generatedManifestSha256": sha256(manifest_path),
        "reportSha256": sha256(report_path),
        "seed": EXPECTED_SEED,
        "productsTotal": target_products,
    }

    payload = (json.dumps(provenance, sort_keys=True, indent=2) + "\n").encode("utf-8")
    fd = os.open(
        provenance_path,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
        0o444,
    )
    try:
        os.write(fd, payload)
        os.fsync(fd)
    finally:
        os.close(fd)
    os.chmod(provenance_path, 0o444)

    print("catalog_dataset_provenance=PASS")
    print(
        json.dumps(
            {
                "dataset_id": dataset_id,
                "approved_source_sha": approved_sha,
                "manifest_sha256": provenance["generatedManifestSha256"],
                "report_sha256": provenance["reportSha256"],
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ProvenanceError, json.JSONDecodeError) as exc:
        print(f"catalog_dataset_provenance=FAIL reason={exc}", file=sys.stderr)
        raise SystemExit(1)

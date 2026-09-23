#!/usr/bin/env python3
"""Fail-closed preflight for the isolated Catalog performance runtime."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
from collections.abc import Iterable, Mapping
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


EXPECTED_DATASETS = {
    "catalog-core-control-v1": {
        "schema": "pawcycle_perf_core_control",
        "products_total": 32,
        "synthetic_products": 0,
    },
    "catalog-core-10k-v1": {
        "schema": "pawcycle_perf_core_10k",
        "products_total": 10000,
        "synthetic_products": 9968,
    },
}
EXPECTED_SEED = 20260826
EXPECTED_USERNAME = "pawcycle_perf_catalog"
REQUIRED_CONFIG_KEYS = {
    "PAWCYCLE_PERF_DATASET_ID",
    "PAWCYCLE_PERF_SCHEMA",
    "PAWCYCLE_PERF_BACKEND_IMAGE",
    "PAWCYCLE_PERF_DB_URL",
    "PAWCYCLE_PERF_DB_USERNAME",
    "PAWCYCLE_PERF_HOST_PORT",
}
IMAGE_DIGEST = re.compile(r"^[^\s]+@sha256:[0-9a-f]{64}$")
KEY_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]*$")
SHA40 = re.compile(r"^[0-9a-f]{40}$")


class ContractError(ValueError):
    pass


def absolute_path(path: Path) -> Path:
    return Path(os.path.abspath(os.path.expanduser(str(path))))


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_secure_parent_chain(path: Path) -> None:
    target = absolute_path(path)
    current = Path(target.anchor)
    for part in target.parts[1:-1]:
        current = current / part
        details = os.lstat(current)
        if stat.S_ISLNK(details.st_mode):
            raise ContractError(f"path component must not be a symlink: {current}")
        if not stat.S_ISDIR(details.st_mode):
            raise ContractError(f"path component must be a directory: {current}")
        if details.st_uid != 0:
            raise ContractError(f"path component must be root-owned: {current}")
        writable = stat.S_IMODE(details.st_mode) & 0o022
        sticky_root = bool(details.st_mode & stat.S_ISVTX) and details.st_uid == 0
        if writable and not sticky_root:
            raise ContractError(f"path component must not be writable by group/other: {current}")


def require_directory(path: Path, expected_mode: int) -> Path:
    target = absolute_path(path)
    require_secure_parent_chain(target / "__child__")
    details = os.lstat(target)
    if stat.S_ISLNK(details.st_mode) or not stat.S_ISDIR(details.st_mode):
        raise ContractError(f"expected regular directory: {target}")
    if details.st_uid != 0:
        raise ContractError(f"directory must be root-owned: {target}")
    if stat.S_IMODE(details.st_mode) != expected_mode:
        raise ContractError(
            f"directory mode must be {expected_mode:04o}: {target} "
            f"(actual={stat.S_IMODE(details.st_mode):04o})"
        )
    return target


def require_file(path: Path, allowed_modes: Iterable[int]) -> Path:
    target = absolute_path(path)
    require_secure_parent_chain(target)
    details = os.lstat(target)
    if stat.S_ISLNK(details.st_mode) or not stat.S_ISREG(details.st_mode):
        raise ContractError(f"expected regular non-symlink file: {target}")
    if details.st_uid != 0:
        raise ContractError(f"file must be root-owned: {target}")
    actual_mode = stat.S_IMODE(details.st_mode)
    allowed = set(allowed_modes)
    if actual_mode not in allowed:
        rendered = ",".join(f"{value:04o}" for value in sorted(allowed))
        raise ContractError(
            f"file mode must be one of [{rendered}]: {target} (actual={actual_mode:04o})"
        )
    return target


def canonical_source_file(source_root: Path, path: Path) -> Path:
    root = source_root.resolve(strict=True)
    lexical = absolute_path(path)
    require_secure_parent_chain(lexical)
    target = lexical.resolve(strict=True)
    try:
        relative = target.relative_to(root)
    except ValueError as exc:
        raise ContractError(
            f"source file must remain inside canonical source root: {path}"
        ) from exc
    if not relative.parts:
        raise ContractError(f"source file must be below source root: {path}")
    try:
        lexical_relative = lexical.relative_to(root)
    except ValueError as exc:
        raise ContractError(
            f"source path must remain below canonical source root: {path}"
        ) from exc

    current = root
    for part in lexical_relative.parts:
        current /= part
        details = os.lstat(current)
        if stat.S_ISLNK(details.st_mode):
            raise ContractError(f"source path component must not be a symlink: {current}")
        if current != target and not stat.S_ISDIR(details.st_mode):
            raise ContractError(f"source path component must be a directory: {current}")
    return target


def require_source_file(source_root: Path, path: Path) -> Path:
    target = canonical_source_file(source_root, path)
    details = os.lstat(target)
    if not stat.S_ISREG(details.st_mode):
        raise ContractError(f"expected source regular file: {target}")
    if details.st_uid != 0:
        raise ContractError(f"source file must be root-owned: {target}")
    if stat.S_IMODE(details.st_mode) != 0o444:
        raise ContractError(f"source file mode must be 0444: {target}")
    return target


def validate_source_root(source_root: Path) -> Mapping[str, object]:
    original = absolute_path(source_root)
    target = original.resolve(strict=True)
    if original != target:
        raise ContractError("source root must not contain symlinked path components")
    details = os.lstat(target)
    if stat.S_ISLNK(details.st_mode) or not stat.S_ISDIR(details.st_mode):
        raise ContractError("source root must be a regular directory")
    if details.st_uid != 0:
        raise ContractError("source root must be root-owned")
    if stat.S_IMODE(details.st_mode) != 0o555:
        raise ContractError("source root mode must be 0555")
    if (target / ".git").exists():
        raise ContractError("materialized source must not contain .git")

    marker = require_source_file(target, target / ".approved-sha")
    approved_sha = marker.read_text(encoding="utf-8").strip()
    if not SHA40.fullmatch(approved_sha):
        raise ContractError("source marker must contain a 40-character lowercase SHA")
    if target.name != approved_sha:
        raise ContractError("source directory name must match the approved SHA marker")

    prepare_wrapper = require_source_file(
        target,
        target / "scripts" / "prepare-product-scale-data.py"
    )
    data_generator = require_source_file(
        target,
        target / "scripts" / "generate-product-data-v2.py"
    )
    base_manifest = require_source_file(
        target,
        target
        / "backend"
        / "src"
        / "main"
        / "resources"
        / "catalog"
        / "demo-catalog.json"
    )
    return {
        "source_root": target,
        "approved_sha": approved_sha,
        "prepare_wrapper_sha256": file_sha256(prepare_wrapper),
        "data_generator_sha256": file_sha256(data_generator),
        "base_manifest_sha256": file_sha256(base_manifest),
    }


def parse_config(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            raise ContractError(f"config must use KEY=VALUE without export (line {line_number})")
        if "=" not in raw:
            raise ContractError(f"config line must use KEY=VALUE (line {line_number})")
        key, value = raw.split("=", 1)
        if key != key.strip() or not KEY_PATTERN.fullmatch(key):
            raise ContractError(f"invalid config key at line {line_number}")
        if key in values:
            raise ContractError(f"duplicate config key: {key}")
        if value != value.strip() or not value:
            raise ContractError(f"config value must be non-empty without edge whitespace: {key}")
        values[key] = value

    missing = REQUIRED_CONFIG_KEYS - values.keys()
    unknown = values.keys() - REQUIRED_CONFIG_KEYS
    if missing:
        raise ContractError(f"missing config keys: {','.join(sorted(missing))}")
    if unknown:
        raise ContractError(f"unknown config keys: {','.join(sorted(unknown))}")
    return values


def validate_db_url(raw: str, expected_schema: str) -> None:
    if not raw.startswith("jdbc:mysql://"):
        raise ContractError("PAWCYCLE_PERF_DB_URL must start with jdbc:mysql://")
    parsed = urlsplit("mysql://" + raw[len("jdbc:mysql://"):])
    if not parsed.hostname:
        raise ContractError("PAWCYCLE_PERF_DB_URL must include a host")
    if parsed.username is not None or parsed.password is not None:
        raise ContractError("PAWCYCLE_PERF_DB_URL must not contain credentials")
    if parsed.path != f"/{expected_schema}":
        raise ContractError(
            f"PAWCYCLE_PERF_DB_URL must target only schema {expected_schema}"
        )
    try:
        port = parsed.port
    except ValueError as exc:
        raise ContractError("PAWCYCLE_PERF_DB_URL contains an invalid port") from exc
    if port is not None and not 1 <= port <= 65535:
        raise ContractError("PAWCYCLE_PERF_DB_URL port must be between 1 and 65535")

    query = parse_qs(parsed.query, keep_blank_values=True)
    ssl_modes = query.get("sslMode") or query.get("sslmode") or []
    if not ssl_modes:
        raise ContractError("PAWCYCLE_PERF_DB_URL must explicitly configure sslMode")
    if ssl_modes[-1].upper() not in {"REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY"}:
        raise ContractError("PAWCYCLE_PERF_DB_URL sslMode must require TLS")


def validate_config(values: Mapping[str, str]) -> Mapping[str, int]:
    dataset_id = values["PAWCYCLE_PERF_DATASET_ID"]
    expected = EXPECTED_DATASETS.get(dataset_id)
    if expected is None:
        raise ContractError(f"unsupported performance dataset: {dataset_id}")

    if values["PAWCYCLE_PERF_SCHEMA"] != expected["schema"]:
        raise ContractError(
            f"dataset {dataset_id} must use schema {expected['schema']}"
        )
    if values["PAWCYCLE_PERF_DB_USERNAME"] != EXPECTED_USERNAME:
        raise ContractError(
            f"performance DB username must be {EXPECTED_USERNAME}"
        )
    if not IMAGE_DIGEST.fullmatch(values["PAWCYCLE_PERF_BACKEND_IMAGE"]):
        raise ContractError("PAWCYCLE_PERF_BACKEND_IMAGE must be pinned by sha256 digest")

    validate_db_url(values["PAWCYCLE_PERF_DB_URL"], str(expected["schema"]))

    try:
        host_port = int(values["PAWCYCLE_PERF_HOST_PORT"])
    except ValueError as exc:
        raise ContractError("PAWCYCLE_PERF_HOST_PORT must be an integer") from exc
    if not 1024 <= host_port <= 65535:
        raise ContractError("PAWCYCLE_PERF_HOST_PORT must be between 1024 and 65535")
    return {"host_port": host_port}


def validate_password_file(path: Path) -> None:
    payload = path.read_bytes()
    if not payload or len(payload) > 4096:
        raise ContractError("DB password file must contain 1..4096 bytes")
    if b"\x00" in payload:
        raise ContractError("DB password file must not contain NUL bytes")


def validate_dataset(
    dataset_dir: Path,
    values: Mapping[str, str],
    source: Mapping[str, object],
) -> Mapping[str, object]:
    dataset_id = values["PAWCYCLE_PERF_DATASET_ID"]
    expected = EXPECTED_DATASETS[dataset_id]
    if dataset_dir.name != dataset_id:
        raise ContractError(
            f"dataset directory name must exactly match dataset ID {dataset_id}"
        )

    manifest_path = require_file(dataset_dir / "manifest.json", {0o444})
    report_path = require_file(dataset_dir / "report.json", {0o444})
    provenance_path = require_file(dataset_dir / "provenance.json", {0o444})

    report = json.loads(report_path.read_text(encoding="utf-8"))
    provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    if report.get("schemaVersion") != 1:
        raise ContractError("unsupported dataset report schemaVersion")
    if report.get("datasetId") != dataset_id:
        raise ContractError("dataset report ID does not match runtime config")
    if report.get("seed") != EXPECTED_SEED:
        raise ContractError(f"dataset report seed must be {EXPECTED_SEED}")

    generated_sha = report.get("generatedManifestSha256")
    if not isinstance(generated_sha, str) or generated_sha != file_sha256(manifest_path):
        raise ContractError("manifest checksum does not match dataset report")
    if report.get("baseManifestSha256") != source["base_manifest_sha256"]:
        raise ContractError("dataset base manifest digest does not match approved source")

    expected_provenance = {
        "schemaVersion": 1,
        "datasetId": dataset_id,
        "approvedSourceSha": source["approved_sha"],
        "prepareWrapperSha256": source["prepare_wrapper_sha256"],
        "dataGeneratorSha256": source["data_generator_sha256"],
        "baseManifestSha256": source["base_manifest_sha256"],
        "generatedManifestSha256": generated_sha,
        "reportSha256": file_sha256(report_path),
        "seed": EXPECTED_SEED,
        "productsTotal": expected["products_total"],
    }
    for key, expected_value in expected_provenance.items():
        if provenance.get(key) != expected_value:
            raise ContractError(f"dataset provenance mismatch: {key}")
    if set(provenance) != set(expected_provenance):
        raise ContractError("dataset provenance contains unexpected or missing fields")

    products = manifest.get("products")
    if not isinstance(products, list):
        raise ContractError("manifest products must be a list")
    actual_products = len(products)
    if actual_products != expected["products_total"]:
        raise ContractError(
            f"manifest Product count mismatch: expected={expected['products_total']} "
            f"actual={actual_products}"
        )

    report_products = report.get("products")
    report_skus = report.get("skus")
    report_inventory = report.get("inventory")
    if not isinstance(report_products, dict) or not isinstance(report_skus, dict):
        raise ContractError("dataset report Product/SKU sections are required")
    if not isinstance(report_inventory, dict):
        raise ContractError("dataset report inventory section is required")

    if report_products.get("base") != 32:
        raise ContractError("dataset report base Product count must be 32")
    if report_products.get("synthetic") != expected["synthetic_products"]:
        raise ContractError("dataset report synthetic Product count mismatch")
    if report_products.get("total") != expected["products_total"]:
        raise ContractError("dataset report total Product count mismatch")

    sku_total = 0
    for product in products:
        if not isinstance(product, dict):
            raise ContractError("manifest Product entries must be objects")
        skus = product.get("skus")
        if not isinstance(skus, list) or not skus:
            raise ContractError("every manifest Product must have at least one SKU")
        sku_total += len(skus)

    if report_skus.get("total") != sku_total:
        raise ContractError("dataset report SKU count does not match manifest")
    if report_inventory.get("total") != sku_total:
        raise ContractError("dataset report Inventory count does not match manifest SKU count")

    for field in ("withoutSku", "duplicateCatalogKeys", "unknownCategoryReferences"):
        if report_products.get(field) != 0:
            raise ContractError(f"dataset report requires products.{field}=0")
    if report_skus.get("duplicateSkuCodes") != 0:
        raise ContractError("dataset report requires skus.duplicateSkuCodes=0")

    return {
        "dataset_id": dataset_id,
        "schema": expected["schema"],
        "products_total": actual_products,
        "skus_total": sku_total,
        "manifest_sha256": generated_sha,
        "approved_source_sha": source["approved_sha"],
        "provenance_sha256": file_sha256(provenance_path),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--config-file", required=True, type=Path)
    parser.add_argument("--password-file", required=True, type=Path)
    parser.add_argument("--dataset-dir", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source = validate_source_root(args.source_root)
    config_file = require_file(args.config_file, {0o600})
    password_file = require_file(args.password_file, {0o400, 0o600})
    validate_password_file(password_file)
    dataset_dir = require_directory(args.dataset_dir, 0o700)

    values = parse_config(config_file)
    config_summary = validate_config(values)
    dataset_summary = validate_dataset(dataset_dir, values, source)

    summary = {
        **dataset_summary,
        **config_summary,
        "backend_image": values["PAWCYCLE_PERF_BACKEND_IMAGE"],
    }
    print("catalog_isolation_preflight=PASS")
    print(json.dumps(summary, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ContractError, OSError, json.JSONDecodeError) as exc:
        print(f"catalog_isolation_preflight=FAIL reason={exc}", file=os.sys.stderr)
        raise SystemExit(1)

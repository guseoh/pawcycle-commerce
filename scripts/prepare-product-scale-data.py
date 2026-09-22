#!/usr/bin/env python3
"""Prepare and verify deterministic Product Data V2 scale manifests."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import math
from collections import Counter
from pathlib import Path
from types import ModuleType
from typing import Any


ROOT = Path(__file__).resolve().parent.parent
GENERATOR = ROOT / "scripts" / "generate-product-data-v2.py"
DEFAULT_BASE_MANIFEST = ROOT / "backend" / "src" / "main" / "resources" / "catalog" / "demo-catalog.json"


def load_generator() -> ModuleType:
    spec = importlib.util.spec_from_file_location("pawcycle_product_data_v2_generator", GENERATOR)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"unable to load generator: {GENERATOR}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def nearest_rank(values: list[int], percentile: float) -> int:
    if not values:
        raise ValueError("price distribution is empty")
    ordered = sorted(values)
    rank = max(1, math.ceil(percentile * len(ordered)))
    return ordered[rank - 1]


def disposable_path(path: Path, label: str) -> Path:
    resolved = path.expanduser().resolve()
    try:
        relative = resolved.relative_to(ROOT)
    except ValueError:
        return resolved
    if not relative.parts or relative.parts[0] != "tmp":
        raise ValueError(f"{label} must be outside the repository or under ignored tmp/: {path}")
    return resolved


def duplicate_count(values: list[str]) -> int:
    return len(values) - len(set(values))


def build_report(
    *,
    dataset_id: str,
    seed: int,
    base_manifest_bytes: bytes,
    base_manifest: dict[str, Any],
    generated_bytes: bytes,
    generated_manifest: dict[str, Any],
) -> dict[str, Any]:
    categories = {
        category["slug"]
        for category in generated_manifest.get("categories", [])
        if isinstance(category, dict) and isinstance(category.get("slug"), str)
    }
    products = generated_manifest.get("products", [])
    if not isinstance(products, list):
        raise ValueError("generated manifest products must be a list")

    catalog_keys: list[str] = []
    sku_codes: list[str] = []
    pet_types: Counter[str] = Counter()
    category_counts: Counter[str] = Counter()
    sku_fanout: Counter[int] = Counter()
    sku_status: Counter[str] = Counter()
    subscribable: Counter[str] = Counter()
    inventory: Counter[str] = Counter()
    prices: list[int] = []
    unknown_categories = 0
    products_without_sku = 0
    sku_total = 0

    for product in products:
        if not isinstance(product, dict):
            raise ValueError("generated manifest product entries must be objects")
        catalog_key = product.get("catalogKey")
        if isinstance(catalog_key, str):
            catalog_keys.append(catalog_key)
        pet_types[str(product.get("petType"))] += 1
        category_slug = str(product.get("categorySlug"))
        category_counts[category_slug] += 1
        if category_slug not in categories:
            unknown_categories += 1

        skus = product.get("skus")
        if not isinstance(skus, list) or not skus:
            products_without_sku += 1
            continue
        sku_fanout[len(skus)] += 1
        sku_total += len(skus)

        for sku in skus:
            if not isinstance(sku, dict):
                raise ValueError("generated manifest SKU entries must be objects")
            sku_code = sku.get("skuCode")
            if isinstance(sku_code, str):
                sku_codes.append(sku_code)
            sku_status[str(sku.get("status"))] += 1

            subscribable_value = sku.get("subscribable")
            if type(subscribable_value) is not bool:
                raise ValueError(f"SKU subscribable must be a boolean: {sku_code}")
            subscribable["true" if subscribable_value else "false"] += 1

            quantity = sku.get("initialInventory")
            if not isinstance(quantity, int) or isinstance(quantity, bool) or quantity < 0:
                raise ValueError(f"SKU initialInventory must be a non-negative integer: {sku_code}")
            if quantity == 0:
                inventory["stockout"] += 1
            elif quantity <= 5:
                inventory["low_1_5"] += 1
            else:
                inventory["normal_gt_5"] += 1

            price = sku.get("price")
            if not isinstance(price, int) or isinstance(price, bool) or price < 0:
                raise ValueError(f"SKU price must be a non-negative integer: {sku_code}")
            prices.append(price)

    base_products = base_manifest.get("products", [])
    if not isinstance(base_products, list):
        raise ValueError("base manifest products must be a list")
    base_count = len(base_products)
    synthetic_count = len(products) - base_count

    report = {
        "schemaVersion": 1,
        "datasetId": dataset_id,
        "seed": seed,
        "baseManifestSha256": sha256_bytes(base_manifest_bytes),
        "generatedManifestSha256": sha256_bytes(generated_bytes),
        "products": {
            "base": base_count,
            "synthetic": synthetic_count,
            "total": len(products),
            "petType": dict(sorted(pet_types.items())),
            "category": dict(sorted(category_counts.items())),
            "withoutSku": products_without_sku,
            "duplicateCatalogKeys": duplicate_count(catalog_keys),
            "unknownCategoryReferences": unknown_categories,
        },
        "skus": {
            "total": sku_total,
            "fanoutByProduct": {str(key): sku_fanout[key] for key in sorted(sku_fanout)},
            "status": dict(sorted(sku_status.items())),
            "subscribable": dict(sorted(subscribable.items())),
            "duplicateSkuCodes": duplicate_count(sku_codes),
        },
        "inventory": {
            "total": sum(inventory.values()),
            "stockout": inventory["stockout"],
            "low_1_5": inventory["low_1_5"],
            "normal_gt_5": inventory["normal_gt_5"],
        },
        "price": {
            "min": min(prices),
            "p50NearestRank": nearest_rank(prices, 0.50),
            "p95NearestRank": nearest_rank(prices, 0.95),
            "max": max(prices),
        },
    }

    violations = [
        report["products"]["withoutSku"],
        report["products"]["duplicateCatalogKeys"],
        report["products"]["unknownCategoryReferences"],
        report["skus"]["duplicateSkuCodes"],
    ]
    if any(violations):
        raise ValueError(f"generated manifest relationship validation failed: {violations}")
    if report["inventory"]["total"] != report["skus"]["total"]:
        raise ValueError("every generated SKU must have exactly one inventory value")
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-manifest", type=Path, default=DEFAULT_BASE_MANIFEST)
    parser.add_argument("--target-products", type=int, required=True)
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--dataset-id")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    if args.target_products < 1:
        parser.error("--target-products must be greater than zero")
    return args


def main() -> int:
    args = parse_args()
    output = disposable_path(args.output, "output")
    report_path = disposable_path(args.report, "report")
    base_path = args.base_manifest.expanduser().resolve()
    if output == report_path:
        raise ValueError("output and report paths must be different")
    if base_path in {output, report_path}:
        raise ValueError("output and report paths must be different from the base manifest")

    base_bytes = base_path.read_bytes()
    base_manifest = json.loads(base_bytes.decode("utf-8"))
    generator = load_generator()
    base_products = base_manifest.get("products")
    if not isinstance(base_products, list):
        raise ValueError("base manifest products must be a list")
    additional_products = args.target_products - len(base_products)
    if additional_products < 0:
        raise ValueError(
            f"target Product count {args.target_products} is smaller than base count {len(base_products)}"
        )

    generated_manifest = generator.generate(base_manifest, additional_products, args.seed)
    generated_bytes = (
        json.dumps(generated_manifest, ensure_ascii=False, indent=2) + "\n"
    ).encode("utf-8")

    if len(generated_manifest["products"]) != args.target_products:
        raise ValueError(
            f"generated Product count mismatch: expected={args.target_products} "
            f"actual={len(generated_manifest['products'])}"
        )

    dataset_id = args.dataset_id or f"catalog-core-{args.target_products}-v1"
    report = build_report(
        dataset_id=dataset_id,
        seed=args.seed,
        base_manifest_bytes=base_bytes,
        base_manifest=base_manifest,
        generated_bytes=generated_bytes,
        generated_manifest=generated_manifest,
    )
    if report["products"]["synthetic"] != additional_products:
        raise ValueError("synthetic Product count does not match the derived additional count")

    output.parent.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(generated_bytes)
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    print(
        "prepared Product scale dataset: "
        f"dataset={dataset_id} target={args.target_products} "
        f"base={len(base_products)} synthetic={additional_products} "
        f"skus={report['skus']['total']} "
        f"manifest_sha256={report['generatedManifestSha256']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

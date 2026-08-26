#!/usr/bin/env python3
"""Generate deterministic synthetic Product Data V2 from the V1 manifest."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path
from typing import Any


DEFAULT_BASE_MANIFEST = Path("backend/src/main/resources/catalog/demo-catalog.json")


def stable_digest(seed: int, product_index: int, sku_index: int = -1) -> bytes:
    value = f"{seed}:{product_index}:{sku_index}".encode("utf-8")
    return hashlib.sha256(value).digest()


def validate_base_manifest(manifest: dict[str, Any]) -> tuple[list[dict[str, Any]], list[str], set[str], set[str]]:
    if manifest.get("version") != 1:
        raise ValueError("base manifest version must be 1")
    categories = manifest.get("categories")
    products = manifest.get("products")
    plans = manifest.get("plans")
    if not isinstance(categories, list) or not isinstance(products, list) or not isinstance(plans, list):
        raise ValueError("base manifest must contain categories, products, and plans lists")

    category_slugs = [category.get("slug") for category in categories]
    if not category_slugs or any(not isinstance(slug, str) or not slug for slug in category_slugs):
        raise ValueError("base manifest categories must have non-empty slugs")
    if len(category_slugs) != len(set(category_slugs)):
        raise ValueError("base manifest category slugs must be unique")

    catalog_keys: set[str] = set()
    sku_codes: set[str] = set()
    for product in products:
        catalog_key = product.get("catalogKey")
        if not isinstance(catalog_key, str) or not catalog_key or catalog_key in catalog_keys:
            raise ValueError("base manifest catalog keys must be unique and non-empty")
        if product.get("categorySlug") not in category_slugs:
            raise ValueError(f"base manifest product category is unknown: {product.get('categorySlug')}")
        catalog_keys.add(catalog_key)
        skus = product.get("skus")
        if not isinstance(skus, list) or not skus:
            raise ValueError(f"base manifest product must have at least one SKU: {catalog_key}")
        for sku in skus:
            sku_code = sku.get("skuCode")
            if not isinstance(sku_code, str) or not sku_code or sku_code in sku_codes:
                raise ValueError("base manifest SKU codes must be unique and non-empty")
            sku_codes.add(sku_code)

    return categories, category_slugs, catalog_keys, sku_codes


def synthetic_product(
    seed: int,
    index: int,
    categories: list[dict[str, Any]],
    thumbnail_pool: list[str],
    seed_token: str,
) -> dict[str, Any]:
    product_digest = stable_digest(seed, index)
    pet_type = "DOG" if index % 2 == 0 else "CAT"
    category = categories[(product_digest[0] + index) % len(categories)]
    category_slug = category["slug"]
    catalog_key = f"synthetic-v2-{seed_token}-{index:05d}"
    product_number = index + 1
    thumbnail_url = thumbnail_pool[index % len(thumbnail_pool)] if thumbnail_pool else None

    skus: list[dict[str, Any]] = []
    for sku_index in range(1 + index % 3):
        sku_digest = stable_digest(seed, index, sku_index)
        inventory_mode = (index + sku_index) % 3
        if inventory_mode == 0:
            initial_inventory = 0
        elif inventory_mode == 1:
            initial_inventory = 1 + (sku_digest[0] % 5)
        else:
            initial_inventory = 20 + (sku_digest[0] % 81)
        sku_code = f"SYNTH-V2-{seed_token}-{index:05d}-{sku_index + 1:02d}"
        skus.append(
            {
                "skuCode": sku_code,
                "name": f"QA pack {sku_index + 1}",
                "price": 5900 + ((seed + index * 37 + sku_index * 13) % 120) * 1000,
                "subscribable": (index + sku_index) % 2 == 0,
                "displayOrder": sku_index + 1,
                "status": "INACTIVE" if (index + sku_index) % 11 == 0 else "ACTIVE",
                "initialInventory": initial_inventory,
            }
        )

    return {
        "catalogKey": catalog_key,
        "name": f"Synthetic QA {pet_type} {category_slug} Product {product_number:05d}",
        "categorySlug": category_slug,
        "shortDescription": f"Synthetic QA data for {pet_type} {category_slug}; not a real product.",
        "description": (
            "Integration and performance fixture only. This synthetic catalog entry does not represent "
            "a real supplier, product, price, or inventory position."
        ),
        "petType": pet_type,
        "thumbnailUrl": thumbnail_url,
        "skus": skus,
    }


def generate(base_manifest: dict[str, Any], additional_products: int, seed: int) -> dict[str, Any]:
    categories, _, base_catalog_keys, base_sku_codes = validate_base_manifest(base_manifest)
    manifest = copy.deepcopy(base_manifest)
    base_products = manifest["products"]
    thumbnail_pool = [product["thumbnailUrl"] for product in base_products if product.get("thumbnailUrl")]
    seed_token = hashlib.sha256(str(seed).encode("utf-8")).hexdigest()[:12]

    generated_catalog_keys: set[str] = set()
    generated_sku_codes: set[str] = set()
    for index in range(additional_products):
        product = synthetic_product(seed, index, categories, thumbnail_pool, seed_token)
        if product["catalogKey"] in base_catalog_keys or product["catalogKey"] in generated_catalog_keys:
            raise ValueError(f"generated catalog key is duplicated: {product['catalogKey']}")
        generated_catalog_keys.add(product["catalogKey"])
        for sku in product["skus"]:
            if sku["skuCode"] in base_sku_codes or sku["skuCode"] in generated_sku_codes:
                raise ValueError(f"generated SKU code is duplicated: {sku['skuCode']}")
            generated_sku_codes.add(sku["skuCode"])
        base_products.append(product)
    return manifest


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-manifest", type=Path, default=DEFAULT_BASE_MANIFEST)
    parser.add_argument("--additional-products", type=int, required=True)
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.additional_products < 0:
        parser.error("--additional-products must be zero or greater")
    return args


def main() -> int:
    args = parse_args()
    base_manifest = json.loads(args.base_manifest.read_text(encoding="utf-8"))
    output_manifest = generate(base_manifest, args.additional_products, args.seed)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(output_manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"generated Product Data V2: base={len(base_manifest['products'])} "
        f"additional={args.additional_products} total={len(output_manifest['products'])} output={args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

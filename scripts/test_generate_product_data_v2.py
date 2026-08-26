from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
GENERATOR = ROOT / "scripts" / "generate-product-data-v2.py"
BASE_MANIFEST = ROOT / "backend" / "src" / "main" / "resources" / "catalog" / "demo-catalog.json"


class GenerateProductDataV2Test(unittest.TestCase):
    def generate(self, output: Path, additional_products: int = 12, seed: int = 20260826) -> None:
        result = subprocess.run(
            [
                sys.executable,
                str(GENERATOR),
                "--base-manifest",
                str(BASE_MANIFEST),
                "--additional-products",
                str(additional_products),
                "--seed",
                str(seed),
                "--output",
                str(output),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_same_seed_and_count_produce_identical_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            first = Path(temp) / "first.json"
            second = Path(temp) / "second.json"
            self.generate(first)
            self.generate(second)
            self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_generated_manifest_preserves_v1_and_covers_data_v2_states(self) -> None:
        base = json.loads(BASE_MANIFEST.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "generated.json"
            self.generate(output)
            manifest = json.loads(output.read_text(encoding="utf-8"))

        products = manifest["products"]
        synthetic_products = products[len(base["products"]):]
        skus = [sku for product in products for sku in product["skus"]]
        synthetic_skus = [sku for product in synthetic_products for sku in product["skus"]]

        self.assertEqual(44, len(products))
        self.assertEqual(base["products"], products[: len(base["products"])])
        self.assertEqual(12, len(synthetic_products))
        self.assertEqual(32, len(set(product["catalogKey"] for product in base["products"])))
        self.assertEqual(len(products), len(set(product["catalogKey"] for product in products)))
        self.assertEqual(len(skus), len(set(sku["skuCode"] for sku in skus)))
        self.assertEqual({"DOG", "CAT"}, {product["petType"] for product in synthetic_products})
        self.assertTrue({1, 2, 3}.issubset({len(product["skus"]) for product in synthetic_products}))
        self.assertTrue(any(sku["initialInventory"] == 0 for sku in synthetic_skus))
        self.assertTrue(any(1 <= sku["initialInventory"] <= 5 for sku in synthetic_skus))
        self.assertTrue(any(sku["initialInventory"] > 5 for sku in synthetic_skus))
        self.assertTrue(any(sku["subscribable"] for sku in synthetic_skus))
        self.assertTrue(any(not sku["subscribable"] for sku in synthetic_skus))
        self.assertTrue(all(1 <= len(product["skus"]) <= 3 for product in synthetic_products))

    def test_count_changes_only_synthetic_product_count(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "generated.json"
            self.generate(output, additional_products=3, seed=7)
            manifest = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual(35, len(manifest["products"]))
        self.assertEqual(32, len(json.loads(BASE_MANIFEST.read_text(encoding="utf-8"))["products"]))
        self.assertEqual(3, len({product["catalogKey"] for product in manifest["products"] if product["catalogKey"].startswith("synthetic-v2-")}))

    def test_negative_count_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            result = subprocess.run(
                [
                    sys.executable,
                    str(GENERATOR),
                    "--additional-products",
                    "-1",
                    "--seed",
                    "1",
                    "--output",
                    str(Path(temp) / "generated.json"),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("additional-products", result.stderr)


if __name__ == "__main__":
    unittest.main()

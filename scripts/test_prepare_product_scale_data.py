from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
PREPARE = ROOT / "scripts" / "prepare-product-scale-data.py"
GENERATOR = ROOT / "scripts" / "generate-product-data-v2.py"
BASE_MANIFEST = ROOT / "backend" / "src" / "main" / "resources" / "catalog" / "demo-catalog.json"


class PrepareProductScaleDataTest(unittest.TestCase):
    def run_prepare(
        self,
        output: Path,
        report: Path,
        *,
        base_manifest: Path = BASE_MANIFEST,
        target_products: int = 35,
        seed: int = 20260826,
        dataset_id: str = "catalog-core-test-v1",
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(PREPARE),
                "--base-manifest",
                str(base_manifest),
                "--target-products",
                str(target_products),
                "--seed",
                str(seed),
                "--dataset-id",
                dataset_id,
                "--output",
                str(output),
                "--report",
                str(report),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_target_total_and_report_are_deterministic(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            first_manifest = root / "first.json"
            first_report = root / "first-report.json"
            second_manifest = root / "second.json"
            second_report = root / "second-report.json"

            first = self.run_prepare(first_manifest, first_report)
            second = self.run_prepare(second_manifest, second_report)

            self.assertEqual(0, first.returncode, first.stderr)
            self.assertEqual(0, second.returncode, second.stderr)
            self.assertEqual(first_manifest.read_bytes(), second_manifest.read_bytes())
            self.assertEqual(first_report.read_bytes(), second_report.read_bytes())

            report = json.loads(first_report.read_text(encoding="utf-8"))
            self.assertEqual("catalog-core-test-v1", report["datasetId"])
            self.assertEqual(20260826, report["seed"])
            self.assertEqual(32, report["products"]["base"])
            self.assertEqual(3, report["products"]["synthetic"])
            self.assertEqual(35, report["products"]["total"])
            self.assertEqual(48, report["skus"]["total"])
            self.assertEqual(48, report["inventory"]["total"])
            self.assertEqual({"1": 23, "2": 11, "3": 1}, report["skus"]["fanoutByProduct"])
            self.assertEqual(0, report["products"]["withoutSku"])
            self.assertEqual(0, report["products"]["duplicateCatalogKeys"])
            self.assertEqual(0, report["products"]["unknownCategoryReferences"])
            self.assertEqual(0, report["skus"]["duplicateSkuCodes"])

    def test_catalog_core_10k_profile_matches_documented_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = root / "catalog-core-10k.json"
            report_path = root / "catalog-core-10k-report.json"

            result = self.run_prepare(
                manifest,
                report_path,
                target_products=10000,
                seed=20260826,
                dataset_id="catalog-core-10k-v1",
            )
            self.assertEqual(0, result.returncode, result.stderr)

            report = json.loads(report_path.read_text(encoding="utf-8"))
            self.assertEqual("catalog-core-10k-v1", report["datasetId"])
            self.assertEqual(20260826, report["seed"])
            self.assertEqual(
                {"base": 32, "synthetic": 9968, "total": 10000},
                {
                    "base": report["products"]["base"],
                    "synthetic": report["products"]["synthetic"],
                    "total": report["products"]["total"],
                },
            )
            self.assertEqual({"CAT": 5000, "DOG": 5000}, report["products"]["petType"])
            self.assertEqual(
                {
                    "food": 2578,
                    "hygiene": 2495,
                    "toilet": 2427,
                    "treats": 2500,
                },
                report["products"]["category"],
            )
            self.assertEqual(
                {"1": 3345, "2": 3333, "3": 3322},
                report["skus"]["fanoutByProduct"],
            )
            self.assertEqual(19977, report["skus"]["total"])
            self.assertEqual(
                {"ACTIVE": 18164, "INACTIVE": 1813},
                report["skus"]["status"],
            )
            self.assertEqual(
                {"false": 9986, "true": 9991},
                report["skus"]["subscribable"],
            )
            self.assertEqual(
                {
                    "total": 19977,
                    "stockout": 6651,
                    "low_1_5": 6650,
                    "normal_gt_5": 6676,
                },
                report["inventory"],
            )
            self.assertEqual(0, report["products"]["withoutSku"])
            self.assertEqual(0, report["products"]["duplicateCatalogKeys"])
            self.assertEqual(0, report["products"]["unknownCategoryReferences"])
            self.assertEqual(0, report["skus"]["duplicateSkuCodes"])

    def test_wrapper_preserves_existing_generator_manifest_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            prepared = root / "prepared.json"
            report = root / "report.json"
            legacy = root / "legacy.json"

            result = self.run_prepare(prepared, report, target_products=35, seed=7)
            self.assertEqual(0, result.returncode, result.stderr)

            legacy_result = subprocess.run(
                [
                    sys.executable,
                    str(GENERATOR),
                    "--base-manifest",
                    str(BASE_MANIFEST),
                    "--additional-products",
                    "3",
                    "--seed",
                    "7",
                    "--output",
                    str(legacy),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(0, legacy_result.returncode, legacy_result.stderr)
            self.assertEqual(legacy.read_bytes(), prepared.read_bytes())

    def test_invalid_subscribable_type_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            malformed = root / "base.json"
            output = root / "generated.json"
            report = root / "report.json"

            base = json.loads(BASE_MANIFEST.read_text(encoding="utf-8"))
            base["products"][0]["skus"][0]["subscribable"] = "true"
            malformed.write_text(
                json.dumps(base, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )

            result = self.run_prepare(
                output,
                report,
                base_manifest=malformed,
                target_products=32,
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("SKU subscribable must be a boolean", result.stderr)
            self.assertFalse(output.exists())
            self.assertFalse(report.exists())

    def test_base_manifest_cannot_be_output_or_report(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            base = root / "base.json"
            original = BASE_MANIFEST.read_bytes()

            for collision in ("output", "report"):
                with self.subTest(collision=collision):
                    base.write_bytes(original)
                    other = root / f"{collision}-other.json"
                    other.unlink(missing_ok=True)
                    output = base if collision == "output" else other
                    report = base if collision == "report" else other

                    result = self.run_prepare(
                        output,
                        report,
                        base_manifest=base,
                        target_products=32,
                    )

                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(
                        "output and report paths must be different from the base manifest",
                        result.stderr,
                    )
                    self.assertEqual(original, base.read_bytes())
                    self.assertFalse(other.exists())

    def test_target_smaller_than_base_is_rejected_without_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output = root / "generated.json"
            report = root / "report.json"

            result = self.run_prepare(output, report, target_products=31)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("smaller than base count 32", result.stderr)
            self.assertFalse(output.exists())
            self.assertFalse(report.exists())

    def test_repository_output_must_stay_under_ignored_tmp(self) -> None:
        forbidden_output = ROOT / "catalog-core-forbidden.json"
        forbidden_report = ROOT / "catalog-core-forbidden-report.json"
        try:
            result = self.run_prepare(forbidden_output, forbidden_report)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("outside the repository or under ignored tmp/", result.stderr)
            self.assertFalse(forbidden_output.exists())
            self.assertFalse(forbidden_report.exists())
        finally:
            forbidden_output.unlink(missing_ok=True)
            forbidden_report.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()

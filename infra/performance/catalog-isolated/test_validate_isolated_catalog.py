from __future__ import annotations

import hashlib
import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
VALIDATOR_PATH = SCRIPT_DIR / "validate-isolated-catalog.py"

spec = importlib.util.spec_from_file_location("validate_isolated_catalog", VALIDATOR_PATH)
if spec is None or spec.loader is None:
    raise RuntimeError("unable to load isolated Catalog validator")
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class IsolatedCatalogValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        if os.geteuid() != 0:
            self.fail("test_validate_isolated_catalog.py must run as root")

    def make_fixture(self, root: Path) -> dict[str, Path]:
        dataset_id = "catalog-core-control-v1"
        dataset_dir = root / dataset_id
        dataset_dir.mkdir(mode=0o700)

        products = []
        for index in range(32):
            products.append(
                {
                    "catalogKey": f"CONTROL-{index:04d}",
                    "categorySlug": "food",
                    "petType": "DOG" if index % 2 == 0 else "CAT",
                    "skus": [
                        {
                            "skuCode": f"CONTROL-SKU-{index:04d}",
                            "status": "ACTIVE",
                            "price": 10000 + index,
                            "subscribable": index % 2 == 0,
                            "initialInventory": 20,
                        }
                    ],
                }
            )
        manifest = {"categories": [{"slug": "food"}], "products": products}
        manifest_bytes = (
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
        ).encode("utf-8")

        manifest_path = dataset_dir / "manifest.json"
        manifest_path.write_bytes(manifest_bytes)
        manifest_path.chmod(0o444)

        report = {
            "schemaVersion": 1,
            "datasetId": dataset_id,
            "seed": 20260826,
            "baseManifestSha256": "b" * 64,
            "generatedManifestSha256": hashlib.sha256(manifest_bytes).hexdigest(),
            "products": {
                "base": 32,
                "synthetic": 0,
                "total": 32,
                "petType": {"CAT": 16, "DOG": 16},
                "category": {"food": 32},
                "withoutSku": 0,
                "duplicateCatalogKeys": 0,
                "unknownCategoryReferences": 0,
            },
            "skus": {
                "total": 32,
                "fanoutByProduct": {"1": 32},
                "status": {"ACTIVE": 32},
                "subscribable": {"false": 16, "true": 16},
                "duplicateSkuCodes": 0,
            },
            "inventory": {
                "total": 32,
                "stockout": 0,
                "low_1_5": 0,
                "normal_gt_5": 32,
            },
            "price": {
                "min": 10000,
                "p50NearestRank": 10015,
                "p95NearestRank": 10030,
                "max": 10031,
            },
        }
        report_path = dataset_dir / "report.json"
        report_path.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        report_path.chmod(0o444)

        config_path = root / f"{dataset_id}.env"
        config_path.write_text(
            "\n".join(
                [
                    f"PAWCYCLE_PERF_DATASET_ID={dataset_id}",
                    "PAWCYCLE_PERF_SCHEMA=pawcycle_perf_core_control",
                    "PAWCYCLE_PERF_BACKEND_IMAGE="
                    + "ghcr.io/guseoh/pawcycle-backend@sha256:"
                    + "a" * 64,
                    "PAWCYCLE_PERF_DB_URL="
                    "jdbc:mysql://mysql.internal:3306/"
                    "pawcycle_perf_core_control?sslMode=REQUIRED",
                    "PAWCYCLE_PERF_DB_USERNAME=pawcycle_perf_catalog",
                    "PAWCYCLE_PERF_HOST_PORT=18080",
                    "",
                ]
            ),
            encoding="utf-8",
        )
        config_path.chmod(0o600)

        password_path = root / "db-password"
        password_path.write_text("fixture-password\n", encoding="utf-8")
        password_path.chmod(0o400)

        return {
            "dataset_dir": dataset_dir,
            "manifest": manifest_path,
            "report": report_path,
            "config": config_path,
            "password": password_path,
        }

    def validate_fixture(self, paths: dict[str, Path]) -> None:
        config_file = validator.require_file(paths["config"], {0o600})
        password_file = validator.require_file(paths["password"], {0o400, 0o600})
        validator.validate_password_file(password_file)
        dataset_dir = validator.require_directory(paths["dataset_dir"], 0o700)
        values = validator.parse_config(config_file)
        validator.validate_config(values)
        validator.validate_dataset(dataset_dir, values)

    def test_valid_control_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.make_fixture(root)
            self.validate_fixture(paths)

    def test_live_schema_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.make_fixture(root)
            values = validator.parse_config(paths["config"])
            values["PAWCYCLE_PERF_SCHEMA"] = "pawcycle"
            with self.assertRaisesRegex(
                validator.ContractError,
                "must use schema pawcycle_perf_core_control",
            ):
                validator.validate_config(values)

    def test_db_url_must_target_dataset_schema_with_tls(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.make_fixture(root)
            values = validator.parse_config(paths["config"])

            values["PAWCYCLE_PERF_DB_URL"] = (
                "jdbc:mysql://mysql.internal:3306/pawcycle?sslMode=REQUIRED"
            )
            with self.assertRaisesRegex(
                validator.ContractError,
                "must target only schema pawcycle_perf_core_control",
            ):
                validator.validate_config(values)

            values["PAWCYCLE_PERF_DB_URL"] = (
                "jdbc:mysql://mysql.internal:3306/"
                "pawcycle_perf_core_control?sslMode=DISABLED"
            )
            with self.assertRaisesRegex(validator.ContractError, "must require TLS"):
                validator.validate_config(values)

    def test_backend_image_requires_digest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.make_fixture(root)
            values = validator.parse_config(paths["config"])
            values["PAWCYCLE_PERF_BACKEND_IMAGE"] = (
                "ghcr.io/guseoh/pawcycle-backend:latest"
            )
            with self.assertRaisesRegex(validator.ContractError, "pinned by sha256"):
                validator.validate_config(values)

    def test_manifest_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.make_fixture(root)
            target = root / "manifest-target.json"
            target.write_bytes(paths["manifest"].read_bytes())
            target.chmod(0o444)
            paths["manifest"].unlink()
            paths["manifest"].symlink_to(target)

            values = validator.parse_config(paths["config"])
            with self.assertRaisesRegex(
                validator.ContractError,
                "regular non-symlink file",
            ):
                validator.validate_dataset(paths["dataset_dir"], values)

    def test_manifest_checksum_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.make_fixture(root)
            report = json.loads(paths["report"].read_text(encoding="utf-8"))
            report["generatedManifestSha256"] = "0" * 64
            paths["report"].chmod(0o644)
            paths["report"].write_text(
                json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
            paths["report"].chmod(0o444)

            values = validator.parse_config(paths["config"])
            with self.assertRaisesRegex(validator.ContractError, "checksum"):
                validator.validate_dataset(paths["dataset_dir"], values)

    def test_insecure_config_mode_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.make_fixture(root)
            paths["config"].chmod(0o644)
            with self.assertRaisesRegex(validator.ContractError, "file mode"):
                validator.require_file(paths["config"], {0o600})

    def test_non_root_owned_dataset_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.make_fixture(root)
            os.chown(paths["dataset_dir"], 65534, 65534)
            with self.assertRaisesRegex(validator.ContractError, "root-owned"):
                validator.require_directory(paths["dataset_dir"], 0o700)

    def test_secret_key_must_not_be_in_runtime_config(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.make_fixture(root)
            paths["config"].chmod(0o644)
            with paths["config"].open("a", encoding="utf-8") as target:
                target.write("PAWCYCLE_PERF_DB_PASSWORD=do-not-store-here\n")
            paths["config"].chmod(0o600)
            with self.assertRaisesRegex(validator.ContractError, "unknown config keys"):
                validator.parse_config(paths["config"])


if __name__ == "__main__":
    unittest.main()

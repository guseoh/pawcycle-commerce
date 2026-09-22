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
APPROVED_SHA = "1" * 40

spec = importlib.util.spec_from_file_location("validate_isolated_catalog", VALIDATOR_PATH)
if spec is None or spec.loader is None:
    raise RuntimeError("unable to load isolated Catalog validator")
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class IsolatedCatalogValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        if os.geteuid() != 0:
            self.fail("test_validate_isolated_catalog.py must run as root")

    def make_source(self, root: Path) -> Path:
        source_root = root / APPROVED_SHA
        prepare_wrapper = source_root / "scripts" / "prepare-product-scale-data.py"
        data_generator = source_root / "scripts" / "generate-product-data-v2.py"
        base_path = (
            source_root
            / "backend"
            / "src"
            / "main"
            / "resources"
            / "catalog"
            / "demo-catalog.json"
        )
        prepare_wrapper.parent.mkdir(parents=True)
        base_path.parent.mkdir(parents=True)

        prepare_wrapper.write_text("# fixture prepare wrapper\n", encoding="utf-8")
        data_generator.write_text("# fixture data generator\n", encoding="utf-8")
        base_path.write_text(
            json.dumps(
                {
                    "categories": [{"slug": "food"}],
                    "products": [],
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        marker = source_root / ".approved-sha"
        marker.write_text(APPROVED_SHA + "\n", encoding="utf-8")

        for path in (prepare_wrapper, data_generator, base_path, marker):
            path.chmod(0o444)
        source_root.chmod(0o555)
        return source_root

    def make_fixture(self, root: Path) -> dict[str, Path]:
        source_root = self.make_source(root)
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

        base_path = (
            source_root
            / "backend"
            / "src"
            / "main"
            / "resources"
            / "catalog"
            / "demo-catalog.json"
        )
        prepare_wrapper = source_root / "scripts" / "prepare-product-scale-data.py"
        data_generator = source_root / "scripts" / "generate-product-data-v2.py"

        report = {
            "schemaVersion": 1,
            "datasetId": dataset_id,
            "seed": 20260826,
            "baseManifestSha256": validator.file_sha256(base_path),
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

        provenance = {
            "schemaVersion": 1,
            "datasetId": dataset_id,
            "approvedSourceSha": APPROVED_SHA,
            "prepareWrapperSha256": validator.file_sha256(prepare_wrapper),
            "dataGeneratorSha256": validator.file_sha256(data_generator),
            "baseManifestSha256": validator.file_sha256(base_path),
            "generatedManifestSha256": validator.file_sha256(manifest_path),
            "reportSha256": validator.file_sha256(report_path),
            "seed": 20260826,
            "productsTotal": 32,
        }
        provenance_path = dataset_dir / "provenance.json"
        provenance_path.write_text(
            json.dumps(provenance, sort_keys=True, indent=2) + "\n",
            encoding="utf-8",
        )
        provenance_path.chmod(0o444)

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
            "source_root": source_root,
            "dataset_dir": dataset_dir,
            "manifest": manifest_path,
            "report": report_path,
            "provenance": provenance_path,
            "config": config_path,
            "password": password_path,
        }

    def source_contract(self, paths: dict[str, Path]):
        return validator.validate_source_root(paths["source_root"])

    def validate_fixture(self, paths: dict[str, Path]) -> None:
        source = self.source_contract(paths)
        config_file = validator.require_file(paths["config"], {0o600})
        password_file = validator.require_file(paths["password"], {0o400, 0o600})
        validator.validate_password_file(password_file)
        dataset_dir = validator.require_directory(paths["dataset_dir"], 0o700)
        values = validator.parse_config(config_file)
        validator.validate_config(values)
        validator.validate_dataset(dataset_dir, values, source)

    def rewrite_json(self, path: Path, payload: dict) -> None:
        path.chmod(0o644)
        path.write_text(
            json.dumps(payload, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
            encoding="utf-8",
        )
        path.chmod(0o444)

    def test_valid_control_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            self.validate_fixture(paths)

    def test_live_schema_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            values = validator.parse_config(paths["config"])
            values["PAWCYCLE_PERF_SCHEMA"] = "pawcycle"
            with self.assertRaisesRegex(
                validator.ContractError,
                "must use schema pawcycle_perf_core_control",
            ):
                validator.validate_config(values)

    def test_db_url_must_target_dataset_schema_with_tls(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
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
            paths = self.make_fixture(Path(temp))
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
                validator.validate_dataset(
                    paths["dataset_dir"],
                    values,
                    self.source_contract(paths),
                )

    def test_manifest_checksum_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            report = json.loads(paths["report"].read_text(encoding="utf-8"))
            report["generatedManifestSha256"] = "0" * 64
            self.rewrite_json(paths["report"], report)

            values = validator.parse_config(paths["config"])
            with self.assertRaisesRegex(validator.ContractError, "checksum"):
                validator.validate_dataset(
                    paths["dataset_dir"],
                    values,
                    self.source_contract(paths),
                )

    def test_base_manifest_digest_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            report = json.loads(paths["report"].read_text(encoding="utf-8"))
            report["baseManifestSha256"] = "0" * 64
            self.rewrite_json(paths["report"], report)

            values = validator.parse_config(paths["config"])
            with self.assertRaisesRegex(
                validator.ContractError,
                "base manifest digest",
            ):
                validator.validate_dataset(
                    paths["dataset_dir"],
                    values,
                    self.source_contract(paths),
                )

    def test_provenance_source_sha_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            provenance = json.loads(
                paths["provenance"].read_text(encoding="utf-8")
            )
            provenance["approvedSourceSha"] = "2" * 40
            self.rewrite_json(paths["provenance"], provenance)

            values = validator.parse_config(paths["config"])
            with self.assertRaisesRegex(
                validator.ContractError,
                "provenance mismatch: approvedSourceSha",
            ):
                validator.validate_dataset(
                    paths["dataset_dir"],
                    values,
                    self.source_contract(paths),
                )

    def test_provenance_prepare_wrapper_digest_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            provenance = json.loads(
                paths["provenance"].read_text(encoding="utf-8")
            )
            provenance["prepareWrapperSha256"] = "0" * 64
            self.rewrite_json(paths["provenance"], provenance)

            values = validator.parse_config(paths["config"])
            with self.assertRaisesRegex(
                validator.ContractError,
                "provenance mismatch: prepareWrapperSha256",
            ):
                validator.validate_dataset(
                    paths["dataset_dir"],
                    values,
                    self.source_contract(paths),
                )

    def test_provenance_data_generator_digest_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            provenance = json.loads(
                paths["provenance"].read_text(encoding="utf-8")
            )
            provenance["dataGeneratorSha256"] = "0" * 64
            self.rewrite_json(paths["provenance"], provenance)

            values = validator.parse_config(paths["config"])
            with self.assertRaisesRegex(
                validator.ContractError,
                "provenance mismatch: dataGeneratorSha256",
            ):
                validator.validate_dataset(
                    paths["dataset_dir"],
                    values,
                    self.source_contract(paths),
                )

    def test_source_marker_must_match_source_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            marker = paths["source_root"] / ".approved-sha"
            marker.chmod(0o644)
            marker.write_text("2" * 40 + "\n", encoding="utf-8")
            marker.chmod(0o444)

            with self.assertRaisesRegex(
                validator.ContractError,
                "directory name must match",
            ):
                validator.validate_source_root(paths["source_root"])

    def test_source_file_mode_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            data_generator = (
                paths["source_root"] / "scripts" / "generate-product-data-v2.py"
            )
            data_generator.chmod(0o644)

            with self.assertRaisesRegex(
                validator.ContractError,
                "source file mode must be 0444",
            ):
                validator.validate_source_root(paths["source_root"])

    def test_missing_provenance_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            paths["provenance"].unlink()

            values = validator.parse_config(paths["config"])
            with self.assertRaises(FileNotFoundError):
                validator.validate_dataset(
                    paths["dataset_dir"],
                    values,
                    self.source_contract(paths),
                )

    def test_insecure_config_mode_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            paths["config"].chmod(0o644)
            with self.assertRaisesRegex(validator.ContractError, "file mode"):
                validator.require_file(paths["config"], {0o600})

    def test_non_root_owned_dataset_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            os.chown(paths["dataset_dir"], 65534, 65534)
            with self.assertRaisesRegex(validator.ContractError, "root-owned"):
                validator.require_directory(paths["dataset_dir"], 0o700)

    def test_secret_key_must_not_be_in_runtime_config(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.make_fixture(Path(temp))
            paths["config"].chmod(0o644)
            with paths["config"].open("a", encoding="utf-8") as target:
                target.write("PAWCYCLE_PERF_DB_PASSWORD=do-not-store-here\n")
            paths["config"].chmod(0o600)
            with self.assertRaisesRegex(validator.ContractError, "unknown config keys"):
                validator.parse_config(paths["config"])


if __name__ == "__main__":
    unittest.main()

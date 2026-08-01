#!/usr/bin/env python3
"""Static and classifier regressions for Repository Validation."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CLASSIFIER_PATH = ROOT / "scripts" / "classify-validation-changes.py"
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "validate-conventions.yml"
SPEC = importlib.util.spec_from_file_location("validation_classifier", CLASSIFIER_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("validation classifier를 불러올 수 없음")
CLASSIFIER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CLASSIFIER)


class ChangeClassifierTest(unittest.TestCase):
    def assert_groups(self, paths: list[str], **expected: bool) -> None:
        actual = CLASSIFIER.classify(paths)
        self.assertEqual(actual, {name: expected.get(name, False) for name in CLASSIFIER.GROUPS})

    def test_docs_only(self) -> None:
        self.assert_groups(["docs/product/overview.md"], harness=True)

    def test_backend_only(self) -> None:
        self.assert_groups(["backend/src/Main.java"], backend=True)

    def test_frontend_only(self) -> None:
        self.assert_groups(["frontend/src/page.tsx"], frontend=True)

    def test_production_only(self) -> None:
        self.assert_groups(["infra/production/deploy.sh"], production=True)

    def test_validator_change_runs_all_groups(self) -> None:
        self.assert_groups(
            ["scripts/validate-task-artifacts.py"],
            harness=True,
            backend=True,
            frontend=True,
            production=True,
        )

    def test_workflow_change_runs_all_groups(self) -> None:
        self.assert_groups(
            [".github/workflows/validate-conventions.yml"],
            harness=True,
            backend=True,
            frontend=True,
            production=True,
        )

    def test_backend_and_frontend_are_combined(self) -> None:
        self.assert_groups(
            ["backend/src/Main.java", "frontend/src/page.tsx"],
            backend=True,
            frontend=True,
        )

    def test_no_changed_paths_allows_all_components_to_skip(self) -> None:
        self.assert_groups([])


class WorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    def test_metadata_edit_has_separate_concurrency_and_skips_code_jobs(self) -> None:
        self.assertIn("github.event.action == 'edited' && 'metadata' || 'code'", self.workflow)
        self.assertIn("if: github.event.action != 'edited'", self.workflow)

    def test_required_check_names_are_preserved(self) -> None:
        self.assertIn("name: Commit and PR conventions", self.workflow)
        self.assertIn("name: Application validation", self.workflow)

    def test_component_jobs_are_parallel_and_backend_owns_mysql(self) -> None:
        for job in ("harness:", "backend:", "frontend:", "production:"):
            self.assertIn(job, self.workflow)
        backend_start = self.workflow.index("\n  backend:\n")
        frontend_start = self.workflow.index("\n  frontend:\n")
        backend_block = self.workflow[backend_start:frontend_start]
        self.assertIn("services:\n      mysql:", backend_block)
        self.assertEqual(self.workflow.count("image: mysql:8.4"), 1)

    def test_aggregate_gate_propagates_failures_and_accepts_skips(self) -> None:
        self.assertIn("needs: [conventions, classify, harness, backend, frontend, production]", self.workflow)
        self.assertIn("success|skipped", self.workflow)
        self.assertIn("exit 1", self.workflow)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Static and classifier regressions for the Lean Harness."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CLASSIFIER_PATH = ROOT / "scripts" / "classify-validation-changes.py"
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "validate-conventions.yml"
METADATA_WORKFLOW_PATH = ROOT / ".github" / "workflows" / "validate-pr-metadata.yml"
PUBLISH_WORKFLOW_PATH = ROOT / ".github" / "workflows" / "publish-production-images.yml"
SPEC = importlib.util.spec_from_file_location("validation_classifier", CLASSIFIER_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("validation classifier를 불러올 수 없음")
CLASSIFIER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CLASSIFIER)


class ChangeClassifierTest(unittest.TestCase):
    def assert_groups(self, paths: list[str], **expected: bool) -> None:
        actual = CLASSIFIER.classify(paths)
        self.assertEqual(actual, {name: expected.get(name, False) for name in CLASSIFIER.GROUPS})

    def test_docs_only_runs_harness(self) -> None:
        self.assert_groups(["docs/product/overview.md"], harness=True)

    def test_empty_change_list_allows_all_components_to_skip(self) -> None:
        self.assert_groups([])

    def test_unknown_root_file_fails_closed(self) -> None:
        self.assert_groups(
            ["unknown.config"],
            harness=True,
            backend=True,
            frontend=True,
            production=True,
        )

    def test_harness_contract_paths_do_not_run_application_stacks(self) -> None:
        for path in (
            "AGENTS.md",
            "backend/AGENTS.md",
            "frontend/AGENTS.md",
            "infra/AGENTS.md",
            "qa/AGENTS.md",
            ".coderabbit.yaml",
            ".github/pull_request_template.md",
            "CONTRIBUTING.md",
            "docs/runbook/lean-harness.md",
            "docs/roles/backend-engineer.md",
            ".agents/skills/backend-engineer/SKILL.md",
            "scripts/validate-task-artifacts.py",
            "scripts/test_validate_task_artifacts.py",
            ".github/scripts/collect-discord-context.py",
            ".github/scripts/normalize-discord-context.py",
        ):
            with self.subTest(path=path):
                self.assert_groups([path], harness=True)

    def test_backend_only(self) -> None:
        self.assert_groups(["backend/src/Main.java"], backend=True)

    def test_backend_http_contract_runs_backend_and_frontend(self) -> None:
        for path in (
            "backend/src/main/java/com/pawcycle/backend/catalog/engagement/api/ProductReviewController.java",
            "backend/src/main/java/com/pawcycle/backend/commerce/AddressRequest.java",
            "backend/src/main/java/com/pawcycle/backend/common/error/ApiErrorResponse.java",
            "backend/src/main/java/com/pawcycle/backend/subscription/SubscriptionController.java",
            "backend/src/main/java/com/pawcycle/backend/subscription/RepeatCommerceController.java",
            "backend/src/main/java/com/pawcycle/backend/subscription/SubscriptionExceptionHandler.java",
            "backend/src/main/java/com/pawcycle/backend/commerce/CommerceExceptionHandler.java",
        ):
            with self.subTest(path=path):
                self.assert_groups([path], backend=True, frontend=True)

    def test_internal_performance_controller_remains_backend_only(self) -> None:
        self.assert_groups(
            ["backend/src/main/java/com/pawcycle/backend/subscription/performance/SubscriptionBurstMeasurementController.java"],
            backend=True,
        )

    def test_backend_internal_and_test_paths_remain_backend_only(self) -> None:
        for path in (
            "backend/src/main/java/com/pawcycle/backend/commerce/CouponEntity.java",
            "backend/src/main/java/com/pawcycle/backend/commerce/coupon/persistence/CouponView.java",
            "backend/src/test/java/com/pawcycle/backend/commerce/CouponTest.java",
        ):
            with self.subTest(path=path):
                self.assert_groups([path], backend=True)

    def test_frontend_only(self) -> None:
        self.assert_groups(["frontend/src/page.tsx"], frontend=True)

    def test_production_only(self) -> None:
        self.assert_groups(["infra/production/deploy.sh"], production=True)

    def test_standalone_metrics_proxy_is_production_only(self) -> None:
        self.assert_groups(["infra/production-metrics-proxy/compose.yaml"], production=True)

    def test_local_integration_fails_safe_to_all_components(self) -> None:
        self.assert_groups(
            ["infra/local-integration/compose.yaml"],
            harness=True,
            backend=True,
            frontend=True,
            production=True,
        )

    def test_classifier_and_workflow_changes_run_all_groups(self) -> None:
        for path in (
            "scripts/classify-validation-changes.py",
            "scripts/test_validate_conventions_workflow.py",
            ".github/workflows/validate-conventions.yml",
            ".github/workflows/publish-production-images.yml",
        ):
            with self.subTest(path=path):
                self.assert_groups(
                    [path],
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


class WorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.metadata_workflow = METADATA_WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.publish_workflow = PUBLISH_WORKFLOW_PATH.read_text(encoding="utf-8")

    def test_code_and_metadata_events_and_concurrency_are_separate(self) -> None:
        self.assertNotIn("edited", self.workflow.split("concurrency:", 1)[0])
        self.assertIn("repository-validation-code-${{ github.event.pull_request.number }}", self.workflow)
        self.assertIn("types: [edited]", self.metadata_workflow)
        self.assertIn("repository-validation-metadata-${{ github.event.pull_request.number }}", self.metadata_workflow)
        code_group = re.search(r"(?m)^\s*group:\s*(.+?)\s*$", self.workflow)
        metadata_group = re.search(r"(?m)^\s*group:\s*(.+?)\s*$", self.metadata_workflow)
        self.assertIsNotNone(code_group)
        self.assertIsNotNone(metadata_group)
        assert code_group is not None and metadata_group is not None
        self.assertNotEqual(code_group.group(1), metadata_group.group(1))

    def test_metadata_workflow_has_no_component_checks(self) -> None:
        self.assertIn("name: PR metadata validation", self.metadata_workflow)
        for forbidden in (
            "Application validation",
            "Classify validation changes",
            "Harness validation",
            "Backend and MySQL validation",
            "Frontend validation",
            "Production contract validation",
        ):
            self.assertNotIn(forbidden, self.metadata_workflow)

    def test_classifier_and_component_jobs_do_not_wait_for_metadata_conventions(self) -> None:
        classify_block = self.workflow[
            self.workflow.index("\n  classify:\n") : self.workflow.index("\n  harness:\n")
        ]
        self.assertNotIn("needs: conventions", classify_block)
        for start_name, end_name in (
            ("harness", "backend"),
            ("backend", "frontend"),
            ("frontend", "production"),
            ("production", "application"),
        ):
            block = self.workflow[
                self.workflow.index(f"\n  {start_name}:\n") : self.workflow.index(f"\n  {end_name}:\n")
            ]
            self.assertIn("needs: classify", block)
            self.assertNotIn("needs: [conventions, classify]", block)

    def test_final_gate_still_requires_conventions_and_selected_components(self) -> None:
        self.assertIn("name: Commit and PR conventions", self.workflow)
        self.assertIn("name: Application validation", self.workflow)
        self.assertIn("needs: [conventions, classify, harness, backend, frontend, production]", self.workflow)
        self.assertIn('require_success conventions "$CONVENTIONS_RESULT"', self.workflow)
        self.assertIn('require_success classify "$CLASSIFY_RESULT"', self.workflow)
        self.assertIn("Selected component did not succeed", self.workflow)
        self.assertIn("Unselected component was not skipped", self.workflow)

    def test_backend_owns_mysql(self) -> None:
        backend_start = self.workflow.index("\n  backend:\n")
        frontend_start = self.workflow.index("\n  frontend:\n")
        backend_block = self.workflow[backend_start:frontend_start]
        self.assertIn("services:\n      mysql:", backend_block)
        self.assertEqual(self.workflow.count("image: mysql:8.4"), 1)

    def test_merge_base_and_rename_safe_diff_are_used(self) -> None:
        self.assertIn('git merge-base "$BASE_SHA" "$HEAD_SHA"', self.workflow)
        self.assertIn('git diff --no-renames --name-only "$merge_base..$HEAD_SHA"', self.workflow)

    def test_production_image_publish_uses_job_level_runtime_diff_gate(self) -> None:
        header = self.publish_workflow.split("permissions:", 1)[0]
        self.assertNotIn("paths:", header)
        self.assertIn("name: Classify production image changes", self.publish_workflow)
        self.assertIn('git diff --no-renames --name-only "$BEFORE_SHA" "$HEAD_SHA"', self.publish_workflow)
        self.assertIn("grep -Eq '^(backend/|frontend/|infra/production/)'", self.publish_workflow)
        publish_block = self.publish_workflow[self.publish_workflow.index("\n  publish:\n") :]
        self.assertIn("needs: classify", publish_block)
        self.assertIn("if: needs.classify.outputs.publish == 'true'", publish_block)
        for forbidden in (
            ".github/workflows/publish-production-images.yml",
            "docs/**",
            "AGENTS.md",
            "README.md",
        ):
            self.assertNotIn(forbidden, self.publish_workflow)

    def test_base_only_change_is_excluded_from_pull_request_diff(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            def git(*args: str) -> str:
                return subprocess.run(
                    ["git", *args],
                    cwd=root,
                    text=True,
                    capture_output=True,
                    check=True,
                    encoding="utf-8",
                ).stdout.strip()

            git("init", "-b", "main")
            git("config", "user.name", "Harness Test")
            git("config", "user.email", "harness@example.invalid")
            (root / "initial.txt").write_text("initial\n", encoding="utf-8")
            git("add", "initial.txt")
            git("commit", "-m", "chore(test): baseline")
            git("switch", "-c", "feature")
            (root / "docs").mkdir()
            (root / "docs" / "change.md").write_text("feature\n", encoding="utf-8")
            git("add", "docs/change.md")
            git("commit", "-m", "docs(test): feature change")
            head_sha = git("rev-parse", "HEAD")
            git("switch", "main")
            (root / "backend").mkdir()
            (root / "backend" / "base.java").write_text("base only\n", encoding="utf-8")
            git("add", "backend/base.java")
            git("commit", "-m", "chore(test): base change")
            base_sha = git("rev-parse", "HEAD")
            merge_base = git("merge-base", base_sha, head_sha)
            changed = git("diff", "--no-renames", "--name-only", f"{merge_base}..{head_sha}").splitlines()

        self.assertEqual(changed, ["docs/change.md"])


class DocumentationContractTest(unittest.TestCase):
    def test_pr_template_has_only_minimum_decision_sections(self) -> None:
        template = (ROOT / ".github" / "pull_request_template.md").read_text(encoding="utf-8")
        for required in ("## 작업", "## 변경", "## 검증", "## 위험"):
            self.assertIn(required, template)
        self.assertNotIn("## 병합 판단", template)
        self.assertNotIn("## 결정과 영향", template)

    def test_harness_distinguishes_spec_from_prompt_and_review_limits(self) -> None:
        harness = (ROOT / "docs" / "runbook" / "lean-harness.md").read_text(encoding="utf-8")
        self.assertIn("Spec ≠ Prompt", harness)
        self.assertIn("Final Lightweight Delta Prompt", harness)
        self.assertIn("파일 수 제한 때문에 coherent PR을 억지로 쪼개지 않는다", harness)
        self.assertIn("특정 업무 prefix allowlist", harness)

    def test_root_agent_owns_write_safety_and_path_agents_own_invariants(self) -> None:
        root_agent = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        backend_agent = (ROOT / "backend" / "AGENTS.md").read_text(encoding="utf-8")
        backend_role = (ROOT / "docs" / "roles" / "backend-engineer.md").read_text(encoding="utf-8")
        backend_skill = (ROOT / ".agents" / "skills" / "backend-engineer" / "SKILL.md").read_text(encoding="utf-8")
        self.assertIn("direct `main` write", root_agent)
        self.assertIn("branch 인자를 생략하지 않는다", root_agent)
        self.assertIn("트랜잭션", backend_agent)
        self.assertNotIn("feat/be/<TASK-ID>", backend_agent)
        self.assertNotIn("feat/be/<TASK-ID>", backend_role)
        self.assertNotIn("feat/be/<TASK-ID>", backend_skill)


if __name__ == "__main__":
    unittest.main()

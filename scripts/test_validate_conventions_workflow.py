#!/usr/bin/env python3
"""Static and classifier regressions for Repository Validation."""

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

    def test_harness_paths_are_classified_independently(self) -> None:
        for path in (
            ".coderabbit.yaml",
            "CONTRIBUTING.md",
            ".githooks/pre-commit",
            ".github/scripts/example.py",
            ".github/fixtures/example.json",
            "scripts/example.py",
        ):
            with self.subTest(path=path):
                self.assert_groups([path], harness=True)

    def test_backend_only(self) -> None:
        self.assert_groups(["backend/src/Main.java"], backend=True)

    def test_frontend_only(self) -> None:
        self.assert_groups(["frontend/src/page.tsx"], frontend=True)

    def test_production_only(self) -> None:
        self.assert_groups(["infra/production/deploy.sh"], production=True)

    def test_production_rename_includes_old_and_new_paths(self) -> None:
        self.assert_groups(
            ["infra/production/deploy.sh", "docs/archive/deploy.sh"],
            harness=True,
            production=True,
        )

    def test_local_integration_fails_safe_to_all_components(self) -> None:
        self.assert_groups(
            ["infra/local-integration/compose.yaml"],
            harness=True,
            backend=True,
            frontend=True,
            production=True,
        )

    def test_role_documents_and_skills_select_owned_components(self) -> None:
        cases = {
            "docs/roles/backend-engineer.md": {"harness": True, "backend": True},
            ".agents/skills/backend-engineer/SKILL.md": {"harness": True, "backend": True},
            "docs/roles/frontend-engineer.md": {"harness": True, "frontend": True},
            ".agents/skills/frontend-engineer/SKILL.md": {"harness": True, "frontend": True},
            "docs/roles/platform-sre.md": {"harness": True, "production": True},
            ".agents/skills/platform-sre/SKILL.md": {"harness": True, "production": True},
            "docs/roles/product-planner.md": {"harness": True},
            "docs/roles/ux-designer.md": {"harness": True},
            "docs/roles/qa-engineer.md": {"harness": True},
            "docs/roles/tech-lead.md": {"harness": True},
        }
        for path, expected in cases.items():
            with self.subTest(path=path):
                self.assert_groups([path], **expected)

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

class WorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.metadata_workflow = METADATA_WORKFLOW_PATH.read_text(encoding="utf-8")

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

    def test_metadata_workflow_has_no_required_or_component_checks(self) -> None:
        self.assertIn("name: PR metadata validation", self.metadata_workflow)
        for forbidden in (
            "Commit and PR conventions",
            "Application validation",
            "Classify validation changes",
            "Harness validation",
            "Backend and MySQL validation",
            "Frontend validation",
            "Production contract validation",
        ):
            self.assertNotIn(forbidden, self.metadata_workflow)

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
        self.assertEqual(self.workflow.count("needs: [conventions, classify]"), 4)
        for start_name, end_name in (
            ("harness", "backend"),
            ("backend", "frontend"),
            ("frontend", "production"),
            ("production", "application"),
        ):
            block = self.workflow[
                self.workflow.index(f"\n  {start_name}:\n") : self.workflow.index(f"\n  {end_name}:\n")
            ]
            self.assertIn("needs: [conventions, classify]", block)
            for component in ("harness", "backend", "frontend", "production"):
                if component != start_name:
                    self.assertNotIn(f"needs.{component}.result", block)

    def test_aggregate_gate_propagates_failures_and_accepts_skips(self) -> None:
        self.assertIn("needs: [conventions, classify, harness, backend, frontend, production]", self.workflow)
        self.assertIn("Selected component did not succeed", self.workflow)
        self.assertIn("Unselected component was not skipped", self.workflow)
        self.assertIn("Invalid classifier output", self.workflow)
        self.assertIn('require_success classify "$CLASSIFY_RESULT"', self.workflow)

    def test_merge_base_and_rename_safe_diff_are_used(self) -> None:
        self.assertIn('git merge-base "$BASE_SHA" "$HEAD_SHA"', self.workflow)
        self.assertIn('git diff --no-renames --name-only "$merge_base..$HEAD_SHA"', self.workflow)

    def test_mysql_diagnostics_include_returned_values(self) -> None:
        self.assertIn("Unexpected MySQL version::${mysql_version}", self.workflow)
        self.assertIn("Unexpected MySQL character set::${character_set}", self.workflow)
        self.assertIn("Unexpected MySQL collation::${collation}", self.workflow)

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
            git("commit", "-m", "chore(test): 초기 기준")
            git("switch", "-c", "feature")
            (root / "docs").mkdir()
            (root / "docs" / "change.md").write_text("feature\n", encoding="utf-8")
            git("add", "docs/change.md")
            git("commit", "-m", "docs(test): 기능 변경")
            head_sha = git("rev-parse", "HEAD")
            git("switch", "main")
            (root / "backend").mkdir()
            (root / "backend" / "base.java").write_text("base only\n", encoding="utf-8")
            git("add", "backend/base.java")
            git("commit", "-m", "chore(test): 기준 변경")
            base_sha = git("rev-parse", "HEAD")
            merge_base = git("merge-base", base_sha, head_sha)
            changed = git("diff", "--no-renames", "--name-only", f"{merge_base}..{head_sha}").splitlines()

        self.assertEqual(changed, ["docs/change.md"])


class DocumentationContractTest(unittest.TestCase):
    def test_report_template_is_minimal_and_operation_sections_are_conditional(self) -> None:
        template = (ROOT / "docs" / "reports" / "task-report-template.md").read_text(encoding="utf-8")
        for required in ("## 작업", "실행 구분:", "## 목적", "## 결과 또는 증거", "## 위험 또는 제한"):
            self.assertIn(required, template)
        for operation_only in ("## 명시적 승인 근거", "## 적용 전 확인", "## 적용 후 확인", "## 독립 확인", "## 복구·rollback", "## 미실행 항목", "## 남은 위험"):
            self.assertIn(operation_only, template)
        self.assertIn('실행 구분이 "실제 운영 실행"일 때만', template)
        self.assertNotIn("QA 문서 경로 또는 생략 사유", template)
        self.assertNotIn("인수인계 생략", template)
        self.assertNotIn("Git 결과", template)

    def test_branch_and_role_documents_share_conditional_contracts(self) -> None:
        contributing = (ROOT / "CONTRIBUTING.md").read_text(encoding="utf-8")
        for branch in (
            "spec/po/<TASK-ID>",
            "design/ux/<TASK-ID>",
            "feat/be/<TASK-ID>",
            "feat/fe/<TASK-ID>",
            "test/qa/<TASK-ID>",
            "ops/sre/<TASK-ID>",
            "ops/tl/<TASK-ID>",
        ):
            self.assertIn(branch, contributing)
        self.assertIn("보고서·인수인계·QA·Runbook·ADR", contributing)
        self.assertIn("조건을 충족할 때만", contributing)

        backend_agent = (ROOT / "backend" / "AGENTS.md").read_text(encoding="utf-8")
        backend_role = (ROOT / "docs" / "roles" / "backend-engineer.md").read_text(encoding="utf-8")
        backend_skill = (ROOT / ".agents" / "skills" / "backend-engineer" / "SKILL.md").read_text(encoding="utf-8")
        active_rule = "하나의 task branch에는 하나의 활성 작업만 둔다."
        for text in (backend_agent, backend_role, backend_skill):
            self.assertIn(active_rule, text)

        frontend_role = (ROOT / "docs" / "roles" / "frontend-engineer.md").read_text(encoding="utf-8")
        ux_role = (ROOT / "docs" / "roles" / "ux-designer.md").read_text(encoding="utf-8")
        platform_skill = (ROOT / ".agents" / "skills" / "platform-sre" / "SKILL.md").read_text(encoding="utf-8")
        qa_skill = (ROOT / ".agents" / "skills" / "qa-engineer" / "SKILL.md").read_text(encoding="utf-8")
        self.assertIn("인수인계를 작성했다면", frontend_role)
        self.assertIn("실제 다음 소비자", ux_role)
        self.assertIn("별도의 명시적 사용자 승인", platform_skill)
        self.assertIn("중복 요청·멱등성", qa_skill)


if __name__ == "__main__":
    unittest.main()

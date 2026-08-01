#!/usr/bin/env python3
"""Regression tests for the lean task artifact validator."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "validate-task-artifacts.py"
TASK_ID = "HARNESS-LEAN-999"


def pr_body(
    *,
    grade: str = "일반",
    execution: str = "저장소 변경",
    purpose: str = "하네스 계약을 단순화한다.",
    scope: str = "validator와 테스트를 변경한다.",
    validation: str = "회귀 테스트 통과",
    risk: str = "실제 운영 실행 없음",
) -> str:
    return f"""## 작업

- 작업 ID: {TASK_ID}
- 작업 등급: {grade}
- 실행 구분: {execution}
- 역할: Tech Lead

## 목적과 범위

- 목적: {purpose}
- 변경 범위: {scope}
- 제외 범위: 제품 코드

## 결정과 영향

- 중요한 결정: 최소 구조만 검사

## 검증

- 실행 결과: {validation}
- 실패·미실행과 이유: 없음

## 위험과 복구

- 남은 위험: {risk}
- 실패·rollback·revert 경계: 일반 revert PR

## 병합 판단

- 남은 차단 리뷰: 없음
- 사용자 판단 항목: 병합 여부
- 자동 병합 없음
"""


def run_validator(root: Path, *args: str, stdin_text: str = "") -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["PYTHONUTF8"] = "1"
    return subprocess.run(
        [sys.executable, str(SCRIPT), "--root", str(root), *args],
        input=stdin_text,
        text=True,
        capture_output=True,
        check=False,
        encoding="utf-8",
        env=env,
    )


def write_report(root: Path, content: str, task_id: str = TASK_ID) -> Path:
    path = root / "docs" / "reports" / task_id / "report.md"
    path.parent.mkdir(parents=True)
    path.write_text(content, encoding="utf-8")
    return path


MINIMAL_REPORT = f"""# 작업 보고서

- 작업 ID: {TASK_ID}
- 작업 등급: 고위험
- 실행 구분: 저장소 변경

## 목적

하네스 변경의 장기 판정을 보존한다.

## 결과 또는 증거

validator 회귀가 통과했다.

## 위험과 제한

실제 운영 실행은 하지 않았다.
"""


PRODUCTION_REPORT = f"""# 실제 운영 실행 보고서

- 작업 ID: {TASK_ID}
- 작업 등급: 고위험
- 실행 구분: 실제 운영 실행

## 목적

승인된 복구 절차를 실행한다.

## 결과 또는 증거

적용 결과를 비민감 evidence로 확인했다.

## 명시적 승인 근거

사용자가 이 실행을 명시적으로 승인했다.

## 적용 전 확인

대상과 복구 지점을 확인했다.

## 적용 후 확인

health와 상태를 확인했다.

## 독립 확인

운영자가 결과를 별도로 확인했다.

## 복구·rollback

승인된 rollback 경로를 확인했다.

## 실행하지 못한 검증과 남은 위험

장기 부하는 미실행이며 후속 위험이다.

## 위험과 제한

관찰 기간이 짧다.
"""


class ValidateTaskArtifactsTest(unittest.TestCase):
    def test_lightweight_pr_without_report_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(Path(tmp), "--from-stdin", stdin_text=pr_body(grade="경량"))
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_standard_repository_change_without_report_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(Path(tmp), "--from-stdin", stdin_text=pr_body())
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_high_risk_repository_change_without_report_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(Path(tmp), "--from-stdin", stdin_text=pr_body(grade="고위험"))
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_production_execution_without_report_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(
                Path(tmp),
                "--from-stdin",
                stdin_text=pr_body(grade="고위험", execution="실제 운영 실행"),
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("실제 운영 실행 보고서", result.stderr)

    def test_production_execution_report_with_all_evidence_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_report(root, PRODUCTION_REPORT)
            result = run_validator(
                root,
                "--from-stdin",
                stdin_text=pr_body(grade="고위험", execution="실제 운영 실행"),
            )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_existing_report_requires_purpose_evidence_and_risk(self) -> None:
        invalid_reports = (
            "# 보고서\n\n## 결과 또는 증거\n\nPASS\n\n## 위험과 제한\n\n없음\n",
            "# 보고서\n\n## 목적\n\n목적\n\n## 위험과 제한\n\n없음\n",
            "# 보고서\n\n## 목적\n\n목적\n\n## 결과 또는 증거\n\nPASS\n",
        )
        for content in invalid_reports:
            with self.subTest(content=content), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                write_report(root, content)
                result = run_validator(root, "--from-stdin", stdin_text=pr_body(grade="고위험"))
                self.assertNotEqual(result.returncode, 0)

    def test_minimal_repository_report_passes_without_git_qa_or_handoff_headings(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_report(root, MINIMAL_REPORT)
            result = run_validator(root, "--from-stdin", stdin_text=pr_body(grade="고위험"))
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_qa_and_handoff_are_not_required(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(Path(tmp), "--from-stdin", stdin_text=pr_body())
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_legacy_report_passes_only_with_explicit_option(self) -> None:
        legacy_id = "BOOTSTRAP-004"
        content = """# Legacy report

## 작업 목적

기존 하네스 기록을 보존한다.

## 주요 결과

기존 검증 결과가 있다.

## 남은 위험

legacy 형식이다.
"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_report(root, content, legacy_id)
            result = run_validator(root, "--task-id", legacy_id, "--allow-legacy-without-grade")
            rejected = run_validator(root, "--task-id", legacy_id)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotEqual(rejected.returncode, 0)

    def test_conflicting_grade_fails(self) -> None:
        body = pr_body() + "\n작업 등급: 고위험\n"
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(Path(tmp), "--from-stdin", stdin_text=body)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("충돌하는 작업 등급", result.stderr)

    def test_conflicting_execution_type_fails(self) -> None:
        body = pr_body() + "\n실행 구분: 실제 운영 실행\n"
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(Path(tmp), "--from-stdin", stdin_text=body)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("충돌하는 실행 구분", result.stderr)

    def test_empty_placeholders_in_required_pr_fields_fail(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(
                Path(tmp),
                "--from-stdin",
                stdin_text=pr_body(purpose="<목적>", scope="-", validation="TBD", risk="[남은 위험]"),
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("비어 있음", result.stderr)

    def test_report_field_conflicts_with_pr_fail(self) -> None:
        report = MINIMAL_REPORT.replace("실행 구분: 저장소 변경", "실행 구분: 실제 운영 실행")
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_report(root, report)
            result = run_validator(root, "--from-stdin", stdin_text=pr_body(grade="고위험"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("실행 구분 불일치", result.stderr)

    def test_supported_task_id_families_are_detected(self) -> None:
        for task_id in ("AUTH-004", "FRONTEND-003", "PRODUCT-002", "HARNESS-LEAN-001"):
            with self.subTest(task_id=task_id), tempfile.TemporaryDirectory() as tmp:
                body = pr_body().replace(TASK_ID, task_id)
                result = run_validator(Path(tmp), "--from-stdin", stdin_text=body)
                self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()

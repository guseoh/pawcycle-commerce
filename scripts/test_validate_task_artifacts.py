#!/usr/bin/env python3
"""Regression tests for the lean task artifact validator."""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "validate-task-artifacts.py"
TASK_ID = "HARNESS-LEAN-999"


def pr_body(
    *,
    task_id: str = TASK_ID,
    grade: str = "일반",
    execution: str = "저장소 변경",
    purpose: str = "하네스 계약을 단순화한다.",
    included: str = "validator와 테스트를 변경한다.",
    excluded: str = "제품 코드는 변경하지 않는다.",
    result: str = "회귀 테스트를 통과했다.",
    not_run: str = "실제 운영 실행은 하지 않았다.",
    risk: str = "알려진 운영 위험 없음",
    recovery: str = "일반 revert PR",
) -> str:
    return f"""## 작업

- 작업 ID: {task_id}
- 작업 등급: {grade}
- 실행 구분: {execution}

## 변경

- 목적: {purpose}
- 포함 범위: {included}
- 제외 범위: {excluded}

## 검증

- 실행 결과: {result}
- 실패·미실행: {not_run}

## 위험

- 남은 위험: {risk}
- 복구 경계: {recovery}
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
    path.parent.mkdir(parents=True, exist_ok=True)
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

## 미실행 항목

장기 부하는 실행하지 않았다.

## 남은 위험

장기 부하에서만 드러나는 위험은 남아 있다.

## 위험과 제한

관찰 기간이 짧다.
"""


class ValidateTaskArtifactsTest(unittest.TestCase):
    def test_structurally_valid_task_id_families_pass(self) -> None:
        for task_id in (
            "AUTH-004",
            "OPS-OCI-002",
            "BACKEND-REFACTOR-004",
            "PERF-PH10-008",
            "HTTP-CLIENT-REFACTOR-001",
            "API-RECON-001",
            "MVP4-FE-004",
            "OPS-OBS-001A",
        ):
            with self.subTest(task_id=task_id), tempfile.TemporaryDirectory() as tmp:
                result = run_validator(
                    Path(tmp),
                    "--from-stdin",
                    stdin_text=pr_body(task_id=task_id),
                )
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_malformed_task_ids_do_not_partially_match(self) -> None:
        for task_id in (
            "ops-001",
            "OPS-01",
            "OPS--001",
            "OPS-001-EXTRA",
            "OPS-001abc",
            "OPS-001_extra",
            "OPS-١٢٣",
        ):
            with self.subTest(task_id=task_id), tempfile.TemporaryDirectory() as tmp:
                result = run_validator(
                    Path(tmp),
                    "--from-stdin",
                    stdin_text=pr_body(task_id=task_id),
                )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 ID", result.stderr)

    def test_repository_changes_do_not_require_reports(self) -> None:
        for grade in ("경량", "일반", "고위험"):
            with self.subTest(grade=grade), tempfile.TemporaryDirectory() as tmp:
                result = run_validator(
                    Path(tmp),
                    "--from-stdin",
                    stdin_text=pr_body(grade=grade),
                )
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_repository_preparation_alias_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(
                Path(tmp),
                "--from-stdin",
                stdin_text=pr_body(grade="고위험", execution="저장소 준비"),
            )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_pr_requires_four_decision_sections(self) -> None:
        cases = {
            "missing purpose": pr_body(purpose="<목적>"),
            "missing include": pr_body(included="<포함 범위>"),
            "missing exclude": pr_body(excluded="<제외 범위>"),
            "missing validation": pr_body(result="<결과>", not_run="<미실행>"),
            "missing risk": pr_body(risk="<위험>"),
            "missing recovery": pr_body(recovery="<복구>"),
        }
        for name, body in cases.items():
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                result = run_validator(Path(tmp), "--from-stdin", stdin_text=body)
            self.assertNotEqual(result.returncode, 0)

    def test_old_six_section_pr_aliases_remain_compatible(self) -> None:
        body = pr_body().replace("## 변경", "## 목적과 범위").replace("- 포함 범위:", "- 변경 범위:").replace("## 위험", "## 위험과 복구").replace("- 복구 경계:", "- 실패·rollback·revert 경계:")
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(Path(tmp), "--from-stdin", stdin_text=body)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_optional_repository_report_is_validated_when_present(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_report(root, MINIMAL_REPORT)
            result = run_validator(
                root,
                "--from-stdin",
                stdin_text=pr_body(grade="고위험"),
            )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_production_execution_requires_high_risk_and_report(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(
                Path(tmp),
                "--from-stdin",
                stdin_text=pr_body(grade="일반", execution="실제 운영 실행"),
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("고위험", result.stderr)

        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(
                Path(tmp),
                "--from-stdin",
                stdin_text=pr_body(grade="고위험", execution="실제 운영 실행"),
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("실제 운영 실행 보고서", result.stderr)

    def test_complete_production_execution_report_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_report(root, PRODUCTION_REPORT)
            result = run_validator(
                root,
                "--from-stdin",
                stdin_text=pr_body(grade="고위험", execution="실제 운영 실행"),
            )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_incomplete_production_report_fails(self) -> None:
        incomplete = PRODUCTION_REPORT.replace(
            "## 독립 확인\n\n운영자가 결과를 별도로 확인했다.\n",
            "",
        )
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_report(root, incomplete)
            result = run_validator(
                root,
                "--from-stdin",
                stdin_text=pr_body(grade="고위험", execution="실제 운영 실행"),
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("독립 확인", result.stderr)

    def test_legacy_report_mode_remains_available(self) -> None:
        legacy = f"""# Legacy report

- 작업 ID: {TASK_ID}

## 목적

과거 작업이다.

## 결과 또는 증거

기존 evidence다.

## 위험과 제한

현재 규칙 이전 산출물이다.
"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_report(root, legacy)
            result = run_validator(root, "--task-id", TASK_ID, "--allow-legacy-without-grade")
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()

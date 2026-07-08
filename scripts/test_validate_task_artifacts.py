#!/usr/bin/env python3
"""Tests for task artifact validation."""

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "validate-task-artifacts.py"
TASK_ID = "OPS-005"
VALID_IDS = (
    "BOOTSTRAP-001",
    "PS-002",
    "ARCH-001",
    "FOUNDATION-001",
    "BUG-001",
    "PERF-001",
    "OPS-001",
    "SEC-001",
    "AUTH-001",
    "DOMAIN-001",
    "API-001",
    "UX-001",
    "DATA-001",
)
INVALID_TEXTS = (
    "DOMAIN-01",
    "DOMAIN-0001",
    "DOMAIN001",
    "API-01",
    "API001",
    "UX-01",
    "UX-0001",
    "UX001",
    "DATA-01",
    "DATA-0001",
    "DATA001",
    "AUTH-01",
    "AUTH-0001",
    "AUTH001",
)


VALID_REPORT = f"""# {TASK_ID} SRE 작업 보고서

## 작업 정보

- 작업 ID: `{TASK_ID}`
- 역할: Platform/SRE

## 입력 문서

- `AGENTS.md`

## 변경 범위

- 하네스 문서와 검증 스크립트

## 변경하지 않은 범위

- 제품 코드

## 실행한 검증

| 명령 | 결과 |
| --- | --- |
| `py -3 -m py_compile scripts/validate-task-artifacts.py` | 통과 |

## 실행하지 못한 검증과 이유

- 없음.

## 남은 위험

- 없음.

## Git 결과

- 커밋 예정.

## PR 결과

- PR 생성 예정.
"""


VALID_HANDOFF = f"""# {TASK_ID} SRE to TL 인수인계

## 전달 목적

- 하네스 강화 결과 전달.

## 대상 역할

- Tech Lead

## 입력 문서

- `AGENTS.md`

## 사용 가능한 결과

- 강화된 검증 스크립트.

## 승인 필요 항목

- 없음.

## 검증 포인트

- 산출물 검증 결과를 확인한다.

## 중단 조건

- Secret 노출 의심.

## 남은 위험

- 없음.
"""


def write_artifacts(
    root: Path,
    *,
    report: str | None = VALID_REPORT,
    handoff: str | None = VALID_HANDOFF,
    task_id: str = TASK_ID,
) -> None:
    if report is not None:
        report_dir = root / "docs" / "reports" / task_id
        report_dir.mkdir(parents=True)
        (report_dir / "sre-report.md").write_text(report, encoding="utf-8")
    if handoff is not None:
        handoff_dir = root / "docs" / "handoffs" / task_id
        handoff_dir.mkdir(parents=True)
        (handoff_dir / "sre-to-tl.md").write_text(handoff, encoding="utf-8")


def run_validator(root: Path, *args: str, stdin_text: str | None = None) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["PYTHONUTF8"] = "1"
    return subprocess.run(
        [sys.executable, str(SCRIPT), "--root", str(root), *args],
        input=stdin_text,
        text=True,
        encoding="utf-8",
        env=env,
        capture_output=True,
        check=False,
    )


class ValidateTaskArtifactsTest(unittest.TestCase):
    def test_valid_report_and_handoff_pass(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_artifacts(root)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"task artifacts validated for {TASK_ID}", result.stdout)

    def test_missing_report_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_artifacts(root, report=None)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 보고서 Markdown 파일 없음", result.stderr)

    def test_missing_handoff_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_artifacts(root, handoff=None)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("역할 인수인계 Markdown 파일 없음", result.stderr)

    def test_missing_report_section_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = VALID_REPORT.replace("## Git 결과\n\n- 커밋 예정.\n\n", "")
            write_artifacts(root, report=report)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 보고서 필수 섹션 없음", result.stderr)

    def test_missing_handoff_section_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            handoff = VALID_HANDOFF.replace("## 중단 조건\n\n- Secret 노출 의심.\n\n", "")
            write_artifacts(root, handoff=handoff)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("역할 인수인계 필수 섹션 없음", result.stderr)

    def test_empty_validation_section_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = VALID_REPORT.replace(
                "## 실행한 검증\n\n| 명령 | 결과 |\n| --- | --- |\n| `py -3 -m py_compile scripts/validate-task-artifacts.py` | 통과 |\n\n",
                "## 실행한 검증\n\n",
            )
            write_artifacts(root, report=report)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("검증 결과 섹션이 비어 있음", result.stderr)

    def test_table_header_only_sections_fail(self) -> None:
        cases = (
            (
                "validation",
                VALID_REPORT.replace(
                    "## 실행한 검증\n\n| 명령 | 결과 |\n| --- | --- |\n| `py -3 -m py_compile scripts/validate-task-artifacts.py` | 통과 |\n\n",
                    "## 실행한 검증\n\n| 명령 | 결과 |\n| --- | --- |\n\n",
                ),
                "검증 결과 섹션이 비어 있음",
            ),
            (
                "not_run",
                VALID_REPORT.replace(
                    "## 실행하지 못한 검증과 이유\n\n- 없음.\n\n",
                    "## 실행하지 못한 검증과 이유\n\n| 명령 | 이유 |\n| --- | --- |\n\n",
                ),
                "실행하지 못한 검증 사유 없음",
            ),
            (
                "risk",
                VALID_REPORT.replace("## 남은 위험\n\n- 없음.\n\n", "## 남은 위험\n\n| 항목 | 내용 |\n| --- | --- |\n\n"),
                "위험/제한/차단/남은 위험 섹션이 비어 있음",
            ),
        )

        for name, report, expected_error in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                write_artifacts(root, report=report)

                result = run_validator(root, "--task-id", TASK_ID)

                self.assertNotEqual(result.returncode, 0)
                self.assertIn(expected_error, result.stderr)

    def test_table_with_data_row_counts_as_content(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = VALID_REPORT.replace(
                "## 남은 위험\n\n- 없음.\n\n",
                "## 남은 위험\n\n| 항목 | 내용 |\n| --- | --- |\n| 남은 위험 | 없음 |\n\n",
            )
            write_artifacts(root, report=report)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertEqual(result.returncode, 0, result.stderr)

    def test_not_run_section_without_reason_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = VALID_REPORT.replace("## 실행하지 못한 검증과 이유\n\n- 없음.\n\n", "## 실행하지 못한 검증과 이유\n\n-\n\n")
            write_artifacts(root, report=report)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("실행하지 못한 검증 사유 없음", result.stderr)

    def test_no_risk_section_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = VALID_REPORT.replace("## 남은 위험\n\n- 없음.\n\n", "")
            write_artifacts(root, report=report)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("위험/제한/차단/남은 위험", result.stderr)

    def test_rate_limit_heading_does_not_satisfy_risk_requirement(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = VALID_REPORT.replace("## 남은 위험\n\n- 없음.\n\n", "## Rate Limit 설정\n\n- API 제한값 문서화.\n\n")
            write_artifacts(root, report=report)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("위험/제한/차단/남은 위험", result.stderr)

    def test_from_stdin_detects_ops_005(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_artifacts(root)

            result = run_validator(root, "--from-stdin", stdin_text=f"PR body references {TASK_ID}\n")

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"task artifacts validated for {TASK_ID}", result.stdout)

    def test_from_stdin_detects_existing_task_prefixes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for task_id in VALID_IDS:
                write_artifacts(root, task_id=task_id)

                result = run_validator(root, "--from-stdin", stdin_text=f"PR body references {task_id}\n")

                self.assertEqual(result.returncode, 0, f"{task_id}: {result.stderr}")
                self.assertIn(f"task artifacts validated for {task_id}", result.stdout)

    def test_from_stdin_rejects_invalid_task_ids(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for invalid_text in INVALID_TEXTS:
                result = run_validator(root, "--from-stdin", stdin_text=f"PR body references {invalid_text}\n")

                self.assertNotEqual(result.returncode, 0, invalid_text)
                self.assertIn("작업 ID를 찾을 수 없음", result.stderr)


if __name__ == "__main__":
    unittest.main()

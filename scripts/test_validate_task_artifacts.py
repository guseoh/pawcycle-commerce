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


def graded_report(grade: str) -> str:
    return VALID_REPORT.replace(
        "- 역할: Platform/SRE\n",
        f"- 역할: Platform/SRE\n- 작업 등급: {grade}\n",
    ) + """\

## QA 필요 여부

- 등급과 변경 위험에 따라 판단함.

## QA 문서 경로 또는 생략 사유

- 제품 동작 변경이 없어 별도 QA 문서를 생략함.
"""


HIGH_RISK_REPORT = graded_report("고위험") + """\

## 명시적 승인 근거

- 사용자가 고위험 변경 범위를 승인함.

## 적용 전 검증

- 기준 상태 검증 통과.

## 적용 후 검증

- 변경 후 회귀 검증 통과.

## 독립 검증

- 별도 CI 검증 경로 통과.

## 복구·롤백 증거

- 변경 커밋 revert 후 기존 검증 경로 복구 가능.
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


def run_validator(
    root: Path,
    *args: str,
    stdin_text: str | None = None,
    allow_legacy_without_grade: bool = True,
) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["PYTHONUTF8"] = "1"
    command = [sys.executable, str(SCRIPT), "--root", str(root)]
    if allow_legacy_without_grade:
        command.append("--allow-legacy-without-grade")
    command.extend(args)
    return subprocess.run(
        command,
        input=stdin_text,
        text=True,
        encoding="utf-8",
        env=env,
        capture_output=True,
        check=False,
    )


class ValidateTaskArtifactsTest(unittest.TestCase):
    def test_legacy_report_and_handoff_pass_with_explicit_option(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_artifacts(root)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"task artifacts validated for {TASK_ID}", result.stdout)
            self.assertIn("명시적 legacy 옵션", result.stderr)

    def test_missing_grade_fails_without_legacy_option(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_artifacts(root)

            result = run_validator(
                root,
                "--from-stdin",
                stdin_text=f"작업 ID: {TASK_ID}\n",
                allow_legacy_without_grade=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 등급 필드가 없음", result.stderr)

    def test_lightweight_without_report_or_handoff_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(Path(tmp), "--task-id", TASK_ID, "--task-grade", "경량")

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"task artifacts validated for {TASK_ID} (경량)", result.stdout)

    def test_from_stdin_lightweight_without_artifacts_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(
                Path(tmp),
                "--from-stdin",
                stdin_text=f"작업 ID: {TASK_ID}\n작업 등급: 경량\n",
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"task artifacts validated for {TASK_ID} (경량)", result.stdout)

    def test_standard_report_with_explicit_handoff_omission_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = graded_report("일반") + """\

## 인수인계 생략

- 다음 역할이 실제로 사용할 결과가 없어 생략함.
"""
            write_artifacts(root, report=report, handoff=None)

            result = run_validator(root, "--task-id", TASK_ID, "--task-grade", "일반")

            self.assertEqual(result.returncode, 0, result.stderr)

    def test_standard_without_report_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(Path(tmp), "--task-id", TASK_ID, "--task-grade", "일반")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 보고서 Markdown 파일 없음", result.stderr)

    def test_standard_without_handoff_or_omission_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_artifacts(root, report=graded_report("일반"), handoff=None)

            result = run_validator(root, "--task-id", TASK_ID, "--task-grade", "일반")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("역할 인수인계 Markdown 파일 없음", result.stderr)

    def test_handoff_omission_template_placeholder_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = graded_report("일반") + """\

## 인수인계 생략

<!-- 확정된 소비자가 없을 때 구체적 사유로 교체합니다. -->

- <구체적 생략 사유>
"""
            write_artifacts(root, report=report, handoff=None)

            result = run_validator(root, "--task-id", TASK_ID, "--task-grade", "일반")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 보고서 인수인계 생략 사유 없음", result.stderr)

    def test_standard_qa_sections_require_actual_content(self) -> None:
        cases = (
            ("## QA 필요 여부\n\n- 등급과 변경 위험에 따라 판단함.\n\n", "## QA 필요 여부\n\n"),
            (
                "## QA 문서 경로 또는 생략 사유\n\n- 제품 동작 변경이 없어 별도 QA 문서를 생략함.\n",
                "## QA 문서 경로 또는 생략 사유\n\n- <QA 문서 경로 또는 구체적 생략 사유>\n",
            ),
        )
        for original, replacement in cases:
            with self.subTest(section=original.splitlines()[0]), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                write_artifacts(root, report=graded_report("일반").replace(original, replacement))

                result = run_validator(root, "--task-id", TASK_ID, "--task-grade", "일반")

                self.assertNotEqual(result.returncode, 0)
                self.assertIn("일반 작업 보고서 필수 증거 섹션이 비어 있음", result.stderr)

    def test_high_risk_report_with_required_evidence_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_artifacts(root, report=HIGH_RISK_REPORT)

            result = run_validator(root, "--task-id", TASK_ID, "--task-grade", "고위험")

            self.assertEqual(result.returncode, 0, result.stderr)

    def test_high_risk_required_evidence_is_enforced(self) -> None:
        headings = (
            "명시적 승인 근거",
            "적용 전 검증",
            "적용 후 검증",
            "독립 검증",
            "복구·롤백 증거",
        )
        for heading in headings:
            with self.subTest(heading=heading), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                report = HIGH_RISK_REPORT.replace(f"## {heading}", "## 기타 증거")
                write_artifacts(root, report=report)

                result = run_validator(root, "--task-id", TASK_ID, "--task-grade", "고위험")

                self.assertNotEqual(result.returncode, 0)
                self.assertIn("고위험 작업 보고서 필수 증거 섹션 없음", result.stderr)

    def test_high_risk_empty_evidence_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = HIGH_RISK_REPORT.replace(
                "## 독립 검증\n\n- 별도 CI 검증 경로 통과.\n\n",
                "## 독립 검증\n\n",
            )
            write_artifacts(root, report=report)

            result = run_validator(root, "--task-id", TASK_ID, "--task-grade", "고위험")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("고위험 작업 보고서 필수 증거 섹션이 비어 있음", result.stderr)

    def test_explicit_grade_must_match_report(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_artifacts(root, report=graded_report("일반"))

            result = run_validator(root, "--task-id", TASK_ID, "--task-grade", "고위험")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 보고서 작업 등급 불일치", result.stderr)

    def test_invalid_grade_field_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(
                Path(tmp),
                "--from-stdin",
                stdin_text=f"작업 ID: {TASK_ID}\n작업 등급: 초고위험\n",
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 등급 필드 값", result.stderr)

    def test_conflicting_grade_fields_fail(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(
                Path(tmp),
                "--from-stdin",
                stdin_text=f"작업 ID: {TASK_ID}\n작업 등급: 경량\n작업 등급: 고위험\n",
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("서로 충돌하는 작업 등급 필드가 있음", result.stderr)

    def test_invalid_cli_task_grade_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_validator(Path(tmp), "--task-id", TASK_ID, "--task-grade", "초고위험")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 등급은 경량, 일반 또는 고위험이어야 함", result.stderr)

    def test_harness_lean_task_id_is_detected(self) -> None:
        task_id = "HARNESS-LEAN-001"
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_artifacts(root, report=graded_report("일반"), task_id=task_id)

            result = run_validator(root, "--from-stdin", stdin_text=f"작업 ID: {task_id}\n작업 등급: 일반\n")

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(task_id, result.stdout)

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

    def test_explicit_handoff_omission_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = VALID_REPORT + """\

## 인수인계 생략

- 다음 역할이 확정되지 않아 형식적인 인수인계를 작성하지 않는다.
"""
            write_artifacts(root, report=report, handoff=None)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"task artifacts validated for {TASK_ID}", result.stdout)

    def test_empty_handoff_omission_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = VALID_REPORT + "\n## 인수인계 생략\n"
            write_artifacts(root, report=report, handoff=None)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 보고서 인수인계 생략 사유 없음", result.stderr)
            self.assertIn("역할 인수인계 Markdown 파일 없음", result.stderr)

    def test_mixed_handoff_omission_sections_fail(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = VALID_REPORT + """\

## 인수인계 생략

- 다음 역할이 확정되지 않아 형식적인 인수인계를 작성하지 않는다.

## Handoff omission
"""
            write_artifacts(root, report=report, handoff=None)

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("작업 보고서 인수인계 생략 사유 없음", result.stderr)
            self.assertIn("역할 인수인계 Markdown 파일 없음", result.stderr)

    def test_handoff_omission_denial_fails(self) -> None:
        for denial in (
            "생략하지 않음.",
            "인수인계를 생략 안 한다.",
            "해당 없음.",
            "Not omitted.",
        ):
            with self.subTest(denial=denial), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                report = VALID_REPORT + f"\n## 인수인계 생략\n\n- {denial}\n"
                write_artifacts(root, report=report, handoff=None)

                result = run_validator(root, "--task-id", TASK_ID)

                self.assertNotEqual(result.returncode, 0)
                self.assertIn("작업 보고서 인수인계 생략 사유 없음", result.stderr)
                self.assertIn("역할 인수인계 Markdown 파일 없음", result.stderr)

    def test_each_report_requires_handoff_omission(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report = VALID_REPORT + """\

## 인수인계 생략

- 다음 역할이 확정되지 않아 형식적인 인수인계를 작성하지 않는다.
"""
            write_artifacts(root, report=report, handoff=None)
            report_dir = root / "docs" / "reports" / TASK_ID
            (report_dir / "qa-report.md").write_text(VALID_REPORT, encoding="utf-8")

            result = run_validator(root, "--task-id", TASK_ID)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn(
                f"작업 보고서 인수인계 생략 사유 없음: {report_dir / 'qa-report.md'}",
                result.stderr,
            )
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

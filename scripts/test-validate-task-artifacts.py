#!/usr/bin/env python3
"""Validate task artifact task ID detection with temporary fixtures."""

from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "validate-task-artifacts.py"

VALID_IDS = [
    "BOOTSTRAP-001",
    "PS-002",
    "ARCH-001",
    "FOUNDATION-001",
    "BUG-001",
    "PERF-001",
    "OPS-001",
    "SEC-001",
    "AUTH-001",
    "AUTH-999",
    "DOMAIN-001",
    "DOMAIN-999",
    "API-001",
    "API-999",
    "UX-001",
    "UX-999",
    "DATA-001",
    "DATA-999",
]

INVALID_TEXTS = [
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
]


def write_artifacts(root: Path, task_id: str) -> None:
    report_dir = root / "docs" / "reports" / task_id
    handoff_dir = root / "docs" / "handoffs" / task_id
    report_dir.mkdir(parents=True)
    handoff_dir.mkdir(parents=True)
    (report_dir / "report.md").write_text(f"# {task_id} report\n", encoding="utf-8")
    (handoff_dir / "handoff.md").write_text(f"# {task_id} handoff\n", encoding="utf-8")


def run_validator(root: Path, stdin_text: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(SCRIPT), "--from-stdin", "--root", str(root)],
        input=stdin_text,
        text=True,
        capture_output=True,
        check=False,
    )


def main() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for task_id in VALID_IDS:
            write_artifacts(root, task_id)
            result = run_validator(root, f"PR body references {task_id}\n")
            if result.returncode != 0:
                print(f"{task_id} 감지 실패: {result.stderr}", file=sys.stderr)
                return 1
            if f"task artifacts validated for {task_id}" not in result.stdout:
                print(f"{task_id} 검증 출력 오류: {result.stdout}", file=sys.stderr)
                return 1

        for invalid_text in INVALID_TEXTS:
            result = run_validator(root, f"PR body references {invalid_text}\n")
            if result.returncode == 0:
                print(f"잘못된 작업 ID가 감지됨: {invalid_text}", file=sys.stderr)
                return 1

        result = run_validator(root, "DOMAIN-001\n")
        if result.returncode != 0:
            print("DOMAIN-001 경로 검사 실패", file=sys.stderr)
            return 1

        result = run_validator(root, "API-001\n")
        if result.returncode != 0:
            print("API-001 경로 검사 실패", file=sys.stderr)
            return 1

        result = run_validator(root, "UX-001\n")
        if result.returncode != 0:
            print("UX-001 경로 검사 실패", file=sys.stderr)
            return 1

        result = run_validator(root, "DATA-001\n")
        if result.returncode != 0:
            print("DATA-001 경로 검사 실패", file=sys.stderr)
            return 1

        result = run_validator(root, "AUTH-001\n")
        if result.returncode != 0:
            print("AUTH-001 경로 검사 실패", file=sys.stderr)
            return 1

    print("Task artifact validator fixture OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

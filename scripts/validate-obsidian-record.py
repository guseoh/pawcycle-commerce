#!/usr/bin/env python3
"""Validate Obsidian PR record generation with a local fixture."""

from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".github" / "scripts" / "record-merged-pr.py"
FIXTURE = ROOT / ".github" / "fixtures" / "obsidian" / "merged-pr.json"


def load_record_module():
    spec = importlib.util.spec_from_file_location("record_merged_pr", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError("병합 PR 기록 모듈을 불러올 수 없습니다.")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main() -> int:
    if not FIXTURE.exists():
        print("Obsidian fixture가 없습니다.", file=sys.stderr)
        return 1

    record = load_record_module()
    for task_id in ("AUTH-004", "FRONTEND-003", "PRODUCT-002", "HARNESS-LEAN-001"):
        if record.task_id_from_text(f"작업 ID: {task_id}") != task_id:
            print(f"병합 PR 작업 ID 추출 실패: {task_id}", file=sys.stderr)
            return 1

    with tempfile.TemporaryDirectory() as tmp:
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--event-path", str(FIXTURE), "--output-root", tmp],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        )
        output_path = Path(result.stdout.strip())
        if not output_path.exists():
            output_path = Path(tmp) / output_path
        if not output_path.exists():
            print("PR 기록 파일이 생성되지 않았습니다.", file=sys.stderr)
            return 1
        try:
            output_path.name.encode("ascii")
        except UnicodeEncodeError:
            print("PR 기록 파일명은 영어와 숫자 기반이어야 합니다.", file=sys.stderr)
            return 1
        content = output_path.read_text(encoding="utf-8")
        required = [
            "type: pull-request",
            "repository: guseoh/pawcycle-commerce",
            "status: merged",
            "taskId: HARNESS-LEAN-001",
            "# PR #12",
            "## 작업 목적",
            "## 변경 파일",
            "## GitHub 링크",
        ]
        missing = [item for item in required if item not in content]
        if missing:
            print("누락된 항목: " + ", ".join(missing), file=sys.stderr)
            return 1
        if "discord.com/api/webhooks" in content or "password=" in content.lower():
            print("기록 파일에 Secret 후보가 포함되었습니다.", file=sys.stderr)
            return 1

    print("Obsidian PR record fixture OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

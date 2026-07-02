#!/usr/bin/env python3
"""Validate Obsidian PR record generation with a local fixture."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".github" / "scripts" / "record-merged-pr.py"
FIXTURE = ROOT / ".github" / "fixtures" / "obsidian" / "merged-pr.json"


def main() -> int:
    if not FIXTURE.exists():
        print("Obsidian fixture가 없습니다.", file=sys.stderr)
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

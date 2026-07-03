#!/usr/bin/env python3
"""Validate task report and handoff artifacts referenced by a pull request."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

TASK_ID_PREFIXES = (
    "BOOTSTRAP",
    "PS",
    "ARCH",
    "FOUNDATION",
    "BUG",
    "PERF",
    "OPS",
    "SEC",
    "DOMAIN",
    "API",
)
TASK_ID_RE = re.compile(rf"\b({'|'.join(TASK_ID_PREFIXES)})-\d{{3}}\b")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--task-id", help="Explicit task ID to validate.")
    parser.add_argument(
        "--from-stdin",
        action="store_true",
        help="Read PR title and body text from standard input and detect the task ID.",
    )
    parser.add_argument(
        "--root",
        default=".",
        help="Repository root. Defaults to the current directory.",
    )
    return parser.parse_args()


def find_task_id(args: argparse.Namespace) -> str:
    if args.task_id:
        return args.task_id

    if not args.from_stdin:
        raise SystemExit("작업 ID를 찾으려면 --task-id 또는 --from-stdin이 필요함")

    text = sys.stdin.read()
    match = TASK_ID_RE.search(text)
    if not match:
        raise SystemExit("PR 제목 또는 본문에서 작업 ID를 찾을 수 없음")
    return match.group(0)


def has_markdown_file(path: Path) -> bool:
    return path.is_dir() and any(child.is_file() and child.suffix == ".md" for child in path.iterdir())


def main() -> int:
    args = parse_args()
    task_id = find_task_id(args)
    root = Path(args.root)

    report_dir = root / "docs" / "reports" / task_id
    handoff_dir = root / "docs" / "handoffs" / task_id

    failures: list[str] = []
    if not has_markdown_file(report_dir):
        failures.append(f"작업 보고서 Markdown 파일 없음: {report_dir}")
    if not has_markdown_file(handoff_dir):
        failures.append(f"역할 인수인계 Markdown 파일 없음: {handoff_dir}")

    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        return 1

    print(f"task artifacts validated for {task_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Classify changed paths into Repository Validation component groups."""

from __future__ import annotations

import argparse
import sys
from pathlib import PurePosixPath

GROUPS = ("harness", "backend", "frontend", "production")
COMMON_HARNESS_FILES = {
    "AGENTS.md",
    ".github/pull_request_template.md",
    ".github/workflows/validate-conventions.yml",
    "scripts/validate-task-artifacts.py",
    "scripts/test_validate_task_artifacts.py",
    "scripts/classify-validation-changes.py",
    "scripts/test_validate_conventions_workflow.py",
    "docs/runbook/lean-harness.md",
}


def normalized(path: str) -> str:
    value = PurePosixPath(path.strip().replace("\\", "/")).as_posix()
    return value[2:] if value.startswith("./") else value


def classify(paths: list[str]) -> dict[str, bool]:
    result = {group: False for group in GROUPS}
    for raw_path in paths:
        path = normalized(raw_path)
        if not path or path == ".":
            continue
        if (
            path in COMMON_HARNESS_FILES
            or path.startswith(".agents/skills/")
            or path.startswith("docs/roles/")
            or path.startswith(".github/workflows/")
        ):
            return {group: True for group in GROUPS}
        if path.startswith("backend/"):
            result["backend"] = True
        elif path.startswith("frontend/"):
            result["frontend"] = True
        elif path.startswith("infra/production/"):
            result["production"] = True
        if (
            path.endswith(".md")
            or path.startswith("docs/")
            or path.startswith("scripts/")
            or path.startswith(".github/scripts/")
            or path.startswith(".github/fixtures/")
        ):
            result["harness"] = True
    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    paths = args.paths or sys.stdin.read().splitlines()
    for group, enabled in classify(paths).items():
        print(f"{group}={'true' if enabled else 'false'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Classify changed paths into Repository Validation component groups."""

from __future__ import annotations

import argparse
import sys
from pathlib import PurePosixPath

GROUPS = ("harness", "backend", "frontend", "production")
ALL_COMPONENT_FILES = {
    "AGENTS.md",
    ".github/pull_request_template.md",
    "scripts/validate-task-artifacts.py",
    "scripts/test_validate_task_artifacts.py",
    "scripts/classify-validation-changes.py",
    "scripts/test_validate_conventions_workflow.py",
    "docs/runbook/lean-harness.md",
}
HARNESS_FILES = {
    ".coderabbit.yaml",
    "CONTRIBUTING.md",
    "docs/reports/README.md",
    "docs/reports/task-report-template.md",
}
ROLE_COMPONENTS = {
    "backend-engineer": ("harness", "backend"),
    "frontend-engineer": ("harness", "frontend"),
    "platform-sre": ("harness", "production"),
    "product-planner": ("harness",),
    "ux-designer": ("harness",),
    "qa-engineer": ("harness",),
    "tech-lead": ("harness",),
}


def normalized(path: str) -> str:
    value = PurePosixPath(path.strip().replace("\\", "/")).as_posix()
    return value[2:] if value.startswith("./") else value


def role_components(path: str) -> tuple[str, ...] | None:
    for role, components in ROLE_COMPONENTS.items():
        if path == f"docs/roles/{role}.md" or path.startswith(f".agents/skills/{role}/"):
            return components
    path_role = {
        "backend/AGENTS.md": "backend-engineer",
        "frontend/AGENTS.md": "frontend-engineer",
        "infra/AGENTS.md": "platform-sre",
        "qa/AGENTS.md": "qa-engineer",
    }.get(path)
    return ROLE_COMPONENTS.get(path_role) if path_role else None


def classify(paths: list[str]) -> dict[str, bool]:
    result = {group: False for group in GROUPS}
    unknown: list[str] = []
    for raw_path in paths:
        path = normalized(raw_path)
        if not path or path == ".":
            continue
        if path in ALL_COMPONENT_FILES or path.startswith(".github/workflows/"):
            return {group: True for group in GROUPS}
        components = role_components(path)
        if components:
            for component in components:
                result[component] = True
        elif path.startswith("infra/local-integration/"):
            return {group: True for group in GROUPS}
        elif path.startswith("backend/"):
            result["backend"] = True
        elif path.startswith("frontend/"):
            result["frontend"] = True
        elif path.startswith("infra/production/") or path.startswith("infra/production-observability/"):
            result["production"] = True
        elif (
            path in HARNESS_FILES
            or path.startswith(".githooks/")
            or path.startswith("docs/")
            or path.startswith("scripts/")
            or path.startswith(".github/scripts/")
            or path.startswith(".github/fixtures/")
        ):
            result["harness"] = True
        else:
            unknown.append(path)
    if unknown:
        print(f"미분류 변경 경로: {', '.join(unknown)}", file=sys.stderr)
        return {group: True for group in GROUPS}
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

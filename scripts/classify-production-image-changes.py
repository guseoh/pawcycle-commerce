#!/usr/bin/env python3
"""Fail-safe GHCR publish decision for paths in a main-push commit diff."""

from __future__ import annotations

import sys

NON_IMAGE_FILES = frozenset({
    "AGENTS.md", "README.md", "CONTRIBUTING.md", ".coderabbit.yaml",
    ".github/pull_request_template.md", "backend/AGENTS.md",
    "frontend/AGENTS.md", "frontend/README.md", "infra/AGENTS.md",
    "qa/AGENTS.md", "frontend/scripts/run-tests.mjs",
    "frontend/scripts/visual-fixture.mjs",
})
NON_IMAGE_PREFIXES = (
    "docs/", ".agents/", ".githooks/", ".github/scripts/",
    ".github/fixtures/", "scripts/", "qa/", "infra/performance/",
    "infra/local-integration/", "backend/src/test/",
)
PUBLISH_WORKFLOW = ".github/workflows/publish-production-images.yml"


def should_publish(paths: list[str]) -> bool:
    """Publish when a changed path may affect an image or is unclassified."""
    for raw in paths:
        path = raw.strip()
        if not path:
            continue
        if path in NON_IMAGE_FILES or path.startswith(NON_IMAGE_PREFIXES):
            continue
        if path.startswith(".github/workflows/") and path != PUBLISH_WORKFLOW:
            continue
        return True
    return False


def main() -> None:
    print("publish=" + ("true" if should_publish(sys.stdin.read().splitlines()) else "false"))


if __name__ == "__main__":
    main()

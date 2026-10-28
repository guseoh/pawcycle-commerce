#!/usr/bin/env python3
"""Fail-safe GHCR publish decision for paths in a main-push commit diff."""

from __future__ import annotations

import sys
import re

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
BUILD_FILES = frozenset({PUBLISH_WORKFLOW, ".dockerignore",
    "infra/production/backend.Dockerfile", "infra/production/frontend.Dockerfile",
    "infra/production/backend.Dockerfile.dockerignore", "infra/production/frontend.Dockerfile.dockerignore"})
RUNTIME_FILES = frozenset({"infra/production/compose.yaml", "infra/production/nginx.https.conf"})


def classify(paths: list[str]) -> dict:
    """Build/deploy intent is independent of permission to auto-activate.

    Risk flags require a policy-approved semantic verifier; no path is SAFE.
    Unknown inputs still build, but never qualify for automatic deployment.
    """
    publish, deploy, risks = False, False, set()
    for path in filter(None, (p.strip() for p in paths)):
        if path in NON_IMAGE_FILES or path.startswith(NON_IMAGE_PREFIXES):
            continue
        if path.startswith("frontend/src/") and re.search(r"\.(?:test|spec)\.[cm]?[jt]sx?$", path):
            continue
        if path in BUILD_FILES or path.startswith(("backend/", "frontend/")):
            publish = deploy = True
            risks.add("application")
            if re.search(r"migration|(?:^|/)(?:db|auth|security|payment|inventory|subscription|commerce)(?:/|\.)", path, re.I):
                risks.add("sensitive")
        elif path in RUNTIME_FILES:
            deploy = True
            risks.add("runtime")
        elif path.startswith(".github/workflows/") or (
                path.startswith("infra/production/") and
                (path.endswith((".py", ".sh", ".service", ".timer")) or
                 path == "infra/production/oci-cd-policy.example.json")):
            # Release tools, tests and systemd are installed separately, never
            # copied into application images or executed from fetched source.
            continue
        else:
            publish = deploy = True
            risks.add("unknown")
    return {"publish": publish, "deploy": deploy, "risks": sorted(risks)}


def should_publish(paths: list[str]) -> bool:
    """Publish when a changed path may affect an image or is unclassified."""
    return classify(paths)["publish"]


def main() -> None:
    print("publish=" + ("true" if should_publish(sys.stdin.read().splitlines()) else "false"))


if __name__ == "__main__":
    main()

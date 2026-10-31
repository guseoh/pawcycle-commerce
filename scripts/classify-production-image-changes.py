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


def image_components(paths: list[str]) -> dict[str, bool]:
    """Select independent images; shared/unclassified build inputs select both."""
    result = {"backend": False, "frontend": False}
    for path in paths:
        if not classify([path])["publish"]:
            continue
        for role in result:
            own = path.startswith(role + "/") or path in {
                "infra/production/" + role + ".Dockerfile",
                "infra/production/" + role + ".Dockerfile.dockerignore",
            }
            other = "frontend" if role == "backend" else "backend"
            foreign = path.startswith(other + "/") or path in {
                "infra/production/" + other + ".Dockerfile",
                "infra/production/" + other + ".Dockerfile.dockerignore",
            }
            result[role] |= own or not foreign
    return result


def application_blockers(paths: list[str]) -> list[str]:
    """Mechanical exclusions, not a proof that arbitrary data writes reverse.

    Non-obvious destructive code changes must use the manual-deploy commit
    trailer during review. Neither mechanism grants migration permission.
    """
    return [p for p in paths if classify([p])["deploy"] and (
        set(classify([p])["risks"]) & {"runtime", "unknown"}
        or p == PUBLISH_WORKFLOW
        or p.startswith(("backend/src/main/resources/", "backend/.env", "frontend/.env"))
        or p.lower().endswith(".sql")
        or re.search(r"(?:^|/)(?:migration|migrations|backfill|data-repair|data-fix)(?:/|\.)", p, re.I)
    )]


def main() -> None:
    paths = sys.stdin.read().splitlines()
    result = {"publish": should_publish(paths)}
    if "--components" in sys.argv[1:]:
        result.update(image_components(paths))
    for name, enabled in result.items():
        print(name + "=" + ("true" if enabled else "false"))


if __name__ == "__main__":
    main()

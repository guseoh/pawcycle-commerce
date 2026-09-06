#!/usr/bin/env python3
"""Validate the provider-neutral Production repository boundary after AWS retirement."""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
PRODUCTION = ROOT / "infra" / "production"
WORKFLOWS = ROOT / ".github" / "workflows"
RUNBOOKS = ROOT / "docs" / "runbook"
ARCHITECTURE = ROOT / "docs" / "architecture" / "production-operations-overview.md"
VALIDATOR = Path(__file__).resolve()

RETIRED_PATHS = (
    WORKFLOWS / "production-deploy.yml",
    WORKFLOWS / "validate-rds-automation-preflight.yml",
    WORKFLOWS / "validate-same-sha-runtime-activation.yml",
    PRODUCTION / "materialize-ssm-env.sh",
    PRODUCTION / "ssm-parameters.env.example",
    PRODUCTION / "pawcycle-production-deploy-ssm-document.json",
    PRODUCTION / "validate-production-ssm-document.py",
    PRODUCTION / "ec2-status-check-alarm-common.sh",
    PRODUCTION / "create-ec2-status-check-alarm.sh",
    PRODUCTION / "cleanup-ec2-status-check-alarm.sh",
    PRODUCTION / "test-ec2-status-check-alarm.sh",
    PRODUCTION / "rds-read-only-preflight.sh",
    PRODUCTION / "rds-transition-gate.sh",
    PRODUCTION / "test-rds-readiness.sh",
    PRODUCTION / "db-backup-restore.sh",
    PRODUCTION / "test-db-backup-restore.sh",
    PRODUCTION / "release-common.sh",
    PRODUCTION / "deploy.sh",
    PRODUCTION / "rollback.sh",
    PRODUCTION / "https.sh",
    PRODUCTION / "production-db-restore.sh",
    PRODUCTION / "subscription-automation-control.sh",
    PRODUCTION / "subscription-automation-preflight.sh",
    PRODUCTION / "test-subscription-automation-preflight-datasource.sh",
    PRODUCTION / "test-production-scripts.sh",
    PRODUCTION / "test-rollback-control-compatibility.sh",
    PRODUCTION / "test-same-sha-runtime-activation.sh",
    PRODUCTION / "release.env.example",
    RUNBOOKS / "OPS-009-aws-operations-foundation.md",
    RUNBOOKS / "OPS-010-production-single-release.md",
    RUNBOOKS / "OPS-011-production-https.md",
    RUNBOOKS / "OPS-013-production-db-backup-restore.md",
    RUNBOOKS / "OPS-015-ec2-status-check-alarm.md",
    RUNBOOKS / "OPS-025-production-db-restore.md",
    RUNBOOKS / "OPS-AUTO-007-production-ssm-document-rollback.md",
    RUNBOOKS / "OPS-DB-002-rds-migration-cutover.md",
    RUNBOOKS / "SUB-AUTO-002-production-subscription-automation.md",
)

AWS_EXECUTION_PATTERNS = (
    re.compile(r"aws-actions/configure-aws-credentials", re.I),
    re.compile(r"\baws\s+(?:ssm|ec2|rds|s3|cloudwatch|sns)\b", re.I),
    re.compile(r"\.rds\.amazonaws\.com", re.I),
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ERROR: {message}")


def active_text_files(root: Path):
    for path in root.rglob("*"):
        if (
            path.is_file()
            and path.resolve() != VALIDATOR
            and path.suffix in {".sh", ".py", ".yml", ".yaml", ".json", ".env"}
        ):
            yield path


def main() -> None:
    for path in RETIRED_PATHS:
        require(not path.exists(), f"retired AWS/Production artifact is still active: {path.relative_to(ROOT)}")

    for root in (WORKFLOWS, PRODUCTION):
        for path in active_text_files(root):
            text = path.read_text(encoding="utf-8")
            for pattern in AWS_EXECUTION_PATTERNS:
                require(
                    pattern.search(text) is None,
                    f"active AWS execution contract remains in {path.relative_to(ROOT)}: {pattern.pattern}",
                )

    publish = (WORKFLOWS / "publish-production-images.yml").read_text(encoding="utf-8")
    require("workflow_dispatch:" in publish, "production image publication must remain explicitly dispatchable")
    require("branches: [main]" in publish or "branches:\n      - main" in publish, "production images must remain main-bound")
    require("production-deploy.yml" not in publish, "image publication must not dispatch a retired deployment workflow")

    readiness = (WORKFLOWS / "production-release-readiness.yml").read_text(encoding="utf-8")
    require("There is no active Production deployment target." in readiness, "readiness workflow must state that no Production target is active")
    require("zero-downtime deployment remain DEFER" in readiness, "readiness workflow must keep zero-downtime deployment deferred")

    architecture = ARCHITECTURE.read_text(encoding="utf-8")
    for marker in (
        "Production deployment target: **없음**",
        "CD: **DEFER**",
        "무중단 배포: **DEFER**",
        "Production Verified가 아니다",
    ):
        require(marker in architecture, f"current Production status marker is missing: {marker}")

    print("Production contract validation passed")


if __name__ == "__main__":
    main()

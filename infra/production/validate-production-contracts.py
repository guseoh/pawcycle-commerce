#!/usr/bin/env python3

"""Repository-only guardrails for the active OCI production contract."""

from __future__ import annotations

import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
PRODUCTION = ROOT / "infra" / "production"
WORKFLOWS = ROOT / ".github" / "workflows"
PUBLISH_WORKFLOW = WORKFLOWS / "publish-production-images.yml"
READINESS_WORKFLOW = WORKFLOWS / "production-release-readiness.yml"
VALIDATION_WORKFLOW = WORKFLOWS / "validate-conventions.yml"
EXTERNAL_PREFLIGHT_WORKFLOW = WORKFLOWS / "validate-external-mysql-automation-preflight.yml"
COMPOSE = PRODUCTION / "compose.yaml"
MYSQL_IMAGE = "mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"
PROXY_IMAGE = "nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ERROR: {message}")


def read(path: Path) -> str:
    require(path.is_file(), f"required file is missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def pinned_actions(source: str) -> None:
    for revision in re.findall(r"uses:\s+[^\s]+@([^\s]+)", source):
        require(bool(re.fullmatch(r"[0-9a-f]{40}", revision)), "workflow actions must use immutable commit pins")


def compose_config() -> dict[str, object] | None:
    """Resolve Compose when a local engine is available; static checks remain below."""
    if shutil.which("docker") is None:
        return None
    with tempfile.TemporaryDirectory(prefix="ops-oci-compose-") as directory:
        env_file = Path(directory) / "backend.env"
        env_file.write_text(
            "PAWCYCLE_DATASOURCE_HOST='db.example.com'\n"
            "PAWCYCLE_DATASOURCE_PORT='3306'\n"
            "PAWCYCLE_DATASOURCE_DATABASE='pawcycle'\n"
            "PAWCYCLE_DATASOURCE_SSL_MODE='REQUIRED'\n"
            "SPRING_DATASOURCE_URL='jdbc:mysql://db.example.com:3306/pawcycle?sslMode=REQUIRED&serverTimezone=UTC'\n"
            "SPRING_DATASOURCE_USERNAME='validator'\n"
            "SPRING_DATASOURCE_PASSWORD='repository-validation-only'\n"
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'\n"
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='7'\n"
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='12345'\n",
            encoding="utf-8",
        )
        environment = os.environ | {
            "RELEASE_SHA": "0" * 40,
            "BACKEND_IMAGE": "ghcr.io/example/pawcycle-commerce-backend",
            "FRONTEND_IMAGE": "ghcr.io/example/pawcycle-commerce-frontend",
            "PAWCYCLE_BACKEND_ENV_FILE": str(env_file),
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED": "false",
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE": "7",
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS": "12345",
            "PAWCYCLE_NGINX_CONFIG": str(PRODUCTION / "nginx.conf"),
        }
        try:
            completed = subprocess.run(
                ["docker", "compose", "--file", str(COMPOSE), "config", "--format", "json"],
                cwd=ROOT,
                env=environment,
                check=True,
                capture_output=True,
                text=True,
                encoding="utf-8",
            )
        except (OSError, subprocess.CalledProcessError):
            return None
        return json.loads(completed.stdout)


def validate_compose() -> None:
    source = read(COMPOSE)
    require("services:\n  backend:" in source, "Compose must start with the Backend service")
    require(not re.search(r"^\s{2}mysql:\s*$", source, re.MULTILINE), "active Compose must not own a local database")
    require("mysql-data" not in source and "PAWCYCLE_MYSQL_" not in source, "local database volume/env contract remains")
    require("depends_on:\n      mysql:" not in source, "Backend must not depend on a local database service")
    require("networks:\n  edge:" in source and "  database-egress:" in source, "Compose network declarations are incomplete")
    require("internal: true" in source, "app network must remain internal")
    require("--network" not in source, "Compose must not embed an execution-time network override")
    require("image: nginx:1.30.3-alpine3.23@sha256:" in source, "proxy image must be immutable")
    for service, memory, cpus, pids in (
        ("backend", "640m", "0.75", "256"),
        ("frontend", "256m", "0.35", "128"),
        ("proxy", "128m", "0.20", "64"),
    ):
        block = re.search(rf"(?ms)^  {service}:\n(.*?)(?=^  [a-z-]+:|^volumes:|^networks:|\Z)", source)
        require(block is not None, f"{service} service block is missing")
        body = block.group(1)
        require(f"mem_limit: {memory}" in body and f"cpus: {cpus}" in body and f"pids_limit: {pids}" in body, f"{service} resource limits changed unexpectedly")
        require("healthcheck:" in body and "restart: unless-stopped" in body and "read_only: true" in body, f"{service} safety contract is incomplete")
        require("no-new-privileges:true" in body and "logging: *default-logging" in body, f"{service} security/logging contract is incomplete")
    require("      - app\n      - database-egress" in source, "Backend must use app and database-egress only")
    require("networks:\n      - app\n    mem_limit: 256m" in source, "Frontend must use app only")
    require("      - edge\n      - app\n    mem_limit: 128m" in source, "Proxy must use edge and app only")
    config = compose_config()
    if config is None:
        return
    services = config.get("services", {})
    require(set(services) == {"backend", "frontend", "proxy"}, "resolved Compose services are not exact")
    require(set(config.get("volumes", {})) == {"certbot-webroot", "letsencrypt"}, "resolved Compose volumes are not exact")
    require("data" not in config.get("networks", {}) and "mysql" not in services, "resolved Compose retained an obsolete database surface")
    require(set(services["backend"].get("networks", {})) == {"app", "database-egress"}, "resolved Backend networks are invalid")
    require(set(services["frontend"].get("networks", {})) == {"app"}, "resolved Frontend networks are invalid")
    require(set(services["proxy"].get("networks", {})) == {"edge", "app"}, "resolved Proxy networks are invalid")
    require(services["proxy"].get("image") == PROXY_IMAGE, "resolved proxy image digest changed")


def validate_workflows() -> None:
    publish = read(PUBLISH_WORKFLOW)
    readiness = read(READINESS_WORKFLOW)
    validation = read(VALIDATION_WORKFLOW)
    external = read(EXTERNAL_PREFLIGHT_WORKFLOW)
    pinned_actions(publish + readiness + validation + external)
    require("permissions:\n  contents: read\n  packages: write" in publish, "image publication permissions changed")
    require(publish.count("platforms: linux/amd64,linux/arm64") == 2, "both application images must publish amd64 and arm64")
    require("setup-qemu-action@c7c53464625b32c7a7e944ae62b3e17d2b600130" in publish, "QEMU action pin changed")
    require("verify_image_platforms" in readiness and '("linux", "amd64")' in readiness and '("linux", "arm64")' in readiness, "readiness must check both image platforms")
    require("on:\n  pull_request:" in external and "permissions:\n  contents: read" in external, "external datasource workflow must be PR-scoped and read-only")
    require("test-subscription-automation-preflight-datasource.sh" in external and "subscription-automation-preflight.sh" in external, "external datasource workflow coverage is incomplete")
    require("test-production-safety-contracts.sh" in validation, "release safety regression is not in Repository Validation")
    for obsolete in ("production-deploy.yml", "mvp4-temporary-auto-production-deploy.yml", "validate-rds-automation-preflight.yml"):
        require(not (WORKFLOWS / obsolete).exists() and obsolete not in validation, f"obsolete workflow remains active: {obsolete}")
    require("production-command-dispatch.sh" not in publish and "invoke-oci-production-command.sh" not in publish, "GitHub image publication must not dispatch Production")
    require("OCI" in readiness or "operator" in readiness.lower(), "readiness must point to the operator boundary")


def validate_runtime_and_release() -> None:
    materialize = read(PRODUCTION / "materialize-runtime-env.sh")
    common = read(PRODUCTION / "release-common.sh")
    deploy = read(PRODUCTION / "deploy.sh")
    rollback = read(PRODUCTION / "rollback.sh")
    helper = read(PRODUCTION / "subscription-automation-preflight.sh")
    expected_keys = (
        "PAWCYCLE_DATASOURCE_HOST", "PAWCYCLE_DATASOURCE_PORT", "PAWCYCLE_DATASOURCE_DATABASE",
        "PAWCYCLE_DATASOURCE_SSL_MODE", "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD", "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED",
        "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE", "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS",
    )
    require(all(key in materialize for key in expected_keys), "runtime materializer key contract is incomplete")
    require("backend.env" in materialize and "mysql.env" not in materialize, "runtime materializer must publish backend.env only")
    require("sslMode=REQUIRED&serverTimezone=UTC" in materialize and "allowPublicKeyRetrieval" not in materialize, "runtime TLS URL contract is invalid")
    require("flock" in materialize and "mv -Tf" in materialize and "current" in materialize, "runtime materialization must be atomic and locked")
    require("private IPv4" in materialize and "approved DNS name" in materialize, "runtime datasource host validation is incomplete")
    require("CONTROL_WORKTREE_PATHS" in common and "materialize-runtime-env.sh" in common and "production-command-dispatch.sh" in common, "active control worktree map is incomplete")
    for obsolete in ("production-db-restore.sh", "materialize-ssm-env.sh", "rds-read-only-preflight.sh", "rds-transition-gate.sh"):
        require(obsolete not in common, f"obsolete control path remains: {obsolete}")
    require("RELEASE_SHA" in common and "BACKEND_DIGEST" in common and "FRONTEND_DIGEST" in common and "PROXY_DIGEST" in common, "application image record fields are incomplete")
    digest_token = "MYSQL_" + "DIGEST"
    volume_token = "active-" + "mysql-volume"
    require(digest_token not in common and volume_token not in common, "database-owned release state remains")
    require("verify_running_release" in common and "backend frontend proxy" in common and "org.opencontainers.image.revision" in common, "application identity verification is incomplete")
    require("database-egress" in common and "managed database was not modified by the Application release lifecycle" in deploy + rollback, "managed DB lifecycle boundary is incomplete")
    require("STATE_TRANSITION_NAME=\"release-state-transition\"" in deploy and "publish_state_or_abort" in deploy, "state publication must fail closed")
    require("migration_bundle_changed" in deploy and "SCHEMA_BOUNDARY=1" in deploy and "automatic contract-boundary restoration is blocked" in deploy, "migration/contract rollback boundary is incomplete")
    require("activate_release \"$CURRENT_SHA\"" in deploy and "stop_failed_release_applications" in deploy and "smoke_release" in common, "health/smoke failure recovery is incomplete")
    require("previous-sha" in rollback and "previous-contract-sha" in rollback and "require_no_migration_boundary_rollback" in rollback, "rollback state/boundary contract is incomplete")
    require("require_subscription_automation_mode" in read(PRODUCTION / "subscription-automation-control.sh") and "Scheduler" in read(PRODUCTION / "subscription-automation-control.sh"), "Scheduler OFF gate is missing")
    require("https_enabled" in common and "verify_https_release" in common, "HTTPS-enabled release verification is missing")
    require("EXTERNAL_MYSQL" in helper and "defaults-extra-file" in helper and "MYSQL_PWD" not in helper, "external datasource preflight credential boundary is invalid")


def validate_oci_artifacts() -> None:
    dispatcher = read(PRODUCTION / "production-command-dispatch.sh")
    wrapper = read(PRODUCTION / "invoke-oci-production-command.sh")
    backup = read(PRODUCTION / "oci-db-backup-restore.sh")
    require('CONTROL_DIR="/opt/pawcycle/control"' in dispatcher and 'STATE_DIR="/opt/pawcycle/state"' in dispatcher, "dispatcher paths must be fixed")
    require('[[ "$EUID" == 0 ]]' in dispatcher and "fetch --prune origin main" in dispatcher, "dispatcher root/fetch gate is incomplete")
    require("https://github\\.com" in dispatcher and "merge-base --is-ancestor" in dispatcher, "dispatcher repository/history gate is incomplete")
    require("ghcr.io/${REPOSITORY}-backend" in dispatcher and "ghcr.io/${REPOSITORY}-frontend" in dispatcher, "dispatcher GHCR derivation is incomplete")
    require("sudo -n /usr/bin/env bash /opt/pawcycle/control/infra/production/production-command-dispatch.sh" in wrapper, "OCI wrapper command boundary is invalid")
    require("textSha256" in wrapper and "chmod 600" in wrapper and "MAX_OUTPUT_BYTES=12288" in wrapper, "OCI wrapper payload/output protection is incomplete")
    require("ACCEPTED|IN_PROGRESS" in wrapper and "SUCCEEDED" in wrapper and "TIMED_OUT|CANCELED" in wrapper, "OCI lifecycle polling is incomplete")
    require("--auth instance_principal" in backup and "--no-overwrite" in backup and "--verify-checksum" in backup, "Object Storage authentication/immutability contract is incomplete")
    require(MYSQL_IMAGE in backup and "--network none" in backup and "SCHEMA_SHA256" in backup and "FLYWAY_SHA256" in backup, "isolated restore verification contract is incomplete")
    require("cutover" not in backup.lower() and "production db" not in backup.lower(), "backup script must not imply managed database cutover")


def validate_documents() -> None:
    adr = read(ROOT / "docs" / "adr" / "ARCH-016-oci-production-runtime-boundary.md")
    old_adr = read(ROOT / "docs" / "adr" / "ARCH-013-rds-single-az.md")
    observability_adr = read(ROOT / "docs" / "adr" / "ARCH-012-production-observability-boundary.md")
    architecture = read(ROOT / "docs" / "architecture" / "production-operations-overview.md")
    ops010 = read(ROOT / "docs" / "runbook" / "OPS-010-production-single-release.md")
    ops011 = read(ROOT / "docs" / "runbook" / "OPS-011-production-https.md")
    opsobs = read(ROOT / "docs" / "runbook" / "OPS-OBS-001-production-observability.md")
    backup_runbook = read(ROOT / "docs" / "runbook" / "OPS-OCI-002-production-db-backup-restore.md")
    index = read(ROOT / "docs" / "runbook" / "README.md")
    readme = read(ROOT / "README.md")
    require("Accepted — Repository Readiness" in adr and "Production Verified" in adr, "ARCH-016 status is incomplete")
    for decision in ("MySQL HeatWave", "instance principal", "operator-approved", "automatic Production deploy", "A1 ARM64", "VERIFY_CA", "DEFER"):
        require(decision in adr, f"ARCH-016 decision is missing: {decision}")
    require("Superseded by ARCH-016" in old_adr, "ARCH-013 supersession notice is missing")
    require("ARCH-016" in observability_adr and "measurement pending" in observability_adr.lower(), "ARCH-012 provider/topology notice is incomplete")
    for term in ("OCI VCN", "Public Application Subnet", "Private DB Subnet", "MySQL HeatWave", "Object Storage", "no public Backend 8080", "no public MySQL 3306", "no initial load balancer", "Home Region", "pending"):
        require(term in architecture, f"current OCI architecture is missing: {term}")
    for runbook in (ops010, ops011, opsobs):
        require("OCI" in runbook and "Production Verified" in runbook, "current runbook must declare OCI repository readiness")
    require(all(term in backup_runbook for term in ("oci-db-backup-restore.sh", "instance principal", "restore-verify", "cleanup", "Production DB mutation 금지")), "OCI backup runbook is incomplete")
    superseded_label = "Superseded " + "A" + "W" + "S" + " Production"
    require("Current OCI Production" in index and superseded_label in index and "Historical evidence" in index, "runbook index categories are missing")
    require("OCI" in readme and "OCI Run Command" in readme, "root README current architecture is stale")


def validate_active_surface() -> None:
    forbidden = (
        "active-" + "mysql-volume",
        "PAWCYCLE_" + "MYSQL_ENV_FILE",
        "MYSQL_" + "DIGEST",
        "pawcycle-production-" + "data",
        "com.docker.compose.service=" + "mysql",
    )
    active_paths = list(WORKFLOWS.glob("*.yml")) + [
        path for path in PRODUCTION.glob("*") if path.is_file() and not path.name.startswith("test-")
    ]
    active_paths += list((ROOT / "infra" / "production-observability").glob("**/*"))
    active_paths += list((ROOT / "infra" / "production-metrics-proxy").glob("**/*"))
    for path in active_paths:
        if not path.is_file():
            continue
        source = path.read_text(encoding="utf-8")
        for token in forbidden:
            pattern = rf"(?<![A-Za-z0-9_-]){re.escape(token)}(?![A-Za-z0-9_-])" if token.startswith("pawcycle-") else re.escape(token)
            require(re.search(pattern, source) is None, f"obsolete runtime token remains in active file: {path.relative_to(ROOT)}")
    for obsolete in (
        "production-deploy.yml", "mvp4-temporary-auto-production-deploy.yml", "validate-rds-automation-preflight.yml",
        "pawcycle-production-deploy-ssm-document.json", "validate-production-ssm-document.py", "materialize-ssm-env.sh",
        "rds-read-only-preflight.sh", "rds-transition-gate.sh", "test-rds-readiness.sh", "db-backup-restore.sh",
        "test-db-backup-restore.sh", "production-db-restore.sh", "ssm-parameters.env.example",
        "ec2-status-check-alarm-common.sh", "create-ec2-status-check-alarm.sh", "cleanup-ec2-status-check-alarm.sh",
        "test-ec2-status-check-alarm.sh",
    ):
        require(not (WORKFLOWS / obsolete).exists() and not (PRODUCTION / obsolete).exists(), f"obsolete runtime artifact remains: {obsolete}")


def validate_tests() -> None:
    for name in (
        "test-materialize-runtime-env.sh", "test-production-command-dispatch.sh", "test-invoke-oci-production-command.sh",
        "test-oci-db-backup-restore.sh", "test-production-safety-contracts.sh", "test-production-scripts.sh",
        "test-production-compose.sh", "test-production-nginx.sh", "test-production-arm64-images.sh",
        "test-subscription-automation-preflight-datasource.sh", "test-demo-catalog-import.sh",
        "test-rollback-control-compatibility.sh", "test-diagnose-backend-state.sh",
    ):
        require((PRODUCTION / name).exists(), f"required regression test is missing: {name}")
    safety = read(PRODUCTION / "test-production-safety-contracts.sh")
    for marker in ("current-sha", "previous-sha", "previous-contract-sha", "release-state-transition", "migration_bundle_changed", "verify_https_release"):
        require(marker in safety, f"safety coverage marker is missing: {marker}")


def main() -> None:
    validate_compose()
    validate_workflows()
    validate_runtime_and_release()
    validate_oci_artifacts()
    validate_documents()
    validate_active_surface()
    validate_tests()
    print("OPS-OCI-002 Stage 3 OCI integration contracts validated")


if __name__ == "__main__":
    main()

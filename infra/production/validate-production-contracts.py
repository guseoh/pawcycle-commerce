#!/usr/bin/env python3

from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
PRODUCTION = ROOT / "infra" / "production"
WORKFLOW = ROOT / ".github" / "workflows" / "publish-production-images.yml"
READINESS_WORKFLOW = ROOT / ".github" / "workflows" / "production-release-readiness.yml"
VALIDATION_WORKFLOW = ROOT / ".github" / "workflows" / "validate-conventions.yml"
DEPLOY_WORKFLOW = ROOT / ".github" / "workflows" / "production-deploy.yml"
DEPLOY_SSM_DOCUMENT = PRODUCTION / "pawcycle-production-deploy-ssm-document.json"
SHA = "0" * 40
MYSQL_IMAGE = "mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"
PROXY_IMAGE = "nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
CERTBOT_IMAGE = "certbot/certbot:v5.7.0@sha256:34ee91d2f43008eb78a007d22f23ed4b2eaa9a454cb27ca2c042b49527a695b4"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ERROR: {message}")


APPROVED_PREFLIGHT_OUTPUT_KEYS = {
    "FLYWAY_V9",
    "FLYWAY_V10",
    "FLYWAY_V11",
    "TABLE_SUBSCRIPTION_ORDERS",
    "TABLE_SUBSCRIPTION_ORDER_ITEMS",
    "UNIQUE_SCHEDULE_ORDER",
    "DUE_INDEX",
    "DUE_CANDIDATE_COUNT",
    "OLDEST_DUE_DATE",
    "DUPLICATE_ORDER_SCHEDULE_GROUPS",
    "ORDERLESS_ADVANCED_SCHEDULES",
    "ORDER_SNAPSHOT_CARDINALITY_ANOMALIES",
    "PROCESSED_ACTIVE_FUTURE_SCHEDULE_ANOMALIES",
}


def top_level_select_projection(statement: str) -> str | None:
    match = re.match(r"\s*SELECT\s+", statement, re.IGNORECASE)
    if match is None:
        return None

    depth = 0
    quote = ""
    projection_start = match.end()
    index = projection_start
    while index < len(statement):
        character = statement[index]
        if quote:
            if character == quote and (index == 0 or statement[index - 1] != "\\"):
                quote = ""
        elif character in "'\"`":
            quote = character
        elif character == "(":
            depth += 1
        elif character == ")":
            depth -= 1
        elif depth == 0 and statement[index : index + 4].upper() == "FROM":
            before = statement[index - 1] if index > projection_start else " "
            after = statement[index + 4] if index + 4 < len(statement) else " "
            if before.isspace() and after.isspace():
                return statement[projection_start:index].strip()
        index += 1
    return None


def approved_preflight_projection(statement: str) -> bool:
    projection = top_level_select_projection(statement)
    if projection is None:
        return False
    match = re.fullmatch(r"CONCAT\(\s*'([A-Z0-9_]+)='\s*,[\s\S]*\)\s*", projection)
    if match is None or match.group(1) not in APPROVED_PREFLIGHT_OUTPUT_KEYS:
        return False
    return re.search(
        r"\bGROUP_CONCAT\s*\(\s*(?:[A-Za-z_][A-Za-z0-9_]*\.)?id\b",
        projection,
        re.IGNORECASE,
    ) is None


def validate_preflight_projection_contract(source: str) -> None:
    sql_bundles = re.findall(r"(?ms)^[A-Z_]+_SQL=\$\(cat <<'SQL'\n(.*?)^SQL\n\)", source)
    statements = [statement.strip() for bundle in sql_bundles for statement in bundle.split(";") if statement.strip()]
    require(statements, "SUB-AUTO-002 preflight SQL bundles are missing")
    require(
        all(approved_preflight_projection(statement) for statement in statements),
        "SUB-AUTO-002 preflight output must use only approved aggregate key projections",
    )
    for unsafe_projection in (
        "SELECT id FROM subscription_schedules",
        "SELECT schedule.id FROM subscription_schedules schedule",
        "SELECT CONCAT('DUE_CANDIDATE_COUNT=', GROUP_CONCAT(orders.id)) FROM subscription_orders orders",
        "SELECT CONCAT('DUE_CANDIDATE_COUNT=', COUNT(*)), schedule.id FROM subscription_schedules schedule",
    ):
        require(
            not approved_preflight_projection(unsafe_projection),
            "SUB-AUTO-002 preflight projection guard accepted an identifier or multiple columns",
        )


def load_compose_config() -> dict[str, object]:
    with tempfile.TemporaryDirectory(prefix="ops011-compose-") as temporary:
        temporary_path = Path(temporary)
        backend_env = temporary_path / "backend.env"
        backend_env.write_text(
            "PAWCYCLE_DATASOURCE_HOST='db.example.com'\n"
            "PAWCYCLE_DATASOURCE_PORT='3306'\n"
            "PAWCYCLE_DATASOURCE_DATABASE='ops010'\n"
            "PAWCYCLE_DATASOURCE_SSL_MODE='REQUIRED'\n"
            "SPRING_DATASOURCE_URL='jdbc:mysql://db.example.com:3306/ops010?sslMode=REQUIRED&serverTimezone=UTC'\n"
            "SPRING_DATASOURCE_USERNAME='ops010'\n"
            "SPRING_DATASOURCE_PASSWORD='not-sensitive'\n"
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'\n"
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='7'\n"
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='12345'\n",
            encoding="utf-8",
        )
        environment = os.environ | {
            "RELEASE_SHA": SHA,
            "BACKEND_IMAGE": "ghcr.io/example/pawcycle-commerce-backend",
            "FRONTEND_IMAGE": "ghcr.io/example/pawcycle-commerce-frontend",
            "PAWCYCLE_BACKEND_ENV_FILE": str(backend_env),
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED": "false",
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE": "7",
            "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS": "12345",
        }
        completed = subprocess.run(
            [
                "docker",
                "compose",
                "--file",
                str(PRODUCTION / "compose.yaml"),
                "config",
                "--format",
                "json",
            ],
            cwd=ROOT,
            env=environment,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        return json.loads(completed.stdout)


def validate_compose() -> None:
    config = load_compose_config()
    services = config["services"]
    require(set(services) == {"backend", "frontend", "proxy"}, "unexpected Compose services")

    for internal_service in ("backend", "frontend"):
        require(not services[internal_service].get("ports"), f"{internal_service} must not publish host ports")

    proxy_ports = services["proxy"].get("ports", [])
    published_ports = {(port.get("published"), port.get("target")) for port in proxy_ports}
    require(
        len(proxy_ports) == 2 and published_ports == {("80", 80), ("443", 443)},
        "proxy must publish exactly HTTP 80 and HTTPS 443",
    )

    require(services["backend"]["image"].endswith(f":{SHA}"), "Backend image must use RELEASE_SHA")
    require(services["frontend"]["image"].endswith(f":{SHA}"), "Frontend image must use RELEASE_SHA")
    require(services["proxy"]["image"] == PROXY_IMAGE, "Nginx image must use the approved immutable digest")
    require(
        services["backend"].get("environment", {}).get("SESSION_COOKIE_SECURE") == "true",
        "Backend Secure session cookie contract must remain enabled",
    )
    backend_environment = services["backend"].get("environment", {})
    require(
        backend_environment.get("PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED") == "false"
        and backend_environment.get("PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE") == "7"
        and backend_environment.get("PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS") == "12345",
        "Backend automation runtime values must be explicit Compose inputs",
    )

    for name, service in services.items():
        require(service.get("restart") == "unless-stopped", f"{name} restart policy must be unless-stopped")
        logging = service.get("logging", {})
        require(logging.get("driver") == "json-file", f"{name} log driver must be json-file")
        options = logging.get("options", {})
        require(logging.get("driver") == "json-file", f"{name} log driver must be json-file")
        require(options.get("max-size") == "10m" and options.get("max-file") == "3", f"{name} log rotation is incomplete")
        require(
            float(service.get("mem_limit", 0)) > 0 and float(service.get("cpus", 0)) > 0,
            f"{name} resource limits are required",
        )
        require(service.get("healthcheck"), f"{name} healthcheck is required")

    total_cpus = sum(float(service["cpus"]) for service in services.values())
    total_memory = sum(float(service["mem_limit"]) for service in services.values())
    require(total_cpus <= 2.0, "combined CPU limits exceed t3.small capacity")
    require(total_memory <= 1664 * 1024 * 1024, "combined memory limits exceed the approved conservative budget")

    require("mysql-data" not in config.get("volumes", {}), "Production Compose must not define a MySQL volume")
    require(
        config["volumes"]["certbot-webroot"]["name"] == "pawcycle-production-certbot-webroot",
        "stable Certbot webroot volume name is required",
    )
    require(
        config["volumes"]["letsencrypt"]["name"] == "pawcycle-production-letsencrypt",
        "stable Let's Encrypt volume name is required",
    )
    proxy_mounts = {mount["target"]: mount for mount in services["proxy"].get("volumes", [])}
    require(proxy_mounts["/var/www/certbot"].get("read_only") is True, "Nginx challenge volume must be read-only")
    require(proxy_mounts["/etc/letsencrypt"].get("read_only") is True, "Nginx certificate volume must be read-only")
    require(config["networks"]["edge"].get("internal") is not True, "proxy edge network must accept the published port")
    require(config["networks"]["app"].get("internal") is True, "app network must be internal")
    require("data" not in config.get("networks", {}), "Production Compose must not define the data network")
    require("database-egress" in config["networks"] and config["networks"]["database-egress"].get("internal") is not True, "database-egress network must be an external bridge")
    require(set(services["backend"].get("networks", {})) == {"app", "database-egress"}, "Backend network contract is invalid")
    require(set(services["frontend"].get("networks", {})) == {"app"}, "Frontend network contract is invalid")
    require(set(services["proxy"].get("networks", {})) == {"edge", "app"}, "Proxy network contract is invalid")


def validate_workflow() -> None:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    readiness_workflow = READINESS_WORKFLOW.read_text(encoding="utf-8")
    validation_workflow = VALIDATION_WORKFLOW.read_text(encoding="utf-8")
    require("permissions:\n  contents: read\n  packages: write" in workflow, "workflow permissions exceed or omit the approved minimum")
    require("if: github.ref == 'refs/heads/main'" in workflow, "non-main image publication must fail closed")
    require(workflow.count("tags: ghcr.io/${{ github.repository }}-") == 2, "both images need repository-derived tags")
    require(workflow.count(":${{ github.sha }}") == 2, "both images must use the same github.sha tag")
    require(workflow.count("org.opencontainers.image.revision=${{ github.sha }}") == 2, "both images need the SHA revision label")
    require(
        workflow.count("platforms: linux/amd64,linux/arm64") == 2,
        "both release images must publish amd64 and arm64 platforms",
    )
    require(not re.search(r"(?:image|tags):[^\n]*:latest(?:\s|$)", workflow), "latest image tag is forbidden")

    for action_reference in re.findall(r"uses:\s+[^\s]+@([^\s]+)", workflow):
        require(bool(re.fullmatch(r"[0-9a-f]{40}", action_reference)), "workflow actions must be pinned to a 40-character commit")

    require(
        "docker/setup-qemu-action@c7c53464625b32c7a7e944ae62b3e17d2b600130" in workflow,
        "multi-platform release publishing must set up QEMU with an immutable action pin",
    )
    require(
        "verify_image_platforms" in readiness_workflow
        and "linux\", \"amd64" in readiness_workflow
        and "linux\", \"arm64" in readiness_workflow,
        "release readiness must fail closed when either required image platform is absent",
    )

    require("infra/production/https.sh" in validation_workflow, "Repository Validation must syntax-check the HTTPS script")
    require("infra/production/diagnose-backend-state.sh" in validation_workflow and "infra/production/test-diagnose-backend-state.sh" in validation_workflow, "Repository Validation must validate the OPS-AUTO-009 read-only diagnostic")
    diagnostic = (PRODUCTION / "diagnose-backend-state.sh").read_text(encoding="utf-8")
    require("--scope production" in diagnostic and "--scope observability" in diagnostic, "OPS-AUTO-009 diagnostic must preserve the two-host boundary")
    require("^http://(127\\.0\\.0\\.1|localhost)" in diagnostic, "Prometheus diagnostic must remain loopback-only")
    require('/api/products' in diagnostic and 'HTTPS_ORIGIN/products' not in diagnostic, "Backend diagnosis must not use the frontend /products route")
    require(
        "infra/production/subscription-automation-control.sh" in validation_workflow
        and "infra/production/subscription-automation-preflight.sh" in validation_workflow,
        "Repository Validation must syntax-check the SUB-AUTO-002 production scripts",
    )
    require(
        "infra/production/db-backup-restore.sh" in validation_workflow,
        "Repository Validation must syntax-check the OPS-013 backup and restore script",
    )
    require(
        "bash infra/production/test-production-nginx.sh" in validation_workflow,
        "Repository Validation must execute the Nginx configuration test",
    )
    require(
        "bash infra/production/test-production-arm64-images.sh" in validation_workflow
        and "docker/setup-qemu-action@c7c53464625b32c7a7e944ae62b3e17d2b600130" in validation_workflow,
        "Repository Validation must execute ARM64 production image builds with immutable QEMU setup",
    )
    require(
        "bash infra/production/test-production-compose.sh" in validation_workflow,
        "Repository Validation must execute the production Compose lifecycle test",
    )
    require(
        all(
            path in validation_workflow
            for path in (
                "infra/production/materialize-runtime-env.sh",
                "infra/production/production-command-dispatch.sh",
                "infra/production/invoke-oci-production-command.sh",
                "infra/production/oci-db-backup-restore.sh",
                "infra/production/test-materialize-runtime-env.sh",
                "infra/production/test-production-command-dispatch.sh",
                "infra/production/test-invoke-oci-production-command.sh",
                "infra/production/test-oci-db-backup-restore.sh",
                "infra/production/test-subscription-automation-preflight-datasource.sh",
            )
        ),
        "Repository Validation must syntax-check and execute the Stage 2 OCI runtime fake tests",
    )
    require(
        "infra/production/test-db-backup-restore.sh" in validation_workflow,
        "Repository Validation must retain syntax coverage for the legacy backup and restore lifecycle test",
    )
    require(
        "infra/production/verify-production-auth-session-smoke.sh" in validation_workflow
        and validation_workflow.count("infra/production/test-production-auth-session-smoke.sh") >= 2,
        "Repository Validation must syntax-check the OPS-017 auth session smoke scripts",
    )
    require(
        "bash infra/production/test-production-auth-session-smoke.sh" in validation_workflow,
        "Repository Validation must execute the OPS-017 fake HTTP contract test",
    )
    require(
        "infra/production/create-production-auth-smoke-member.sh" in validation_workflow
        and "infra/production/test-create-production-auth-smoke-member-lifecycle.sh" in validation_workflow,
        "Repository Validation must syntax-check the OPS-020 shell contracts",
    )
    require(
        "python -m py_compile infra/production/test-create-production-auth-smoke-member.py"
        in validation_workflow
        and 'sudo "$(command -v python)" infra/production/test-create-production-auth-smoke-member.py'
        in validation_workflow,
        "Repository Validation must execute the OPS-020 fake Docker and PTY contract test",
    )
    require(
        "bash infra/production/test-create-production-auth-smoke-member-lifecycle.sh"
        in validation_workflow,
        "Repository Validation must execute the isolated OPS-020 Docker lifecycle test",
    )


def validate_oidc_deploy_contract() -> None:
    workflow = DEPLOY_WORKFLOW.read_text(encoding="utf-8")
    document = json.loads(DEPLOY_SSM_DOCUMENT.read_text(encoding="utf-8"))

    require("on:\n  workflow_dispatch:" in workflow, "Production deploy must use workflow_dispatch")
    require("push:" not in workflow and "pull_request:" not in workflow, "Production deploy trigger must be dispatch-only")
    for action_reference in re.findall(r"uses:\s+[^\s]+@([^\s]+)", workflow):
        require(bool(re.fullmatch(r"[0-9a-f]{40}", action_reference)), "Production deploy actions must be pinned to a 40-character commit")
    require("environment: production" in workflow, "Production deploy job must require the production environment")
    require(
        "permissions:\n  contents: read\n  id-token: write" in workflow,
        "Production deploy permissions must be limited to checkout and OIDC",
    )
    require("group: pawcycle-production-deploy" in workflow and "cancel-in-progress: false" in workflow, "concurrent Production deploys must be blocked")
    require("AWS_ACCESS_KEY_ID" not in workflow and "AWS_SECRET_ACCESS_KEY" not in workflow and "ssh" not in workflow.lower(), "long-lived AWS keys and SSH are forbidden")
    for workflow_input in (
        "operation",
        "target_sha",
        "approved_contract_from_sha",
        "approved_control_sha",
        "approved_migration_target_sha",
    ):
        require(f"inputs:\n      {workflow_input}:" in workflow or f"\n      {workflow_input}:" in workflow, f"Production deploy workflow input is missing: {workflow_input}")
    require("^[0-9a-f]{40}$" in workflow and "git merge-base --is-ancestor" in workflow, "target SHA must be validated against main")
    require("ghcr.io/${GITHUB_REPOSITORY}-backend:${TARGET_SHA}" in workflow and "ghcr.io/${GITHUB_REPOSITORY}-frontend:${TARGET_SHA}" in workflow, "Production deploy must reuse the PawCycle GHCR image contract")
    require("aws-actions/configure-aws-credentials@acca2b1b2070338fb9fd1ca27ecee81d687e58e5" in workflow, "OIDC credentials action must use the approved pinned revision")
    require("role-duration-seconds: 3600" in workflow, "OIDC session must outlive the bounded Production deployment workflow")
    for variable in (
        "vars.AWS_PRODUCTION_DEPLOY_ROLE_ARN",
        "vars.AWS_REGION",
        "vars.PAWCYCLE_PRODUCTION_SSM_DOCUMENT_NAME",
        "vars.PAWCYCLE_PRODUCTION_SSM_DOCUMENT_VERSION",
        "vars.PAWCYCLE_PRODUCTION_SSM_TARGET_TAG_KEY",
        "vars.PAWCYCLE_PRODUCTION_SSM_TARGET_TAG_VALUE",
    ):
        require(variable in workflow, f"Production deploy must use the GitHub Environment variable: {variable}")
    require(
        "aws ssm describe-instance-information" in workflow
        and "Key=tag:${SSM_TARGET_TAG_KEY},Values=${SSM_TARGET_TAG_VALUE}" in workflow
        and "Production SSM target tag must resolve to exactly one managed node" in workflow
        and "resolved Production target is not an online EC2 managed node" in workflow,
        "Production deploy must resolve exactly one online EC2 SSM target before dispatch",
    )
    require("--instance-ids \"$SSM_INSTANCE_ID\"" in workflow and "--targets " not in workflow, "SSM dispatch must use only the pre-resolved Production instance")
    require(
        "SSM document version must be a positive immutable numeric version" in workflow
        and "--document-version \"$SSM_DOCUMENT_VERSION\"" in workflow,
        "Production deploy must pin an immutable numeric SSM document version",
    )
    require(
        "Operation=${operation},TargetSha=${TARGET_SHA},ApprovedContractFromSha=${APPROVED_CONTRACT_FROM_SHA},ApprovedControlSha=${APPROVED_CONTROL_SHA},ApprovedMigrationTargetSha=${APPROVED_MIGRATION_TARGET_SHA}"
        in workflow
        and "run_ssm_operation preflight" in workflow
        and "run_ssm_operation deploy" in workflow
        and "run_ssm_operation control-adopt" in workflow
        and 'OPERATION: ${{ inputs.operation }}' in workflow,
        "workflow must run the same approved target and boundary values through deploy preflight or control-only adoption",
    )
    require("--max-concurrency \"1\"" in workflow and "--max-errors \"0\"" in workflow, "SSM dispatch must fail closed")
    require("aws ssm list-command-invocations" in workflow and "Success)" in workflow and "status could not be determined" in workflow and "print_ssm_plugin_output" in workflow and "CommandPlugins" in workflow, "SSM result must fail closed and show bounded plugin output on failure")
    require("SSM preflight command: Success" in workflow and "SSM deploy command: Success" in workflow and "Production Verified" not in workflow, "summary must record both command successes without declaring Production verification")

    require(document.get("schemaVersion") == "2.2", "PawCycle deploy SSM document must use schema 2.2")
    parameters = document.get("parameters")
    require(
        isinstance(parameters, dict)
        and set(parameters)
        == {
            "Operation",
            "TargetSha",
            "ApprovedContractFromSha",
            "ApprovedControlSha",
            "ApprovedMigrationTargetSha",
        },
        "SSM document must accept only the bounded operation, target, and boundary approvals",
    )
    operation = parameters["Operation"]
    require(
        operation.get("type") == "String"
        and "interpolationType" not in operation
        and operation.get("allowedPattern") == "^(preflight|deploy|control-adopt)$"
        and operation.get("minChars") == 6
        and operation.get("maxChars") == 13,
        "SSM Operation must use the exact bounded raw parameter contract",
    )
    target = parameters["TargetSha"]
    require(
        target.get("type") == "String"
        and "interpolationType" not in target
        and target.get("allowedPattern") == "^[0-9a-f]{40}$"
        and target.get("minChars") == 40
        and target.get("maxChars") == 40,
        "SSM TargetSha must use the exact bounded raw parameter contract",
    )
    for approval_name in (
        "ApprovedContractFromSha",
        "ApprovedControlSha",
        "ApprovedMigrationTargetSha",
    ):
        approval = parameters[approval_name]
        require(
            approval.get("type") == "String"
            and approval.get("default") == ""
            and "interpolationType" not in approval
            and approval.get("allowedPattern") == "^$|^[0-9a-f]{40}$"
            and approval.get("minChars") == 0
            and approval.get("maxChars") == 40,
            f"SSM {approval_name} must be an optional bounded raw SHA",
        )
    steps = document.get("mainSteps")
    require(isinstance(steps, list) and len(steps) == 1 and steps[0].get("action") == "aws:runShellScript", "SSM document must contain one bounded Linux shell step")
    command = "\n".join(steps[0].get("inputs", {}).get("runCommand", []))
    expected_first_line = (
        "exec /usr/bin/env bash -s -- "
        '"{{Operation}}" "{{TargetSha}}" "{{ApprovedContractFromSha}}" '
        '"{{ApprovedControlSha}}" "{{ApprovedMigrationTargetSha}}" '
        "<<'PAWCYCLE_PRODUCTION_SSM_SCRIPT'"
    )
    require(
        command.startswith(expected_first_line + "\n#!/usr/bin/env bash\nset -Eeuo pipefail")
        and command.endswith("\nPAWCYCLE_PRODUCTION_SSM_SCRIPT"),
        "SSM document must enter its bounded Bash command body with exact positional parameters",
    )
    require(
        command.splitlines()[0] == expected_first_line
        and [*re.findall(r"\{\{\s*([A-Za-z][A-Za-z0-9]*)\s*\}\}", command.splitlines()[0])] == [
            "Operation",
            "TargetSha",
            "ApprovedContractFromSha",
            "ApprovedControlSha",
            "ApprovedMigrationTargetSha",
        ]
        and all(f'"${{{index}:-}}"' in command for index in range(1, 6))
        and not any(f"SSM_{name}" in command for name in (
            "Operation",
            "TargetSha",
            "ApprovedContractFromSha",
            "ApprovedControlSha",
            "ApprovedMigrationTargetSha",
        ))
        and "{{ TargetSha }}" not in command,
        "SSM document must not depend on ENV_VAR materialization or interpolate outside the exact Bash argv",
    )
    for trusted_line in (
        "export GIT_CONFIG_COUNT=1",
        "export GIT_CONFIG_KEY_0=safe.directory",
        "export GIT_CONFIG_VALUE_0=/opt/pawcycle/control",
    ):
        require(command.count(trusted_line) == 1, f"SSM trusted-directory contract must contain exactly one line: {trusted_line}")
    require("safe.directory=*" not in command, "SSM trusted-directory contract must not allow wildcard safe.directory")
    require("GIT_CONFIG_GLOBAL" not in command and "GIT_CONFIG_SYSTEM" not in command, "SSM trusted-directory contract must not alter persistent config paths")
    require("git config --global" not in command and "git config --system" not in command, "SSM trusted-directory contract must not invoke persistent config mutation")
    require("GIT_CONFIG_KEY_1" not in command and "GIT_CONFIG_VALUE_1" not in command, "SSM trusted-directory contract must contain one process config entry")
    require("/opt/pawcycle/control" in command and "infra/production/deploy.sh" in command, "SSM document must reuse the existing PawCycle control deploy script")
    require("git -C \"$control_dir\" config --get remote.origin.url" in command and "https://github.com/*/*.git" in command, "SSM document must derive the approved PawCycle GitHub repository without SSH")
    require(
        'cd "$control_dir"' in command
        and "git fetch --prune origin refs/heads/main:refs/remotes/origin/main" in command
        and 'git merge-base --is-ancestor "$target_sha" refs/remotes/origin/main' in command,
        "SSM document must fetch and validate target main history without moving the approved control HEAD",
    )
    require(
        'current_sha_file="$state_dir/current-sha"' in command
        and 'git merge-base --is-ancestor "$current_sha" "$target_sha"' in command
        and "use the protected rollback procedure" in command,
        "automatic deploy must reject older or divergent releases and preserve the rollback boundary",
    )
    require("ghcr.io/${repository}-backend" in command and "ghcr.io/${repository}-frontend" in command, "SSM document must derive both GHCR repositories from the PawCycle control contract")
    require("--operation \"$operation\"" in command and "--sha \"$target_sha\"" in command and "--backend-image \"$backend_image\"" in command and "--frontend-image \"$frontend_image\"" in command and "--approved-contract-from-sha \"$approved_contract_from_sha\"" in command and "--approved-control-sha \"$approved_control_sha\"" in command and "--approved-migration-target-sha \"$approved_migration_target_sha\"" in command, "SSM document must pass only bounded operation, target, and boundary approval inputs to deploy.sh")
    require("--adopt-contract-sha" not in command and "aws ssm" not in command and "ssh" not in command.lower(), "SSM document must not bypass deploy contract or accept remote shell paths")


def validate_scripts() -> None:
    common = (PRODUCTION / "release-common.sh").read_text(encoding="utf-8")
    deploy = (PRODUCTION / "deploy.sh").read_text(encoding="utf-8")
    rollback = (PRODUCTION / "rollback.sh").read_text(encoding="utf-8")
    automation_control = (PRODUCTION / "subscription-automation-control.sh").read_text(encoding="utf-8")
    automation_preflight = (PRODUCTION / "subscription-automation-preflight.sh").read_text(encoding="utf-8")
    automation_runbook = (
        ROOT / "docs" / "runbook" / "SUB-AUTO-002-production-subscription-automation.md"
    ).read_text(encoding="utf-8")
    db_restore = (PRODUCTION / "production-db-restore.sh").read_text(encoding="utf-8")
    materialize = (PRODUCTION / "materialize-ssm-env.sh").read_text(encoding="utf-8")
    https = (PRODUCTION / "https.sh").read_text(encoding="utf-8")
    script_tests = (PRODUCTION / "test-production-scripts.sh").read_text(encoding="utf-8")
    nginx_tests = (PRODUCTION / "test-production-nginx.sh").read_text(encoding="utf-8")
    compose_tests = (PRODUCTION / "test-production-compose.sh").read_text(encoding="utf-8")
    ec2_alarm_common = (PRODUCTION / "ec2-status-check-alarm-common.sh").read_text(encoding="utf-8")
    ec2_alarm_create = (PRODUCTION / "create-ec2-status-check-alarm.sh").read_text(encoding="utf-8")
    ec2_alarm_cleanup = (PRODUCTION / "cleanup-ec2-status-check-alarm.sh").read_text(encoding="utf-8")
    auth_session_smoke = (PRODUCTION / "verify-production-auth-session-smoke.sh").read_text(encoding="utf-8")
    auth_session_tests = (PRODUCTION / "test-production-auth-session-smoke.sh").read_text(encoding="utf-8")
    auth_member = (PRODUCTION / "create-production-auth-smoke-member.sh").read_text(encoding="utf-8")
    auth_member_pty = (PRODUCTION / "test-create-production-auth-smoke-member.py").read_text(encoding="utf-8")
    auth_member_lifecycle = (
        PRODUCTION / "test-create-production-auth-smoke-member-lifecycle.sh"
    ).read_text(encoding="utf-8")
    release_scripts = "\n".join((common, deploy, rollback, automation_control, db_restore))
    require("metrics-proxy" not in common and "metrics-proxy" not in deploy and "metrics-proxy" not in rollback, "Application release lifecycle must not own metrics-proxy")
    rollback_initialize = rollback[
        rollback.index("initialize_rollback_context() {") :
        rollback.index("\n}", rollback.index("initialize_rollback_context() {")) + 2
    ]

    require('APPROVED_AWS_REGION="ap-northeast-2"' in ec2_alarm_common, "OPS-015 alarm region must be Seoul")
    for variable in ("PAWCYCLE_ALERT_REGION", "PAWCYCLE_ALERT_INSTANCE_ID", "PAWCYCLE_ALERT_EMAIL", "PAWCYCLE_ALERT_ACCOUNT_ID", "PAWCYCLE_ALERT_RESOURCE_PREFIX"):
        require(variable in ec2_alarm_common, f"OPS-015 alarm input is missing: {variable}")
    require("StatusCheckFailed" in ec2_alarm_create and "--period 60" in ec2_alarm_create and "--evaluation-periods 2" in ec2_alarm_create and "--datapoints-to-alarm 2" in ec2_alarm_create, "OPS-015 metric period and evaluation contract is missing")
    require("--threshold 1" in ec2_alarm_create and "--comparison-operator GreaterThanOrEqualToThreshold" in ec2_alarm_create, "OPS-015 threshold contract is missing")
    require("--alarm-actions \"$topic_arn\"" in ec2_alarm_create and "--ok-actions \"$topic_arn\"" in ec2_alarm_create, "OPS-015 ALARM and OK must use the same SNS topic")
    require("existing alarm does not match the approved StatusCheckFailed contract" in ec2_alarm_common, "OPS-015 must not overwrite a conflicting alarm")
    require("SNS topic does not have exactly one approved email subscription" in ec2_alarm_common, "OPS-015 cleanup must reject unexpected SNS subscribers")
    require("ActionsEnabled==\\`true\\`" in ec2_alarm_common and "DatapointsToAlarm==\\`2\\`" in ec2_alarm_common, "OPS-015 alarm action and datapoint contract is missing")
    require("validate_runtime_target" in ec2_alarm_common and "sts get-caller-identity" in ec2_alarm_common and "ec2 describe-instances" in ec2_alarm_common, "OPS-015 must validate the AWS account and EC2 target before mutation")

    require(
        r"^https://([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)\.duckdns\.org$" in auth_session_smoke,
        "OPS-017 must accept only a lowercase single-label DuckDNS HTTPS origin",
    )
    require('APPROVED_DOMAIN_FILE="/opt/pawcycle/state/https-domain"' in auth_session_smoke and "URL does not match the approved production HTTPS domain state" in auth_session_smoke, "OPS-017 must bind credentials to the approved production HTTPS domain state")
    require("--disable\n    --silent" in auth_session_smoke and "--proto '=https'" in auth_session_smoke and "--tlsv1.2" in auth_session_smoke and "--max-redirs 0" in auth_session_smoke, "OPS-017 HTTPS requests must ignore curlrc, verify TLS, and reject redirects")
    require(not re.search(r"^[ \t]+(?:-k|--insecure|-L|--location)(?:[ \t]|$)", auth_session_smoke, re.MULTILINE), "OPS-017 must not disable TLS verification or follow redirects")
    require("set +x" in auth_session_smoke and "set -x" not in auth_session_smoke, "OPS-017 must disable shell tracing")
    require("exec 3<>/dev/tty" in auth_session_smoke and "[[ -t 3 ]]" in auth_session_smoke, "OPS-017 credentials must require an interactive terminal")
    require("IFS= read -r -u 3 OPERATOR_EMAIL" in auth_session_smoke and "IFS= read -r -s -u 3 OPERATOR_PASSWORD" in auth_session_smoke, "OPS-017 credentials must use TTY-only input with hidden password echo")
    require("--header \"@$header_file\"" in auth_session_smoke and "--data-binary @-" in auth_session_smoke, "OPS-017 credentials and CSRF token must not be placed in curl arguments")
    require("chmod 700 \"$WORK_DIR\"" in auth_session_smoke and "chmod 600 \"$sensitive_file\"" in auth_session_smoke, "OPS-017 sensitive temporary paths must use restrictive modes")
    require("trap cleanup EXIT" in auth_session_smoke and "trap 'exit 130' INT" in auth_session_smoke and "trap 'exit 143' TERM" in auth_session_smoke, "OPS-017 temporary credentials and files must be cleaned on all exits")
    for path in ("/products", "/login", "/api/products", "/api/auth/csrf", "/api/auth/login", "/api/auth/me", "/api/auth/logout"):
        require(path in auth_session_smoke, f"OPS-017 smoke path is missing: {path}")
    require("session ID did not rotate after login" in auth_session_smoke and "CSRF token did not rotate after login" in auth_session_smoke, "OPS-017 must verify session and CSRF rotation")
    require("session cookie is not Secure and HttpOnly" in auth_session_smoke, "OPS-017 must verify authenticated session cookie security attributes")
    require("login and current member identities do not match" in auth_session_smoke, "OPS-017 must compare login and current member identities")
    require("STALE_COOKIE_JAR" in auth_session_smoke and "stale session rejection" in auth_session_smoke, "OPS-017 must reject the pre-logout session")
    for scenario in (
        "auth-code-missing",
        "csrf-missing",
        "csrf-not-rotated",
        "session-not-rotated",
        "cookie-not-secure",
        "cookie-not-http-only",
        "member-mismatch",
        "logout-failure",
        "authenticated-after-logout",
        "mid-request-failure",
    ):
        require(f"run_case {scenario} " in auth_session_tests, f"OPS-017 fake HTTP regression scenario is missing: {scenario}")
    require("different-duckdns-host" in auth_session_tests and "run_invalid_url_case \\\n  different-duckdns-host" in auth_session_tests, "OPS-017 approved domain mismatch regression scenario is missing")
    require("run_non_tty_case" in auth_session_tests, "OPS-017 non-TTY regression scenario is missing")

    require("set +x" in auth_member and "set -x" not in auth_member, "OPS-020 must disable shell tracing")
    require(
        auth_member.index("exec 3<>/dev/tty") < auth_member.index("docker pull"),
        "OPS-020 must reject a missing TTY before Docker access",
    )
    require(
        '[[ "$EUID" == "0" ]]' in auth_member
        and auth_member.index('[[ "$EUID" == "0" ]]') < auth_member.index("docker pull"),
        "OPS-020 must reject non-root execution before Docker access",
    )
    require(
        "stty -echo" in auth_member
        and 'stty "$TTY_STATE"' in auth_member
        and "trap cleanup EXIT" in auth_member
        and "trap 'exit 130' INT" in auth_member
        and "trap 'exit 143' TERM" in auth_member,
        "OPS-020 terminal echo restoration is incomplete",
    )
    require(
        "coproc MEMBER_CONTAINER" in auth_member
        and 'printf \'%s\\n%s\\n\' "$OPERATOR_EMAIL" "$OPERATOR_PASSWORD" >&"$MEMBER_INPUT_FD"'
        in auth_member,
        "OPS-020 credentials must cross only a Bash builtin stdin pipe",
    )
    docker_run_start = auth_member.index("docker run")
    docker_run_end = auth_member.index("2>/dev/null", docker_run_start)
    docker_run_block = auth_member[docker_run_start:docker_run_end]
    for option in (
        "--rm",
        "--interactive",
        "--log-driver none",
        "--read-only",
        "--tmpfs /tmp:size=64m,mode=1777",
        "--user pawcycle",
        "--security-opt no-new-privileges:true",
        "--cap-drop ALL",
        "--memory 640m",
        "--cpus 0.75",
        "--pids-limit 256",
    ):
        require(option in auth_member, f"OPS-020 Docker security option is missing: {option}")
    require(
        "--publish" not in docker_run_block
        and re.search(r"(?:^|\s)-p(?:\s|$)", docker_run_block) is None
        and "--restart" not in docker_run_block
        and "--volume" not in docker_run_block,
        "OPS-020 must not publish ports, restart, or mount a volume",
    )
    require(
        "org.opencontainers.image.revision" in auth_member
        and "BACKEND_DIGEST" in auth_member
        and "@sha256:" in auth_member
        and "latest" not in auth_member,
        "OPS-020 immutable Backend image verification is incomplete",
    )
    require(
        'exec 9>>"$DEPLOY_LOCK_FILE"' in auth_member
        and "flock --nonblock 9" in auth_member
        and auth_member.index("flock --nonblock 9") < auth_member.index("docker pull"),
        "OPS-020 must share the production release lock before Docker access",
    )
    require(
        "MEMBER_COMMAND_TIMEOUT_SECONDS=180" in auth_member
        and "exec timeout" in auth_member
        and "--kill-after=10s" in auth_member
        and "terminate_member_process" in auth_member,
        "OPS-020 one-shot execution and cleanup must be time bounded",
    )
    require(
        "validate_backend_env_contract" in auth_member
        and "stream_backend_env" in auth_member
        and "--env-file <(stream_backend_env)" in auth_member,
        "OPS-020 must stream the Compose runtime env without persisting a converted file",
    )
    require(
        "production Backend image reference is invalid" in auth_member
        and "production Backend revision is invalid" in auth_member
        and "production Backend image identity is invalid" in auth_member
        and "production Backend is not healthy" in auth_member,
        "OPS-020 must match the running Backend to the approved release",
    )
    require(
        "pawcycle-production-data" in auth_member
        and "--spring.main.web-application-type=none" in auth_member
        and "--pawcycle.maintenance.create-auth-smoke-member.enabled=true" in auth_member
        and "--spring.flyway.enabled=false" in auth_member,
        "OPS-020 maintenance and data-network contract is incomplete",
    )
    require(
        "docker rm --force" in auth_member
        and "CONTAINER_STARTED" in auth_member
        and "com.pawcycle.ops020.scope" in auth_member,
        "OPS-020 ownership-scoped one-shot Container cleanup is incomplete",
    )
    require(
        "password was echoed to the terminal" in auth_member_pty
        and "raw Docker stderr was exposed" in auth_member_pty
        and "Docker run contract exposed or enabled" in auth_member_pty,
        "OPS-020 fake Docker and PTY regressions are incomplete",
    )
    require(
        "runtime-env-contract-ok" in auth_member_pty
        and "process.terminate()" in auth_member_pty
        and '"--label", "com.pawcycle.ops020.scope=auth-smoke-member"' in auth_member_pty,
        "OPS-020 PTY test must cover runtime env streaming and bounded child cleanup",
    )
    require(
        "pawcycle-production" not in auth_member_lifecycle
        and "/opt/pawcycle" not in auth_member_lifecycle
        and "com.pawcycle.ops020.test" in auth_member_lifecycle
        and "mysql:8.4.10@sha256:" in auth_member_lifecycle,
        "OPS-020 lifecycle test must use only labeled isolated MySQL resources",
    )
    for evidence in (
        "one-shot lifecycle changed the schema",
        "one-shot lifecycle changed Flyway history",
        "duplicate execution changed the existing member",
        "one-shot lifecycle created non-member domain data",
    ):
        require(evidence in auth_member_lifecycle, f"OPS-020 lifecycle evidence is missing: {evidence}")

    require("^ghcr\\.io/" in common, "deploy input must be restricted to GHCR")
    require("^[0-9a-f]{40}$" in common, "deploy input must require a full commit SHA")
    require("org.opencontainers.image.revision" in common and ".RepoDigests" in common, "revision and digest preflight are required")
    require(MYSQL_IMAGE in common and PROXY_IMAGE in common, "base images must be pinned to approved immutable digests")
    require("image digest drift detected for previously verified release SHA" in common, "same-SHA digest drift must fail closed")
    require("MYSQL_DIGEST=%s" in common and "PROXY_DIGEST=%s" in common, "base image digests must be part of each SHA record")
    require("cmp -s" in common, "existing SHA image records must be compared rather than overwritten")

    for release_contract_path in (
        "infra/production/compose.yaml",
        "infra/production/nginx.conf",
        "infra/production/nginx.https.conf",
    ):
        require(release_contract_path in common, f"release contract path is missing: {release_contract_path}")
    for control_path in (
        "infra/production/release-common.sh",
        "infra/production/deploy.sh",
        "infra/production/rollback.sh",
        "infra/production/subscription-automation-control.sh",
        "infra/production/subscription-automation-preflight.sh",
        "infra/production/production-db-restore.sh",
        "infra/production/materialize-ssm-env.sh",
    ):
        require(control_path in common, f"control worktree path is missing: {control_path}")
    require(
        "RELEASE_CONTRACT_PATHS" in common
        and "CONTROL_WORKTREE_PATHS" in common
        and "git status --porcelain --untracked-files=all" in common,
        "release compatibility and clean control worktree boundaries are incomplete",
    )
    require(
        "validate_runtime_contract_compatibility" in common
        and "release_contract_changed" in common
        and "require_contract_boundary_approval" in common
        and "production release contract differs from the approved contract SHA" in common,
        "release contract compatibility gate is missing",
    )
    require(
        "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED" in materialize
        and "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE" in materialize
        and "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS" in materialize
        and "validate_subscription_automation_settings" in common
        and "require_subscription_automation_mode false" in deploy
        and "subscription-automation-control.sh deactivate" in rollback
        and "stop Backend then escalate to the user" in rollback,
        "application deploy and rollback must require an explicit Scheduler OFF runtime",
    )
    require(
        "MIGRATION_BUNDLE_PATH" in common
        and 'git -C "$CONTROL_WORKTREE_ROOT" cat-file' in common
        and 'git -C "$CONTROL_WORKTREE_ROOT" diff' in common
        and "migration_bundle_changed" in common
        and "require_migration_boundary_approval" in common
        and "require_no_migration_boundary_rollback" in rollback
        and "automatic pre-migration release restoration is blocked" in deploy
        and "automatic contract-boundary restoration is blocked" in deploy
        and "MySQL was preserved" in deploy,
        "schema-boundary automatic and manual pre-migration rollback gates are incomplete",
    )
    require(
        "--max-due-candidates" in automation_control
        and "activate_backend_runtime" in automation_control
        and "stop_backend_service" in automation_control
        and "Scheduler deactivation postflight failed; Scheduler remains OFF" in automation_control
        and "subscription-automation-preflight.sh" in automation_control,
        "Scheduler activation must be a separate bounded and fail-closed command",
    )
    require(
        "compose stop backend || true" not in common
        and "compose ps --status running --quiet backend" in common
        and "force-recreate proxy" in common,
        "Backend stop and proxy upstream refresh must fail closed",
    )
    for evidence in (
        "FLYWAY_V9=",
        "FLYWAY_V10=",
        "FLYWAY_V11=",
        "UNIQUE_SCHEDULE_ORDER=",
        "DUE_INDEX=",
        "DUE_CANDIDATE_COUNT=",
        "OLDEST_DUE_DATE=",
        "DUPLICATE_ORDER_SCHEDULE_GROUPS=",
        "ORDERLESS_ADVANCED_SCHEDULES=",
        "ORDER_SNAPSHOT_CARDINALITY_ANOMALIES=",
        "PROCESSED_ACTIVE_FUTURE_SCHEDULE_ANOMALIES=",
        "SUBSCRIPTION_AUTOMATION_PREFLIGHT=PASS",
    ):
        require(evidence in automation_preflight, f"SUB-AUTO-002 read-only preflight evidence is missing: {evidence}")
    require(
        "raw database output was suppressed" in automation_preflight
        and "subscription, Schedule, or Order IDs" in automation_preflight,
        "SUB-AUTO-002 preflight output must remain aggregate and identifier-free",
    )
    validate_preflight_projection_contract(automation_preflight)
    for boundary in (
        "Scheduler OFF",
        "migration bundle",
        "자동복귀를 차단",
        "Flyway repair",
        "OPS-025",
        "실제 실행은 별도 고위험 사용자 승인",
    ):
        require(boundary in automation_runbook, f"SUB-AUTO-002 Runbook boundary is missing: {boundary}")
    require(
        "production control contract worktree is not clean" in common
        and "production control SHA differs from contract state" in common
        and "requested runtime contract SHA does not match current control HEAD" in common,
        "explicit clean control HEAD approval gate is missing",
    )
    require(
        "PENDING_CONTRACT_SHA" in common
        and 'write_state contract-sha "$PENDING_CONTRACT_SHA"' in deploy
        and "load_runtime_contract" in common
        and "load_runtime_contract" in rollback
        and "--adopt-contract-sha" in deploy
        and "previous-contract-sha" in deploy
        and "previous-contract-sha" in rollback,
        "application release and production control state must be separated",
    )
    require(
        "--operation <preflight|deploy|control-adopt>" in deploy
        and "initialize_read_only_release_context" in deploy
        and "PAWCYCLE_PREFLIGHT_RECORD_IMAGES=false" in deploy
        and "passed without changing containers, DB, or state" in deploy
        and "record_images" in common
        and "--approved-contract-from-sha" in deploy
        and "--approved-control-sha" in deploy
        and "--approved-migration-target-sha" in deploy,
        "read-only approval preflight and repeated deploy boundary gates are incomplete",
    )
    require(
        "require_control_only_contract_adoption" in common
        and "current_clean_control_sha" in common
        and '"$target_sha" == "$current_release_sha"' in common
        and "running $service is not healthy during control contract adoption" in common
        and "control-only contract state publication failed" in deploy
        and "Production control contract adopted without Application activation" in deploy,
        "control-only contract adoption must bind both approved Control SHAs, revalidate the running Release, and publish only contract state",
    )
    require(
        "contract-boundary-missing-output" in script_tests
        and "contract-boundary-preflight-output" in script_tests
        and "migration-boundary-missing-output" in script_tests
        and "both-boundaries-missing-output" in script_tests
        and 'boundary_state/previous-contract-sha")" == "$SHA_A"' in script_tests
        and "automatic contract-boundary restoration is blocked" in script_tests,
        "contract and migration boundary regression coverage is missing",
    )
    require(
        "control_only_adopt" in script_tests
        and "prior-control-mismatch-output" in script_tests
        and "new-control-mismatch-output" in script_tests
        and "control-only-$failure_case" in script_tests
        and "dirty-control health smoke state-publication" in script_tests
        and "FAKE_STATE_PUBLICATION_FAIL" in script_tests,
        "control-only contract adoption regression coverage is missing",
    )
    require(
        "acquire_release_lock" in rollback_initialize
        and 'TARGET_SHA="$(read_state_sha previous-sha)"' in rollback_initialize
        and rollback_initialize.index("acquire_release_lock")
        < rollback_initialize.index('read_state_sha previous-sha')
        and 'TARGET_SHA="$(<"$PAWCYCLE_STATE_DIR/previous-sha")"' not in rollback,
        "implicit rollback target must be validated and read only after the shared release lock",
    )
    require(
        "FAKE_FLOCK_PREVIOUS_STATE" in script_tests
        and 'export FAKE_FLOCK_PREVIOUS_SHA="$SHA_C"' in script_tests
        and 'rollback_without_sha "$rollback_stale_state"' in script_tests
        and 'rollback_stale_state/current-sha")" == "$SHA_C"' in script_tests,
        "implicit rollback stale-target regression evidence is missing",
    )
    require("--pull never" in common, "activation must not replace preflighted images")
    require(
        'PAWCYCLE_MYSQL_VOLUME="$ACTIVE_MYSQL_VOLUME"' in common
        and "load_active_mysql_volume" in common
        and "active MySQL volume state is missing" in common
        and "mysql is not using the active MySQL volume state" in common,
        "deploy and rollback must bind Compose and the running MySQL mount to protected active volume state",
    )
    require('PAWCYCLE_HTTP_PORT="80"' in common, "production HTTP port must ignore ambient overrides")
    require('PAWCYCLE_HTTPS_PORT="443"' in common, "production HTTPS port must ignore ambient overrides")
    require("for service in mysql backend frontend" in common, "health wait must cover MySQL and both application services")
    require("wait_healthy proxy" in common, "health wait must cover Nginx")
    require("docker exec" in common and "127.0.0.1:8081/products" in common, "release smoke must bypass public redirects")
    require("previous release was restored" in deploy, "automatic restoration evidence is missing")
    require("database restoration or volume deletion" in rollback, "rollback data boundary is missing")
    require(not re.search(r"docker\s+(?:compose\s+)?(?:volume\s+rm|.*down.*(?:-v|--volumes))", release_scripts), "release scripts must not delete volumes")
    for evidence in (
        "initial release did not fail when smoke failed",
        "target release did not fail when smoke failed",
        "same-SHA application digest drift did not fail closed",
        "pinned base image digest drift did not fail closed",
        "missing runtime contract state did not fail closed",
        "missing runtime contract state failed for the wrong reason",
        "control SHA drift without explicit adoption did not fail closed",
        "incompatible control contract transition did not fail closed",
        "dirty production control worktree did not fail closed",
        "rollback with control SHA drift did not fail closed",
        "incompatible production runtime contract did not fail closed",
        "rollback with incompatible production runtime contract did not fail closed",
        "application deploy enabled the Scheduler",
        "unexpected due candidate count did not block activation",
        "duplicate Order aggregate anomaly did not fail closed",
        "deactivation aggregate anomaly did not leave Scheduler enabled",
        "activation postflight failure left a Scheduler ON Backend running",
        "Backend stop failure was not fail-closed",
        "migration comparison depended on caller CWD",
        "backend replacement did not refresh proxy routing",
        "database restore enabled the Scheduler",
        "schema-boundary target failure was reported as success",
        "manual pre-migration rollback did not fail closed",
        "rollback previous-sha symlink did not fail closed",
        "rollback previous-sha mode violation did not fail closed",
        "missing active MySQL volume state did not fail closed",
        "candidate cutover failure was reported as success",
        "candidate manifest label mismatch was reported as success",
        "OPS-025 production DB restore fake success, failure, cutover, and revert lifecycle tests passed",
    ):
        require(evidence in script_tests, f"release regression evidence is missing: {evidence}")

    for leaf in ("MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD"):
        require(f"get_parameter {leaf}" in materialize, f"required SSM parameter is missing: {leaf}")
    for leaf in (
        "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED",
        "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE",
        "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS",
    ):
        require(f"get_parameter {leaf}" in materialize, f"required automation SSM parameter is missing: {leaf}")
    require("--with-decryption" in materialize, "SecureString decryption flag is required")
    require(materialize.count("chmod 600") >= 2, "runtime files and completion marker must be mode 600")
    require("set +x" in materialize, "materializer must disable shell tracing")
    require("realpath -e" in materialize, "previous runtime bundle deletion must validate the resolved path")
    require('rm -rf -- "$PREVIOUS_BUNDLE"' in materialize, "previous plaintext runtime bundle must be removed")
    require('flock --nonblock 9' in materialize, "runtime materialization must reject concurrent writers")
    require("concurrent runtime materialization did not fail closed" in script_tests, "materialization concurrency regression evidence is missing")

    require(CERTBOT_IMAGE in common, "Certbot must be pinned to the approved multi-platform digest")
    require(CERTBOT_IMAGE in nginx_tests, "Nginx tests must exercise the same pinned Certbot image")
    require(
        "--platform linux/amd64" not in https
        and "--platform linux/amd64" not in common
        and "--platform linux/amd64" not in nginx_tests,
        "Production HTTPS runtime and Nginx tests must use the execution host architecture",
    )
    require("set +x" in https, "HTTPS operations must disable shell tracing")
    require("certonly --webroot" in https and "renew --cert-name" in https, "HTTP-01 issuance and renewal commands are required")
    require("--dry-run" in https and "nginx -s reload" in https, "renewal rehearsal and post-renew reload are required")
    require("certificate renewal failed; Nginx was not reloaded" in https, "renewal failure must preserve the running service")
    require(not re.search(r"docker\s+(?:compose\s+)?(?:volume\s+rm|.*down.*(?:-v|--volumes))", https), "HTTPS script must not delete volumes")
    require("current release state is missing" in https, "HTTPS operations must bind to the active release")
    require("mode must be 600" in common and "content is invalid" in common, "HTTPS state marker must fail closed")
    require("HTTPS domain state must be a regular non-symlink file" in common, "approved HTTPS domain must reject symlinks")
    require("HTTPS domain state mode must be 600" in common, "approved HTTPS domain must be mode 600")
    require("select_https_domain" in https and "approve_https_domain" in https, "HTTPS domain approval must be separated from runtime selection")
    require("verify_challenge_path\n  approve_https_domain" not in https, "local bootstrap challenge validation must not approve the domain")
    require(
        'validate_https_certificate "$HTTPS_DOMAIN"\n    approve_https_domain' in https,
        "HTTPS domain approval must happen only after certificate validation",
    )
    require('local expected_domain="${1:-}"' in common, "certificate validation must accept a pre-approval candidate hostname")
    require("generated HTTPS Nginx configuration mode must be 600" in common, "generated Nginx state must be mode 600")
    require("verify_https_release || return 1" in common, "deploy and rollback must enforce the HTTPS release gate")
    require("from cryptography import x509" in common, "certificate parsing must use the public cryptography API")
    require("from cryptography import x509" in nginx_tests, "pinned Certbot image must exercise the public certificate API")
    require("_test_decode_cert" not in common + https, "private CPython certificate parsing API is forbidden")
    require("--cacert" in compose_tests and "--insecure" not in compose_tests, "Compose HTTPS smoke must verify its test certificate")
    require(
        "BOOTSTRAP_DOMAIN" in compose_tests
        and '--header "Host: $BOOTSTRAP_DOMAIN"' in compose_tests
        and "/.well-known/acme-challenge/probe" in compose_tests,
        "Compose bootstrap must verify catch-all HTTP-01 routing for an unapproved candidate hostname",
    )
    require("FAKE_CHALLENGE_FAIL_AT_COUNT" in script_tests, "challenge recovery failure must be tested independently from hostname")
    require(
        "bootstrap HTTP service was restored, but HTTP-01 challenge path validation failed" in https
        and "HTTPS activation and bootstrap HTTP restoration both failed" in https,
        "HTTPS activation recovery errors must distinguish challenge failure from restoration failure",
    )
    for evidence in (
        "HTTPS release gate failure changed current SHA",
        "HTTPS rollback gate failure changed current SHA",
        "challenge probe remained after failed validation",
        "failed pre-approval flow persisted HTTPS domain",
        "certificate issuance failure approved HTTPS domain",
        "certificate validation failure approved HTTPS domain",
        "domain candidate cleanup failure approved HTTPS domain",
        "different HTTPS domain was accepted after certificate approval",
        "different HTTPS domain was accepted after HTTPS activation",
        "HTTPS activation failure did not fail after challenge recovery error",
        "HTTPS activation failure did not fail after bootstrap recovery failure",
        "HTTPS domain symlink did not fail closed",
    ):
        require(evidence in script_tests, f"HTTPS regression evidence is missing: {evidence}")


def validate_nginx() -> None:
    bootstrap = (PRODUCTION / "nginx.conf").read_text(encoding="utf-8")
    https = (PRODUCTION / "nginx.https.conf").read_text(encoding="utf-8")

    require("/.well-known/acme-challenge/" in bootstrap, "bootstrap HTTP-01 location is missing")
    require("listen 8081" in bootstrap, "bootstrap internal smoke listener is missing")
    require("listen 443 ssl" in https, "HTTPS listener is missing")
    require("listen 80 default_server" in https and "return 444" in https, "unknown HTTP Host must fail closed")
    require("ssl_reject_handshake on" in https, "unknown TLS SNI must fail closed")
    require("server_name __PAWCYCLE_DOMAIN__" in https, "approved runtime hostname placeholder is missing")
    require("return 301 https://__PAWCYCLE_DOMAIN__$request_uri" in https, "redirect must use only the approved hostname")
    require("https://$host$request_uri" not in https, "untrusted Host must not be reflected into redirects")
    require(https.count("/etc/letsencrypt/live/pawcycle-production/fullchain.pem") == 2, "both TLS server contexts must load the certificate")
    require(https.count("/etc/letsencrypt/live/pawcycle-production/privkey.pem") == 2, "both TLS server contexts must load the certificate key")
    require("/.well-known/acme-challenge/" in https, "HTTPS-mode HTTP-01 exception is missing")
    require("/etc/letsencrypt/live/pawcycle-production/fullchain.pem" in https, "stable full chain path is missing")
    require("/etc/letsencrypt/live/pawcycle-production/privkey.pem" in https, "stable private key path is missing")
    require(https.count("proxy_set_header X-Forwarded-Proto https") >= 4, "HTTPS forwarding contract is incomplete")
    require("listen 8081" in https, "HTTPS internal smoke listener is missing")


def validate_backup_restore() -> None:
    backup_restore = (PRODUCTION / "db-backup-restore.sh").read_text(encoding="utf-8")
    backup_tests = (PRODUCTION / "test-db-backup-restore.sh").read_text(encoding="utf-8")
    runbook = (ROOT / "docs" / "runbook" / "OPS-013-production-db-backup-restore.md").read_text(encoding="utf-8")
    logical_commands = re.sub(r"\\\n\s*", " ", backup_restore)

    require(MYSQL_IMAGE in backup_restore, "restore verification must use the production pinned MySQL image")
    require(
        'PRODUCTION_MYSQL_VOLUME="pawcycle-production-mysql-data"' in backup_restore,
        "backup source volume must be the stable production MySQL volume",
    )
    require(
        'PRODUCTION_PROJECT="pawcycle-production"' in backup_restore,
        "backup source must be selected from the production Compose project",
    )
    require("--single-transaction" in backup_restore and "--quick" in backup_restore, "logical dump consistency options are missing")
    require("--skip-lock-tables" in backup_restore, "logical backup must not lock production tables")
    require(
        all(option in backup_restore for option in ("--routines", "--events", "--triggers", "--hex-blob", "--set-gtid-purged=OFF")),
        "logical dump coverage or isolated restore safety options are incomplete",
    )
    require("gzip --test" in backup_restore, "compressed dump integrity must be checked")
    require(
        "checksum file must contain exactly one entry" in backup_restore
        and "checksum target filename is invalid" in backup_restore
        and 'actual_hash="$(sha256sum "$file"' in backup_restore,
        "downloaded checksum files must bind one validated hash to the expected local basename",
    )
    require(
        'upload_and_verify "${base_key}.complete"' in backup_restore
        and 'upload_and_verify "${base_key}.verify.sha256"' in backup_restore
        and backup_restore.index('upload_and_verify "${base_key}.complete"')
        > backup_restore.index('upload_and_verify "${base_key}.verify.sha256"'),
        "S3 completion marker must be uploaded after the verified backup object set",
    )
    require(
        'production MySQL changed during backup verification' in backup_restore
        and 'upload_and_verify "${base_key}.complete"' in backup_restore
        and backup_restore.index('production MySQL changed during backup verification')
        < backup_restore.index('upload_and_verify "${base_key}.complete"'),
        "production MySQL identity and health must be rechecked before publishing the completion marker",
    )
    require("--server-side-encryption AES256" in backup_restore, "every S3 upload must explicitly request SSE-S3")
    require("--storage-class STANDARD" in backup_restore, "every S3 upload must explicitly use S3 Standard")
    require(
        "MAX_SINGLE_UPLOAD_BYTES=5000000000" in backup_restore
        and "MAX_METADATA_OBJECT_BYTES=1048576" in backup_restore
        and "object_size_limit" in backup_restore
        and "backup object exceeds its approved S3 upload size limit" in backup_restore,
        "single-request S3 uploads must fail before the decimal 5 GB object size limit",
    )
    require(
        "head_object_size" in backup_restore
        and "S3 object exceeds the approved download size limit" in backup_restore
        and 'complete_size="$(head_object_size' in backup_restore
        and 'get_object "${base_key}.complete"' in backup_restore
        and backup_restore.index('complete_size="$(head_object_size')
        < backup_restore.index('get_object "${base_key}.complete"'),
        "all backup object sizes and encryption metadata must be checked before any restore download",
    )
    require(
        "insufficient free disk to download the verified backup object set" in backup_restore,
        "restore downloads must reserve local work disk before fetching objects",
    )
    require(
        "gzip --decompress --stdout" in backup_restore
        and "uncompressed logical dump size" in backup_restore
        and "compressed_size * 8" not in backup_restore,
        "restore disk preflight must use the measured uncompressed dump size rather than a compression-ratio guess",
    )
    require("get-public-access-block" in backup_restore, "bucket Public Access Block must be verified")
    require("get-bucket-encryption" in backup_restore, "bucket SSE-S3 default encryption must be verified")
    require("get-bucket-versioning" in backup_restore, "bucket versioning must be rejected by the retention preflight")
    require(
        'APPROVED_AWS_REGION="ap-northeast-2"' in backup_restore
        and '[[ "$1" == "$APPROVED_AWS_REGION" ]]' in backup_restore,
        "backup execution must fail closed outside the approved Seoul region",
    )
    require(
        "db-backup-restore.sh backup [--state-dir <path>]" in backup_restore
        and "db-backup-restore.sh restore-verify --backup-id <id> [--state-dir <path>]" in backup_restore
        and all(f"\n      {flag})" not in backup_restore for flag in ("--bucket", "--region", "--prefix"))
        and all(
            f'PAWCYCLE_BACKUP_{name}="${{PAWCYCLE_BACKUP_{name}:-${name}}}"' in backup_tests
            for name in ("BUCKET", "REGION", "PREFIX")
        ),
        "S3 identifiers must be accepted only through the PAWCYCLE_BACKUP environment variables",
    )
    require(
        "PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" in backup_restore
        and "--expected-bucket-owner" in backup_restore
        and "12-digit AWS account ID" in backup_restore,
        "every S3 API request must bind to the expected bucket owner",
    )
    require(
        "write_restore_manifest" in backup_restore
        and "write_source_manifest" not in backup_restore
        and 'create_restore_mysql\n  import_dump\n  write_restore_manifest "$manifest"' in backup_restore,
        "backup metadata must be generated from the isolated import of the dump snapshot",
    )
    require(
        "get-bucket-lifecycle-configuration" in backup_restore
        and "--query 'length(Rules)'" in backup_restore
        and '[[ "$lifecycle_rule_count" == "1" ]]' in backup_restore
        and "Expiration.Days==\\`14\\`" in backup_restore,
        "the dedicated bucket must have exactly one enabled 14-day expiration lifecycle for the requested prefix",
    )
    require("AWS request failed; bucket and object identifiers were suppressed" in backup_restore, "AWS failures must not print bucket identifiers")
    require(
        all(
            name in backup_restore
            for name in (
                "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI",
                "AWS_CONTAINER_CREDENTIALS_FULL_URI",
                "AWS_EC2_METADATA_SERVICE_ENDPOINT",
                "AWS_ENDPOINT_URL",
                "AWS_ENDPOINT_URL_S3",
                "AWS_IGNORE_CONFIGURED_ENDPOINT_URLS=true",
            )
        ),
        "ambient container, IMDS, and service endpoint overrides must be rejected or ignored",
    )
    require(
        "AWS credentials and endpoint overrides must not come from the ambient environment" in backup_restore
        and "AWS_CONFIG_FILE=/dev/null" in backup_restore
        and "AWS_SHARED_CREDENTIALS_FILE=/dev/null" in backup_restore,
        "backup AWS access must remain bound to the EC2 instance role and normal S3 endpoint",
    )
    require(
        backup_restore.count('2>"$MYSQL_ERROR_FILE"') >= 5
        and "source MySQL metadata query failed" in backup_restore
        and "restore-decompression-failed" in backup_restore
        and "restore-sql-import-failed" in backup_restore,
        "MySQL and dump failures must suppress credential or row-bearing stderr",
    )
    require(
        "--protocol=TCP --host=127.0.0.1" in backup_restore
        and '"$MYSQL_DATABASE" --execute="SELECT 1;"' in backup_restore
        and "consecutive_successes >= 2" in backup_restore,
        "restore readiness must require two authenticated TCP queries against the target database",
    )
    require(
        'pipeline_status=("${PIPESTATUS[@]}")' in backup_restore
        and 'gzip_status="${pipeline_status[0]}"' in backup_restore
        and 'mysql_status="${pipeline_status[1]}"' in backup_restore,
        "restore import must preserve decompressor and MySQL client exit statuses",
    )
    require(
        '> /dev/null 2>"$MYSQL_ERROR_FILE"' in backup_restore,
        "logical restore client output must not expose dump statements or rows",
    )
    require("--network none" in backup_restore, "restore MySQL must use the Docker none network")
    require(
        re.search(r"\bdocker\s+(?:create|run)\b[^\n]*(?:\s-p(?:\s|$)|\s--publish(?:=|\s))", logical_commands) is None,
        "restore docker create or run commands must not publish host ports",
    )
    require(
        'production_mount' in backup_restore and "restore container must not mount the production MySQL volume" in backup_restore,
        "restore isolation must reject the production volume",
    )
    require(
        "production MySQL changed during backup verification" in backup_restore
        and "production MySQL changed during isolated restore verification" in backup_restore,
        "backup and restore verification must recheck the production MySQL identity and health",
    )
    require(
        "com.pawcycle.ops013.scope=restore" in backup_restore,
        "temporary restore resources need a cleanup ownership label",
    )
    require("trap cleanup_trap EXIT INT TERM" in backup_restore, "success and failure must clean temporary restore resources")
    require(
        "OPS-013 temporary resource cleanup failed" in backup_restore,
        "temporary resource cleanup failure must prevent a successful result",
    )
    require(
        'if (( status == 0 )) && [[ -n "$SUCCESS_MESSAGE" ]]' in backup_restore,
        "final success evidence must be emitted only after temporary resource cleanup",
    )
    require("set +x" in backup_restore, "backup and restore operations must disable shell tracing")
    require("install -d -m 700" in backup_restore and "chmod 600" in backup_restore, "root-only temporary path modes are incomplete")
    require(
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"' in backup_restore
        and 'export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"' in backup_restore,
        "database credentials must remain inside the source or isolated MySQL container",
    )
    require(
        not re.search(r"docker\s+exec[^\n]*--env[^\n]*(?:MYSQL_PWD|MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD)", backup_restore),
        "database credentials must not be passed through docker exec command arguments",
    )
    require(
        not re.search(r"docker\s+volume\s+rm[^\n]*pawcycle-production-mysql-data", backup_restore),
        "OPS-013 must never remove the production MySQL volume",
    )
    require(
        "PRODUCTION_VOLUME_CREATED=0" in backup_tests
        and 'if [[ "$PRODUCTION_VOLUME_CREATED" == "1" ]]' in backup_tests
        and "PRODUCTION_VOLUME_CREATED=1" in backup_tests,
        "the lifecycle test may remove the production-named fixture volume only when it created that volume",
    )
    require(
        "oversized S3 object was not rejected before download" in backup_tests
        and "untrusted checksum target filename was not rejected" in backup_tests,
        "download size and checksum target regression tests are required",
    )
    require(
        "전용 신규 빈 bucket만 허용" in runbook
        and "if ! aws s3api create-bucket" in runbook
        and "전용 신규 bucket 생성 실패: 기존 bucket을 변경하지 마세요." in runbook
        and "put-bucket-lifecycle-configuration" in runbook
        and "기존 bucket 재사용은 이 Runbook 범위에서 제외" in runbook
        and "put-bucket-policy" in runbook,
        "the Runbook must restrict full lifecycle and policy replacement to a dedicated new bucket",
    )
    require(
        "--preserve-env=PAWCYCLE_BACKUP_BUCKET,PAWCYCLE_BACKUP_REGION,PAWCYCLE_BACKUP_PREFIX,PAWCYCLE_BACKUP_EXPECTED_BUCKET_OWNER" in runbook
        and '--bucket "$BACKUP_BUCKET"' not in runbook
        and '--region "$BACKUP_REGION"' not in runbook
        and '--prefix "$BACKUP_PREFIX"' not in runbook,
        "the Runbook must pass backup identifiers only through the supported environment variables",
    )
    require(
        runbook.count("--state-dir /opt/pawcycle/state") >= 2
        and "active-mysql-volume" in runbook,
        "OPS-013 backup and restore verification must follow the protected active volume state",
    )
    for evidence in (
        "backup failure was reported as success",
        "upload failure was reported as success",
        "bucket encryption mismatch was reported as success",
        "bucket public access mismatch was reported as success",
        "bucket versioning mismatch was reported as success",
        "bucket lifecycle mismatch was reported as success",
        "overlapping bucket lifecycle rules were reported as success",
        "IMDS endpoint override was reported as success",
        "checksum mismatch was reported as success",
        "restore decompression failure stage was not reported",
        "restore SQL import failure stage was not reported",
        "verification mismatch was reported as success",
        "temporary restore container remained",
        "temporary restore volume remained",
        "temporary restore work file remained",
        "source production fixture changed during backup or restore verification",
        "source production volume was removed",
        "non-Seoul backup region was reported as success",
        "unexpected S3 bucket owner was reported as success",
    ):
        require(evidence in backup_tests, f"OPS-013 regression evidence is missing: {evidence}")


def validate_actual_production_restore() -> None:
    common = (PRODUCTION / "release-common.sh").read_text(encoding="utf-8")
    backup_restore = (PRODUCTION / "db-backup-restore.sh").read_text(encoding="utf-8")
    restore = (PRODUCTION / "production-db-restore.sh").read_text(encoding="utf-8")
    script_tests = (PRODUCTION / "test-production-scripts.sh").read_text(encoding="utf-8")
    backup_tests = (PRODUCTION / "test-db-backup-restore.sh").read_text(encoding="utf-8")
    runbook = (
        ROOT / "docs" / "runbook" / "OPS-025-production-db-restore.md"
    ).read_text(encoding="utf-8")
    workflow = (
        ROOT / ".github" / "workflows" / "validate-conventions.yml"
    ).read_text(encoding="utf-8")
    cutover_body = restore[
        restore.index("\ncutover() {") : restore.index("\nrevert() {")
    ]
    revert_body = restore[
        restore.index("\nrevert() {") : restore.index("\nmain() {")
    ]
    common_initialize = common[
        common.index("initialize_release_context() {") :
        common.index("\n}", common.index("initialize_release_context() {")) + 2
    ]
    transition_initialize = restore[
        restore.index("initialize_transition_context() {") :
        restore.index("\n}", restore.index("initialize_transition_context() {")) + 2
    ]
    candidate_runtime = backup_restore[
        backup_restore.index("prepare_candidate_runtime() {") :
        backup_restore.index("\nrecord_value() {")
    ]
    candidate_create = backup_restore[
        backup_restore.index("create_restore_mysql() {") :
        backup_restore.index("\nimport_dump() {")
    ]
    staged_activation = restore[
        restore.index("activate_database_release() {") :
        restore.index("\nrestore_source_after_failed_cutover() {")
    ]

    require(
        "restore-candidate --backup-id <id>" in backup_restore
        and "verify_prior_restore_record" in backup_restore
        and "db-restore-verified" in backup_restore,
        "candidate preparation must require a prior successful OPS-013 restore verification record",
    )
    require(
        "--network none" in backup_restore
        and "--env-file" not in candidate_create
        and "candidate-root-password" in candidate_runtime
        and "candidate-user-password" in candidate_runtime
        and "MYSQL_ROOT_PASSWORD_FILE=/run/secrets/mysql-root-password" in candidate_create
        and "MYSQL_PASSWORD_FILE=/run/secrets/mysql-user-password" in candidate_create
        and 'TEMP_VOLUME="$CANDIDATE_VOLUME"' in backup_restore
        and "PRESERVE_TEMP_VOLUME=1" in backup_restore,
        "candidate preparation must use file-injected credentials in an isolated retained volume",
    )
    require(
        "com.pawcycle.ops025.scope=candidate" in backup_restore
        and "com.pawcycle.ops025.source-volume" in backup_restore
        and "com.pawcycle.ops025.backup-sha256" in backup_restore
        and "com.pawcycle.ops025.manifest-sha256" in backup_restore,
        "candidate volume ownership and backup identity labels are incomplete",
    )
    require(
        "active-mysql-volume" in common
        and "previous-mysql-volume" in restore
        and 'PAWCYCLE_MYSQL_VOLUME="$ACTIVE_MYSQL_VOLUME"' in common,
        "active and recovery MySQL volume state must survive later deploy and rollback commands",
    )
    require(
        "acquire_release_lock" in common_initialize
        and common_initialize.index("acquire_release_lock")
        < common_initialize.index("load_active_mysql_volume")
        and "acquire_release_lock" in transition_initialize
        and transition_initialize.index("acquire_release_lock")
        < transition_initialize.index("read_state_sha")
        < transition_initialize.index("load_active_mysql_volume")
        and "another production release or database restore command is running" in common,
        "deploy, rollback, cutover, and revert must acquire the shared lock before protected state reads",
    )
    require(
        "compose stop proxy frontend backend" in cutover_body
        and "write_database_record" in cutover_body
        and "compose stop mysql" in cutover_body
        and cutover_body.index("compose stop proxy frontend backend")
        < cutover_body.index("write_database_record")
        < cutover_body.index("compose stop mysql"),
        "cutover must quiesce application writes before recording and stopping source MySQL",
    )
    require(
        "verify_active_database" in restore
        and all(
            field in restore
            for field in (
                "SCHEMA_SHA256",
                "FLYWAY_SHA256",
                "FLYWAY_COUNT",
                "TABLE_members",
                "TABLE_products",
                "TABLE_skus",
                "TABLE_subscriptions",
            )
        ),
        "cutover and revert must verify schema, Flyway history, and core table manifests",
    )
    require(
        "compose stop proxy frontend backend" in revert_body
        and "db-restore-revert-candidate" in revert_body
        and "candidate-current" in revert_body
        and "compose stop mysql" in revert_body
        and revert_body.index("compose stop proxy frontend backend")
        < revert_body.index("write_database_record")
        < revert_body.index("compose stop mysql")
        and revert_body.count(
            'restore_candidate_after_failed_revert "$candidate_volume" "$current_candidate_record"'
        ) >= 3
        and 'restore_candidate_after_failed_revert "$candidate_volume" "$candidate_record"'
        not in revert_body,
        "revert must snapshot the quiesced current candidate and use it for every post-snapshot fallback",
    )
    require(
        "FAKE_CANDIDATE_TABLE_COUNT=3" in script_tests
        and "grep -Fq 'TABLE_members=2' \"$STATE_DIR/db-restore-candidate\""
        in script_tests
        and 'grep -Fq "TABLE_${table}=3" "$STATE_DIR/db-restore-revert-candidate"'
        in script_tests,
        "candidate data drift fallback must be tested against the latest protected manifest",
    )
    first_manifest = staged_activation.index("verify_active_database")
    backend_start = staged_activation.index(
        "compose up --detach --pull never --remove-orphans backend frontend"
    )
    second_manifest = staged_activation.index(
        "verify_active_database", first_manifest + 1
    )
    proxy_start = staged_activation.index(
        "compose up --detach --pull never --no-deps --force-recreate proxy"
    )
    require(
        first_manifest < backend_start < second_manifest < proxy_start,
        "candidate/source manifests must be checked before application startup and again before proxy traffic",
    )
    require(
        "candidate cutover failed; source volume and application state were restored" in restore
        and "both MySQL volumes were preserved" in restore
        and "source database revert failed; candidate volume and application state were restored" in restore
        and "application write-path stop failed; source release reactivation was attempted without cutover" in restore,
        "cutover and revert failure boundaries are incomplete",
    )
    require(
        'validate_candidate_record "$source_volume" true' in restore
        and "refusing to remove a path outside the database restore state work prefix" in restore,
        "revert record revalidation or protected temporary cleanup boundary is missing",
    )
    require(
        re.search(r"docker\s+volume\s+rm", restore) is None
        and "docker cp" not in restore
        and "docker export" not in restore
        and "docker cp" not in backup_restore
        and "docker export" not in backup_restore,
        "actual production restore scripts must not delete volumes or copy raw data directories",
    )
    require(
        "Flyway history" in restore
        and "downgrade schema" in restore
        and "retry" in restore,
        "forbidden schema and automatic retry boundaries must be visible in the restore command",
    )
    for evidence in (
        "candidate cutover failure was reported as success",
        "application write-path stop failure was reported as success",
        "source revert activation failure was reported as success",
        "FAKE_CANDIDATE_TABLE_COUNT=3",
        "db-restore-revert-candidate",
        "candidate manifest label mismatch was reported as success",
        "OPS-025 production DB restore fake success, failure, cutover, and revert lifecycle tests passed",
    ):
        require(evidence in script_tests, f"OPS-025 fake lifecycle evidence is missing: {evidence}")
    for evidence in (
        "verified candidate volume was not preserved",
        "candidate preparation container remained",
        "candidate volume did not contain the verified logical restore",
        "OPS-025 candidate preservation lifecycle tests passed",
    ):
        require(evidence in backup_tests, f"OPS-025 isolated candidate evidence is missing: {evidence}")
    require(
        "infra/production/production-db-restore.sh" in workflow,
        "Repository Validation must syntax-check the actual production restore script",
    )
    for boundary in (
        "명시적 승인",
        "쓰기 중단",
        "completion marker",
        "restore-verify",
        "candidate",
        "schema fingerprint",
        "Flyway",
        "외부 HTTPS",
        "원래 volume 복귀",
        "자동 삭제하지",
        "raw datadir",
        "EBS",
    ):
        require(boundary in runbook, f"OPS-025 Runbook boundary is missing: {boundary}")


def validate_ops_db_002() -> None:
    materialize = (PRODUCTION / "materialize-ssm-env.sh").read_text(encoding="utf-8")
    common = (PRODUCTION / "release-common.sh").read_text(encoding="utf-8")
    compose = (PRODUCTION / "compose.yaml").read_text(encoding="utf-8")
    preflight = (PRODUCTION / "rds-read-only-preflight.sh").read_text(encoding="utf-8")
    gate = (PRODUCTION / "rds-transition-gate.sh").read_text(encoding="utf-8")
    readiness_test = (PRODUCTION / "test-rds-readiness.sh").read_text(encoding="utf-8")
    runbook = (ROOT / "docs" / "runbook" / "OPS-DB-002-rds-migration-cutover.md").read_text(encoding="utf-8")
    adr = (ROOT / "docs" / "adr" / "ARCH-013-rds-single-az.md").read_text(encoding="utf-8")
    workflow = VALIDATION_WORKFLOW.read_text(encoding="utf-8")
    for field in ("--datasource-host", "--datasource-port", "--datasource-ssl-mode"):
        require(field in materialize, f"OPS-DB-002 materializer option is incomplete: {field}")
    for field in ("PAWCYCLE_DATASOURCE_HOST", "PAWCYCLE_DATASOURCE_PORT", "PAWCYCLE_DATASOURCE_SSL_MODE"):
        require(field in materialize and field in common, f"OPS-DB-002 datasource field is incomplete: {field}")
    require("sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC" in materialize and "sslMode=REQUIRED&serverTimezone=UTC" in materialize, "Docker and RDS JDBC URL derivation is incomplete")
    require("datasource runtime combination is not approved" in common and "materialized single-quoted format" in common, "runtime datasource bundle must fail closed")
    egress_block = compose[compose.index("  database-egress:") :]
    require("database-egress" in compose and "PAWCYCLE_DATABASE_EGRESS_NETWORK" in common and "internal: true" not in egress_block, "Backend RDS egress must be dedicated and non-internal")
    allowed = {"ec2:describe-instances", "ec2:describe-vpcs", "ec2:describe-subnets", "ec2:describe-security-groups", "rds:describe-orderable-db-instance-options"}
    require(all(token in preflight for token in allowed) and "aws_ro()" in preflight, "RDS preflight allowlist is incomplete")
    require("--vpc --query" in preflight and "--vpc true" not in preflight and "MinStorageSize" in preflight and "MaxStorageSize" in preflight, "RDS orderability must use the AWS CLI --vpc boolean flag and verify 20 GiB support")
    forbidden_aws = re.findall(r"\b(?:aws|aws_ro)\s+(?:ec2|rds)\s+([a-z0-9-]+)", preflight)
    require(all(name.startswith("describe-") for name in forbidden_aws), "RDS preflight contains an AWS mutation surface")
    require("Single-AZ" in preflight and "PubliclyAccessible=false" in preflight and "retention requires later cost" in preflight, "creation-time RDS boundaries are incomplete")
    for boundary in ("db-restore-verified", "db-restore-candidate", "deploy.lock", "SOURCE_TARGET_DISTINCT", "PRODUCTION_CUTOVER", "EVIDENCE_PHASE", "FINAL_CONSISTENCY_VERIFIED", "TARGET_DATABASE_SHA256", "validate_evidence_target", "post-activation Backend health, API smoke, and HTTPS gates remain pending"):
        require(boundary in gate, f"RDS transition gate boundary is missing: {boundary}")
    require("acquire_runtime_read_lock" in common and ".materialize.lock" in common, "runtime bundle reads must share the materialization lock")
    require("compose up" not in gate and not re.search(r"docker\s+(?:run|create|stop|rm|volume\s+rm)", gate), "RDS transition gate must remain read-only")
    require("PAWCYCLE_RDS_GATE_TEST_MODE" not in gate and "source \"$SCRIPT_DIR/release-common.sh\"" in gate, "RDS transition gate must not bypass production verification or duplicate runtime parsing")
    for boundary in ("validate_runtime_bundle", "read_runtime_setting", "com.docker.compose.project=pawcycle-production", "com.docker.compose.service=mysql", "TARGET_DATABASE_SHA256", "record contains an unknown or secret-shaped key", "git -C \"$CONTROL_WORKTREE_ROOT\" status", "flock -sn"):
        require(boundary in gate, f"RDS transition gate boundary is missing: {boundary}")
    require("rds-read-only-preflight.sh" in common and "rds-transition-gate.sh" in common, "new RDS controls must participate in clean Control worktree validation")
    require("FAKE_AWS_MUTATION" in readiness_test and "gate cutover" in readiness_test and "secret identity mismatch did not fail closed" in readiness_test and "FAKE_GIT_DIRTY" in readiness_test and "FAKE_LOCK_BUSY" in readiness_test, "RDS fake readiness test evidence is incomplete")
    require("ARCH-013" in adr and all(term in adr for term in ("Single-AZ", "Multi-AZ", "RDS Proxy", "VERIFY_IDENTITY", "PITR")), "ARCH-013 scope is incomplete")
    for section in ("## Rehearsal", "## Production cutover", "## Rollback", "SOURCE_TARGET_DISTINCT=true", "PRODUCTION_CUTOVER=false", "Production Verified", "runtime-rds", "runtime-docker", "LatestRestorableTime", "deploy.lock concurrency"):
        require(section in runbook, f"OPS-DB-002 Runbook boundary is missing: {section}")
    require("rds-read-only-preflight.sh" in workflow and "rds-transition-gate.sh" in workflow and "test-rds-readiness.sh" in workflow and "py_compile infra/production/validate-production-contracts.py" in workflow, "workflow must validate OPS-DB-002 fixture contracts")


def validate_stage2_contracts() -> None:
    compose = (PRODUCTION / "compose.yaml").read_text(encoding="utf-8")
    materialize = (PRODUCTION / "materialize-runtime-env.sh").read_text(encoding="utf-8")
    materialize_test = (PRODUCTION / "test-materialize-runtime-env.sh").read_text(encoding="utf-8")
    common = (PRODUCTION / "release-common.sh").read_text(encoding="utf-8")
    deploy = (PRODUCTION / "deploy.sh").read_text(encoding="utf-8")
    rollback = (PRODUCTION / "rollback.sh").read_text(encoding="utf-8")
    helpers = {
        path.name: path.read_text(encoding="utf-8")
        for path in (
            PRODUCTION / "subscription-automation-control.sh",
            PRODUCTION / "subscription-automation-preflight.sh",
            PRODUCTION / "import-demo-catalog.sh",
            PRODUCTION / "create-production-auth-smoke-member.sh",
            PRODUCTION / "diagnose-backend-state.sh",
        )
    }
    dispatcher = (PRODUCTION / "production-command-dispatch.sh").read_text(encoding="utf-8")
    dispatcher_test = (PRODUCTION / "test-production-command-dispatch.sh").read_text(encoding="utf-8")
    invoke = (PRODUCTION / "invoke-oci-production-command.sh").read_text(encoding="utf-8")
    invoke_test = (PRODUCTION / "test-invoke-oci-production-command.sh").read_text(encoding="utf-8")
    backup = (PRODUCTION / "oci-db-backup-restore.sh").read_text(encoding="utf-8")
    backup_test = (PRODUCTION / "test-oci-db-backup-restore.sh").read_text(encoding="utf-8")
    active_lifecycle = "\n".join((common, deploy, rollback, *helpers.values()))

    require(set(re.findall(r"^  ([a-z][a-z0-9-]+):$", compose, re.MULTILINE)) >= {"backend", "frontend", "proxy"}, "Stage 2 Compose services are incomplete")
    require("  mysql:" not in compose and "mysql-data" not in compose and "  data:" not in compose, "Stage 2 Compose must not own a local MySQL service, volume, or data network")
    require("database-egress:" in compose and "PAWCYCLE_DATASOURCE_HOST" not in compose, "Compose must use the protected external database runtime bundle")
    require(re.search(r"backend:.*?networks:\s*\n(?:\s+- .*\n){2}", compose, re.DOTALL) is not None, "Backend must have two runtime networks")

    required_runtime_keys = {
        "PAWCYCLE_DATASOURCE_HOST", "PAWCYCLE_DATASOURCE_PORT", "PAWCYCLE_DATASOURCE_DATABASE",
        "PAWCYCLE_DATASOURCE_SSL_MODE", "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD", "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED",
        "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE", "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS",
    }
    require(all(key in materialize for key in required_runtime_keys), "runtime materializer key contract is incomplete")
    require("--source-file" in materialize and "--output-dir" in materialize and "--ssm-prefix" not in materialize and "aws " not in materialize, "OCI runtime materializer must be provider neutral")
    require("set +x" in materialize and "eval" not in materialize and "source \"$SOURCE_FILE\"" not in materialize, "runtime source must be parsed without shell evaluation")
    require("flock --nonblock" in materialize and "mv -Tf" in materialize and "mysql.env" not in materialize and "MYSQL_ROOT_PASSWORD" not in materialize, "runtime bundle atomic and secret boundaries are incomplete")
    require(all(token in materialize_test for token in ("db.example.com", "10.20.30.40", "mysql", "localhost", "127.0.0.1", "8.8.8.8", "concurrent materialization")), "runtime materializer regression coverage is incomplete")

    forbidden_active = ("active-mysql-volume", "PAWCYCLE_MYSQL_ENV_FILE", "PAWCYCLE_MYSQL_VOLUME", "MYSQL_DIGEST")
    require(not any(token in active_lifecycle for token in forbidden_active), "Application lifecycle retains local MySQL ownership")
    require(all(token in common for token in ("RELEASE_SHA=", "BACKEND_DIGEST=", "FRONTEND_DIGEST=", "PROXY_DIGEST=")), "release image state must use the four-field contract")
    require("managed database was not modified by the Application release lifecycle" in deploy and "managed database was not modified by the Application release lifecycle" in rollback, "application failure boundary must preserve the managed database")
    require("for service in backend frontend proxy" in common and "database-egress" in common and "exactly one expected container" in common, "running release verification must cover the three application services and egress")
    require("compose up --detach --pull never --remove-orphans backend frontend" in common and "compose stop proxy frontend backend" in common, "application activation and stop order must exclude the database")

    require(helpers["subscription-automation-preflight.sh"].count("DATABASE_PREFLIGHT_TARGET=\"EXTERNAL_MYSQL\"") == 1, "subscription preflight must use one external MySQL path")
    require("pawcycle-production-database-egress" in helpers["subscription-automation-preflight.sh"] and "--defaults-extra-file=/run/pawcycle/mysql-client.cnf" in helpers["subscription-automation-preflight.sh"], "subscription preflight must use the egress network and option file")
    require("MYSQL_PWD" not in helpers["subscription-automation-preflight.sh"] and "--password" not in helpers["subscription-automation-preflight.sh"], "subscription preflight must not expose a password through env or argv")
    require(all("DATA_NETWORK=\"pawcycle-production-database-egress\"" in helpers[name] for name in ("import-demo-catalog.sh", "create-production-auth-smoke-member.sh")), "catalog and auth helpers must use database-egress")
    require("external_tls_required" in helpers["diagnose-backend-state.sh"] and "database_runtime" in helpers["diagnose-backend-state.sh"] and "active-mysql-volume" not in helpers["diagnose-backend-state.sh"], "diagnostic runtime field must be external and non-sensitive")

    require("CONTROL_DIR=\"/opt/pawcycle/control\"" in dispatcher and "STATE_DIR=\"/opt/pawcycle/state\"" in dispatcher, "dispatcher paths must be fixed")
    require("[[ \"$EUID\" == 0 ]]" in dispatcher and "fetch --prune origin main" in dispatcher and "merge-base --is-ancestor" in dispatcher, "dispatcher validation gates are incomplete")
    require("exec /usr/bin/env bash" in dispatcher and "checkout" not in dispatcher and "reset" not in dispatcher and "rebase" not in dispatcher, "dispatcher must exec the bounded deploy path without history mutation")
    require("Production command dispatcher" in dispatcher_test, "dispatcher fake test is missing")

    require("command create" in invoke and "command-execution get" in invoke and "TEXT" in invoke and "textSha256" in invoke, "OCI Run Command payload contract is incomplete")
    require("/opt/pawcycle/control/infra/production/production-command-dispatch.sh" in invoke and "sudo -n /usr/bin/env bash" in invoke, "OCI wrapper must invoke only the fixed dispatcher")
    require(all(state in invoke for state in ("ACCEPTED", "IN_PROGRESS", "SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELED")), "OCI execution lifecycle is incomplete")
    require("if [[ \"$OPERATION\" == deploy ]]; then run_command preflight; fi" in invoke and "malformed OCI command execution response" in invoke, "OCI deploy sequencing and fail-closed parsing are incomplete")
    require("OCI Run Command wrapper fake lifecycle tests passed" in invoke_test, "OCI Run Command fake test is missing")

    require(MYSQL_IMAGE in backup and "--auth instance_principal" in backup and "--no-overwrite" in backup and "--verify-checksum" in backup, "OCI backup must use the pinned multi-arch MySQL tool and immutable Object Storage uploads")
    require(
        backup.index('upload_object "$dump"') < backup.index('upload_object "$manifest"') < backup.index('upload_object "$complete"'),
        "OCI backup completion marker must be uploaded last",
    )
    require("--network none" in backup and "restore-production" not in backup and "cutover" not in backup, "OCI restore verification must be isolated and non-cutover")
    require(
        all(
            marker in backup_test
            for marker in (
                "OCI Object Storage backup, restore-verify, cleanup",
                "object already exists was reported as success",
                "hash mismatch was reported as success",
                "missing completion was reported as success",
                "cleanup failure was reported as success",
            )
        ),
        "OCI backup fake lifecycle test is missing",
    )

    require((PRODUCTION / "materialize-ssm-env.sh").exists() and (PRODUCTION / "db-backup-restore.sh").exists() and (PRODUCTION / "production-db-restore.sh").exists() and DEPLOY_WORKFLOW.exists() and DEPLOY_SSM_DOCUMENT.exists(), "AWS legacy artifacts must remain for Stage 3 retirement")


def main() -> None:
    validate_compose()
    validate_workflow()
    validate_oidc_deploy_contract()
    validate_stage2_contracts()
    validate_nginx()
    print("OPS-OCI-002 Stage 2 OCI production runtime contracts validated")
    print("OPS-AUTO-003 OIDC and restricted SSM deploy legacy contracts retained")


if __name__ == "__main__":
    main()

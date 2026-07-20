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
SHA = "0" * 40
MYSQL_IMAGE = "mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d"
PROXY_IMAGE = "nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ERROR: {message}")


def load_compose_config() -> dict[str, object]:
    with tempfile.TemporaryDirectory(prefix="ops010-compose-") as temporary:
        temporary_path = Path(temporary)
        mysql_env = temporary_path / "mysql.env"
        backend_env = temporary_path / "backend.env"
        mysql_env.write_text(
            "MYSQL_DATABASE=ops010\n"
            "MYSQL_USER=ops010\n"
            "MYSQL_PASSWORD=not-sensitive\n"
            "MYSQL_ROOT_PASSWORD=not-sensitive-root\n",
            encoding="utf-8",
        )
        backend_env.write_text(
            "SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/ops010\n"
            "SPRING_DATASOURCE_USERNAME=ops010\n"
            "SPRING_DATASOURCE_PASSWORD=not-sensitive\n",
            encoding="utf-8",
        )
        environment = os.environ | {
            "RELEASE_SHA": SHA,
            "BACKEND_IMAGE": "ghcr.io/example/pawcycle-commerce-backend",
            "FRONTEND_IMAGE": "ghcr.io/example/pawcycle-commerce-frontend",
            "PAWCYCLE_MYSQL_ENV_FILE": str(mysql_env),
            "PAWCYCLE_BACKEND_ENV_FILE": str(backend_env),
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
    require(set(services) == {"mysql", "backend", "frontend", "proxy"}, "unexpected Compose services")

    for internal_service in ("mysql", "backend", "frontend"):
        require(not services[internal_service].get("ports"), f"{internal_service} must not publish host ports")

    proxy_ports = services["proxy"].get("ports", [])
    require(len(proxy_ports) == 1, "proxy must publish exactly one port")
    require(
        proxy_ports[0].get("published") == "80" and proxy_ports[0].get("target") == 80,
        "proxy must publish host HTTP 80 only",
    )

    require(services["backend"]["image"].endswith(f":{SHA}"), "Backend image must use RELEASE_SHA")
    require(services["frontend"]["image"].endswith(f":{SHA}"), "Frontend image must use RELEASE_SHA")
    require(services["mysql"]["image"] == MYSQL_IMAGE, "MySQL image must use the approved immutable digest")
    require(services["proxy"]["image"] == PROXY_IMAGE, "Nginx image must use the approved immutable digest")

    for name, service in services.items():
        require(service.get("restart") == "unless-stopped", f"{name} restart policy must be unless-stopped")
        logging = service.get("logging", {})
        require(logging.get("driver") == "json-file", f"{name} log driver must be json-file")
        options = logging.get("options", {})
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

    require(config["volumes"]["mysql-data"]["name"] == "pawcycle-production-mysql-data", "stable MySQL volume name is required")
    require(config["networks"]["edge"].get("internal") is not True, "proxy edge network must accept the published port")
    require(config["networks"]["app"].get("internal") is True, "app network must be internal")
    require(config["networks"]["data"].get("internal") is True, "data network must be internal")


def validate_workflow() -> None:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    require("permissions:\n  contents: read\n  packages: write" in workflow, "workflow permissions exceed or omit the approved minimum")
    require("if: github.ref == 'refs/heads/main'" in workflow, "non-main image publication must fail closed")
    require(workflow.count("tags: ghcr.io/${{ github.repository }}-") == 2, "both images need repository-derived tags")
    require(workflow.count(":${{ github.sha }}") == 2, "both images must use the same github.sha tag")
    require(workflow.count("org.opencontainers.image.revision=${{ github.sha }}") == 2, "both images need the SHA revision label")
    require("platforms: linux/amd64" in workflow, "EC2 x86_64 image platform is required")
    require(not re.search(r"(?:image|tags):[^\n]*:latest(?:\s|$)", workflow), "latest image tag is forbidden")

    for action_reference in re.findall(r"uses:\s+[^\s]+@([^\s]+)", workflow):
        require(bool(re.fullmatch(r"[0-9a-f]{40}", action_reference)), "workflow actions must be pinned to a 40-character commit")


def validate_scripts() -> None:
    common = (PRODUCTION / "release-common.sh").read_text(encoding="utf-8")
    deploy = (PRODUCTION / "deploy.sh").read_text(encoding="utf-8")
    rollback = (PRODUCTION / "rollback.sh").read_text(encoding="utf-8")
    materialize = (PRODUCTION / "materialize-ssm-env.sh").read_text(encoding="utf-8")
    script_tests = (PRODUCTION / "test-production-scripts.sh").read_text(encoding="utf-8")
    release_scripts = "\n".join((common, deploy, rollback))

    require("^ghcr\\.io/" in common, "deploy input must be restricted to GHCR")
    require("^[0-9a-f]{40}$" in common, "deploy input must require a full commit SHA")
    require("org.opencontainers.image.revision" in common and ".RepoDigests" in common, "revision and digest preflight are required")
    require(MYSQL_IMAGE in common and PROXY_IMAGE in common, "base images must be pinned to approved immutable digests")
    require("image digest drift detected for previously verified release SHA" in common, "same-SHA digest drift must fail closed")
    require("MYSQL_DIGEST=%s" in common and "PROXY_DIGEST=%s" in common, "base image digests must be part of each SHA record")
    require("cmp -s" in common, "existing SHA image records must be compared rather than overwritten")
    require("git diff --quiet" in common and "':(top)infra/production'" in common, "release contract compatibility gate is missing")
    require("--pull never" in common, "activation must not replace preflighted images")
    require('PAWCYCLE_MYSQL_VOLUME="pawcycle-production-mysql-data"' in common, "production volume name must ignore ambient overrides")
    require('PAWCYCLE_HTTP_PORT="80"' in common, "production HTTP port must ignore ambient overrides")
    require("for service in mysql backend frontend" in common, "health wait must cover MySQL and both application services")
    require("wait_healthy proxy" in common, "health wait must cover Nginx")
    require("if ! curl" in common and "Frontend HTTP smoke failed" in common and "Backend HTTP smoke failed" in common, "both HTTP smoke failures must return explicitly")
    require("previous release was restored" in deploy, "automatic restoration evidence is missing")
    require("database restoration or volume deletion" in rollback, "rollback data boundary is missing")
    require(not re.search(r"docker\s+(?:compose\s+)?(?:volume\s+rm|.*down.*(?:-v|--volumes))", release_scripts), "release scripts must not delete volumes")
    for evidence in (
        "initial release did not fail when smoke failed",
        "target release did not fail when smoke failed",
        "same-SHA application digest drift did not fail closed",
        "pinned base image digest drift did not fail closed",
        "incompatible infra/production contract did not fail closed",
        "rollback with incompatible infra/production contract did not fail closed",
    ):
        require(evidence in script_tests, f"release regression evidence is missing: {evidence}")

    for leaf in ("MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD"):
        require(f"get_parameter {leaf}" in materialize, f"required SSM parameter is missing: {leaf}")
    require("--with-decryption" in materialize, "SecureString decryption flag is required")
    require(materialize.count("chmod 600") >= 2, "runtime files and completion marker must be mode 600")
    require("set +x" in materialize, "materializer must disable shell tracing")
    require("realpath -e" in materialize, "previous runtime bundle deletion must validate the resolved path")
    require('rm -rf -- "$PREVIOUS_BUNDLE"' in materialize, "previous plaintext runtime bundle must be removed")
    require('flock --nonblock 9' in materialize, "runtime materialization must reject concurrent writers")
    require("concurrent runtime materialization did not fail closed" in script_tests, "materialization concurrency regression evidence is missing")


def main() -> None:
    validate_compose()
    validate_workflow()
    validate_scripts()
    print("OPS-010 production contracts validated")


if __name__ == "__main__":
    main()

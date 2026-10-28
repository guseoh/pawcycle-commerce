#!/usr/bin/env python3
"""Approved, host-local OCI application releases. No Cloud API or DB mutation.

Linux/root only. JSON inputs are operator-controlled evidence, not credentials.
state.json is authoritative; legacy projections are fenced by the shared marker.
"""

import argparse
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import stat
import subprocess
import tempfile
import time

SHA = re.compile(r"[0-9a-f]{40}")
DIGEST = re.compile(r"sha256:[0-9a-f]{64}")
PROJECT = "pawcycle-production"
ORIGIN = "https://github.com/guseoh/pawcycle-commerce.git"
DOMAIN = "pawcycle.duckdns.org"
COMPOSE = "infra/production/compose.yaml"
NGINX = "infra/production/nginx.https.conf"
PROXY = "nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
NETWORKS = {
    "backend": {"pawcycle-production-app", "pawcycle-production-database-egress"},
    "frontend": {"pawcycle-production-app"},
    "proxy": {"pawcycle-production-app", "pawcycle-production-edge"},
}
VOLUMES = {"pawcycle-production-letsencrypt": "/etc/letsencrypt",
           "pawcycle-production-certbot-webroot": "/var/www/certbot"}
GIB = 1024 ** 3
# Versioned allowlist for the approved Application Nginx contract. Source SHA
# remains independent; only comments and whitespace outside strings may differ.
NGINX_CONTRACT_SHA256 = "dfb93f4ff2133519a14512241e8bb5818087629f785619743816ea1ee9560865"
NGINX_TOKEN = re.compile(r'''\s+|\#[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[{};]|[^\s{};\#"'\\]+''')


class Refused(Exception):
    """Safe diagnostic; never include raw subprocess output or input values."""


def require(condition, message):
    if not condition:
        raise Refused(message)


def digest(data):
    return "sha256:" + hashlib.sha256(data).hexdigest()


def encoded(value):
    return (json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n").encode()


def identity(value):
    return digest(encoded(value))


def nginx_contract_fingerprint(config):
    text = config.decode("utf-8")
    tokens, cursor = [], 0
    for match in NGINX_TOKEN.finditer(text):
        require(match.start() == cursor, "unsupported Nginx contract syntax")
        cursor = match.end()
        token = match.group()
        if not token.isspace() and not token.startswith("#"):
            tokens.append(token)
    require(cursor == len(text), "incomplete Nginx contract syntax")
    return hashlib.sha256(json.dumps(tokens, separators=(",", ":")).encode()).hexdigest()


def validate_nginx_contract(config):
    # A hash supplied by the release operator proves provenance, not policy.
    # Pin the entire reviewed directive/token structure so added directives,
    # nested header overrides or commented-out security checks cannot pass.
    require(nginx_contract_fingerprint(config) == NGINX_CONTRACT_SHA256,
            "Nginx configuration violates approved security/DNS contract")


def validate_manifest(manifest):
    require(set(manifest) == {"sha", "backend", "frontend", "compose_source_sha", "compose_sha256",
                              "nginx_source_sha", "nginx_sha256", "migration_tree"}, "unexpected manifest fields")
    for key in ("sha", "compose_source_sha", "nginx_source_sha", "migration_tree"):
        require(isinstance(manifest[key], str) and SHA.fullmatch(manifest[key]), "invalid manifest SHA")
    for key in ("compose_sha256", "nginx_sha256"):
        require(isinstance(manifest[key], str) and DIGEST.fullmatch(manifest[key]), "invalid source digest")
    for role in ("backend", "frontend"):
        image = manifest[role]
        require(set(image) == {"repository", "digest", "config_digest", "unpacked_bytes"}, "unexpected image fields")
        require(image["repository"] == "ghcr.io/guseoh/pawcycle-commerce-" + role and
                isinstance(image["digest"], str) and DIGEST.fullmatch(image["digest"]) and
                isinstance(image["config_digest"], str) and DIGEST.fullmatch(image["config_digest"]) and
                type(image["unpacked_bytes"]) is int and image["unpacked_bytes"] > 0, "invalid image identity/budget")


def protected(path, directory=False, mode=None):
    """Reject symlinks, hardlinked files and writable/untrusted ancestors."""
    path = Path(path)
    require(path.is_absolute(), "absolute path required")
    for item in reversed((path, *path.parents)):
        info = item.lstat()
        require(not stat.S_ISLNK(info.st_mode) and info.st_uid == 0,
                "unsafe path ownership or symlink")
        require(not info.st_mode & 0o022 or
                (stat.S_ISDIR(info.st_mode) and info.st_mode & stat.S_ISVTX),
                "unsafe writable path")
    info = path.lstat()
    require(stat.S_ISDIR(info.st_mode) if directory else
            stat.S_ISREG(info.st_mode) and info.st_nlink == 1, "unsafe file type")
    if mode is not None:
        require(stat.S_IMODE(info.st_mode) == mode, "unsafe path permissions")
    return path


def read_private(path):
    return protected(path, mode=0o600).read_bytes()


def load(path):
    return json.loads(read_private(path))


def sync_directory(path):
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def atomic(path, data):
    path = Path(path)
    protected(path.parent, directory=True, mode=0o700)
    if path.exists() or path.is_symlink():
        protected(path, mode=0o600)
    fd, temporary = tempfile.mkstemp(prefix=".oci-write-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        sync_directory(path.parent)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


@contextlib.contextmanager
def locked(path, create=False):
    path = Path(path)
    protected(path.parent, directory=True)
    if path.exists() or path.is_symlink():
        protected(path, mode=0o600)
    else:
        require(create, "required shared lock is missing")
    fd = os.open(path, os.O_RDWR | os.O_NOFOLLOW | (os.O_CREAT if create else 0), 0o600)
    try:
        protected(path, mode=0o600)
        info = os.fstat(fd)
        require((info.st_dev, info.st_ino) == (path.stat().st_dev, path.stat().st_ino),
                "lock changed during acquisition")
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Refused("another deploy, catalog/auth operation or renewal is running") from None
        yield
    finally:
        os.close(fd)


class Host:
    def __init__(self, repository, state, runtime, renewal_lock):
        self.repository, self.state, self.runtime = map(Path, (repository, state, runtime))
        self.renewal_lock = Path(renewal_lock)

    def command(self, *args, env=None, timeout=120):
        # Ignore caller Docker/Compose targeting and config overrides. Keep only
        # root's normal CLI authentication location and executable search path.
        clean = {key: os.environ[key] for key in ("PATH", "HOME", "LANG") if key in os.environ}
        clean.update(env or {})
        try:
            result = subprocess.run(args, env=clean, capture_output=True, timeout=timeout)
        except (OSError, subprocess.TimeoutExpired):
            raise Refused("command unavailable or timed out") from None
        require(result.returncode == 0, "command failed (raw output suppressed)")
        return result.stdout

    def docker(self, *args, **kwargs):
        return self.command("docker", "--host", "unix:///var/run/docker.sock", *args, **kwargs)

    def git(self, *args):
        return self.command("git", "-C", str(self.repository), *args)

    def initial(self):
        require(os.geteuid() == 0, "root on the approved Linux host is required")
        protected(self.repository, directory=True)
        protected(self.state, directory=True, mode=0o700)
        protected(self.runtime, directory=True, mode=0o700)
        require(self.git("remote", "get-url", "origin").decode().strip() == ORIGIN,
                "wrong source repository")
        remote = self.git("ls-remote", "origin", "refs/heads/main").decode().split()
        require(len(remote) == 2 and SHA.fullmatch(remote[0]), "main unavailable")
        self.main = remote[0]
        require(self.git("rev-parse", "refs/remotes/origin/main").decode().strip() == self.main,
                "cached main is stale; refresh source separately")
        require(read_private(self.state / "https-domain").decode().strip() == DOMAIN and
                read_private(self.state / "https-enabled").decode().strip() == "enabled",
                "HTTPS adoption is required")
        require(not (self.state / "https-reload-pending").exists(), "HTTPS reload is pending")
        current_link = self.runtime / "current"
        require(current_link.is_symlink(), "protected runtime bundle is required")
        bundle = current_link.resolve(strict=True)
        require(bundle.parent == self.runtime and bundle.name.startswith(".bundle."),
                "runtime bundle escapes protected directory")
        protected(bundle, directory=True, mode=0o700)
        self.backend_env = protected(bundle / "backend.env", mode=0o600)
        read_private(bundle / ".complete")
        self.env_hash = digest(self.backend_env.read_bytes())
        # Inspect injection shape, never source shell code or echo values.
        entries = {}
        for line in self.backend_env.read_text().splitlines():
            if not line or line.startswith("#"):
                continue
            key, sep, value = line.partition("=")
            require(sep and key not in entries and value, "invalid runtime injection shape")
            entries[key] = value.strip("'\"")
        for key in ("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
                    "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED", "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE",
                    "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS"):
            require(entries.get(key), "required runtime injection is missing")
        self.automation = {key: entries[key] for key in entries if key.startswith("PAWCYCLE_SUBSCRIPTION_AUTOMATION_")}
        require(self.automation["PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED"] in ("true", "false"),
                "invalid automation injection")
        for key in ("PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE", "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS"):
            require(self.automation[key].isdigit() and int(self.automation[key]) > 0, "invalid automation injection")

    def sources(self, manifest):
        for key in ("sha", "compose_source_sha", "nginx_source_sha"):
            require(SHA.fullmatch(manifest[key]), "40-character lowercase main SHA required")
            self.git("merge-base", "--is-ancestor", manifest[key], self.main)
        compose = self.git("show", manifest["compose_source_sha"] + ":" + COMPOSE)
        template = self.git("show", manifest["nginx_source_sha"] + ":" + NGINX)
        nginx = template.replace(b"__PAWCYCLE_DOMAIN__", DOMAIN.encode())
        require(digest(compose) == manifest["compose_sha256"] and
                digest(nginx) == manifest["nginx_sha256"], "Compose/Nginx provenance mismatch")
        validate_nginx_contract(nginx)
        # Migration equality is necessary, never a substitute for approved
        # application/data compatibility evidence.
        migration = self.git("rev-parse", manifest["sha"] + ":backend/src/main/resources/db/migration").decode().strip()
        require(migration == manifest["migration_tree"], "migration provenance mismatch")
        return compose, nginx

    def registry(self, manifest):
        compressed = 0
        for role in ("backend", "frontend"):
            image = manifest[role]
            require(image["repository"] == "ghcr.io/guseoh/pawcycle-commerce-" + role and
                    DIGEST.fullmatch(image["digest"]) and DIGEST.fullmatch(image["config_digest"]),
                    "invalid image pair or digest")
            ref = image["repository"] + ":" + manifest["sha"]
            raw = self.docker("buildx", "imagetools", "inspect", ref, "--raw")
            require(digest(raw) == image["digest"], "registry tag/digest mismatch")
            document = json.loads(raw)
            selected_ref = image["repository"] + "@" + image["digest"]
            if "manifests" in document:
                matches = [entry for entry in document["manifests"]
                           if entry.get("platform", {}).get("os") == "linux" and
                           entry.get("platform", {}).get("architecture") == "amd64"]
                require(len(matches) == 1, "registry linux/amd64 platform is ambiguous or missing")
                child_digest = matches[0]["digest"]
                require(DIGEST.fullmatch(child_digest), "invalid platform digest")
                raw = self.docker("buildx", "imagetools", "inspect",
                                  image["repository"] + "@" + child_digest, "--raw")
                require(digest(raw) == child_digest, "platform manifest was tampered")
                document = json.loads(raw)
                selected_ref = image["repository"] + "@" + child_digest
            require(document["config"]["digest"] == image["config_digest"], "registry image ID mismatch")
            # Buildx fetches the digest-addressed config without pulling layers.
            remote = json.loads(self.docker("buildx", "imagetools", "inspect", selected_ref,
                                           "--format", "{{json .Image}}"))
            if "architecture" not in remote:
                remote = remote.get("linux/amd64", {})
            labels = remote.get("config", {}).get("Labels", {})
            require(remote.get("os") == "linux" and remote.get("architecture") == "amd64" and
                    labels.get("org.opencontainers.image.revision") == manifest["sha"] and
                    labels.get("org.opencontainers.image.source") == ORIGIN.removesuffix(".git"),
                    "registry platform/revision/source mismatch")
            size = sum(layer["size"] for layer in document["layers"])
            require(size > 0 and type(image["unpacked_bytes"]) is int and
                    image["unpacked_bytes"] >= size, "unpacked size evidence is missing or too small")
            compressed += size
        return compressed

    def image(self, manifest, role):
        image = manifest[role]
        ref = image["repository"] + "@" + image["digest"]
        local = json.loads(self.docker("image", "inspect", ref))[0]
        labels = local["Config"].get("Labels") or {}
        require(local["Id"] == image["config_digest"] and
                local["Os"] == "linux" and local["Architecture"] == "amd64" and
                labels.get("org.opencontainers.image.revision") == manifest["sha"] and
                labels.get("org.opencontainers.image.source") == ORIGIN.removesuffix(".git") and
                ref in local["RepoDigests"], "local image identity/platform/provenance mismatch")
        require(local["Size"] <= image["unpacked_bytes"], "unpacked size exceeds approved budget")
        return local["Size"]

    def capacity(self, candidate, current, approval, compressed, *, rollback=False):
        retained = sum(self.image(current, role) for role in ("backend", "frontend"))
        reserve, extra = approval.get("reserve_bytes"), approval.get("rollback_extra_bytes")
        require(type(reserve) is int and reserve >= GIB and type(extra) is int and extra >= 0 and
                approval.get("capacity_evidence"), "capacity evidence is missing")
        if rollback:
            require(approval.get("action") == "rollback" and compressed == 0 and extra > 0,
                    "rollback needs approved positive additional-write capacity evidence")
            # Both image sets are already charged to actual disk usage. Verify
            # the target again, but budget only measured state/config/container
            # write overhead plus the unchanged reserve; no pull or unpack.
            for role in ("backend", "frontend"):
                self.image(candidate, role)
            needed = reserve + extra
        else:
            needed = (2 * compressed + sum(candidate[role]["unpacked_bytes"] for role in ("backend", "frontend")) +
                      reserve + extra)
        info = json.loads(self.docker("info", "--format", "{{json .}}"))
        root = Path(info["DockerRootDir"])
        require(info["OSType"] == "linux" and os.uname().machine == "x86_64", "unsupported host platform")
        require(root.stat().st_dev == self.state.stat().st_dev == self.backend_env.stat().st_dev,
                "separate filesystems need a separately reviewed capacity contract")
        usage = os.statvfs(root)
        require(usage.f_bavail * usage.f_frsize >= needed,
                "insufficient rollback reserve/additional-write space" if rollback else
                "insufficient pull/unpack/retention/reserve space")
        return {"required_free_bytes": needed, "retained_image_bytes": retained}

    def paths(self, manifest):
        directory = self.state / ("oci-" + identity(manifest).split(":")[1])
        return directory, directory / "compose.yaml", directory / "nginx.conf"

    def materialize(self, manifest):
        compose, nginx = self.sources(manifest)
        directory, compose_path, nginx_path = self.paths(manifest)
        if not directory.exists():
            directory.mkdir(mode=0o700)
            sync_directory(self.state)
        protected(directory, directory=True, mode=0o700)
        for path, content in ((compose_path, compose), (nginx_path, nginx)):
            if path.exists():
                require(read_private(path) == content, "stored configuration changed")
            else:
                atomic(path, content)

    def compose(self, manifest, *args):
        _, compose, nginx = self.paths(manifest)
        require(digest(read_private(compose)) == manifest["compose_sha256"] and
                digest(read_private(nginx)) == manifest["nginx_sha256"], "snapshot hash mismatch")
        env = {"RELEASE_SHA": manifest["sha"], "PAWCYCLE_NGINX_CONFIG": str(nginx),
               "PAWCYCLE_BACKEND_ENV_FILE": str(self.backend_env), **self.automation}
        # Digest references avoid tag races between validation and Compose up.
        env.update({role.upper() + "_IMAGE": manifest[role]["repository"] for role in ("backend", "frontend")})
        return self.docker("compose", "--env-file", "/dev/null", "--project-name", PROJECT,
                           "--file", str(compose), *args, env=env, timeout=300)

    def containers(self):
        ids = self.docker("ps", "-aq", "--filter", "label=com.docker.compose.project=" + PROJECT).decode().split()
        require(len(ids) == 3, "application service set is not exactly three containers")
        result = {}
        for container in json.loads(self.docker("inspect", *ids)):
            labels = container["Config"]["Labels"]
            role = labels["com.docker.compose.service"]
            require(role in NETWORKS and role not in result and
                    labels["com.docker.compose.project"] == PROJECT, "unexpected application service identity")
            require(set(container["NetworkSettings"]["Networks"]) == NETWORKS[role], "runtime network boundary mismatch")
            require(container["State"]["Running"] and
                    container["State"].get("Health", {}).get("Status") == "healthy", "application is unhealthy")
            if role in ("backend", "frontend"):
                require(not container["Mounts"] and not container["HostConfig"].get("PortBindings"),
                        "application mount/port boundary mismatch")
            result[role] = container
        return result

    def runtime_check(self, manifest):
        containers = self.containers()
        for role in ("backend", "frontend"):
            self.image(manifest, role)
            require(containers[role]["Image"] == manifest[role]["config_digest"], "running image ID mismatch")
        proxy = containers["proxy"]
        require(proxy["Config"]["Image"] == PROXY, "proxy image mismatch")
        require(set(proxy["HostConfig"].get("PortBindings") or {}) == {"80/tcp", "443/tcp"},
                "proxy published port boundary mismatch")
        for container in containers.values():
            files = container["Config"]["Labels"].get("com.docker.compose.project.config_files", "").split(",")
            require(len(files) == 1 and digest(protected(Path(files[0])).read_bytes()) == manifest["compose_sha256"],
                    "running Compose source fingerprint mismatch")
        mounts = proxy["Mounts"]
        require(len(mounts) == 3 and all(not mount["RW"] for mount in mounts), "proxy mounts must be read-only")
        for name, target in VOLUMES.items():
            require(any(m.get("Name") == name and m["Destination"] == target for m in mounts), "certificate volume boundary mismatch")
            self.docker("volume", "inspect", name)
        config = [m for m in mounts if m["Destination"] == "/etc/nginx/conf.d/default.conf"]
        require(len(config) == 1 and config[0]["Type"] == "bind", "runtime Nginx bind missing")
        require(digest(protected(Path(config[0]["Source"])).read_bytes()) == manifest["nginx_sha256"],
                "applied Nginx fingerprint mismatch")
        for name in set.union(*NETWORKS.values()):
            network = json.loads(self.docker("network", "inspect", name))[0]
            require(bool(network["Internal"]) == (name == "pawcycle-production-app"), "network isolation mismatch")
            if name == "pawcycle-production-database-egress":
                require(set(network.get("Containers", {})) == {containers["backend"]["Id"]},
                        "database egress is not exclusive to Backend")
        return containers

    def validate_config(self, manifest):
        compose_bytes, nginx_bytes = self.sources(manifest)
        # Transient validation files only; no release state or containers change.
        with tempfile.TemporaryDirectory(prefix=".oci-config-", dir=self.state) as temporary:
            directory = Path(temporary)
            compose, nginx = directory / "compose.yaml", directory / "nginx.conf"
            atomic(compose, compose_bytes)
            atomic(nginx, nginx_bytes)
            env = {"RELEASE_SHA": manifest["sha"], "PAWCYCLE_NGINX_CONFIG": str(nginx),
                   "PAWCYCLE_BACKEND_ENV_FILE": str(self.backend_env), **self.automation,
                   **{role.upper() + "_IMAGE": manifest[role]["repository"] for role in ("backend", "frontend")}}
            config = json.loads(self.docker("compose", "--env-file", "/dev/null", "--project-name", PROJECT,
                                           "--file", str(compose), "config", "--format", "json", env=env))
            self.docker("run", "--rm", "--pull", "never", "--network", "none", "--read-only",
                        "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
                        "--tmpfs", "/var/cache/nginx:size=16m", "--tmpfs", "/var/run:size=1m",
                        "--mount", "type=bind,src=" + str(nginx) + ",dst=/etc/nginx/conf.d/default.conf,readonly",
                        "--mount", "type=volume,src=pawcycle-production-letsencrypt,dst=/etc/letsencrypt,readonly,volume-nocopy",
                        "--mount", "type=volume,src=pawcycle-production-certbot-webroot,dst=/var/www/certbot,readonly,volume-nocopy",
                        "--entrypoint", "nginx", PROXY, "-t")
        require(set(config["services"]) == set(NETWORKS), "Compose service set mismatch")
        require(set(config["volumes"]) == {"letsencrypt", "certbot-webroot"}, "Compose volume set mismatch")
        require(set(config["networks"]) == {"app", "edge", "database-egress"}, "Compose network set mismatch")
        for key in config["networks"]:
            network = config["networks"][key]
            require(network["name"] == PROJECT + "-" + key and bool(network.get("internal")) == (key == "app"),
                    "Compose network isolation mismatch")
        for key, volume in config["volumes"].items():
            require(volume["name"] == PROJECT + "-" + key, "Compose certificate volume mismatch")
        for role in ("backend", "frontend"):
            service = config["services"][role]
            require(service["image"] == manifest[role]["repository"] + ":" + manifest["sha"] and
                    not service.get("volumes") and not service.get("ports") and
                    {config["networks"][key]["name"] for key in service["networks"]} == NETWORKS[role],
                    "Compose application boundary mismatch")
        require(config["services"]["proxy"]["image"] == PROXY, "Compose proxy image mismatch")
        proxy = config["services"]["proxy"]
        require({config["networks"][key]["name"] for key in proxy["networks"]} == NETWORKS["proxy"],
                "Compose proxy network boundary mismatch")
        require(len(proxy["volumes"]) == 3 and all(volume.get("read_only") for volume in proxy["volumes"]),
                "Compose proxy read-only mounts mismatch")
        require({port["target"] for port in proxy["ports"]} == {80, 443} and len(proxy["ports"]) == 2,
                "Compose proxy port boundary mismatch")

    def siblings(self):
        # Fence accidental lifecycle changes to any sibling container, network
        # or certificate volume. Independent sibling changes also fail closed.
        ids = self.docker("ps", "-aq").decode().split()
        containers = json.loads(self.docker("inspect", *ids)) if ids else []
        networks = json.loads(self.docker("network", "inspect", *sorted(set.union(*NETWORKS.values()))))
        volumes = json.loads(self.docker("volume", "inspect", *VOLUMES))
        return {"containers": {c["Id"]: c["State"]["StartedAt"] for c in containers
                               if c["Config"].get("Labels", {}).get("com.docker.compose.project") != PROJECT},
                "networks": {n["Name"]: n["Id"] for n in networks},
                "volumes": {v["Name"]: v.get("CreatedAt") for v in volumes}}

    def smoke(self, manifest):
        self.runtime_check(manifest)
        proxy = PROJECT + "-proxy-1"
        self.docker("exec", proxy, "nginx", "-t")
        # wget follows redirects, so examine every HTTP status and require a
        # single successful response rather than accepting a final redirect.
        for path in ("/products", "/api/products"):
            raw = self.docker("exec", proxy, "sh", "-c",
                              "wget -S -O /dev/null http://127.0.0.1:8081" + path + " 2>&1")
            statuses = re.findall(rb"HTTP/\S+ (\d{3})", raw)
            require(len(statuses) == 1 and statuses[0].startswith(b"2"), "internal smoke was not direct 2xx")
            code = self.command("curl", "--silent", "--show-error", "--max-time", "15", "--output", "/dev/null",
                                "--write-out", "%{http_code}", "https://" + DOMAIN + path)
            require(re.fullmatch(rb"2\d\d", code), "external HTTPS smoke was not direct 2xx")
        self.command("bash", str(self.repository / "infra/production/verify-observability-application-identity.sh"), "snapshot")
        ids = self.docker("ps", "-q", "--filter", "label=com.docker.compose.project=pawcycle-production-metrics-proxy",
                          "--filter", "label=com.docker.compose.service=metrics-proxy").decode().split()
        require(len(ids) == 1, "metrics proxy identity missing")
        metrics = json.loads(self.docker("inspect", ids[0]))[0]
        require(metrics["State"].get("Health", {}).get("Status") == "healthy", "metrics proxy unhealthy")
        self.docker("exec", ids[0], "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:9464/actuator/prometheus")

    def activate(self, manifest, pull=True):
        # Pull immutable refs, then pin the legacy SHA tags locally. With
        # --pull never, Compose cannot resolve a moving registry tag at up time.
        for role in ("backend", "frontend"):
            image = manifest[role]
            ref = image["repository"] + "@" + image["digest"]
            if pull:
                self.docker("pull", "--platform", "linux/amd64", ref, timeout=600)
            self.image(manifest, role)
            self.docker("tag", ref, image["repository"] + ":" + manifest["sha"])
        self.materialize(manifest)
        self.compose(manifest, "up", "-d", "--no-deps", "--pull", "never", "--wait", "--wait-timeout", "180", "backend", "frontend")
        # Source path is release-specific; proxy recreation updates only its
        # read-only config bind, preserving both named certificate volumes.
        self.compose(manifest, "up", "-d", "--no-deps", "--pull", "never", "--wait", "--wait-timeout", "90", "proxy")
        self.smoke(manifest)


class Release:
    def __init__(self, host):
        self.host, self.state = host, host.state

    def read_state(self):
        path = self.state / "oci-release-state.json"
        if not path.exists():
            return None
        state = load(path)
        require(state["format"] == 1 and state["current"]["verified"] is True, "invalid release state")
        for record in (state["current"], state.get("previous")):
            if record:
                validate_manifest(record["manifest"])
                require(record["verified"] is True, "unverified release record")
        return state

    def projections(self, state):
        current, previous = state["current"]["manifest"], state.get("previous")
        atomic(self.state / "current-sha", (current["sha"] + "\n").encode())
        atomic(self.state / "previous-sha", ((previous["manifest"]["sha"] if previous else "") + "\n").encode())
        for record in (state["current"], previous):
            if not record:
                continue
            manifest = record["manifest"]
            lines = [role.upper() + "_DIGEST=" + manifest[role]["repository"] + "@" + manifest[role]["digest"]
                     for role in ("backend", "frontend")]
            atomic(self.state / (manifest["sha"] + ".images"), ("\n".join(lines) + "\n").encode())

    def check_projections(self, state):
        current = state["current"]["manifest"]
        require(read_private(self.state / "current-sha").decode().strip() == current["sha"], "stale current projection")
        expected = "\n".join(role.upper() + "_DIGEST=" + current[role]["repository"] + "@" + current[role]["digest"]
                             for role in ("backend", "frontend")) + "\n"
        require(read_private(self.state / (current["sha"] + ".images")).decode() == expected, "stale image projection")

    def journal(self, phase, **values):
        atomic(self.state / "release-state-transition", encoded({"phase": phase, **values}))

    def commit(self, state):
        # Marker remains durable across interruption of ANY multi-file write.
        atomic(self.state / "oci-release-state.json", encoded(state))
        self.projections(state)
        atomic(self.state / "oci-release-result.json", encoded({"result": state["result"], "current": state["current"]["manifest"]["sha"]}))
        (self.state / "release-state-transition").unlink()
        sync_directory(self.state)

    def approval(self, action, manifest, state, approval):
        current = state["current"]["manifest"]["sha"] if state else None
        require(approval["action"] == action and approval["manifest_sha256"] == identity(manifest) and
                approval["expected_current"] == current and approval.get("evidence") and
                approval.get("db_compatibility_evidence") and approval.get("db_epoch"),
                "approval/evidence is absent or stale")
        require(approval["expires_unix"] > time.time() and approval["expires_unix"] <= time.time() + 86400,
                "approval expired or validity exceeds one day")
        if state:
            baseline = state["current"]
            require(baseline["runtime_env_sha256"] == self.host.env_hash and
                    baseline["db_epoch"] == approval["db_epoch"] and
                    baseline["manifest"]["migration_tree"] == manifest["migration_tree"] and
                    current in approval["compatible_with"] and approval["rollback_safe"] is True,
                    "DB/runtime compatibility or reversible application/data evidence missing")
        else:
            require(action == "adopt" and approval.get("baseline_verified") is True,
                    "initial baseline needs separate approval and evidence")

    def run(self, action, manifest=None, approval=None, execute=False):
        host = self.host
        host.initial()
        with locked(self.state / "deploy.lock", create=execute), locked(host.renewal_lock, create=execute):
            state = self.read_state()
            marker = self.state / "release-state-transition"
            if action == "status":
                return {"adopted": state is not None, "recovery_required": marker.exists() or marker.is_symlink()}
            require(execute or action == "preflight", "explicit --execute-approved required")
            if action == "recover-state":
                require(state and marker.exists(), "no authoritative state to reconcile")
                transition = load(marker)
                require(transition["phase"] in ("activating", "restoring", "recovery-required"),
                        "unknown transition requires manual investigation")
                manifest = state["current"]["manifest"]
                self.approval(action, manifest, state, approval)
                host.sources(manifest)
                host.smoke(manifest)
                self.approval(action, manifest, state, approval)
                self.commit(state)
                return {"result": "state-reconciled"}
            require(not marker.exists() and not marker.is_symlink(), "incomplete transition: manual recovery gate")
            validate_manifest(manifest)
            if action == "rollback":
                require(state and state.get("previous") and state["previous"]["verified"] is True,
                        "verified previous release is unavailable")
                require(manifest == state["previous"]["manifest"], "rollback target is not verified previous")
                previous = state["previous"]
                require(previous.get("evidence") and previous.get("compatibility_evidence") and
                        DIGEST.fullmatch(previous.get("approval_sha256", "")) and
                        previous["runtime_env_sha256"] == host.env_hash and
                        previous["db_epoch"] == state["current"]["db_epoch"],
                        "stored rollback approval/runtime/DB evidence is missing or incompatible")
                for role in ("backend", "frontend"):
                    host.image(manifest, role)
            self.approval(action, manifest, state, approval)
            host.sources(manifest)
            # Only the exact previously verified record can bypass Registry.
            # Its local digest/config ID/platform and saved evidence were
            # checked above; no layers will be pulled on this path.
            compressed = 0 if action == "rollback" else host.registry(manifest)
            if action == "adopt":
                require(state is None, "baseline is already adopted")
                # Existing legacy current may be absent, but disagreement is
                # never overwritten as if it were a proven baseline.
                if (self.state / "current-sha").exists():
                    require(read_private(self.state / "current-sha").decode().strip() == manifest["sha"], "legacy current disagrees")
                if (self.state / (manifest["sha"] + ".images")).exists():
                    self.check_projections({"current": {"manifest": manifest}})
                host.smoke(manifest)
            else:
                require(state is not None, "verified current baseline is unavailable")
                self.check_projections(state)
                host.sources(state["current"]["manifest"])
                host.smoke(state["current"]["manifest"])
                if action in ("activate", "preflight"):
                    host.git("merge-base", "--is-ancestor", state["current"]["manifest"]["sha"], manifest["sha"])
            host.validate_config(manifest)
            require(digest(read_private(host.backend_env)) == host.env_hash,
                    "runtime bundle changed during preflight")
            capacity = host.capacity(manifest, state["current"]["manifest"] if state else manifest, approval, compressed,
                                     rollback=action == "rollback")
            if action == "preflight":
                return {"result": "preflight-passed", **capacity}
            if state and manifest == state["current"]["manifest"]:
                return {"result": "already-current"}
            record = {"manifest": manifest, "verified": True, "runtime_env_sha256": host.env_hash,
                      "db_epoch": approval["db_epoch"], "evidence": approval["evidence"],
                      "compatibility_evidence": approval["db_compatibility_evidence"],
                      "approval_sha256": identity(approval)}
            # Registry/config checks may be slow; never start a transition with
            # approval that expired while those read-only checks were running.
            self.approval(action, manifest, state, approval)
            self.journal("activating", target=manifest["sha"], previous=approval["expected_current"])
            if action != "adopt":
                siblings = host.siblings()
                try:
                    host.activate(manifest, pull=action != "rollback")
                    require(host.siblings() == siblings, "sibling project identity changed")
                except (Exception, KeyboardInterrupt):
                    self.journal("restoring", target=manifest["sha"], previous=approval["expected_current"])
                    # Never downgrade schema or restore DB. Equality + explicit
                    # compatibility evidence was checked before mutation.
                    try:
                        host.activate(state["current"]["manifest"], pull=False)
                        require(host.siblings() == siblings, "sibling project identity changed during recovery")
                    except (Exception, KeyboardInterrupt):
                        self.journal("recovery-required", target=manifest["sha"], previous=approval["expected_current"])
                        raise Refused("activation and code recovery failed; transition retained") from None
                    state["result"] = "failed-release-restored"
                    self.commit(state)
                    raise Refused("activation failed; previous verified release restored") from None
            # An IO failure during commit MUST retain the marker. Do not
            # implicitly roll back a potentially committed current pointer.
            result = {"format": 1, "current": record, "previous": state["current"] if state else None,
                      "result": "adopted" if action == "adopt" else action + "-verified"}
            self.commit(result)
            return {"result": result["result"], **capacity}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("status", "preflight", "adopt", "activate", "rollback", "recover-state"))
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--state-dir", type=Path, default=Path("/opt/pawcycle/state"))
    parser.add_argument("--runtime-dir", type=Path, default=Path("/opt/pawcycle/runtime"))
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--approval", type=Path)
    parser.add_argument("--execute-approved", action="store_true")
    args = parser.parse_args()
    if args.action != "status":
        if not args.approval:
            parser.error("--approval is required")
        if args.action != "recover-state" and not args.manifest:
            parser.error("--manifest is required")
    try:
        def interrupted(*_):
            raise KeyboardInterrupt
        signal.signal(signal.SIGTERM, interrupted)
        host = Host(args.repository, args.state_dir, args.runtime_dir,
                    "/run/lock/pawcycle-production-https-renewal.lock")
        result = Release(host).run(args.action, load(args.manifest) if args.manifest else None,
                                   load(args.approval) if args.approval else None, args.execute_approved)
        print(json.dumps(result, sort_keys=True))
    except (Refused, OSError, ValueError, KeyError, TypeError, KeyboardInterrupt) as error:
        print("ERROR: " + (str(error) if isinstance(error, Refused) else "invalid input/state or interrupted operation"))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

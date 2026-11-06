#!/usr/bin/env python3
"""Opt-in offline Docker integration, ONLY in the disposable nested daemon.

See OPS-ARM64-RELEASE-003 report for bootstrap commands and explicit limits.
Uses actual exported app images; synthetic labels/entrypoints are test fixtures.
No GHCR, DB, TLS, application HTTP or native ARM64 host coverage is claimed.
"""

import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("release_tests", Path(__file__).with_name("test-oci-release.py"))
tests = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(tests)
release = tests.release
ROLES = ("backend", "frontend")


def docker(*args, data=None):
    return subprocess.run(["docker", "--host", "unix:///var/run/docker.sock", *args],
                          input=data, check=True, capture_output=True, timeout=600).stdout


def archive_records(path):
    """Verify actual content bytes, not reserialized JSON or inspect IDs."""
    records = {}
    with tarfile.open(path) as archive:
        def blob(descriptor):
            raw = archive.extractfile("blobs/sha256/" + descriptor["digest"].split(":")[1]).read()
            release.require(release.digest(raw) == descriptor["digest"] and
                            len(raw) == descriptor["size"], "archive content mismatch")
            return raw

        for entry in json.load(archive.extractfile("index.json"))["manifests"]:
            top = blob(entry)
            documents = [top.decode()]
            document = json.loads(top)
            if document["mediaType"] in release.INDEX_MEDIA_TYPES:
                matches = [item for item in document["manifests"]
                           if item.get("platform", {}).get("architecture") == "arm64" and
                           item.get("platform", {}).get("os") == "linux"]
                release.require(len(matches) == 1, "archive platform ambiguous or missing")
                child = blob(matches[0])
                documents.append(child.decode())
                document = json.loads(child)
            config = json.loads(blob(document["config"]))
            release.require(config["os"] == "linux" and config["architecture"] == "arm64",
                            "archive config platform mismatch")
            name = entry["annotations"]["io.containerd.image.name"]
            records[name] = (entry["digest"], document["config"]["digest"], documents, config)
    return records


COMPOSE = b'''services:
  backend:
    image: ${BACKEND_IMAGE}:${BACKEND_RELEASE_SHA}
    platform: linux/arm64
    networks: [app, database-egress]
    entrypoint: [/bin/sh, -c, 'while :; do sleep 3600; done']
    healthcheck:
      test: [CMD, /bin/sh, -c, 'kill -0 1']
      interval: 1s
      timeout: 1s
      retries: 3
  frontend:
    image: ${FRONTEND_IMAGE}:${FRONTEND_RELEASE_SHA}
    platform: linux/arm64
    networks: [app]
    entrypoint: [/bin/sh, -c, 'while :; do sleep 3600; done']
    healthcheck:
      test: [CMD, /bin/sh, -c, 'kill -0 1']
      interval: 1s
      timeout: 1s
      retries: 3
  proxy:
    image: pawcycle-frontend:arm64-local
    platform: linux/arm64
    networks: [app, edge]
    entrypoint: [/bin/sh, -c, 'while :; do sleep 3600; done']
    healthcheck:
      test: [CMD, /bin/sh, -c, 'kill -0 1']
      interval: 1s
      timeout: 1s
      retries: 3

networks:
  app:
    name: pawcycle-production-app
    internal: true
  edge:
    name: pawcycle-production-edge
    internal: false
  database-egress:
    name: pawcycle-production-database-egress
    internal: false
'''


class DockerHost(tests.FakeHost):
    """Keep engine, state, image gates, capacity, materialize and Compose real.

    Source/main/HTTPS/registry evidence is an offline test adapter. Pull intent
    is recorded but transported from preloaded images, never from a registry.
    """
    docker = release.Host.docker
    image = release.Host.image
    local_image = release.Host.local_image
    materialize = release.Host.materialize
    compose = release.Host.compose
    containers = release.Host.containers
    capacity = release.Host.capacity

    def __init__(self, root, records):
        super().__init__(root)
        self.records = records
        self.automation = {}
        self.kill_on_smoke = set()

    def sources(self, target):
        return COMPOSE, b"isolated nginx fixture\n"

    def registry(self, target):
        compressed = 0
        for role in ROLES:
            record = self.records[target[role]["repository"] + ":" + target["sha"]]
            documents = record[2]
            _, child, _ = release.image_graph(target[role], documents, "arm64")
            compressed += sum(layer["size"] for layer in child["layers"])
            self.registry_manifests.setdefault(release.identity(target), {})[role] = copy.deepcopy(documents)
        return compressed

    def validate_config(self, target):
        self.materialize(target)
        config = json.loads(self.compose(target, "config", "--format", "json"))
        release.require(set(config["services"]) == set((*ROLES, "proxy")), "fixture services changed")
        expected = {"backend": {"app", "database-egress"}, "frontend": {"app"},
                    "proxy": {"app", "edge"}}
        release.require(set(config["networks"]) == {"app", "edge", "database-egress"},
                        "fixture network set changed")
        for name in config["networks"]:
            network = config["networks"][name]
            release.require(network["name"] == release.PROJECT + "-" + name and
                            bool(network.get("internal")) == (name == "app"),
                            "fixture network isolation changed")
            actual = json.loads(self.docker("network", "inspect", network["name"]))[0]
            release.require(bool(actual["Internal"]) == (name == "app"),
                            "fixture runtime network isolation changed")
        for role, networks in expected.items():
            service = config["services"][role]
            release.require(set(service.get("networks", {})) == networks and
                            not service.get("network_mode") and not service.get("ports") and
                            not service.get("volumes") and service.get("healthcheck"),
                            "fixture service boundary changed")

    def siblings(self):
        return self.containers()["proxy"]["Id"]

    def smoke(self, target):
        if target["sha"] in self.kill_on_smoke:
            self.docker("kill", self.containers()["frontend"]["Id"])
        live = self.containers()
        for role in ROLES:
            local = self.local_image(target, role)
            release.require(live[role]["Image"] == local["Id"] and
                            live[role]["State"]["Running"] and
                            live[role]["State"].get("Health", {}).get("Status") == "healthy",
                            "isolated container identity/health mismatch")

    def activate(self, target, pull=True, frontend_only=False, services=None):
        self.modes.append((target["sha"], pull))
        release.Host.activate(self, target, pull=False, frontend_only=frontend_only, services=services)


class DockerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        release.require(os.environ.get("PAWCYCLE_ISOLATED_DOCKER") == "1" and
                        json.loads(docker("info", "--format", "{{json .}}"))["Name"] ==
                        "pawcycle-arm64-release-test", "disposable nested daemon required")
        cls.original = archive_records("/src/tmp/arm64-images.tar")
        docker("load", "-i", "/src/tmp/arm64-images.tar")
        cls.arch = patch.object(release, "host_architecture", return_value="arm64")
        cls.arch.start()  # Explicitly emulated: Engine and Python host remain amd64.
        cls.addClassCleanup(cls.arch.stop)
        tags = []
        with tempfile.TemporaryDirectory() as context:
            for char in ("a", "b"):
                for role in ROLES:
                    tag = "ghcr.io/guseoh/pawcycle-commerce-" + role + ":" + char * 40
                    recipe = ("FROM pawcycle-" + role + ":arm64-local\nLABEL "
                              "org.opencontainers.image.revision=" + char * 40 + " "
                              "org.opencontainers.image.source=" + release.ORIGIN.removesuffix(".git") + "\n")
                    docker("build", "--platform", "linux/arm64", "--network", "none", "--pull=false",
                           "--provenance=false", "-t", tag, "-f", "-", context, data=recipe.encode())
                    tags.append(tag)
        docker("image", "save", "-o", "/tmp/arm64-fixtures.tar", *tags)
        cls.records = archive_records("/tmp/arm64-fixtures.tar")

    def target(self, char):
        target = tests.manifest(char)
        target.update(compose_sha256=release.digest(COMPOSE),
                      nginx_sha256=release.digest(b"isolated nginx fixture\n"))
        for role in ROLES:
            record = self.records[target[role]["repository"] + ":" + target["sha"]]
            target[role].update(digest=record[0], config_digest=record[1])
        return target

    def test_original_image_platform_digest_config_and_missing_provenance(self):
        host = release.Host("/tmp", "/tmp", "/tmp", "/tmp/unused")
        for role in ROLES:
            name = "docker.io/library/pawcycle-" + role + ":arm64-local"
            top, config, documents, raw_config = self.original[name]
            target = tests.manifest("a")
            target[role].update(repository="pawcycle-" + role, digest=top, config_digest=config)
            host.registry_manifests[release.identity(target)] = {role: documents}
            _, _, selected = release.image_graph(target[role], documents, "arm64")
            inspected = json.loads(docker("image", "inspect", "--platform", "linux/arm64", name))[0]
            self.assertEqual(selected, inspected["Id"])
            # Engine inspect adds absent optional fields as null defaults.
            self.assertEqual({k: v for k, v in raw_config["config"].items() if v is not None},
                             {k: v for k, v in inspected["Config"].items() if v is not None})
            with self.assertRaisesRegex(release.Refused, "identity/platform/provenance"):
                host.local_image(target, role)
            print(json.dumps({"original": role, "platform": "linux/arm64", "digest": top,
                              "config_digest": config, "provenance": "REFUSED (missing labels)"}), flush=True)

    def test_real_compose_release_rollback_fail_closed_and_recovery(self):
        with tempfile.TemporaryDirectory(prefix="arm64-release-") as directory:
            host = DockerHost(Path(directory), self.records)
            engine = release.Release(host, application_only=True)
            a, b = self.target("a"), self.target("b")
            host.registry(a)
            host.materialize(a)
            host.compose(a, "up", "-d", "--pull", "never", "--wait")
            try:
                engine.run("adopt", a, tests.approval("adopt", a), True)
                before = host.containers()
                # Actual content mismatch must stop before any container replacement.
                tampered = copy.deepcopy(b)
                tampered["frontend"]["config_digest"] = "sha256:" + "0" * 64
                with self.assertRaisesRegex(release.Refused, "config digest"):
                    engine.run("activate", tampered, tests.approval("activate", tampered, a["sha"]), True)
                self.assertEqual({role: item["Id"] for role, item in before.items()},
                                 {role: item["Id"] for role, item in host.containers().items()})
                self.assertFalse((host.state / "release-state-transition").exists())
                engine.run("activate", b, tests.approval("activate", b, a["sha"]), True)
                self.assertNotEqual(before["frontend"]["Id"], host.containers()["frontend"]["Id"])
                # New process: reload saved digest graph, no Registry or pull.
                host.registry_manifests.clear()
                with patch.object(host, "registry", side_effect=AssertionError("rollback contacted Registry")):
                    engine.run("rollback", a, tests.approval("rollback", a, b["sha"]), True)
                self.assertEqual((a["sha"], False), host.modes[-1])
                self.assertEqual(a, engine.read_state()["current"]["manifest"])
                # Kill a replaced real container during health checks; restore A locally.
                host.kill_on_smoke = {b["sha"]}
                with self.assertRaisesRegex(release.Refused, "previous verified release restored"):
                    engine.run("activate", b, tests.approval("activate", b, a["sha"]), True)
                host.smoke(a)
                self.assertEqual("failed-release-restored", engine.read_state()["result"])
                # Recovery also fails: retain marker and fence subsequent releases.
                # Fail only after transition starts, not the baseline preflight smoke.
                original_activate = host.activate
                def fail_both(target, **options):
                    host.kill_on_smoke = {a["sha"], b["sha"]}
                    return original_activate(target, **options)
                with patch.object(host, "activate", side_effect=fail_both):
                    with self.assertRaisesRegex(release.Refused, "transition retained"):
                        engine.run("activate", b, tests.approval("activate", b, a["sha"]), True)
                self.assertEqual("recovery-required", release.load(host.state / "release-state-transition")["phase"])
                with self.assertRaisesRegex(release.Refused, "incomplete transition"):
                    engine.run("activate", b, tests.approval("activate", b, a["sha"]), True)
                host.kill_on_smoke.clear()
                host.activate(a, pull=False, services=ROLES)
                result = engine.run("recover-state", approval=tests.approval("recover-state", a, a["sha"]), execute=True)
                self.assertEqual("state-reconciled", result["result"])
                self.assertFalse((host.state / "release-state-transition").exists())
                self.assertEqual(before["proxy"]["Id"], host.containers()["proxy"]["Id"])
            finally:
                host.compose(a, "down")  # Only this disposable nested daemon's fixture.


if __name__ == "__main__":
    unittest.main(verbosity=2)

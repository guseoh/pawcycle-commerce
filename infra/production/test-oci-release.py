#!/usr/bin/env python3
"""Disposable Linux/root tests; no Production paths, registry pull or DB access."""

import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("oci_release", Path(__file__).with_name("oci-release.py"))
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


def manifest(char):
    return {
        "sha": char * 40, "compose_source_sha": "c" * 40, "nginx_source_sha": "d" * 40,
        "compose_sha256": "sha256:" + "e" * 64, "nginx_sha256": "sha256:" + "f" * 64,
        "migration_tree": "1" * 40,
        **{role: {"repository": "ghcr.io/guseoh/pawcycle-commerce-" + role,
                  "digest": "sha256:" + char * 64, "config_digest": "sha256:" + char * 64,
                  "unpacked_bytes": 2 * release.GIB} for role in ("backend", "frontend")},
    }


def approval(action, target, current=None):
    return {"action": action, "manifest_sha256": release.identity(target), "expected_current": current,
            "evidence": "OPS-OCI-CD-02/isolated", "db_compatibility_evidence": "isolated-pair-data-check",
            "db_epoch": "isolated-epoch", "expires_unix": int(time.time()) + 600,
            "compatible_with": [current] if current else [], "rollback_safe": True,
            "baseline_verified": True, "capacity_evidence": "isolated-budget",
            "reserve_bytes": release.GIB, "rollback_extra_bytes": release.GIB}


def local_image_record(target, role):
    image = target[role]
    return {"Id": image["config_digest"], "Os": "linux", "Architecture": "amd64", "Size": 100,
            "RepoDigests": [image["repository"] + "@" + image["digest"]],
            "Config": {"Labels": {"org.opencontainers.image.revision": target["sha"],
                                  "org.opencontainers.image.source": release.ORIGIN.removesuffix(".git")}}}


class FakeHost(release.Host):
    """Replace external effects only, retain engine/state/permissions/flock."""
    def __init__(self, root):
        super().__init__(root, root / "state", root / "runtime", root / "renewal.lock")
        self.state.mkdir(mode=0o700)
        self.runtime.mkdir(mode=0o700)
        for path in (self.state / "deploy.lock", self.renewal_lock):
            release.atomic(path, b"")
        self.backend_env = self.runtime / "backend.env"
        release.atomic(self.backend_env, b"isolated-only\n")
        self.env_hash = release.digest(self.backend_env.read_bytes())
        self.live = manifest("a")
        self.calls, self.failures = [], []
        self.reject = None

    def initial(self):
        release.protected(self.state, directory=True, mode=0o700)

    def sources(self, target):
        if self.reject == "source":
            raise release.Refused("source tampered")

    def registry(self, target):
        if self.reject == "registry":
            raise release.Refused("registry mismatch")
        return 100

    def capacity(self, *args):
        if self.reject == "disk":
            raise release.Refused("insufficient disk")
        return {"required_free_bytes": 10 * release.GIB, "retained_image_bytes": 4 * release.GIB}

    def image(self, target, role):
        return 100

    def validate_config(self, target):
        if self.reject == "config":
            raise release.Refused("wrong Compose boundary")

    def smoke(self, target):
        release.require(self.live == target, "stale running release or unhealthy smoke")

    def git(self, *args):
        if self.reject == "stale-candidate":
            raise release.Refused("not forward")
        return b""

    def siblings(self):
        return {"metrics": "unchanged", "performance": "unchanged"}

    def activate(self, target, pull=True):
        self.calls.append(target["sha"])
        if not pull:
            self.recovery_used_local_images = True
        self.live = target
        if self.failures:
            failure = self.failures.pop(0)
            if failure:
                raise failure


class LifecycleTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.host = FakeHost(self.root)
        self.engine = release.Release(self.host)
        self.a, self.b = manifest("a"), manifest("b")

    def tearDown(self):
        self.temporary.cleanup()

    def adopt(self):
        return self.engine.run("adopt", self.a, approval("adopt", self.a), True)

    def activate(self):
        return self.engine.run("activate", self.b, approval("activate", self.b, self.a["sha"]), True)

    def test_missing_baseline_and_execute_gate_do_not_mutate(self):
        with self.assertRaises(release.Refused):
            self.activate()
        with self.assertRaises(release.Refused):
            self.engine.run("adopt", self.a, approval("adopt", self.a))
        self.assertEqual([], self.host.calls)
        self.assertIsNone(self.engine.read_state())

    def test_adopt_requires_separate_baseline_evidence(self):
        evidence = approval("adopt", self.a)
        evidence["baseline_verified"] = False
        with self.assertRaises(release.Refused):
            self.engine.run("adopt", self.a, evidence, True)

    def test_success_duplicate_and_explicit_rollback(self):
        self.adopt()
        self.activate()
        state = self.engine.read_state()
        self.assertEqual(self.b, state["current"]["manifest"])
        self.assertEqual(self.a, state["previous"]["manifest"])
        result = self.engine.run("activate", self.b, approval("activate", self.b, self.b["sha"]), True)
        self.assertEqual("already-current", result["result"])
        self.assertEqual([self.b["sha"]], self.host.calls)
        self.engine.run("rollback", self.a, approval("rollback", self.a, self.b["sha"]), True)
        self.assertEqual(self.a, self.engine.read_state()["current"]["manifest"])
        self.assertEqual(self.a["sha"], release.read_private(self.host.state / "current-sha").decode().strip())
        self.assertFalse((self.host.state / "release-state-transition").exists())

    def test_preflight_is_nonmutating(self):
        self.adopt()
        result = self.engine.run("preflight", self.b, approval("preflight", self.b, self.a["sha"]))
        self.assertEqual("preflight-passed", result["result"])
        self.assertEqual([], self.host.calls)
        self.assertEqual(self.a, self.engine.read_state()["current"]["manifest"])

    def test_rollback_uses_saved_evidence_and_local_images_without_registry(self):
        self.adopt()
        self.activate()
        def inspect_local(*args, **kwargs):
            self.assertEqual(("image", "inspect"), args[:2])
            role = "backend" if "-backend@" in args[2] else "frontend"
            return release.encoded([local_image_record(self.a, role)])
        with patch.object(self.host, "registry", side_effect=AssertionError("Registry unavailable")), \
                patch.object(self.host, "docker", side_effect=inspect_local), \
                patch.object(self.host, "image", side_effect=lambda target, role: release.Host.image(self.host, target, role)):
            self.engine.run("rollback", self.a, approval("rollback", self.a, self.b["sha"]), True)
        self.assertEqual(self.a, self.host.live)
        self.assertTrue(self.host.recovery_used_local_images)
        self.assertEqual(self.a, self.engine.read_state()["current"]["manifest"])
        self.assertFalse((self.host.state / "release-state-transition").exists())

    def test_rollback_rejects_missing_or_tampered_local_image_without_registry(self):
        self.adopt()
        self.activate()
        for reason in ("missing", "Id", "Architecture", "RepoDigests"):
            local = local_image_record(self.a, "backend")
            if reason != "missing":
                local[reason] = [] if reason == "RepoDigests" else "tampered"
            with self.subTest(reason=reason), \
                    patch.object(self.host, "registry", side_effect=AssertionError("Registry unavailable")), \
                    patch.object(self.host, "docker", side_effect=release.Refused("local image missing") if reason == "missing" else None,
                                 return_value=release.encoded([local])), \
                    patch.object(self.host, "image", side_effect=lambda target, role: release.Host.image(self.host, target, role)):
                with self.assertRaisesRegex(release.Refused, "local"):
                    self.engine.run("rollback", self.a, approval("rollback", self.a, self.b["sha"]), True)
                self.assertEqual(self.b, self.host.live)
                self.assertEqual([self.b["sha"]], self.host.calls)

    def test_rollback_requires_stored_approval_evidence(self):
        self.adopt()
        self.activate()
        state = self.engine.read_state()
        del state["previous"]["approval_sha256"]
        release.atomic(self.host.state / "oci-release-state.json", release.encoded(state))
        with patch.object(self.host, "registry", side_effect=AssertionError("Registry unavailable")):
            with self.assertRaisesRegex(release.Refused, "stored rollback approval"):
                self.engine.run("rollback", self.a, approval("rollback", self.a, self.b["sha"]), True)
        self.assertEqual([self.b["sha"]], self.host.calls)

    def test_normal_release_keeps_remote_registry_gate(self):
        self.adopt()
        with patch.object(self.host, "registry", side_effect=release.Refused("Registry unavailable")) as registry:
            with self.assertRaisesRegex(release.Refused, "Registry unavailable"):
                self.activate()
            registry.assert_called_once_with(self.b)
        self.assertEqual([], self.host.calls)

    def test_preflight_refuses_missing_evidence_and_tampering_before_effect(self):
        self.adopt()
        for key, value in (("db_compatibility_evidence", ""), ("expected_current", "c" * 40),
                           ("db_epoch", "changed"), ("rollback_safe", False), ("compatible_with", []),
                           ("manifest_sha256", "sha256:" + "0" * 64), ("expires_unix", 0)):
            with self.subTest(key=key):
                evidence = approval("activate", self.b, self.a["sha"])
                evidence[key] = value
                with self.assertRaises(release.Refused):
                    self.engine.run("activate", self.b, evidence, True)
        for failure in ("source", "registry", "disk", "config", "stale-candidate"):
            with self.subTest(failure=failure):
                self.host.reject = failure
                with self.assertRaises(release.Refused):
                    self.activate()
        self.assertEqual([], self.host.calls)

    def test_schema_or_runtime_change_blocks_activation_and_rollback(self):
        self.adopt()
        altered = copy.deepcopy(self.b)
        altered["migration_tree"] = "2" * 40
        with self.assertRaises(release.Refused):
            self.engine.run("activate", altered, approval("activate", altered, self.a["sha"]), True)
        self.host.env_hash = "changed"
        with self.assertRaises(release.Refused):
            self.activate()
        self.assertEqual([], self.host.calls)

    def test_approval_expiring_during_preflight_cannot_start_transition(self):
        self.adopt()
        evidence = approval("activate", self.b, self.a["sha"])
        def expire(*args):
            evidence["expires_unix"] = 0
            return {}
        with patch.object(self.host, "capacity", side_effect=expire):
            with self.assertRaisesRegex(release.Refused, "expired"):
                self.engine.run("activate", self.b, evidence, True)
        self.assertEqual([], self.host.calls)
        self.assertFalse((self.host.state / "release-state-transition").exists())

    def test_unknown_or_unverified_previous_never_activates(self):
        self.adopt()
        with self.assertRaises(release.Refused):
            self.engine.run("rollback", self.b, approval("rollback", self.b, self.a["sha"]), True)
        self.activate()
        with self.assertRaises(release.Refused):
            self.engine.run("rollback", manifest("c"), approval("rollback", manifest("c"), self.b["sha"]), True)

    def test_partial_health_nginx_redirect_and_interrupt_restore_verified_current(self):
        self.adopt()
        for failure in (release.Refused("health fail"), release.Refused("Nginx 502"),
                        release.Refused("301 only"), KeyboardInterrupt()):
            self.host.failures = [failure]
            with self.assertRaisesRegex(release.Refused, "previous verified release restored"):
                self.activate()
            self.assertEqual(self.a, self.host.live)
            self.assertTrue(self.host.recovery_used_local_images)
            self.assertEqual("failed-release-restored", self.engine.read_state()["result"])
            self.assertFalse((self.host.state / "release-state-transition").exists())

    def test_partial_replacement_decode_io_and_state_errors_restore_verified_current(self):
        self.adopt()
        for failure in (ValueError("invalid state"), OSError("I/O error"),
                        json.JSONDecodeError("invalid JSON", "{", 1), KeyError("state"), TypeError("state")):
            with self.subTest(error=type(failure).__name__):
                self.host.failures = [failure]
                with self.assertRaisesRegex(release.Refused, "previous verified release restored"):
                    self.activate()
                self.assertEqual(self.a, self.host.live)
                self.assertEqual(self.a, self.engine.read_state()["current"]["manifest"])
                self.assertEqual("failed-release-restored", self.engine.read_state()["result"])
                self.assertFalse((self.host.state / "release-state-transition").exists())

    def test_post_activation_state_error_also_restores(self):
        self.adopt()
        with patch.object(self.host, "siblings", side_effect=[{}, ValueError("decode failure"), {}]):
            with self.assertRaisesRegex(release.Refused, "previous verified release restored"):
                self.activate()
        self.assertEqual(self.a, self.host.live)

    def test_decode_io_errors_during_recovery_keep_manual_gate(self):
        self.adopt()
        for recovery_failure in (ValueError("recovery parse failure"), OSError("recovery I/O failure")):
            with self.subTest(error=type(recovery_failure).__name__):
                self.host.failures = [ValueError("partial replacement"), recovery_failure]
                with self.assertRaisesRegex(release.Refused, "transition retained"):
                    self.activate()
                marker = self.host.state / "release-state-transition"
                self.assertEqual("recovery-required", release.load(marker)["phase"])
                self.assertEqual(self.a, self.engine.read_state()["current"]["manifest"])
                with self.assertRaisesRegex(release.Refused, "incomplete transition"):
                    self.activate()
                # Reset only this disposable test scenario; no CLI unlock exists.
                marker.unlink()
                self.host.live = self.a

    def test_forced_exit_during_recovery_keeps_transition(self):
        self.adopt()
        self.host.failures = [OSError("partial replacement"), SystemExit(137)]
        with self.assertRaises(SystemExit):
            self.activate()
        self.assertEqual("restoring", release.load(self.host.state / "release-state-transition")["phase"])
        self.assertEqual(self.a, self.engine.read_state()["current"]["manifest"])

    def test_recovery_failure_retains_marker_and_never_claims_success(self):
        self.adopt()
        self.host.failures = [release.Refused("partial"), release.Refused("recovery failure")]
        with self.assertRaisesRegex(release.Refused, "transition retained"):
            self.activate()
        self.assertTrue((self.host.state / "release-state-transition").exists())
        self.assertEqual(self.a, self.engine.read_state()["current"]["manifest"])
        with self.assertRaisesRegex(release.Refused, "incomplete transition"):
            self.activate()

    def test_process_death_keeps_transition_and_requires_manual_gate(self):
        self.adopt()
        self.host.failures = [SystemExit(137)]
        with self.assertRaises(SystemExit):
            self.activate()
        self.assertTrue((self.host.state / "release-state-transition").exists())
        self.assertEqual(self.a, self.engine.read_state()["current"]["manifest"])
        with self.assertRaises(release.Refused):
            self.engine.run("recover-state", approval=approval("recover-state", self.a, self.a["sha"]), execute=True)

    def test_interrupted_projection_reconciles_only_verified_live_authority(self):
        self.adopt()
        with patch.object(self.engine, "projections", side_effect=OSError("disk full")):
            with self.assertRaises(OSError):
                self.activate()
        self.assertTrue((self.host.state / "release-state-transition").exists())
        self.assertEqual(self.b, self.engine.read_state()["current"]["manifest"])
        self.engine.run("recover-state", approval=approval("recover-state", self.b, self.b["sha"]), execute=True)
        self.assertFalse((self.host.state / "release-state-transition").exists())
        self.engine.check_projections(self.engine.read_state())

    def test_stale_current_and_torn_state_stop_before_mutation(self):
        self.adopt()
        release.atomic(self.host.state / "current-sha", b"wrong\n")
        with self.assertRaises(release.Refused):
            self.activate()
        release.atomic(self.host.state / "oci-release-state.json", b'{"format":')
        with self.assertRaises(ValueError):
            self.activate()
        self.assertEqual([], self.host.calls)

    def test_deploy_and_renewal_locks_contend_with_external_flock(self):
        self.adopt()
        for path in (self.host.state / "deploy.lock", self.host.renewal_lock):
            with self.subTest(path=path), release.locked(path):
                other = subprocess.run(["flock", "-n", str(path), "true"])
                self.assertNotEqual(0, other.returncode)
                with self.assertRaises(release.Refused):
                    self.activate()
        self.assertEqual([], self.host.calls)

    def test_lock_symlink_permissions_owner_hardlink_and_parent_rejected(self):
        for kind in ("symlink", "permissions", "owner", "hardlink", "parent"):
            with self.subTest(kind=kind):
                root = self.root / kind
                root.mkdir(mode=0o700)
                target = root / "deploy.lock"
                release.atomic(target, b"sentinel")
                if kind == "symlink":
                    target.rename(root / "original")
                    target.symlink_to(root / "original")
                elif kind == "permissions":
                    target.chmod(0o666)
                elif kind == "owner":
                    os.chown(target, 1000, 1000)
                elif kind == "hardlink":
                    os.link(target, root / "alias")
                else:
                    root.chmod(0o777)
                with self.assertRaises(release.Refused):
                    with release.locked(target):
                        self.fail("unsafe lock acquired")


class BoundaryTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.host = release.Host(self.root, self.root, self.root, self.root / "renewal.lock")
        self.target = manifest("a")

    def tearDown(self):
        self.temporary.cleanup()

    def test_manifest_schema_rejects_mixed_repository_missing_digest_and_wrong_sha(self):
        for change in (lambda m: m.update(sha="main"), lambda m: m["frontend"].update(repository="other"),
                       lambda m: m["backend"].pop("digest"), lambda m: m.update(secret="do-not-store")):
            altered = copy.deepcopy(self.target)
            change(altered)
            with self.assertRaises(release.Refused):
                release.validate_manifest(altered)

    def registry_fixture(self, platform="amd64"):
        child = release.encoded({"config": {"digest": self.target["backend"]["config_digest"]},
                                 "layers": [{"size": 100}]})
        index = release.encoded({"manifests": [{"platform": {"os": "linux", "architecture": platform},
                                               "digest": release.digest(child)}]})
        for role in ("backend", "frontend"):
            self.target[role]["digest"] = release.digest(index)
        return index, child

    def test_registry_index_platform_and_content_digests(self):
        index, child = self.registry_fixture()
        remote = release.encoded({"architecture": "amd64", "os": "linux", "config": {"Labels": {
            "org.opencontainers.image.revision": self.target["sha"],
            "org.opencontainers.image.source": release.ORIGIN.removesuffix(".git")}}})
        with patch.object(self.host, "docker", side_effect=[index, child, remote, index, child, remote]):
            self.assertEqual(200, self.host.registry(self.target))
        with patch.object(self.host, "docker", side_effect=[index, child + b" "]):
            with self.assertRaises(release.Refused):
                self.host.registry(self.target)
        with patch.object(self.host, "docker", return_value=index + b" "):
            with self.assertRaises(release.Refused):
                self.host.registry(self.target)
        index, _ = self.registry_fixture("arm64")
        with patch.object(self.host, "docker", return_value=index):
            with self.assertRaises(release.Refused):
                self.host.registry(self.target)

    def test_local_image_revision_digest_platform_tamper(self):
        image = self.target["backend"]
        correct = {"Id": image["config_digest"], "Os": "linux", "Architecture": "amd64", "Size": 100,
                   "RepoDigests": [image["repository"] + "@" + image["digest"]],
                   "Config": {"Labels": {"org.opencontainers.image.revision": self.target["sha"],
                                         "org.opencontainers.image.source": release.ORIGIN.removesuffix(".git")}}}
        with patch.object(self.host, "docker", return_value=release.encoded([correct])):
            self.assertEqual(100, self.host.image(self.target, "backend"))
        for key, value in (("Id", "wrong"), ("Architecture", "arm64"), ("RepoDigests", [])):
            altered = copy.deepcopy(correct)
            altered[key] = value
            with patch.object(self.host, "docker", return_value=release.encoded([altered])):
                with self.assertRaises(release.Refused):
                    self.host.image(self.target, "backend")
        correct["Config"]["Labels"]["org.opencontainers.image.revision"] = "b" * 40
        with patch.object(self.host, "docker", return_value=release.encoded([correct])):
            with self.assertRaises(release.Refused):
                self.host.image(self.target, "backend")
        with patch.object(self.host, "docker", side_effect=release.Refused("local image missing")):
            with self.assertRaisesRegex(release.Refused, "local image missing"):
                self.host.image(self.target, "backend")

    def test_peak_budget_does_not_assume_4_9gb_or_reclaim_cache(self):
        self.host.backend_env = self.root
        evidence = approval("activate", self.target)
        fake_usage = type("Usage", (), {"f_bavail": int(4.9 * release.GIB), "f_frsize": 1})()
        with patch.object(self.host, "image", return_value=release.GIB), \
                patch.object(self.host, "docker", return_value=release.encoded({"DockerRootDir": str(self.root), "OSType": "linux"})), \
                patch.object(release.os, "statvfs", return_value=fake_usage):
            with self.assertRaisesRegex(release.Refused, "insufficient"):
                self.host.capacity(self.target, self.target, evidence, release.GIB)
            fake_usage.f_bavail = 20 * release.GIB
            result = self.host.capacity(self.target, self.target, evidence, release.GIB)
            self.assertEqual(8 * release.GIB, result["required_free_bytes"])
            self.assertEqual(2 * release.GIB, result["retained_image_bytes"])

    def test_subprocess_failures_never_print_raw_values(self):
        with self.assertRaises(release.Refused) as error:
            self.host.command("sh", "-c", "echo private-value >&2; exit 1")
        self.assertNotIn("private-value", str(error.exception))

    def test_sources_verify_separate_commits_actual_bytes_and_migration_tree(self):
        self.host.main = "9" * 40
        compose = b"source compose\n"
        nginx = Path(__file__).with_name("nginx.https.conf").read_bytes()
        self.target["compose_sha256"] = release.digest(compose)
        self.target["nginx_sha256"] = release.digest(nginx.replace(b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode()))
        outputs = [b"", b"", b"", compose, nginx, self.target["migration_tree"].encode()]
        with patch.object(self.host, "git", side_effect=outputs):
            self.assertEqual(compose, self.host.sources(self.target)[0])
        outputs[4] = b"tampered nginx"
        with patch.object(self.host, "git", side_effect=outputs):
            with self.assertRaisesRegex(release.Refused, "provenance"):
                self.host.sources(self.target)

    def test_historical_c09302a8_nginx_is_rejected_even_with_correct_source_hash(self):
        self.host.main = "9" * 40
        historical = Path(__file__).with_name("fixtures") / "nginx-c09302a8.conf"
        nginx = historical.read_bytes()
        blob = hashlib.sha1(b"blob " + str(len(nginx)).encode() + b"\0" + nginx).hexdigest()
        self.assertEqual("418cf824323778884f8e03fb222be8416cb9379b", blob)
        self.target["nginx_source_sha"] = "c09302a82d541f0c351d0f619f30232c7adaca51"
        self.target["compose_sha256"] = release.digest(b"compose")
        self.target["nginx_sha256"] = release.digest(nginx.replace(b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode()))
        with patch.object(self.host, "git", side_effect=[b"", b"", b"", b"compose", nginx]):
            with self.assertRaisesRegex(release.Refused, "approved security/DNS contract"):
                self.host.sources(self.target)

    def test_nginx_contract_rejects_security_routing_and_dns_regressions(self):
        current = Path(__file__).with_name("nginx.https.conf").read_bytes().replace(
            b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode())
        release.validate_nginx_contract(current)
        release.validate_nginx_contract(b"# source metadata\n" + current.replace(b"    ", b"\t"))
        for directive in (b"add_header Strict-Transport-Security", b"add_header X-Content-Type-Options",
                          b"add_header X-Frame-Options", b"add_header X-XSS-Protection", b"add_header Referrer-Policy",
                          b"limit_req_zone", b"limit_req zone=login_per_ip", b"location ^~ /.well-known/acme-challenge/",
                          b"listen 8081;", b"resolver 127.0.0.11", b"server backend:8080 resolve;",
                          b"server frontend:3000 resolve;", b"zone pawcycle_application_backend"):
            with self.subTest(directive=directive):
                altered = current.replace(directive, b"# disabled " + directive)
                with self.assertRaises(release.Refused):
                    release.validate_nginx_contract(altered)
        for extra in (b"\nserver { listen 443 ssl; }\n", b"\ninclude weakened.conf;\n"):
            with self.assertRaises(release.Refused):
                release.validate_nginx_contract(current + extra)

    def test_smoke_refuses_redirect_chain_even_if_final_response_is_200(self):
        def docker(*args, **kwargs):
            if "nginx" in args:
                return b""
            return b"HTTP/1.1 301 Moved\nHTTP/1.1 200 OK\n"
        with patch.object(self.host, "runtime_check"), patch.object(self.host, "docker", side_effect=docker):
            with self.assertRaisesRegex(release.Refused, "direct 2xx"):
                self.host.smoke(self.target)

    def test_safe_default_cli_cannot_activate_without_inputs(self):
        result = subprocess.run(["python3", str(Path(__file__).with_name("oci-release.py")),
                                 "activate", "--repository", str(self.root)], capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(b"--approval is required", result.stderr)

    def test_recovery_never_pulls_and_only_reconciles_application_services(self):
        commands, compose_calls = [], []
        with patch.object(self.host, "docker", side_effect=lambda *a, **k: commands.append(a) or b""), \
                patch.object(self.host, "image", return_value=100), \
                patch.object(self.host, "materialize"), patch.object(self.host, "smoke"), \
                patch.object(self.host, "compose", side_effect=lambda *a: compose_calls.append(a) or b""):
            self.host.activate(self.target, pull=False)
        self.assertFalse(any(command[0] == "pull" for command in commands))
        self.assertEqual(2, len(compose_calls))
        self.assertEqual(("backend", "frontend"), compose_calls[0][-2:])
        self.assertEqual("proxy", compose_calls[1][-1])
        for call in compose_calls:
            self.assertIn("--no-deps", call)
            self.assertIn("never", call)
            self.assertNotIn("down", call)

    def test_main_preflight_refuses_stale_cached_main(self):
        self.host.runtime.mkdir(exist_ok=True)
        with patch.object(self.host, "git", side_effect=[release.ORIGIN.encode(),
                b"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\trefs/heads/main\n", b"b" * 40]):
            with self.assertRaisesRegex(release.Refused, "cached main is stale"):
                self.host.initial()

    def test_rendered_compose_boundaries_and_read_only_nginx_validation(self):
        self.host.backend_env = self.root / "backend.env"
        self.host.automation = {}
        networks = {name: {"name": release.PROJECT + "-" + name, "internal": name == "app"}
                    for name in ("app", "edge", "database-egress")}
        config = {"networks": networks,
                  "volumes": {name: {"name": release.PROJECT + "-" + name}
                              for name in ("letsencrypt", "certbot-webroot")},
                  "services": {
                      role: {"image": self.target[role]["repository"] + ":" + self.target["sha"],
                             "networks": {name.removeprefix(release.PROJECT + "-"): {} for name in release.NETWORKS[role]}}
                      for role in ("backend", "frontend")}}
        config["services"]["proxy"] = {"image": release.PROXY, "networks": {"app": {}, "edge": {}},
                                         "volumes": [{"read_only": True}] * 3,
                                         "ports": [{"target": 80}, {"target": 443}]}
        commands = []
        def docker(*args, **kwargs):
            commands.append(args)
            return release.encoded(config) if args[0] == "compose" else b""
        with patch.object(self.host, "sources", return_value=(b"compose", b"nginx")), \
                patch.object(self.host, "docker", side_effect=docker):
            self.host.validate_config(self.target)
            self.assertEqual("run", commands[1][0])
            self.assertIn("none", commands[1])
            self.assertIn("never", commands[1])
            self.assertIn("--read-only", commands[1])
            for mutate in (
                lambda c: c["services"].update(mysql={}),
                lambda c: c["services"]["backend"].update(ports=[{"target": 8080}]),
                lambda c: c["networks"]["edge"].update(internal=True),
                lambda c: c["volumes"]["letsencrypt"].update(name="unrelated-volume"),
                lambda c: c["services"]["proxy"].update(ports=[{"target": 8081}]),
            ):
                original = copy.deepcopy(config)
                mutate(config)
                with self.assertRaises(release.Refused):
                    self.host.validate_config(self.target)
                config = original


if __name__ == "__main__":
    unittest.main()

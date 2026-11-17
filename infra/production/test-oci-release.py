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

CLASSIC_STORE = {"OSType": "linux", "Driver": "overlay2", "DriverStatus": [["Backing Filesystem", "extfs"]]}
CONTAINERD_STORE = {"OSType": "linux", "Driver": "overlayfs",
                    "DriverStatus": [["driver-type", "io.containerd.snapshotter.v1"]]}


def local_inspector(local, platform=None, info=CLASSIC_STORE):
    def docker(*args, **kwargs):
        if args == ("info", "--format", "{{json .}}"):
            return release.encoded(info)
        if args[:2] != ("image", "inspect"):
            raise AssertionError("unexpected command in local-only image verification")
        return release.encoded([platform if "--platform" in args else local])
    return docker


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
            "Config": {"Labels": {"org.opencontainers.image.revision": release.image_sha(target, role),
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
        self.modes = []
        self.service_calls = []
        self.service_ids = {"frontend": ("frontend-id", "frontend-start"), "backend": ("backend-id", "backend-start"),
                            "proxy": ("proxy-id", "proxy-start")}
        self.mutate_backend_during_frontend = False
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

    def capacity(self, *args, **kwargs):
        if self.reject == "disk":
            raise release.Refused("insufficient disk")
        return {"required_free_bytes": 10 * release.GIB, "retained_image_bytes": 4 * release.GIB}

    def image(self, target, role):
        return 100

    def validate_config(self, target):
        if self.reject == "config":
            raise release.Refused("wrong Compose boundary")

    def smoke(self, target, session_redis=True):
        release.require(self.live == target, "stale running release or unhealthy smoke")

    def git(self, *args):
        if self.reject == "stale-candidate":
            raise release.Refused("not forward")
        return b""

    def siblings(self):
        return {"metrics": "unchanged", "performance": "unchanged"}

    def containers(self):
        return {role: {"Id": identity[0], "State": {"StartedAt": identity[1]}}
                for role, identity in self.service_ids.items()}

    def activate(self, target, pull=True, frontend_only=False, services=None):
        self.calls.append(target["sha"])
        self.modes.append((target["sha"], pull, frontend_only))
        if services is not None:
            self.service_calls.append((tuple(services), pull))
            for role in services:
                self.service_ids[role] = (role + target["sha"], role + target["sha"])
            drift = getattr(self, "unselected_drift", None)
            if pull and drift:
                self.service_ids[drift] = ("unexpected", "unexpected")
        if self.mutate_backend_during_frontend and frontend_only and pull:
            self.service_ids["backend"] = ("unexpected-recreated-backend", "new-start")
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

    def test_retained_backend_state_survives_activation_and_explicit_rollback(self):
        self.b["backend"] = {**self.a["backend"], "source_sha": self.a["sha"]}
        self.adopt()
        self.activate()
        current = self.engine.read_state()["current"]["manifest"]
        self.assertEqual(self.a["backend"]["digest"], current["backend"]["digest"])
        self.assertEqual(self.a["sha"], release.image_sha(current, "backend"))
        self.engine.run("rollback", self.a, approval("rollback", self.a, self.b["sha"]), True)
        self.assertEqual(self.a, self.host.live)
        self.assertTrue(self.host.recovery_used_local_images)

    def test_application_release_and_local_rollback_track_each_service(self):
        self.adopt()
        engine = release.Release(self.host, application_only=True)
        for index, selected in enumerate((("frontend",), ("backend",), ("backend", "frontend"))):
            with self.subTest(selected=selected):
                previous = engine.read_state()["current"]["manifest"]
                target = manifest(chr(ord("b") + index))
                for role in {"backend", "frontend"} - set(selected):
                    target[role] = {**previous[role], "source_sha": release.image_sha(previous, role)}
                ids = copy.deepcopy(self.host.service_ids)
                permission = approval("activate", target, previous["sha"])
                for field in ("db_epoch", "db_compatibility_evidence", "compatible_with", "rollback_safe"):
                    permission.pop(field)
                engine.run("activate", target, permission, True)
                self.assertEqual((selected, True), self.host.service_calls[-1])
                for role in {"backend", "frontend", "proxy"} - set(selected):
                    self.assertEqual(ids[role], self.host.service_ids[role])
                self.assertIsNone(engine.read_state()["current"]["compatibility_evidence"])
        previous = engine.read_state()["previous"]["manifest"]
        engine.run("rollback", previous, approval("rollback", previous, target["sha"]), True)
        self.assertEqual((selected, False), self.host.service_calls[-1])
        self.assertEqual(previous, engine.read_state()["current"]["manifest"])

    def test_application_failure_restores_only_selected_service_and_fences_drift(self):
        self.adopt()
        self.b["frontend"] = {**self.a["frontend"], "source_sha": self.a["sha"]}
        engine = release.Release(self.host, application_only=True)
        ids = copy.deepcopy(self.host.service_ids)
        self.host.failures = [ValueError("unhealthy")]
        with self.assertRaisesRegex(release.Refused, "previous verified release restored"):
            engine.run("activate", self.b, approval("activate", self.b, self.a["sha"]), True)
        self.assertEqual([(("backend",), True), (("backend",), False)], self.host.service_calls)
        for role in ("frontend", "proxy"):
            self.assertEqual(ids[role], self.host.service_ids[role])
        self.host.unselected_drift = "proxy"
        with self.assertRaisesRegex(release.Refused, "recovery failed"):
            engine.run("activate", self.b, approval("activate", self.b, self.a["sha"]), True)
        self.assertTrue((self.host.state / "release-state-transition").exists())

    def test_application_mode_cannot_change_migration_or_runtime(self):
        self.adopt()
        engine = release.Release(self.host, application_only=True)
        for key in ("migration_tree", "compose_sha256", "nginx_sha256"):
            changed = copy.deepcopy(self.b)
            changed[key] = ("2" * 40 if key == "migration_tree" else "sha256:" + "2" * 64)
            with self.assertRaisesRegex(release.Refused, "migration/runtime"):
                engine.run("activate", changed, approval("activate", changed, self.a["sha"]), True)
        self.assertEqual([], self.host.calls)

    def test_application_adopt_needs_no_db_attestation_but_requires_baseline_approval(self):
        engine = release.Release(self.host, application_only=True)
        permission = approval("adopt", self.a)
        for key in ("db_epoch", "db_compatibility_evidence", "compatible_with", "rollback_safe"):
            permission.pop(key)
        permission["baseline_verified"] = False
        with self.assertRaisesRegex(release.Refused, "initial application baseline"):
            engine.run("adopt", self.a, permission, True)
        permission["baseline_verified"] = True
        self.assertEqual("adopted", engine.run("adopt", self.a, permission, True)["result"])
        self.assertIsNone(engine.read_state()["current"]["db_epoch"])

    def test_frontend_only_release_preserves_backend_and_proxy(self):
        self.b["backend"] = {**self.a["backend"], "source_sha": self.a["sha"]}
        self.adopt()
        ids = copy.deepcopy(self.host.service_ids)
        isolated = release.Release(self.host, frontend_only=True)
        result = isolated.run("activate", self.b, approval("activate", self.b, self.a["sha"]), True)
        self.assertEqual("activate-verified", result["result"])
        self.assertEqual(ids, self.host.service_ids)
        self.assertEqual([(self.b["sha"], True, True)], self.host.modes)
        self.assertEqual(self.a["backend"]["digest"], isolated.read_state()["current"]["manifest"]["backend"]["digest"])

    def test_frontend_only_failure_restores_frontend_without_restarting_other_services(self):
        self.b["backend"] = {**self.a["backend"], "source_sha": self.a["sha"]}
        self.adopt()
        self.host.failures = [ValueError("isolated frontend smoke failed")]
        ids = copy.deepcopy(self.host.service_ids)
        isolated = release.Release(self.host, frontend_only=True)
        with self.assertRaisesRegex(release.Refused, "previous verified release restored"):
            isolated.run("activate", self.b, approval("activate", self.b, self.a["sha"]), True)
        self.assertEqual(ids, self.host.service_ids)
        self.assertEqual([(self.b["sha"], True, True), (self.a["sha"], False, True)], self.host.modes)
        self.assertEqual(self.a, isolated.read_state()["current"]["manifest"])

    def test_frontend_only_unexpected_backend_recreation_requires_manual_recovery(self):
        self.b["backend"] = {**self.a["backend"], "source_sha": self.a["sha"]}
        self.adopt()
        self.host.mutate_backend_during_frontend = True
        isolated = release.Release(self.host, frontend_only=True)
        with self.assertRaisesRegex(release.Refused, "recovery failed"):
            isolated.run("activate", self.b, approval("activate", self.b, self.a["sha"]), True)
        self.assertTrue((self.host.state / "release-state-transition").exists())
        self.assertEqual([True, True], [m[2] for m in self.host.modes])
        self.assertEqual(self.a, isolated.read_state()["current"]["manifest"])

    def test_frontend_only_requires_retained_backend_and_same_runtime(self):
        self.adopt()
        isolated = release.Release(self.host, frontend_only=True)
        for candidate in (self.b,
                          {**self.b, "backend": {**self.a["backend"], "source_sha": self.a["sha"]},
                           "nginx_sha256": "sha256:" + "0" * 64}):
            with self.assertRaisesRegex(release.Refused, "frontend-only mode cannot change"):
                isolated.run("activate", candidate, approval("activate", candidate, self.a["sha"]), True)
        self.assertEqual([], self.host.calls)

    def test_real_host_frontend_only_invokes_compose_for_frontend_alone(self):
        commands = []
        tagged = []
        self.host.image = lambda target, role: tagged.append(role) or 100
        with patch.object(self.host, "docker", side_effect=lambda *args, **kw: commands.append(args) or b""), \
                patch.object(self.host, "materialize"), \
                patch.object(self.host, "compose") as compose, \
                patch.object(self.host, "smoke"):
            release.Host.activate(self.host, self.b, frontend_only=True)
            self.assertEqual(1, compose.call_count)
            self.assertEqual("frontend", compose.call_args.args[-1])
            self.assertNotIn("backend", compose.call_args.args[-2:])
            self.assertNotIn("proxy", compose.call_args.args[-2:])
        self.assertEqual(["backend", "frontend"], tagged)
        self.assertTrue(all("frontend" in " ".join(x) or x[0] not in ("pull", "tag") for x in commands))
        self.assertEqual(2, len(commands))  # Frontend pull and tag only.

    def test_rollback_uses_saved_evidence_and_local_images_without_registry(self):
        self.adopt()
        self.activate()
        def inspect_local(*args, **kwargs):
            if args[0] == "info":
                return release.encoded(CLASSIC_STORE)
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
                    patch.object(self.host, "capacity", side_effect=AssertionError("image rejection must precede capacity")), \
                    patch.object(self.host, "docker", side_effect=release.Refused("local image missing") if reason == "missing"
                                 else local_inspector(local)), \
                    patch.object(self.host, "image", side_effect=lambda target, role: release.Host.image(self.host, target, role)):
                with self.assertRaisesRegex(release.Refused, "local"):
                    self.engine.run("rollback", self.a, approval("rollback", self.a, self.b["sha"]), True)
                self.assertEqual(self.b, self.host.live)
                self.assertEqual([self.b["sha"]], self.host.calls)

    def test_rollback_uses_real_write_budget_without_new_image_unpack_or_registry(self):
        self.adopt()
        self.activate()
        evidence = approval("rollback", self.a, self.b["sha"])
        actual_extra = 128 * 1024 ** 2
        evidence["rollback_extra_bytes"] = actual_extra
        free = evidence["reserve_bytes"] + actual_extra
        usage = type("Usage", (), {"f_bavail": free, "f_frsize": 1})()
        info = release.encoded({"DockerRootDir": str(self.host.state), "OSType": "linux"})
        with patch.object(self.host, "registry", side_effect=AssertionError("Registry unavailable")), \
                patch.object(self.host, "docker", return_value=info), \
                patch.object(release.os, "statvfs", return_value=usage), \
                patch.object(self.host, "capacity", side_effect=lambda *a, **kw: release.Host.capacity(self.host, *a, **kw)):
            usage.f_bavail = free - 1
            with self.assertRaisesRegex(release.Refused, "insufficient"):
                self.engine.run("rollback", self.a, evidence, True)
            self.assertEqual(self.b, self.host.live)
            self.assertFalse((self.host.state / "release-state-transition").exists())
            usage.f_bavail = free
            result = self.engine.run("rollback", self.a, evidence, True)
        self.assertEqual(free, result["required_free_bytes"])
        self.assertLess(free, sum(self.a[role]["unpacked_bytes"] for role in ("backend", "frontend")))
        self.assertEqual(self.a, self.host.live)
        self.assertTrue(self.host.recovery_used_local_images)
        self.engine.check_projections(self.engine.read_state())

    def test_rollback_still_refuses_missing_release_approval_and_db_compatibility(self):
        self.adopt()
        self.activate()
        for key, value in (("db_compatibility_evidence", ""), ("db_epoch", "different"),
                           ("rollback_safe", False), ("evidence", "")):
            with self.subTest(key=key), \
                    patch.object(self.host, "registry", side_effect=AssertionError("Registry unavailable")), \
                    patch.object(self.host, "capacity", side_effect=AssertionError("approval must precede capacity")):
                evidence = approval("rollback", self.a, self.b["sha"])
                evidence[key] = value
                with self.assertRaises(release.Refused):
                    self.engine.run("rollback", self.a, evidence, True)
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
        def expire(*args, **kwargs):
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


PREVIOUS_NGINX_SOURCE = "7" * 40
CURRENT_NGINX_SOURCE = "8" * 40
UNKNOWN_NGINX_SOURCE = "6" * 40


class NginxContractSourceHost(FakeHost):
    """FakeHost whose sources() runs the real Host provenance + contract check."""

    def __init__(self, root):
        super().__init__(root)
        self.main = "9" * 40
        here = Path(__file__)
        current = here.with_name("nginx.https.conf").read_bytes()
        self.templates = {
            PREVIOUS_NGINX_SOURCE: (here.with_name("fixtures") / "nginx-128a78cb.conf").read_bytes(),
            CURRENT_NGINX_SOURCE: current,
            # A config nobody approved: one rate quietly raised.
            UNKNOWN_NGINX_SOURCE: current.replace(b"rate=10r/m;\nlimit_req_zone $binary_remote_addr zone=payment",
                                                   b"rate=99r/m;\nlimit_req_zone $binary_remote_addr zone=payment"),
        }
        assert self.templates[UNKNOWN_NGINX_SOURCE] != current

    def sources(self, target):
        return release.Host.sources(self, target)

    def git(self, *args):
        if args[0] == "show":
            revision, path = args[1].split(":", 1)
            return b"compose\n" if path == release.COMPOSE else self.templates[revision]
        if args[0] == "rev-parse":
            return b"1" * 40 + b"\n"
        return b""

    def activate(self, target, pull=True, frontend_only=False, services=None):
        # Real activation materializes the release snapshot through sources().
        self.sources(target)
        super().activate(target, pull, frontend_only, services)


def nginx_manifest(char, nginx_source, templates):
    target = manifest(char)
    target["compose_sha256"] = release.digest(b"compose\n")
    target["nginx_source_sha"] = nginx_source
    target["nginx_sha256"] = release.digest(
        templates[nginx_source].replace(b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode()))
    return target


class NginxContractTransitionTests(unittest.TestCase):
    """The first R4/R11 release must leave the previous approved Nginx behind."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.host = NginxContractSourceHost(self.root)
        self.engine = release.Release(self.host)
        templates = self.host.templates
        self.old = nginx_manifest("a", PREVIOUS_NGINX_SOURCE, templates)
        self.new = nginx_manifest("b", CURRENT_NGINX_SOURCE, templates)
        self.host.live = self.old

    def tearDown(self):
        self.temporary.cleanup()

    def adopt_old(self):
        self.engine.run("adopt", self.old, approval("adopt", self.old), True)

    def test_previous_fingerprint_is_an_explicit_single_entry(self):
        previous = (Path(__file__).with_name("fixtures") / "nginx-128a78cb.conf").read_bytes().replace(
            b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode())
        self.assertEqual(frozenset({release.nginx_contract_fingerprint(previous)}),
                         release.PREVIOUS_NGINX_CONTRACT_SHA256)
        self.assertNotIn(release.NGINX_CONTRACT_SHA256, release.PREVIOUS_NGINX_CONTRACT_SHA256)

    def test_current_previous_contract_to_new_contract_preflights_and_completes(self):
        self.adopt_old()
        result = self.engine.run("preflight", self.new, approval("preflight", self.new, self.old["sha"]))
        self.assertEqual("preflight-passed", result["result"])
        result = self.engine.run("activate", self.new, approval("activate", self.new, self.old["sha"]), True)
        self.assertEqual("activate-verified", result["result"])
        state = self.engine.read_state()
        self.assertEqual(self.new, state["current"]["manifest"])
        self.assertEqual(self.old, state["previous"]["manifest"])
        self.assertFalse(self.host.nginx_baseline)

    def test_failed_activation_restores_the_previous_contract_current(self):
        self.adopt_old()
        self.host.failures = [RuntimeError("isolated activation failure")]
        with self.assertRaisesRegex(release.Refused, "previous verified release restored"):
            self.engine.run("activate", self.new, approval("activate", self.new, self.old["sha"]), True)
        self.assertEqual(self.old, self.host.live)
        self.assertEqual(self.old, self.engine.read_state()["current"]["manifest"])
        self.assertFalse(self.host.nginx_baseline)

    def test_new_target_with_previous_contract_is_refused(self):
        self.adopt_old()
        stale = nginx_manifest("c", PREVIOUS_NGINX_SOURCE, self.host.templates)
        for action in ("preflight", "activate"):
            with self.subTest(action=action):
                with self.assertRaisesRegex(release.Refused, "approved security/DNS contract"):
                    self.engine.run(action, stale, approval(action, stale, self.old["sha"]), action == "activate")
        self.assertEqual([], self.host.calls)
        self.assertEqual(self.old, self.engine.read_state()["current"]["manifest"])

    def test_rollback_to_previous_contract_stays_refused(self):
        self.adopt_old()
        self.engine.run("activate", self.new, approval("activate", self.new, self.old["sha"]), True)
        with self.assertRaisesRegex(release.Refused, "approved security/DNS contract"):
            self.engine.run("rollback", self.old, approval("rollback", self.old, self.new["sha"]), True)
        self.assertEqual(self.new, self.engine.read_state()["current"]["manifest"])

    def test_unknown_current_contract_is_refused(self):
        unknown = nginx_manifest("c", UNKNOWN_NGINX_SOURCE, self.host.templates)
        self.host.live = unknown
        with self.assertRaisesRegex(release.Refused, "approved security/DNS contract"):
            self.engine.run("adopt", unknown, approval("adopt", unknown), True)
        self.assertIsNone(self.engine.read_state())
        # A baseline adopted under the previous contract is refused once that
        # contract is no longer on the explicit allowlist.
        self.host.live = self.old
        self.adopt_old()
        with patch.object(release, "PREVIOUS_NGINX_CONTRACT_SHA256", frozenset()):
            with self.assertRaisesRegex(release.Refused, "approved security/DNS contract"):
                self.engine.run("preflight", self.new, approval("preflight", self.new, self.old["sha"]))

    def test_unknown_target_contract_is_refused(self):
        self.adopt_old()
        unknown = nginx_manifest("c", UNKNOWN_NGINX_SOURCE, self.host.templates)
        with self.assertRaisesRegex(release.Refused, "approved security/DNS contract"):
            self.engine.run("activate", unknown, approval("activate", unknown, self.old["sha"]), True)
        self.assertEqual([], self.host.calls)

    def test_baseline_scope_admits_only_the_listed_previous_contract(self):
        historical = (Path(__file__).with_name("fixtures") / "nginx-c09302a8.conf").read_bytes().replace(
            b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode())
        previous = (Path(__file__).with_name("fixtures") / "nginx-128a78cb.conf").read_bytes().replace(
            b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode())
        with self.assertRaises(release.Refused):
            release.validate_nginx_contract(previous)
        release.validate_nginx_contract(previous, baseline=True)
        with self.assertRaises(release.Refused):
            release.validate_nginx_contract(historical, baseline=True)


class HostArchitectureTests(unittest.TestCase):
    def test_supported_native_machines_and_unsupported_host(self):
        for machine, expected in (("x86_64", "amd64"), ("aarch64", "arm64")):
            with self.subTest(machine=machine), patch.object(release.os, "uname") as uname:
                uname.return_value.machine = machine
                self.assertEqual(expected, release.host_architecture())
        with patch.object(release.os, "uname") as uname:
            uname.return_value.machine = "riscv64"
            with self.assertRaisesRegex(release.Refused, "unsupported host platform"):
                release.host_architecture()


class BoundaryTests(unittest.TestCase):
    def setUp(self):
        # Existing fixtures describe amd64, regardless of the runner's CPU.
        self.arch_patch = patch.object(release, "host_architecture", return_value="amd64")
        self.arch_patch.start()
        self.addCleanup(self.arch_patch.stop)
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.host = release.Host(self.root, self.root, self.root, self.root / "renewal.lock")
        self.target = manifest("a")

    def tearDown(self):
        self.temporary.cleanup()

    def test_arm64_registry_and_offline_descriptor_graph_fail_closed(self):
        index, child = self.registry_fixture("arm64")
        remote = release.encoded({"architecture": "arm64", "os": "linux", "config": {"Labels": {
            "org.opencontainers.image.revision": self.target["sha"],
            "org.opencontainers.image.source": release.ORIGIN.removesuffix(".git")}}})
        with patch.object(release, "host_architecture", return_value="arm64"):
            with patch.object(self.host, "docker", side_effect=[index, child, remote, index, child, remote]):
                self.assertEqual(200, self.host.registry(self.target))
            documents = self.host.registry_manifests[release.identity(self.target)]["backend"]
            local = local_image_record(self.target, "backend")
            local["Architecture"] = "arm64"
            local["Id"] = self.target["backend"]["digest"]
            local["Descriptor"] = {"digest": local["Id"], "size": len(index),
                                   "mediaType": json.loads(index)["mediaType"]}
            platform = copy.deepcopy(local)
            platform["Id"] = release.digest(child)
            platform["Descriptor"].update(digest=platform["Id"], size=len(child),
                                          mediaType=json.loads(child)["mediaType"])
            with patch.object(self.host, "docker", side_effect=local_inspector(local, platform, CONTAINERD_STORE)) as docker:
                self.assertEqual(100, self.host.image(self.target, "backend"))
                self.assertEqual(("image", "inspect", "--platform", "linux/arm64"), docker.call_args.args[:4])
            # Saved evidence is also used without registry access on rollback.
            documents[1] += " "
            with patch.object(self.host, "docker", side_effect=local_inspector(local, platform, CONTAINERD_STORE)):
                with self.assertRaisesRegex(release.Refused, "platform manifest was tampered"):
                    self.host.image(self.target, "backend")
            documents[1] = child.decode()
            platform["Architecture"] = "amd64"
            with patch.object(self.host, "docker", side_effect=local_inspector(local, platform, CONTAINERD_STORE)):
                with self.assertRaisesRegex(release.Refused, "local platform manifest mismatch"):
                    self.host.image(self.target, "backend")
            local.pop("Descriptor")
            local["Id"] = self.target["backend"]["config_digest"]
            with patch.object(self.host, "docker", side_effect=local_inspector(local)):
                self.assertEqual(100, self.host.image(self.target, "backend"))
            local["Architecture"] = "amd64"
            with patch.object(self.host, "docker", side_effect=local_inspector(local)):
                with self.assertRaisesRegex(release.Refused, "identity/platform/provenance"):
                    self.host.image(self.target, "backend")
            for bad in (index + b" ",):
                with patch.object(self.host, "docker", return_value=bad):
                    with self.assertRaises(release.Refused):
                        self.host.registry(self.target)
            with patch.object(self.host, "docker", side_effect=[index, child + b" "]):
                with self.assertRaises(release.Refused):
                    self.host.registry(self.target)
            wrong_remote = json.loads(remote)
            wrong_remote["architecture"] = "amd64"
            with patch.object(self.host, "docker", side_effect=[index, child, release.encoded(wrong_remote)]):
                with self.assertRaisesRegex(release.Refused, "platform/revision/source"):
                    self.host.registry(self.target)
            duplicate = json.loads(index)
            duplicate["manifests"].append(copy.deepcopy(duplicate["manifests"][0]))
            duplicate = release.encoded(duplicate)
            self.target["backend"]["digest"] = release.digest(duplicate)
            with patch.object(self.host, "docker", return_value=duplicate):
                with self.assertRaisesRegex(release.Refused, "ambiguous or missing"):
                    self.host.registry(self.target)

    def test_arm64_pull_and_local_only_recovery(self):
        with patch.object(release, "host_architecture", return_value="arm64"), \
                patch.object(self.host, "docker") as docker, \
                patch.object(self.host, "image", return_value=100), \
                patch.object(self.host, "materialize"), patch.object(self.host, "compose"), \
                patch.object(self.host, "smoke"):
            self.host.activate(self.target, services=("backend",))
            self.assertTrue(any(call.args[:3] == ("pull", "--platform", "linux/arm64")
                                for call in docker.call_args_list))
            docker.reset_mock()
            self.host.activate(self.target, pull=False, services=("backend",))
            self.assertFalse(any(call.args[0] == "pull" for call in docker.call_args_list))

    def test_arm64_capacity_preserves_pull_and_rollback_reserves(self):
        with patch.object(release, "host_architecture", return_value="arm64"):
            self.test_peak_budget_does_not_assume_4_9gb_or_reclaim_cache()
            self.test_rollback_capacity_requires_measured_extra_and_keeps_approved_reserve()

    def test_arm64_single_manifest_and_wrapped_remote_config(self):
        _, child = self.registry_fixture("arm64")
        for role in ("backend", "frontend"):
            self.target[role]["digest"] = release.digest(child)
        remote = release.encoded({"linux/arm64": {"architecture": "arm64", "os": "linux", "config": {"Labels": {
            "org.opencontainers.image.revision": self.target["sha"],
            "org.opencontainers.image.source": release.ORIGIN.removesuffix(".git")}}}})
        with patch.object(release, "host_architecture", return_value="arm64"), \
                patch.object(self.host, "docker", side_effect=[child, remote, child, remote]):
            self.assertEqual(200, self.host.registry(self.target))
        self.target["backend"]["config_digest"] = "sha256:" + "0" * 64
        with patch.object(release, "host_architecture", return_value="arm64"), \
                patch.object(self.host, "docker", return_value=child):
            with self.assertRaisesRegex(release.Refused, "config digest"):
                self.host.registry(self.target)

    def test_manifest_schema_rejects_mixed_repository_missing_digest_and_wrong_sha(self):
        for change in (lambda m: m.update(sha="main"), lambda m: m["frontend"].update(repository="other"),
                       lambda m: m["backend"].pop("digest"), lambda m: m.update(secret="do-not-store")):
            altered = copy.deepcopy(self.target)
            change(altered)
            with self.assertRaises(release.Refused):
                release.validate_manifest(altered)

    def test_retained_backend_preserves_original_registry_and_local_provenance(self):
        self.target["backend"]["source_sha"] = "b" * 40
        release.validate_manifest(self.target)
        _, child = self.registry_fixture()
        for role in ("backend", "frontend"):
            self.target[role]["digest"] = release.digest(child)
        def remote(sha):
            return release.encoded({"architecture": "amd64", "os": "linux", "config": {"Labels": {
                "org.opencontainers.image.revision": sha,
                "org.opencontainers.image.source": release.ORIGIN.removesuffix(".git")}}})
        with patch.object(self.host, "docker", side_effect=[child, remote("b" * 40), child, remote("a" * 40)]) as docker:
            self.assertEqual(200, self.host.registry(self.target))
            self.assertIn("ghcr.io/guseoh/pawcycle-commerce-backend:" + "b" * 40, docker.call_args_list[0].args)
        with patch.object(self.host, "docker", side_effect=[child, remote("a" * 40)]):
            with self.assertRaisesRegex(release.Refused, "revision/source"):
                self.host.registry(self.target)
        local = local_image_record(self.target, "backend")
        with patch.object(self.host, "docker", side_effect=local_inspector(local)):
            self.assertEqual(100, self.host.image(self.target, "backend"))
        local["Config"]["Labels"]["org.opencontainers.image.revision"] = "a" * 40
        with patch.object(self.host, "docker", side_effect=local_inspector(local)):
            with self.assertRaises(release.Refused):
                self.host.image(self.target, "backend")
        self.target["backend"]["source_sha"] = "invalid"
        with self.assertRaises(release.Refused):
            release.validate_manifest(self.target)
        self.target["backend"]["source_sha"] = "b" * 40
        self.target["frontend"]["source_sha"] = "b" * 40
        release.validate_manifest(self.target)
        self.target["frontend"]["source_sha"] = "invalid"
        with self.assertRaises(release.Refused):
            release.validate_manifest(self.target)

    def registry_fixture(self, platform="amd64"):
        child = release.encoded({"mediaType": "application/vnd.oci.image.manifest.v1+json",
                                 "config": {"digest": self.target["backend"]["config_digest"]},
                                 "layers": [{"size": 100}]})
        index = release.encoded({"mediaType": "application/vnd.oci.image.index.v1+json",
                                 "manifests": [{"platform": {"os": "linux", "architecture": platform},
                                               "mediaType": "application/vnd.oci.image.manifest.v1+json",
                                               "size": len(child),
                                               "digest": release.digest(child)}]})
        for role in ("backend", "frontend"):
            self.target[role]["digest"] = release.digest(index)
        return index, child

    def descriptor_fixture(self, indexed=True, architecture="amd64"):
        index, child = self.registry_fixture(architecture)
        documents = [index.decode(), child.decode()] if indexed else [child.decode()]
        if not indexed:
            self.target["backend"]["digest"] = release.digest(child)
        self.host.registry_manifests[release.identity(self.target)] = {"backend": documents}
        local = local_image_record(self.target, "backend")
        local["Architecture"] = architecture
        local["Id"] = self.target["backend"]["digest"]
        local["Descriptor"] = {"digest": local["Id"], "size": len(documents[0].encode()),
                               "mediaType": json.loads(documents[0])["mediaType"]}
        platform = copy.deepcopy(local)
        platform["Id"] = release.digest(child)
        platform["Descriptor"].update(digest=platform["Id"], size=len(child),
                                      mediaType="application/vnd.oci.image.manifest.v1+json")
        return local, platform

    def test_containerd_index_and_single_manifest_ids_are_not_config_ids(self):
        for indexed in (True, False):
            with self.subTest(indexed=indexed):
                local, platform = self.descriptor_fixture(indexed)
                self.assertNotEqual(local["Id"], self.target["backend"]["config_digest"])
                with patch.object(self.host, "docker", side_effect=local_inspector(local, platform, CONTAINERD_STORE)) as docker:
                    self.assertEqual(100, self.host.image(self.target, "backend"))
                self.assertEqual(("image", "inspect", "--platform", "linux/amd64"), docker.call_args.args[:4])

    def test_containerd_rejects_descriptor_mapping_config_and_local_tampering(self):
        gates = {"Id": "local image descriptor mismatch", "Descriptor": "local image descriptor mismatch",
                 "descriptor_media_type": "local image descriptor mismatch", "descriptor_size": "local image descriptor mismatch",
                 "RepoDigests": "local image identity/platform/provenance mismatch",
                 "platform": "local platform manifest mismatch", "platform_descriptor_size": "local platform manifest mismatch",
                 "platform_size": "local platform manifest mismatch", "platform_config": "local platform manifest mismatch",
                 "platform_repo_digests": "local platform manifest mismatch",
                 "graph": "platform manifest was tampered", "config": "registry config digest mismatch",
                 "missing": "verified local descriptor graph is unavailable"}
        for field, gate in gates.items():
            with self.subTest(field=field):
                local, platform = self.descriptor_fixture()
                documents = self.host.registry_manifests[release.identity(self.target)]["backend"]
                if field == "Id":
                    local["Id"] = self.target["backend"]["config_digest"]
                elif field == "Descriptor":
                    local["Descriptor"]["digest"] = "sha256:" + "0" * 64
                elif field == "descriptor_media_type":
                    local["Descriptor"]["mediaType"] = "application/vnd.example.invalid+json"
                elif field == "descriptor_size":
                    local["Descriptor"]["size"] += 1
                elif field == "RepoDigests":
                    local["RepoDigests"] = []
                elif field == "platform":
                    platform["Id"] = "sha256:" + "0" * 64
                elif field == "platform_descriptor_size":
                    platform["Descriptor"]["size"] += 1
                elif field == "platform_size":
                    platform["Size"] += 1
                elif field == "platform_config":
                    platform["Config"] = {"Labels": {}}
                elif field == "platform_repo_digests":
                    platform["RepoDigests"] = []
                elif field == "graph":
                    documents[1] += " "
                elif field == "config":
                    self.target["backend"]["config_digest"] = "sha256:" + "0" * 64
                    self.host.registry_manifests[release.identity(self.target)] = {"backend": documents}
                else:
                    self.host.registry_manifests.clear()
                with patch.object(self.host, "docker", side_effect=local_inspector(local, platform, CONTAINERD_STORE)):
                    with self.assertRaisesRegex(release.Refused, gate):
                        self.host.image(self.target, "backend")

    def test_running_container_uses_verified_local_id(self):
        local, _ = self.descriptor_fixture()
        for running in (local["Id"], self.target["backend"]["config_digest"]):
            containers = {"backend": {"Image": running}, "frontend": {"Image": running},
                          "proxy": {"Config": {"Image": "stop-after-image-check"}}}
            with patch.object(self.host, "containers", return_value=containers), \
                    patch.object(self.host, "local_image", return_value=local):
                message = "proxy image mismatch" if running == local["Id"] else "running image ID mismatch"
                with self.assertRaisesRegex(release.Refused, message):
                    self.host.runtime_check(self.target)

    def test_containerd_missing_descriptor_never_falls_back_to_config_id(self):
        for descriptor in ("absent", None, {}):
            with self.subTest(descriptor=descriptor):
                local = local_image_record(self.target, "backend")
                if descriptor != "absent":
                    local["Descriptor"] = descriptor
                with patch.object(self.host, "docker", side_effect=local_inspector(local, info=CONTAINERD_STORE)):
                    with self.assertRaisesRegex(release.Refused, "containerd image descriptor is missing"):
                        self.host.image(self.target, "backend")

    def test_running_container_also_requires_engine_store_and_descriptor(self):
        local = local_image_record(self.target, "backend")
        containers = {"backend": {"Image": local["Id"]}}
        with patch.object(self.host, "containers", return_value=containers), \
                patch.object(self.host, "docker", side_effect=local_inspector(local, info=CONTAINERD_STORE)):
            with self.assertRaisesRegex(release.Refused, "containerd image descriptor is missing"):
                self.host.runtime_check(self.target)

    def test_unknown_malformed_and_contradictory_engine_store_is_refused(self):
        cases = [({}, "image store information is incomplete"),
                 ({**CLASSIC_STORE, "DriverStatus": None}, "image store information is incomplete"),
                 ({**CLASSIC_STORE, "DriverStatus": [["driver-type"]]}, "image store information is incomplete"),
                 ({**CONTAINERD_STORE, "DriverStatus": CONTAINERD_STORE["DriverStatus"] * 2},
                  "image store information is ambiguous"),
                 ({**CLASSIC_STORE, "OSType": "windows"}, "unsupported image store platform"),
                 ({**CLASSIC_STORE, "Driver": "unknown"}, "unsupported or contradictory image store information"),
                 ({**CONTAINERD_STORE, "DriverStatus": []}, "unsupported or contradictory image store information"),
                 ({**CLASSIC_STORE, "DriverStatus": CONTAINERD_STORE["DriverStatus"]},
                  "unsupported or contradictory image store information"),
                 ({**CONTAINERD_STORE, "DriverStatus": [["driver-type", "unknown"]]},
                  "unsupported or contradictory image store information")]
        for info, gate in cases:
            with self.subTest(info=info), patch.object(self.host, "docker", side_effect=local_inspector(
                    local_image_record(self.target, "backend"), info=info)):
                with self.assertRaisesRegex(release.Refused, gate):
                    self.host.image(self.target, "backend")
        for descriptor in (None, {"digest": self.target["backend"]["config_digest"]}):
            local = local_image_record(self.target, "backend")
            local["Descriptor"] = descriptor
            with self.subTest(classic_descriptor=descriptor), patch.object(self.host, "docker", side_effect=local_inspector(local)):
                with self.assertRaisesRegex(release.Refused, "classic image store returned a descriptor"):
                    self.host.image(self.target, "backend")

    def test_arm64_containerd_rollback_reloads_saved_graph_without_registry(self):
        with patch.object(release, "host_architecture", return_value="arm64"):
            self.test_containerd_rollback_reloads_saved_graph_without_registry(architecture="arm64")

    def test_containerd_rollback_reloads_saved_graph_without_registry(self, architecture="amd64"):
        local, platform = self.descriptor_fixture(architecture=architecture)
        documents = copy.deepcopy(self.host.registry_manifests[release.identity(self.target)]["backend"])
        host = FakeHost(self.root)
        host.live = self.target
        engine = release.Release(host)
        def registry(target):
            host.registry_manifests[release.identity(target)] = {role: documents for role in ("backend", "frontend")}
            return 100
        with patch.object(host, "registry", side_effect=registry):
            engine.run("adopt", self.target, approval("adopt", self.target), True)
            candidate = manifest("b")
            engine.run("activate", candidate, approval("activate", candidate, self.target["sha"]), True)
        host.registry_manifests.clear()  # Simulate a new CLI process.
        for info, gate in ((CONTAINERD_STORE, "containerd image descriptor is missing"),
                           ({**CLASSIC_STORE, "Driver": "unknown"}, "unsupported or contradictory image store information")):
            with self.subTest(rollback_store=info), \
                    patch.object(host, "registry", side_effect=AssertionError("no Registry on rollback")), \
                    patch.object(host, "docker", side_effect=local_inspector(
                        local_image_record(self.target, "backend"), info=info)), \
                    patch.object(host, "image", side_effect=lambda target, role: release.Host.image(host, target, role)):
                with self.assertRaisesRegex(release.Refused, gate):
                    engine.run("rollback", self.target, approval("rollback", self.target, candidate["sha"]), True)
                self.assertEqual(candidate, host.live)
        def inspect(*args, **kwargs):
            if args[0] == "info":
                return release.encoded(CONTAINERD_STORE)
            self.assertEqual(("image", "inspect"), args[:2])
            role = "backend" if "-backend@" in args[-1] else "frontend"
            record = copy.deepcopy(platform if "--platform" in args else local)
            record["RepoDigests"] = [self.target[role]["repository"] + "@" + self.target[role]["digest"]]
            return release.encoded([record])
        with patch.object(host, "registry", side_effect=AssertionError("no Registry on rollback")), \
                patch.object(host, "docker", side_effect=inspect), \
                patch.object(host, "image", side_effect=lambda target, role: release.Host.image(host, target, role)):
            engine.run("rollback", self.target, approval("rollback", self.target, candidate["sha"]), True)
        self.assertEqual(self.target, engine.read_state()["current"]["manifest"])

    def test_single_registry_manifest_and_config_tampering(self):
        _, child = self.registry_fixture()
        for role in ("backend", "frontend"):
            self.target[role]["digest"] = release.digest(child)
        remote = release.encoded({"architecture": "amd64", "os": "linux", "config": {"Labels": {
            "org.opencontainers.image.revision": self.target["sha"],
            "org.opencontainers.image.source": release.ORIGIN.removesuffix(".git")}}})
        with patch.object(self.host, "docker", side_effect=[child, remote, child, remote]):
            self.assertEqual(200, self.host.registry(self.target))
        self.target["backend"]["config_digest"] = "sha256:" + "0" * 64
        with patch.object(self.host, "docker", return_value=child):
            with self.assertRaisesRegex(release.Refused, "config digest"):
                self.host.registry(self.target)

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
        with patch.object(self.host, "docker", side_effect=local_inspector(correct)):
            self.assertEqual(100, self.host.image(self.target, "backend"))
        for key, value in (("Id", "wrong"), ("Architecture", "arm64"), ("RepoDigests", [])):
            altered = copy.deepcopy(correct)
            altered[key] = value
            with patch.object(self.host, "docker", side_effect=local_inspector(altered)):
                gate = "local config ID mismatch" if key == "Id" else "local image identity/platform/provenance mismatch"
                with self.assertRaisesRegex(release.Refused, gate):
                    self.host.image(self.target, "backend")
        correct["Config"]["Labels"]["org.opencontainers.image.revision"] = "b" * 40
        with patch.object(self.host, "docker", side_effect=local_inspector(correct)):
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

    def test_rollback_capacity_requires_measured_extra_and_keeps_approved_reserve(self):
        self.host.backend_env = self.root
        evidence = approval("rollback", self.target)
        usage = type("Usage", (), {"f_bavail": 20 * release.GIB, "f_frsize": 1})()
        with patch.object(self.host, "image", return_value=release.GIB) as images, \
                patch.object(self.host, "docker", return_value=release.encoded({"DockerRootDir": str(self.root), "OSType": "linux"})), \
                patch.object(release.os, "statvfs", return_value=usage):
            for extra in (128 * 1024 ** 2, 512 * 1024 ** 2):
                evidence["rollback_extra_bytes"] = extra
                result = self.host.capacity(self.target, self.target, evidence, 0, rollback=True)
                self.assertEqual(evidence["reserve_bytes"] + extra, result["required_free_bytes"])
            self.assertEqual(8, images.call_count)  # current and target, both roles on each check
            for key, value in (("rollback_extra_bytes", 0), ("rollback_extra_bytes", -1),
                               ("rollback_extra_bytes", None), ("capacity_evidence", ""), ("reserve_bytes", 0)):
                with self.subTest(key=key, value=value):
                    invalid = {**evidence, key: value}
                    with self.assertRaises(release.Refused):
                        self.host.capacity(self.target, self.target, invalid, 0, rollback=True)
            for key in ("rollback_extra_bytes", "capacity_evidence"):
                invalid = dict(evidence)
                del invalid[key]
                with self.assertRaises(release.Refused):
                    self.host.capacity(self.target, self.target, invalid, 0, rollback=True)
            with self.assertRaises(release.Refused):
                self.host.capacity(self.target, self.target, evidence, release.GIB, rollback=True)
            with self.assertRaises(release.Refused):
                self.host.capacity(self.target, self.target, {**evidence, "action": "preflight"}, 0, rollback=True)

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

    def test_retained_backend_requires_ancestor_and_unchanged_backend_inputs(self):
        self.host.main = "9" * 40
        self.target["backend"]["source_sha"] = "b" * 40
        compose = b"compose\n"
        nginx = Path(__file__).with_name("nginx.https.conf").read_bytes()
        self.target["compose_sha256"] = release.digest(compose)
        self.target["nginx_sha256"] = release.digest(nginx.replace(b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode()))
        outputs = [b"", b"", b"", compose, nginx, self.target["migration_tree"].encode()]
        with patch.object(self.host, "git", side_effect=outputs + [b"", b""]) as git:
            self.host.sources(self.target)
            self.assertEqual(("merge-base", "--is-ancestor", "b" * 40, self.target["sha"]), git.call_args_list[-2].args)
            self.assertEqual(("diff", "--no-renames", "--name-only", "b" * 40, self.target["sha"]), git.call_args.args)
        for tail in ([release.Refused("not ancestor")], [b"", b"backend/src/Main.java\n"]):
            with patch.object(self.host, "git", side_effect=outputs + tail):
                with self.assertRaises(release.Refused):
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

    def test_query_logging_128a78cb_nginx_is_rejected_even_with_correct_source_hash(self):
        # The last config approved before R4/R11 logged the default combined
        # format (query strings such as ?paymentKey=) and limited only login.
        self.host.main = "9" * 40
        previous = Path(__file__).with_name("fixtures") / "nginx-128a78cb.conf"
        nginx = previous.read_bytes()
        blob = hashlib.sha1(b"blob " + str(len(nginx)).encode() + b"\0" + nginx).hexdigest()
        self.assertEqual("2af5653e0fd286feb6344e683ede7d8582b28c3a", blob)
        self.assertNotIn(b"access_log", nginx)
        self.target["nginx_source_sha"] = "128a78cb2602c7f18ea098d02467479b8c5c3d34"
        self.target["compose_sha256"] = release.digest(b"compose")
        self.target["nginx_sha256"] = release.digest(nginx.replace(b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode()))
        with patch.object(self.host, "git", side_effect=[b"", b"", b"", b"compose", nginx]):
            with self.assertRaisesRegex(release.Refused, "approved security/DNS contract"):
                self.host.sources(self.target)

    def test_nginx_contract_rejects_access_log_and_rate_limit_weakening(self):
        current = Path(__file__).with_name("nginx.https.conf").read_bytes().replace(
            b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode())
        self.assertEqual(5, current.count(b"access_log /var/log/nginx/access.log pawcycle_json;"))
        for before, after in (
                (b'"uri":"$uri"', b'"uri":"$request_uri"'),
                (b"access_log /var/log/nginx/access.log pawcycle_json;", b"access_log /var/log/nginx/access.log main;"),
                (b"access_log /var/log/nginx/access.log pawcycle_json;", b"access_log off;"),
                (b"zone=csrf_per_ip:2m rate=30r/m", b"zone=csrf_per_ip:2m rate=3000r/m"),
                (b"zone=search_per_ip burst=30", b"zone=search_per_ip burst=3000"),
                (b"(?:q|%71)", b"(?:q)"),
                (b"zone=payment_per_ip burst=10 nodelay;", b"zone=payment_per_ip burst=10;"),
                (b"return 429 '{\"code\":\"RATE_LIMITED\"", b"return 503 '{\"code\":\"RATE_LIMITED\""),
                (b"proxy_set_header X-Request-ID $pawcycle_request_id;", b"")):
            with self.subTest(before=before, after=after):
                self.assertIn(before, current)
                with self.assertRaises(release.Refused):
                    release.validate_nginx_contract(current.replace(before, after, 1))

    def test_nginx_contract_rejects_security_routing_and_dns_regressions(self):
        current = Path(__file__).with_name("nginx.https.conf").read_bytes().replace(
            b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode())
        release.validate_nginx_contract(current)
        release.validate_nginx_contract(b"# source metadata\n" + current.replace(b"    ", b"\t"))
        for directive in (b"add_header Strict-Transport-Security", b"add_header X-Content-Type-Options",
                          b"add_header X-Frame-Options", b"add_header X-XSS-Protection", b"add_header Referrer-Policy",
                          b"limit_req_zone", b"limit_req zone=login_per_ip", b"location ^~ /.well-known/acme-challenge/",
                          b"listen 8081;", b"resolver 127.0.0.11", b"server backend:8080 resolve;",
                          b"server frontend:3000 resolve;", b"zone pawcycle_application_backend",
                          b"log_format pawcycle_json", b"access_log /var/log/nginx/access.log pawcycle_json;",
                          b"map $args $pawcycle_search_limit_key", b"limit_req zone=csrf_per_ip",
                          b"limit_req zone=search_per_ip", b"limit_req zone=checkout_per_ip",
                          b"limit_req zone=payment_per_ip", b"limit_req zone=interaction_per_ip",
                          b"limit_req zone=ai_per_ip", b"location @rate_limited"):
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

    def test_smoke_uses_runtime_identity_without_executing_checkout_code(self):
        commands = []
        def docker(*args, **kwargs):
            if args[0] == "ps":
                return b"metrics-id\n"
            if args[0] == "inspect":
                return json.dumps([{"State": {"Health": {"Status": "healthy"}}}]).encode()
            if "sh" in args:
                return b"HTTP/1.1 200 OK\n"
            return b""
        def command(*args, **kwargs):
            commands.append(args)
            self.assertEqual("curl", args[0])
            return b"200"
        with patch.object(self.host, "runtime_check") as identity, \
                patch.object(self.host, "docker", side_effect=docker) as calls, \
                patch.object(self.host, "command", side_effect=command):
            self.host.smoke(self.target)
        identity.assert_called_once_with(self.target)
        self.assertEqual(2, len(commands))
        self.assertTrue(any("http://127.0.0.1:9464/actuator/prometheus" in call.args for call in calls.call_args_list))

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

    def test_real_host_selective_apply_and_recovery_never_invoke_proxy(self):
        for services in (("frontend",), ("backend",), ("backend", "frontend")):
            for pull in (True, False):
                with self.subTest(services=services, pull=pull):
                    target = copy.deepcopy(self.target)
                    target["frontend"]["source_sha"] = "b" * 40
                    commands = []
                    with patch.object(self.host, "docker", side_effect=lambda *a, **k: commands.append(a) or b""), \
                            patch.object(self.host, "image", return_value=100), \
                            patch.object(self.host, "materialize"), patch.object(self.host, "smoke"), \
                            patch.object(self.host, "compose") as compose:
                        self.host.activate(target, pull=pull, services=services)
                    self.assertEqual(1, compose.call_count)
                    self.assertEqual(services, compose.call_args.args[-len(services):])
                    self.assertNotIn("proxy", compose.call_args.args[1:])
                    tagged = [a for a in commands if a[0] == "tag"]
                    self.assertEqual(len(services), len(tagged))
                    for role in services:
                        self.assertTrue(any(a[-1] == target[role]["repository"] + ":" + release.image_sha(target, role) for a in tagged))
                    self.assertEqual(len(services) if pull else 0, len([a for a in commands if a[0] == "pull"]))

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
            self.assertEqual(commands[1][commands[1].index("--cap-drop") + 1], "ALL")
            self.assertIn("no-new-privileges:true", commands[1])
            self.assertNotIn("--cap-add", commands[1])
            self.assertNotIn("--privileged", commands[1])
            for name in ("client_temp", "proxy_temp", "fastcgi_temp", "uwsgi_temp", "scgi_temp"):
                mount = "/var/cache/nginx/" + name + ":size=1m,uid=101,gid=101,mode=0700"
                self.assertEqual(commands[1].count(mount), 1)
                position = commands[1].index(mount)
                self.assertGreater(position, 0)
                self.assertEqual(commands[1][position - 1], "--tmpfs")
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


SESSION_ENVIRONMENT = {"PAWCYCLE_SESSION_STORE": "memory", "PAWCYCLE_SESSION_REDIS_HOST": "session-redis",
                       "PAWCYCLE_SESSION_REDIS_PORT": "6379"}
# What Compose resolves from the existing runtime env file alone (no session keys).
OLD_RUNTIME_ENVIRONMENT = {"SPRING_PROFILES_ACTIVE": "production", "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED": "false"}


def rendered_config(target, environment):
    networks = {name: {"name": release.PROJECT + "-" + name, "internal": name == "app"}
                for name in ("app", "edge", "database-egress")}
    config = {"networks": networks,
              "volumes": {name: {"name": release.PROJECT + "-" + name} for name in ("letsencrypt", "certbot-webroot")},
              "services": {role: {"image": target[role]["repository"] + ":" + target["sha"],
                                  "networks": {name.removeprefix(release.PROJECT + "-"): {}
                                               for name in release.NETWORKS[role]}}
                           for role in ("backend", "frontend")}}
    config["services"]["backend"]["environment"] = dict(environment)
    config["services"]["proxy"] = {"image": release.PROXY, "networks": {"app": {}, "edge": {}},
                                   "volumes": [{"read_only": True}] * 3,
                                   "ports": [{"target": 80}, {"target": 443}]}
    return config


def session_redis_container(**changes):
    container = {"Id": "session-redis-id", "Config": {"Image": release.SESSION_REDIS_IMAGE,
                 "Labels": {"com.docker.compose.project": release.SESSION_REDIS_PROJECT,
                            "com.docker.compose.service": "session-redis"}},
                 "State": {"Running": True, "Health": {"Status": "healthy"}, "StartedAt": "redis-start"},
                 "NetworkSettings": {"Networks": {"pawcycle-production-app": {}}, "Ports": {"6379/tcp": None}},
                 "HostConfig": {"PortBindings": {}}}
    for path, value in changes.items():
        node = container
        keys = path.split(".")
        for key in keys[:-1]:
            node = node[key]
        node[keys[-1]] = value
    return container


class SessionRedisDocker:
    """Fake Docker CLI for rendered Compose config and the sibling identity."""
    def __init__(self, config, sibling=None):
        self.config, self.sibling, self.commands = config, sibling, []

    def __call__(self, *args, **kwargs):
        self.commands.append(args)
        if args[0] == "compose":
            return release.encoded(self.config)
        if args[0] == "ps":
            return (self.sibling["Id"] + "\n").encode() if self.sibling else b""
        if args[0] == "inspect":
            return release.encoded([self.sibling])
        return b""

    def session_lookups(self):
        return [c for c in self.commands if c[0] == "ps" and
                "label=com.docker.compose.project=" + release.SESSION_REDIS_PROJECT in c]


class ConfigValidatingHost(FakeHost):
    """FakeHost whose preflight runs the real rendered-Compose validation."""
    def __init__(self, root, docker):
        super().__init__(root)
        self.automation = {}
        self.fake_docker = docker

    def sources(self, target):
        return b"compose", b"nginx"

    def docker(self, *args, **kwargs):
        return self.fake_docker(*args, **kwargs)

    validate_config = release.Host.validate_config


class StopAfterSessionGate(Exception):
    pass


class SessionStoreLifecycleHost(ConfigValidatingHost):
    """Per-release session store; preflight and smoke run the real session gates."""
    def __init__(self, root, docker, environments):
        super().__init__(root, docker)
        self.environments = environments
        self.smoke_calls = []

    def validate_config(self, target):
        self.fake_docker.config = rendered_config(target, self.environments[target["sha"]])
        release.Host.validate_config(self, target)

    def docker(self, *args, **kwargs):
        if args[0] == "exec":
            raise StopAfterSessionGate()
        return self.fake_docker(*args, **kwargs)

    def smoke(self, target, session_redis=True):
        FakeHost.smoke(self, target)
        self.smoke_calls.append((target["sha"], session_redis))
        backend = {"Config": {"Env": [key + "=" + value for key, value in self.environments[target["sha"]].items()]}}
        with patch.object(self, "runtime_check", return_value={"backend": backend}):
            try:
                release.Host.smoke(self, target, session_redis)
            except StopAfterSessionGate:
                pass


class SessionRedisReleaseTests(unittest.TestCase):
    """AUTH-005: session store selection and the sibling session Redis boundary."""
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.a, self.b = manifest("a"), manifest("b")

    def tearDown(self):
        self.temporary.cleanup()

    def preflight(self, environment, sibling=None):
        docker = SessionRedisDocker(rendered_config(self.a, OLD_RUNTIME_ENVIRONMENT), sibling)
        host = ConfigValidatingHost(Path(tempfile.mkdtemp(dir=self.root)), docker)
        engine = release.Release(host)
        # Baseline adopted from a release that predates AUTH-005 (no session keys).
        engine.run("adopt", self.a, approval("adopt", self.a), True)
        docker.config = rendered_config(self.b, environment)
        docker.commands.clear()
        result = engine.run("preflight", self.b, approval("preflight", self.b, self.a["sha"]))
        return result, docker, host

    def test_existing_runtime_env_file_preflights_old_and_new_compose(self):
        # The runtime env file and its hash are unchanged; only Compose differs.
        for label, environment in (("pre-AUTH-005 compose", OLD_RUNTIME_ENVIRONMENT),
                                   ("memory default", {**OLD_RUNTIME_ENVIRONMENT, **SESSION_ENVIRONMENT})):
            with self.subTest(label):
                result, docker, host = self.preflight(environment)
                self.assertEqual("preflight-passed", result["result"])
                self.assertEqual([], docker.session_lookups(), "memory must not require the sibling")
                state = json.loads((host.state / "oci-release-state.json").read_text())
                self.assertEqual(host.env_hash, state["current"]["runtime_env_sha256"])

    def test_redis_selection_preflights_only_with_healthy_internal_sibling(self):
        redis = {**OLD_RUNTIME_ENVIRONMENT, **SESSION_ENVIRONMENT, "PAWCYCLE_SESSION_STORE": "redis"}
        result, docker, _ = self.preflight(redis, session_redis_container())
        self.assertEqual("preflight-passed", result["result"])
        self.assertEqual(1, len(docker.session_lookups()))
        self.assertFalse(any(c[0] in ("start", "restart", "stop", "rm") or
                             (c[0] == "compose" and "session-redis" in c) for c in docker.commands),
                         "release must never mutate the sibling")
        for label, sibling in (
            ("missing", None),
            ("unhealthy", session_redis_container(**{"State.Health": {"Status": "starting"}})),
            ("stopped", session_redis_container(**{"State.Running": False})),
            ("unpinned image", session_redis_container(**{"Config.Image": "redis:latest"})),
            ("extra network", session_redis_container(**{"NetworkSettings.Networks":
                                                         {"pawcycle-production-app": {}, "bridge": {}}})),
            ("host port binding", session_redis_container(**{"HostConfig.PortBindings":
                                                             {"6379/tcp": [{"HostPort": "6379"}]}})),
            ("published port", session_redis_container(**{"NetworkSettings.Ports":
                                                          {"6379/tcp": [{"HostIp": "0.0.0.0", "HostPort": "6379"}]}})),
        ):
            with self.subTest(label):
                with self.assertRaises(release.Refused):
                    self.preflight(redis, sibling)

    def test_session_store_selection_rejects_unknown_store_and_other_targets(self):
        self.assertEqual("memory", release.session_store({}))
        self.assertEqual("memory", release.session_store(SESSION_ENVIRONMENT))
        for environment in ({"PAWCYCLE_SESSION_STORE": "jdbc"}, {"PAWCYCLE_SESSION_STORE": ""},
                            {**SESSION_ENVIRONMENT, "PAWCYCLE_SESSION_STORE": "redis",
                             "PAWCYCLE_SESSION_REDIS_HOST": "redis"},
                            {**SESSION_ENVIRONMENT, "PAWCYCLE_SESSION_STORE": "redis",
                             "PAWCYCLE_SESSION_REDIS_PORT": "6380"},
                            {"PAWCYCLE_SESSION_STORE": "redis"}):
            with self.subTest(environment=environment):
                with self.assertRaises(release.Refused):
                    release.session_store(environment)

    def test_session_switch_is_not_a_runtime_env_file_key(self):
        # The canonical runtime contract stays exact: a session key in
        # backend.env is refused, so the existing baseline env (and its hash)
        # remains the accepted file and Compose is the single switch.
        entries = {"PAWCYCLE_DATASOURCE_HOST": "db.internal", "PAWCYCLE_DATASOURCE_PORT": "3306",
                   "PAWCYCLE_DATASOURCE_SSL_MODE": "REQUIRED",
                   "SPRING_DATASOURCE_URL": "jdbc:mysql://db.internal:3306/pawcycle?sslMode=REQUIRED",
                   "SPRING_DATASOURCE_USERNAME": "fixture", "SPRING_DATASOURCE_PASSWORD": "fixture",
                   "PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED": "false",
                   "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE": "10",
                   "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS": "60000"}
        self.assertEqual(entries, release.parse_canonical(release.serialize(entries)))
        with self.assertRaises(release.EnvRefused):
            release.parse_canonical(release.serialize({**entries, "PAWCYCLE_SESSION_STORE": "redis"}))

    def test_running_redis_backend_smoke_checks_sibling(self):
        host = release.Host(self.root, self.root, self.root, self.root / "renewal.lock")
        backend = {"Config": {"Env": ["SPRING_PROFILES_ACTIVE=production", "PAWCYCLE_SESSION_STORE=redis",
                                      "PAWCYCLE_SESSION_REDIS_HOST=session-redis",
                                      "PAWCYCLE_SESSION_REDIS_PORT=6379"]}}
        with patch.object(host, "runtime_check", return_value={"backend": backend}), \
                patch.object(host, "session_redis", side_effect=release.Refused("session Redis unhealthy")):
            with self.assertRaisesRegex(release.Refused, "session Redis unhealthy"):
                host.smoke(self.a)
        backend["Config"]["Env"][1] = "PAWCYCLE_SESSION_STORE=memory"
        with patch.object(host, "runtime_check", return_value={"backend": backend}), \
                patch.object(host, "session_redis") as sibling, \
                patch.object(host, "docker", side_effect=release.Refused("stop after session gate")):
            with self.assertRaisesRegex(release.Refused, "stop after session gate"):
                host.smoke(self.a)
        sibling.assert_not_called()

    def test_siblings_fence_includes_session_redis_but_not_application(self):
        host = release.Host(self.root, self.root, self.root, self.root / "renewal.lock")
        application = {"Id": "backend-id", "State": {"StartedAt": "backend-start"},
                       "Config": {"Labels": {"com.docker.compose.project": release.PROJECT}}}
        sibling = session_redis_container()
        def docker(*args, **kwargs):
            if args[:2] == ("ps", "-aq"):
                return b"backend-id\nsession-redis-id\n"
            if args[0] == "inspect":
                return release.encoded([application, sibling])
            if args[:2] == ("network", "inspect"):
                return release.encoded([{"Name": name, "Id": name + "-id"} for name in args[2:]])
            return release.encoded([{"Name": name, "CreatedAt": "created"} for name in args[2:]])
        with patch.object(host, "docker", side_effect=docker):
            fence = host.siblings()
        self.assertEqual({"session-redis-id": "redis-start"}, fence["containers"])

    def test_session_redis_restart_during_release_never_claims_success(self):
        host = FakeHost(self.root)
        engine = release.Release(host)
        engine.run("adopt", self.a, approval("adopt", self.a), True)
        before = {"containers": {"session-redis-id": "redis-start"}}
        after = {"containers": {"session-redis-id": "redis-restarted"}}
        with patch.object(host, "siblings", side_effect=[before, after, after]):
            with self.assertRaisesRegex(release.Refused, "recovery failed; transition retained"):
                engine.run("activate", self.b, approval("activate", self.b, self.a["sha"]), True)
        self.assertTrue((host.state / "release-state-transition").exists())
        state = json.loads((host.state / "oci-release-state.json").read_text())
        self.assertEqual(self.a["sha"], state["current"]["manifest"]["sha"])

    def lifecycle(self, a_environment, b_environment):
        docker = SessionRedisDocker(None, session_redis_container())
        host = SessionStoreLifecycleHost(Path(tempfile.mkdtemp(dir=self.root)), docker,
                                         {self.a["sha"]: a_environment, self.b["sha"]: b_environment})
        engine = release.Release(host)
        engine.run("adopt", self.a, approval("adopt", self.a), True)
        engine.run("activate", self.b, approval("activate", self.b, self.a["sha"]), True)
        return engine, host, docker

    def test_rollback_to_memory_succeeds_while_current_session_redis_is_down(self):
        memory = {**OLD_RUNTIME_ENVIRONMENT, **SESSION_ENVIRONMENT}
        redis = {**memory, "PAWCYCLE_SESSION_STORE": "redis"}
        for label, sibling in (("stopped", session_redis_container(**{"State.Running": False})),
                               ("unhealthy", session_redis_container(**{"State.Health": {"Status": "unhealthy"}})),
                               ("missing", None)):
            with self.subTest(label):
                engine, host, docker = self.lifecycle(memory, redis)
                docker.sibling = sibling
                # Forward releases from the redis current still require a healthy sibling.
                with self.assertRaisesRegex(release.Refused, "session Redis"):
                    engine.run("preflight", self.b, approval("preflight", self.b, self.b["sha"]))
                host.smoke_calls.clear()
                result = engine.run("rollback", self.a, approval("rollback", self.a, self.b["sha"]), True)
                self.assertEqual("rollback-verified", result["result"])
                state = engine.read_state()
                self.assertEqual(self.a, state["current"]["manifest"])
                self.assertFalse((host.state / "release-state-transition").exists())
                # Current identity smoke still ran, only without the sibling gate.
                self.assertEqual([(self.b["sha"], False)], host.smoke_calls)

    def test_rollback_to_redis_target_still_requires_healthy_session_redis(self):
        memory = {**OLD_RUNTIME_ENVIRONMENT, **SESSION_ENVIRONMENT}
        redis = {**memory, "PAWCYCLE_SESSION_STORE": "redis"}
        engine, host, docker = self.lifecycle(redis, memory)
        docker.sibling = session_redis_container(**{"State.Running": False})
        with self.assertRaisesRegex(release.Refused, "session Redis unhealthy"):
            engine.run("rollback", self.a, approval("rollback", self.a, self.b["sha"]), True)
        self.assertEqual(self.b, engine.read_state()["current"]["manifest"])
        self.assertFalse((host.state / "release-state-transition").exists())
        self.assertEqual([self.b["sha"]], host.calls, "refused before any activation")
        docker.sibling = session_redis_container()
        engine.run("rollback", self.a, approval("rollback", self.a, self.b["sha"]), True)
        self.assertEqual(self.a, engine.read_state()["current"]["manifest"])


if __name__ == "__main__":
    unittest.main()

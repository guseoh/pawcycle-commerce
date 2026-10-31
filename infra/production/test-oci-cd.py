#!/usr/bin/env python3
"""Disposable Linux/root CD integration. No Docker socket, network or live DB."""
import contextlib
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


cd = module("cd", HERE / "oci-cd.py")
fixture = module("release_fixture", HERE / "test-oci-release.py")
fixture.release = cd.release
r = cd.release


class FakeCDHost(fixture.FakeHost):
    def __init__(self, root):
        super().__init__(root)
        self.main = "b" * 40
        self.next_candidate = "b" * 40
        self.changed = ["frontend/src/app/page.tsx"]
        self.ci_failure = False
        self.refreshes = 0
        self.b = fixture.manifest("b")
        self.proof_patch = {}

    def refresh(self):
        self.refreshes += 1
        self.initial()

    def candidate(self, current):
        return self.next_candidate

    def paths(self, base, head):
        return self.changed

    def ci(self, sha):
        r.require(not self.ci_failure, "exact SHA CI unavailable")

    def manifest(self, sha, policy, current=None):
        result = copy.deepcopy(self.b)
        if current is not None:
            result["backend"] = {**current["backend"], "source_sha": r.image_sha(current, "backend")}
        return result

    def compatibility(self, current, target, policy):
        return {"current": current, "target": target, "db_epoch": "isolated-epoch",
                "runtime_env_sha256": self.env_hash, "application_data_compatible": True,
                "rollback_safe": True, "evidence": "isolated/semantic-bidirectional-check",
                "observations": {"isolated_db_baseline": True, "forward_and_reverse": True},
                "checked_unix": int(time.time()), "expires_unix": int(time.time()) + 90,
                **self.proof_patch}


class ControllerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.host = FakeCDHost(self.root)
        r.Release(self.host).run("adopt", fixture.manifest("a"), fixture.approval("adopt", fixture.manifest("a")), True)
        self.code = self.root / "installed"
        self.code.mkdir(mode=0o700)
        for name, path in (("oci-cd.py", HERE / "oci-cd.py"), ("oci-release.py", HERE / "oci-release.py"),
                           ("production_env.py", HERE / "production_env.py"),
                           ("classify-production-image-changes.py", HERE.parents[1] / "scripts/classify-production-image-changes.py")):
            shutil.copyfile(path, self.code / name)
            (self.code / name).chmod(0o600)
        self.policy_path = self.root / "policy.json"
        self.sentinel = self.root / "enabled"
        r.atomic(self.sentinel, b"enabled\n")
        self.here_patch = patch.object(cd, "HERE", self.code)
        self.enable_patch = patch.object(cd, "ENABLED_PATH", self.sentinel)
        self.here_patch.start()
        self.enable_patch.start()
        a = fixture.manifest("a")
        self.policy = {"format": 1, "enabled": True, "target": "oci-app01", "repository": r.ORIGIN,
                       "approval_evidence": "isolated/initial-policy", "expires_unix": int(time.time()) + 3600,
                       "allowed_prefixes": ["frontend/src/", "backend/src/"], "allowed_risks": ["application"],
                       "runtime_env_sha256": self.host.env_hash, "db_epoch": "isolated-epoch", "migration_tree": a["migration_tree"],
                       "compose_sha256": a["compose_sha256"], "nginx_sha256": a["nginx_sha256"],
                       "controller_sha256": {p.name: r.digest(p.read_bytes()) for p in self.code.iterdir()},
                       "capacity_evidence": "isolated/peak-measured", "reserve_bytes": r.GIB, "rollback_extra_bytes": r.GIB,
                       "stop_on": ["gate-failure", "activation-failure", "transition", "db-runtime-drift"], "notifications": "journal"}
        self.save()

    def tearDown(self):
        self.here_patch.stop()
        self.enable_patch.stop()
        self.temp.cleanup()

    def save(self):
        r.atomic(self.policy_path, r.encoded(self.policy))

    def run_cd(self):
        with contextlib.redirect_stdout(io.StringIO()):
            return cd.Controller(self.host, self.policy_path).run()

    def blocked(self):
        self.assertEqual("blocked", self.run_cd()["result"])
        self.assertEqual([], self.host.calls)

    def test_success_and_docs_only_duplicate_are_idempotent(self):
        self.assertEqual("activate-verified", self.run_cd()["result"])
        self.assertEqual(["b" * 40], self.host.calls)
        self.host.next_candidate = None
        self.assertEqual("no-candidate", self.run_cd()["result"])
        self.assertEqual(["b" * 40], self.host.calls)

    def test_uninstalled_off_and_ci_have_zero_side_effects(self):
        for mode in ("absent", "OFF", "CI", "sentinel"):
            with self.subTest(mode=mode):
                self.policy["enabled"] = mode != "OFF"
                self.save()
                if mode == "absent":
                    self.policy_path.unlink()
                if mode == "sentinel":
                    self.sentinel.unlink()
                before = {p: p.read_bytes() for p in self.host.state.iterdir()}
                with patch.dict(os.environ, {"GITHUB_ACTIONS": "true"} if mode == "CI" else {}, clear=True):
                    self.blocked()
                self.assertEqual(before, {p: p.read_bytes() for p in self.host.state.iterdir()})
                self.assertEqual(0, self.host.refreshes)

    def test_policy_permission_source_identity_expiry_and_kill_switch(self):
        for field, value in (("expires_unix", 0), ("target", "other-host"), ("approval_evidence", None),
                             ("controller_sha256", {}), ("allowed_prefixes", ["../"]), ("allowed_risks", ["unknown"])):
            original = copy.deepcopy(self.policy)
            self.policy[field] = value
            self.save()
            self.blocked()
            self.policy = original
        self.save()
        self.policy_path.chmod(0o644)
        self.blocked()
        self.policy_path.chmod(0o600)
        r.atomic(self.host.state / "oci-cd-disabled", b"operator stop\n")
        self.blocked()

    def test_out_of_scope_unknown_and_sensitive_are_not_safe(self):
        for paths in (["frontend/package.json"], ["unknown.toml"], ["backend/src/main/resources/db/migration/V99.sql"],
                      ["backend/src/main/java/com/pawcycle/backend/subscription/Worker.java"], ["infra/production/compose.yaml"]):
            self.host.changed = paths
            self.blocked()

    def test_db_runtime_migration_and_config_drift(self):
        for field in ("db_epoch", "runtime_env_sha256", "migration_tree", "compose_sha256", "nginx_sha256"):
            original = self.policy[field]
            self.policy[field] = "drift"
            self.save()
            self.blocked()
            self.policy[field] = original

    def test_no_semantic_or_stale_evidence_blocks_even_equal_migration_tree(self):
        for field, value in (("application_data_compatible", False), ("rollback_safe", False), ("evidence", ""),
                             ("observations", {}), ("target", "c" * 40), ("current", "c" * 40),
                             ("expires_unix", 0), ("db_epoch", "drift"), ("runtime_env_sha256", "drift")):
            self.host.proof_patch = {field: value}
            self.blocked()

    def test_late_ci_publish_waits_then_runs(self):
        self.host.ci_failure = True
        self.blocked()
        self.host.ci_failure = False
        self.assertEqual("activate-verified", self.run_cd()["result"])

    def test_disk_registry_config_and_lock_fail_before_activation(self):
        for failure in ("disk", "registry", "config", "stale-candidate"):
            self.host.reject = failure
            self.blocked()
        self.host.reject = None
        for path in (self.host.state / "deploy.lock", self.host.renewal_lock, self.host.state / "oci-cd.lock"):
            with r.locked(path, create=True):
                self.blocked()

    def test_main_advancing_during_preflight_can_retry_without_latch(self):
        with patch.object(self.host, "validate_config", side_effect=lambda _: setattr(self.host, "main", "c" * 40)):
            result = self.run_cd()
        self.assertEqual("blocked", result["result"])
        self.assertEqual([], self.host.calls)
        self.assertFalse(result["disabled"])
        self.host.main = "b" * 40
        self.assertEqual("activate-verified", self.run_cd()["result"])

    def test_kill_during_final_observation_blocks_before_application_mutation(self):
        original = self.host.compatibility
        calls = []
        def observe(*args):
            calls.append(1)
            if len(calls) == 2:
                r.atomic(self.host.state / "oci-cd-disabled", b"operator-stop\n")
            return original(*args)
        with patch.object(self.host, "compatibility", side_effect=observe):
            result = self.run_cd()
        self.assertEqual("blocked", result["result"])
        self.assertEqual([], self.host.calls)
        self.assertTrue(result["disabled"])  # operator kill remains authoritative

    def test_final_guard_ci_candidate_and_interrupt_are_retryable(self):
        for error in (r.Refused("CI pending"), r.Refused("candidate superseded"), KeyboardInterrupt()):
            with patch.object(self.host, "ci", side_effect=[None, error]):
                result = self.run_cd()
            self.assertEqual("blocked", result["result"])
            self.assertFalse(result["disabled"])
            self.assertEqual([], self.host.calls)
        self.assertEqual("activate-verified", self.run_cd()["result"])

    def test_attempt_is_durable_before_transition_and_interruption_latches(self):
        original = r.Release.journal
        def journal(engine, phase, **kwargs):
            self.assertTrue((self.host.state / "oci-cd-attempt").exists())
            if phase == "activating":
                raise KeyboardInterrupt()
            return original(engine, phase, **kwargs)
        with patch.object(r.Release, "journal", journal):
            self.assertEqual("failed-disabled", self.run_cd()["result"])
        self.assertEqual([], self.host.calls)
        self.assertTrue((self.host.state / "oci-cd-attempt").exists())

    def test_commit_uncertainty_keeps_attempt_and_transition(self):
        with patch.object(r.Release, "commit", side_effect=OSError("isolated disk failure")):
            self.assertEqual("failed-disabled", self.run_cd()["result"])
        self.assertEqual(["b" * 40], self.host.calls)
        self.assertTrue((self.host.state / "oci-cd-attempt").exists())
        self.assertTrue((self.host.state / "release-state-transition").exists())

    def test_health_failure_recovers_only_current_and_disables_retry(self):
        self.host.failures = [ValueError("fake smoke failed")]
        self.assertEqual("failed-disabled", self.run_cd()["result"])
        self.assertEqual(["b" * 40, "a" * 40], self.host.calls)
        self.assertTrue(self.host.recovery_used_local_images)
        self.assertEqual("a" * 40, r.Release(self.host).read_state()["current"]["manifest"]["sha"])
        calls = list(self.host.calls)
        self.run_cd()
        self.assertEqual(calls, self.host.calls)

    def test_failed_recovery_and_uncertain_commit_leave_manual_marker(self):
        self.host.failures = [ValueError("fake smoke"), ValueError("fake recovery")]
        self.assertEqual("failed-disabled", self.run_cd()["result"])
        self.assertTrue((self.host.state / "release-state-transition").exists())

    def test_incomplete_transition_and_unverified_baseline_block(self):
        r.atomic(self.host.state / "release-state-transition", b"{}\n")
        self.assertEqual("failed-disabled", self.run_cd()["result"])
        self.assertEqual([], self.host.calls)
        (self.host.state / "release-state-transition").unlink()
        (self.host.state / "oci-release-state.json").unlink()
        self.blocked()

    def test_compatibility_observer_requires_installed_hash_and_permissions(self):
        observer = self.root / "observer"
        r.atomic(observer, b"#!/bin/sh\nprintf '{}'\n")
        observer.chmod(0o700)
        policy = {"compatibility_observer": str(observer), "observer_sha256": r.digest(observer.read_bytes())}
        self.assertEqual({}, cd.CDHost.compatibility(self.host, "a" * 40, "b" * 40, policy))
        policy["observer_sha256"] = "sha256:" + "0" * 64
        with self.assertRaises(r.Refused):
            cd.CDHost.compatibility(self.host, "a" * 40, "b" * 40, policy)
        policy.update(observer_sha256=r.digest(observer.read_bytes()), compatibility_mode=cd.AUTO_MODE)
        with patch.object(self.host, "command", return_value=b"{}") as command:
            cd.CDHost.compatibility(self.host, "a" * 40, "b" * 40, policy, self.policy_path)
            command.assert_called_once_with(str(observer), "a" * 40, "b" * 40,
                                            "--auto-policy", str(self.policy_path), timeout=60)


class AutomaticControllerTests(unittest.TestCase):
    save = ControllerTests.save
    run_cd = ControllerTests.run_cd
    blocked = ControllerTests.blocked

    def setUp(self):
        ControllerTests.setUp(self)
        self.addCleanup(lambda: ControllerTests.tearDown(self))
        self.observer_tests = module("auto_observer_fixture", HERE / "test-oci-compat-observer.py")
        self.observer = self.observer_tests.compat
        self.source = self.observer_tests.AutomaticObserverTests()
        self.addCleanup(self.source.doCleanups)
        self.source.setUp()
        self.policy.update(self.source.policy)
        self.policy["runtime_env_sha256"] = self.host.env_hash
        (self.source.bundle / "backend.env").write_bytes(self.host.backend_env.read_bytes())
        self.save()
        self.host.main = self.source.target
        self.host.git = lambda *args: self.source.git_cmd(self.source.mirror, *args).encode()
        self.host.candidate = lambda current: cd.CDHost.candidate(self.host, current)
        self.host.paths = lambda *args: cd.CDHost.paths(self.host, *args)
        self.host.compatibility = lambda current, target, policy, path: self.observer.observe(
            current, target, auto_policy=path)
        self.host.b["sha"] = self.source.target
        self.host.b["migration_tree"] = self.policy["migration_tree"]
        state = r.Release(self.host).read_state()
        state["current"]["manifest"]["sha"] = self.source.baseline
        state["current"]["manifest"]["migration_tree"] = self.policy["migration_tree"]
        state["current"]["db_epoch"] = self.policy["db_epoch"]
        self.host.live = copy.deepcopy(state["current"]["manifest"])
        r.atomic(self.host.state / "oci-release-state.json", r.encoded(state))
        r.Release(self.host).projections(state)

    def test_real_git_and_observer_to_release_without_manual_pair_proof(self):
        self.assertFalse(self.source.proof.exists())
        self.assertEqual("activate-verified", self.run_cd()["result"])
        self.assertEqual([self.source.target], self.host.calls)
        self.assertEqual([(self.source.target, True, True)], self.host.modes)
        state = r.Release(self.host).read_state()
        self.assertEqual(self.source.target, state["current"]["manifest"]["sha"])
        self.assertEqual(self.source.baseline, state["current"]["manifest"]["backend"]["source_sha"])
        self.assertEqual(state["previous"]["manifest"]["backend"]["digest"],
                         state["current"]["manifest"]["backend"]["digest"])
        self.assertIn(cd.AUTO_MODE, state["current"]["compatibility_evidence"])
        self.assertIn("/backend/sha256:", state["current"]["compatibility_evidence"])
        self.assertEqual("no-candidate", self.run_cd()["result"])
        # A second merge advances once, then docs-only and duplicate ticks do not deploy.
        self.source.write(self.observer.CSS_PATH, self.source.css.replace(b'#f3b88f', b'#f1b88f'))
        target = self.source.commit()
        self.host.b = copy.deepcopy(self.host.b)
        self.host.b["sha"] = target
        self.source.write('docs/after.md', b'no release\n')
        self.host.main = self.source.commit()
        self.assertEqual("activate-verified", self.run_cd()["result"])
        self.assertEqual([self.source.target, target], self.host.calls)
        self.assertTrue(all(frontend_only for _, _, frontend_only in self.host.modes))
        self.assertEqual("no-candidate", self.run_cd()["result"])

    def test_auto_ci_registry_capacity_db_policy_and_backend_reject_with_zero_deploys(self):
        self.host.ci_failure = True
        self.blocked()
        self.host.ci_failure = False
        for reject in ("registry", "disk"):
            self.host.reject = reject
            self.blocked()
        self.host.reject = None
        with patch.object(self.host, "manifest", return_value=self.host.b):
            self.blocked()  # A newly built Backend cannot enter automatic approval.
        original = self.policy["db_epoch"]
        self.policy["db_epoch"] = "sha256:" + "0" * 64
        self.save()
        self.blocked()
        self.policy["db_epoch"] = original
        self.policy["expires_unix"] = 0
        self.save()
        self.blocked()

    def test_auto_failure_restores_verified_current_and_requires_manual_recovery(self):
        self.host.failures = [ValueError("isolated smoke failure")]
        self.assertEqual("failed-disabled", self.run_cd()["result"])
        self.assertEqual([self.source.target, self.source.baseline], self.host.calls)
        self.assertEqual([(self.source.target, True, True),
                          (self.source.baseline, False, True)], self.host.modes)
        self.assertTrue(self.host.recovery_used_local_images)
        self.run_cd()
        self.assertEqual(2, len(self.host.calls))

    def test_auto_locks_and_unsafe_tail_stop_before_deployment(self):
        for path in (self.host.state / "oci-cd.lock", self.host.state / "deploy.lock", self.host.renewal_lock):
            with r.locked(path, create=True):
                self.blocked()
        self.source.write('infra/production/oci-release.py', b'changed tool\n')
        self.host.main = self.source.commit()
        self.blocked()


class BareFetchTests(unittest.TestCase):
    def test_real_fetch_mapping_ff_initial_competition_nonff_and_cas(self):
        for mode in ("initial", "ff", "during-fetch", "before-cas", "non-ff"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                remote, local = root / "remote.git", root / "local.git"
                def git(path, *args, data=None):
                    env = dict(os.environ, GIT_AUTHOR_NAME="isolated", GIT_COMMITTER_NAME="isolated",
                               GIT_AUTHOR_EMAIL="isolated@example.invalid", GIT_COMMITTER_EMAIL="isolated@example.invalid")
                    return subprocess.check_output(["git", "-C", str(path), *args], input=data, env=env,
                                                   stderr=subprocess.DEVNULL).decode().strip()
                for path in (remote, local):
                    subprocess.check_call(["git", "init", "--bare", "-q", str(path)])
                tree = git(remote, "mktree", data=b"")
                a = git(remote, "commit-tree", tree, data=b"a\n")
                b = git(remote, "commit-tree", tree, "-p", a, data=b"b\n")
                c = git(remote, "commit-tree", tree, data=b"unrelated\n")
                git(remote, "update-ref", "refs/heads/main", a)
                git(local, "remote", "add", "origin", r.ORIGIN)
                # Default remote mapping reproduces the reported opportunistic
                # origin/main update unless refresh supplies an empty refmap.
                git(local, "config", "remote.origin.fetch", "+refs/heads/*:refs/remotes/origin/*")
                host = cd.CDHost(local, root, root, root)
                original_command = host.command
                seen = []
                phase = [None]
                def command(*args, **kwargs):
                    if "fetch" in args:
                        args = (args[0], "-c", "url." + remote.as_uri() + ".insteadOf=" + r.ORIGIN, *args[1:])
                        result = original_command(*args, **kwargs)
                        seen.append(git(local, "for-each-ref", "--format=%(objectname)", "refs/remotes/origin/main"))
                        if phase[0] == "during-fetch":
                            git(local, "update-ref", "refs/remotes/origin/main", b)
                        return result
                    if "update-ref" in args and phase[0] == "before-cas":
                        git(local, "update-ref", "refs/remotes/origin/main", b)
                    return original_command(*args, **kwargs)
                with patch.object(host, "command", side_effect=command), patch.object(host, "initial"), \
                        patch.dict(os.environ, {}, clear=True):
                    host.refresh()
                    self.assertEqual([""], seen)
                    self.assertEqual(a, git(local, "rev-parse", "refs/remotes/origin/main"))
                    if mode == "initial":
                        continue
                    git(remote, "update-ref", "refs/heads/main", c if mode == "non-ff" else b)
                    phase[0] = mode
                    if mode == "ff":
                        host.refresh()
                        self.assertEqual(a, seen[-1])
                        self.assertEqual(b, git(local, "rev-parse", "refs/remotes/origin/main"))
                    else:
                        with self.assertRaises(r.Refused):
                            host.refresh()
                        self.assertEqual(a if mode == "non-ff" else b,
                                         git(local, "rev-parse", "refs/remotes/origin/main"))
                        if mode != "non-ff":
                            self.assertEqual(a, seen[-1])


class EvidenceTests(unittest.TestCase):
    def test_automatic_manifest_reuses_backend_and_only_discovers_target_frontend(self):
        host = cd.CDHost("/unused", "/unused", "/unused", "/unused")
        a, sha = fixture.manifest("a"), "b" * 40
        raw = r.encoded({"config": {"digest": "sha256:" + "c" * 64}})
        policy = {"compatibility_mode": cd.AUTO_MODE, "unpacked_bytes": {"frontend": r.GIB}}
        with patch.object(host, "git", side_effect=[b"compose", b"nginx", b"1" * 40]), \
                patch.object(host, "docker", return_value=raw) as docker, patch.object(host, "registry"):
            target = host.manifest(sha, policy, a)
        self.assertEqual({**a["backend"], "source_sha": a["sha"]}, target["backend"])
        docker.assert_called_once_with("buildx", "imagetools", "inspect",
                                       "ghcr.io/guseoh/pawcycle-commerce-frontend:" + sha, "--raw")
        # A second automatic release keeps the original Backend source, not the previous Frontend SHA.
        with patch.object(host, "git", side_effect=[b"compose", b"nginx", b"1" * 40]), \
                patch.object(host, "docker", return_value=raw), patch.object(host, "registry"):
            next_target = host.manifest("d" * 40, policy, target)
        self.assertEqual(target["backend"], next_target["backend"])

    def test_installed_service_starts_isolated_without_importing_cwd_or_pythonpath(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            installed, untrusted = root / "installed", root / "untrusted"
            installed.mkdir()
            untrusted.mkdir()
            service = (HERE / "systemd/pawcycle-oci-cd.service").read_text()
            self.assertIn("ExecStart=/usr/bin/python3 -I ", service)
            for name, source in (("oci-cd.py", HERE / "oci-cd.py"),
                                 ("oci-release.py", HERE / "oci-release.py"),
                                 ("production_env.py", HERE / "production_env.py"),
                                 ("classify-production-image-changes.py", HERE.parents[1] / "scripts/classify-production-image-changes.py")):
                shutil.copyfile(source, installed / name)
            (untrusted / "production_env.py").write_text("raise RuntimeError('untrusted helper imported')\n")
            env = dict(os.environ, PYTHONPATH=str(untrusted))
            for name in ("oci-cd.py", "oci-release.py"):
                result = subprocess.run([sys.executable, "-I", str(installed / name), "--help"],
                                        cwd=untrusted, env=env, capture_output=True, timeout=15)
                self.assertEqual(0, result.returncode, result.stderr.decode())
                self.assertIn(b"usage:", result.stdout)
            # A missing installed helper must fail closed, never use cwd/env.
            (installed / "production_env.py").unlink()
            result = subprocess.run([sys.executable, "-I", str(installed / "oci-cd.py"), "--help"],
                                    cwd=untrusted, env=env, capture_output=True, timeout=15)
            self.assertNotEqual(0, result.returncode)
            self.assertNotIn(b"untrusted helper imported", result.stderr)

    def test_manifest_discovery_binds_pair_and_revalidates_registry(self):
        sha = "a" * 40
        host = cd.CDHost("/unused", "/unused", "/unused", "/unused")
        document = {"config": {"digest": "sha256:" + "c" * 64}, "layers": [{"size": 100}]}
        raw = r.encoded(document)
        with patch.object(host, "git", side_effect=[b"compose", b"nginx", b"1" * 40]), \
                patch.object(host, "docker", return_value=raw), patch.object(host, "registry") as registry:
            target = host.manifest(sha, {"unpacked_bytes": {"backend": r.GIB, "frontend": r.GIB}})
        self.assertEqual(sha, target["sha"])
        for role in ("backend", "frontend"):
            self.assertEqual(r.digest(raw), target[role]["digest"])
            self.assertEqual(document["config"]["digest"], target[role]["config_digest"])
        registry.assert_called_once_with(target)
        index = r.encoded({"manifests": [{"digest": "sha256:" + "d" * 64,
                                          "platform": {"os": "linux", "architecture": "amd64"}}]})
        with patch.object(host, "git", side_effect=[b"compose", b"nginx", b"1" * 40]), \
                patch.object(host, "docker", side_effect=[index, b"altered child"]):
            with self.assertRaises(r.Refused):
                host.manifest(sha, {"unpacked_bytes": {"backend": r.GIB, "frontend": r.GIB}})

    def test_exact_main_sha_and_latest_attempt_jobs(self):
        sha = "a" * 40
        host = cd.CDHost("/unused", "/unused", "/unused", "/unused")
        failure = {}
        def pages(endpoint, key):
            if key == "workflow_runs":
                workflow = endpoint.split("/")[2]
                return [{"event": failure.get("event", "push"), "head_sha": failure.get("sha", sha),
                         "head_branch": "main", "path": ".github/workflows/" + workflow,
                         "head_repository": {"full_name": cd.REPO}, "run_number": 1, "run_attempt": 1,
                         "id": 1, "status": "completed", "conclusion": failure.get("conclusion", "success")}]
            return [{"name": name, "head_sha": sha, "status": "completed", "conclusion": "success"}
                    for name in cd.JOBS | {"Publish production images"} if name != failure.get("missing")]
        with patch.object(host, "pages", side_effect=pages):
            host.ci(sha)
            for entry in ({"event": "pull_request"}, {"sha": "b" * 40}, {"conclusion": "failure"},
                          {"missing": "Backend and MySQL validation"}, {"missing": "Publish production images"}):
                failure.clear()
                failure.update(entry)
                with self.assertRaises(r.Refused):
                    host.ci(sha)

    def test_git_history_docs_tail_latest_pending_and_no_reverse(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(["git", "-C", str(root), *args], stderr=subprocess.DEVNULL).decode().strip()
            git("init", "-q")
            git("config", "user.email", "isolated@example.invalid")
            git("config", "user.name", "isolated")
            def commit(path):
                target = root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(str(time.time_ns()))
                git("add", path)
                git("commit", "-qm", "isolated")
                return git("rev-parse", "HEAD")
            baseline = commit("README.md")
            first = commit("frontend/src/app/page.tsx")
            second = commit("backend/src/main/java/Payment.java")
            tip = commit("docs/note.md")
            host = cd.CDHost(root, root, root, root)
            host.main = tip
            self.assertEqual(second, host.candidate(baseline))
            self.assertEqual(second, host.candidate(first))
            self.assertIsNone(host.candidate(second))
            host.main = first
            with self.assertRaises(r.Refused):
                host.candidate(second)

    def test_source_refresh_requires_bare_correct_origin_and_never_checks_out(self):
        host = cd.CDHost("/unused", "/unused", "/unused", "/unused")
        with patch.object(r, "protected"), patch.object(os, "geteuid", return_value=0), \
                patch.dict(os.environ, {}, clear=True), patch.object(host, "initial"), patch.object(host, "command") as command:
            with patch.object(host, "git", side_effect=[b"false"]):
                with self.assertRaises(r.Refused):
                    host.refresh()
            command.assert_not_called()
            with patch.object(host, "git", side_effect=[b"true", b"https://example.invalid/untrusted.git"]):
                with self.assertRaises(r.Refused):
                    host.refresh()
            command.assert_not_called()
            with patch.object(host, "git", side_effect=[b"true", r.ORIGIN.encode(), b"", b"a" * 40, b""]):
                host.refresh()
            args = command.call_args.args
            self.assertIn("refs/heads/main:refs/heads/cd-main", args)
            self.assertIn("--refmap=", args)
            self.assertNotIn("--force", args)
            self.assertNotIn("checkout", args)


if __name__ == "__main__":
    unittest.main()

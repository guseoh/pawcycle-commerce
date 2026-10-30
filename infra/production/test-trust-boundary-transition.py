#!/usr/bin/env python3
"""Disposable-root models plus actual Supervisor/parser integration.

All paths and Docker runtime metadata are synthetic. The real Compose config
parser is read-only and does not use a daemon. No SSH or Production execution.
"""
import fcntl
import copy
import hashlib
import importlib.util
import io
import json
import os
import signal
from pathlib import Path
import stat
import subprocess
import sys
import time
import tempfile
import unittest
from unittest.mock import patch
from production_env import (parse, projection, serialize, verify_effective,
                            Refused, AUTOMATION, signature, stable_read)
import trust_transition as transition


class Stop(Exception):
    """Only fixed reason codes may escape private comparisons."""


class Rehearsal(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="g0-t2a-fixture-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.paths = [self.root / "deploy.lock", self.root / "renewal.lock"]
        self.fds = []
        self.addCleanup(self.release)
        for path, mode in zip(self.paths, (0o600, 0o644)):
            path.write_bytes(b"nonsecret-lock-sentinel")
            path.chmod(mode)
        self.expected = [self.identity(p.lstat()) for p in self.paths]

    @staticmethod
    def identity(info):
        return info.st_dev, info.st_ino

    def validate(self, index, fd, mode):
        path_info = self.paths[index].lstat()
        fd_info = os.fstat(fd)
        for info in (path_info, fd_info):
            if (not stat.S_ISREG(info.st_mode) or info.st_nlink != 1
                    or (info.st_uid, info.st_gid) != (0, 0)
                    or stat.S_IMODE(info.st_mode) != mode
                    or self.identity(info) != self.expected[index]):
                raise Stop("LOCK_IDENTITY")

    def acquire(self):
        try:
            for index, mode in enumerate((0o600, 0o644)):
                fd = os.open(self.paths[index], os.O_RDWR | os.O_NOFOLLOW | os.O_CLOEXEC)
                self.fds.append(fd)
                self.validate(index, fd, mode)
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                self.validate(index, fd, mode)
        except BaseException:
            self.release()
            raise

    def align(self):
        if len(self.fds) != 2:
            raise Stop("LOCKS_REQUIRED")
        self.validate(0, self.fds[0], 0o600)
        self.validate(1, self.fds[1], 0o644)
        before = os.pread(self.fds[1], 4096, 0)
        os.fchmod(self.fds[1], 0o600)
        os.fsync(self.fds[1])
        self.validate(1, self.fds[1], 0o600)
        if os.pread(self.fds[1], 4096, 0) != before:
            raise Stop("LOCK_BYTES")

    def release(self):
        while self.fds:
            os.close(self.fds.pop())  # renewal before deploy; kernel unlocks.

    def assert_unlocked(self):
        for path in self.paths:
            fd = os.open(path, os.O_RDWR | os.O_NOFOLLOW)
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            finally:
                os.close(fd)

    @staticmethod
    def sync_dir(path):
        fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)

    def bundle(self, verifier, fail_at=None):
        """Synthetic nonsecret env; verifier stands in for private full check."""
        bundle = self.root / ".bundle.fixture"
        bundle.mkdir(mode=0o700)
        env = bundle / "backend.env"
        fd = os.open(env, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as stream:
            stream.write(b"FIXTURE='nonsecret'\n")
            stream.flush()
            os.fsync(stream.fileno())
        try:
            passed = verifier()
        except Exception:
            raise Stop("PRIVATE_CHECK_ERROR") from None
        if passed is not True:
            raise Stop("ENV_MISMATCH")
        if fail_at == "before-marker":
            raise Stop("INTERRUPTED")
        fd = os.open(bundle / ".complete", os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        os.fsync(fd)
        os.close(fd)
        self.sync_dir(bundle)
        if fail_at == "before-rename":
            raise Stop("INTERRUPTED")
        temporary = self.root / ".current.fixture"
        temporary.symlink_to(bundle.name)
        os.replace(temporary, self.root / "current")
        if fail_at == "before-parent-fsync":
            raise Stop("DURABILITY_UNKNOWN")
        self.sync_dir(self.root)

    def test_fd_alignment_preserves_identity_bytes_and_contention(self):
        self.acquire()
        self.assertTrue(all(not os.get_inheritable(fd) for fd in self.fds))
        for path in self.paths:
            other = os.open(path, os.O_RDWR)
            try:
                with self.assertRaises(BlockingIOError):
                    fcntl.flock(other, fcntl.LOCK_EX | fcntl.LOCK_NB)
            finally:
                os.close(other)
        self.align()
        self.assertEqual([self.identity(p.stat()) for p in self.paths], self.expected)
        self.assertEqual(self.paths[1].read_bytes(), b"nonsecret-lock-sentinel")
        self.release()
        self.assert_unlocked()

    def test_renewal_contention_releases_deploy(self):
        other = os.open(self.paths[1], os.O_RDWR)
        try:
            fcntl.flock(other, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaises(BlockingIOError):
                self.acquire()
            self.assertEqual(self.fds, [])
        finally:
            os.close(other)
        self.assert_unlocked()

    def test_inode_drift_stops_before_fchmod(self):
        self.acquire()
        old = self.paths[1].with_suffix(".old")
        self.paths[1].rename(old)
        self.paths[1].write_bytes(b"replacement")
        self.paths[1].chmod(0o644)
        with self.assertRaisesRegex(Stop, "LOCK_IDENTITY"):
            self.align()
        self.assertEqual(stat.S_IMODE(self.paths[1].stat().st_mode), 0o644)
        self.assertEqual(old.read_bytes(), b"nonsecret-lock-sentinel")

    def test_owner_mismatch_rejected(self):
        os.chown(self.paths[1], 65534, 65534)
        with self.assertRaisesRegex(Stop, "LOCK_IDENTITY"):
            self.acquire()
        self.assertEqual(self.fds, [])

    def test_symlink_and_hardlink_rejected(self):
        self.paths[1].rename(self.root / "target")
        self.paths[1].symlink_to(self.root / "target")
        with self.assertRaises(OSError):
            self.acquire()
        self.paths[1].unlink()
        os.link(self.root / "target", self.paths[1])
        with self.assertRaisesRegex(Stop, "LOCK_IDENTITY"):
            self.acquire()

    def test_partial_fsync_failure_preserves_0600_and_releases(self):
        self.acquire()
        try:
            with patch("os.fsync", side_effect=OSError("fixture EIO")):
                with self.assertRaises(OSError):
                    self.align()
        finally:
            self.release()
        self.assertEqual(stat.S_IMODE(self.paths[1].stat().st_mode), 0o600)
        self.assertEqual(self.paths[1].read_bytes(), b"nonsecret-lock-sentinel")
        self.assert_unlocked()

    def test_source_timeout_releases_without_alignment(self):
        self.acquire()
        try:
            with self.assertRaises(subprocess.TimeoutExpired):
                subprocess.run([sys.executable, "-c", "import time; time.sleep(30)"],
                               timeout=0.05, check=True, capture_output=True)
        finally:
            self.release()
        self.assertEqual(stat.S_IMODE(self.paths[1].stat().st_mode), 0o644)
        self.assert_unlocked()

    def test_process_death_releases_kernel_locks(self):
        code = ("import os,fcntl,sys; fds=[os.open(p,os.O_RDWR) for p in sys.argv[1:]];"
                "[fcntl.flock(f,fcntl.LOCK_EX|fcntl.LOCK_NB) for f in fds];"
                "print('held',flush=True);sys.stdin.read()")
        with subprocess.Popen([sys.executable, "-c", code, *map(str, self.paths)],
                              stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, text=True) as child:
            try:
                self.assertEqual(child.stdout.readline().strip(), "held")
            finally:
                child.terminate()
                child.wait(timeout=5)
        self.assert_unlocked()

    def test_legacy_exec_redirect_truncates_before_failed_flock(self):
        self.acquire()
        result = subprocess.run(["bash", "-c", 'exec 9>"$1"; flock -n 9',
                                 "fixture", str(self.paths[1])], capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.identity(self.paths[1].stat()), self.expected[1])
        self.assertEqual(self.paths[1].read_bytes(), b"")

    def test_env_mismatch_never_completes(self):
        with self.assertRaisesRegex(Stop, "ENV_MISMATCH"):
            self.bundle(lambda: False)
        self.assertFalse((self.root / ".bundle.fixture/.complete").exists())
        self.assertFalse((self.root / "current").exists())

    def test_private_exception_redacted(self):
        def fail():
            raise ValueError("nonsecret-fixture-value-must-not-escape")
        with self.assertRaisesRegex(Stop, "^PRIVATE_CHECK_ERROR$"):
            self.bundle(fail)
        self.assertFalse((self.root / ".bundle.fixture/.complete").exists())

    def test_interruption_before_marker(self):
        with self.assertRaisesRegex(Stop, "INTERRUPTED"):
            self.bundle(lambda: True, "before-marker")
        self.assertFalse((self.root / ".bundle.fixture/.complete").exists())

    def test_interruption_before_rename(self):
        with self.assertRaisesRegex(Stop, "INTERRUPTED"):
            self.bundle(lambda: True, "before-rename")
        self.assertTrue((self.root / ".bundle.fixture/.complete").exists())
        self.assertFalse((self.root / "current").exists())

    def test_crash_window_requires_observation_then_initial_absence_recovery(self):
        with self.assertRaisesRegex(Stop, "DURABILITY_UNKNOWN"):
            self.bundle(lambda: True, "before-parent-fsync")
        current = self.root / "current"
        self.assertEqual(current.resolve(), self.root / ".bundle.fixture")
        self.assertTrue((current / ".complete").exists())
        current.unlink()  # fixture-only recovery, never a lock unlink.
        self.sync_dir(self.root)
        self.assertTrue((self.root / ".bundle.fixture/backend.env").exists())
        self.assertFalse(current.exists())

    def test_success_marker_mode_and_atomic_link(self):
        self.bundle(lambda: True)
        self.assertEqual((self.root / "current").resolve(), self.root / ".bundle.fixture")
        self.assertEqual(stat.S_IMODE((self.root / "current/.complete").stat().st_mode), 0o600)


class FakeRunner:
    def __init__(self):
        self.calls = []
        self.documents = []
        self.host = None
        self.timer = 'active'
        self.fail = None
        self.resolved = None
        self.started = False
        self.renewal_active = False
        self.queued_before_start = False

    def check(self):
        if self.fail == 'window':
            raise Refused('WINDOW_EXPIRED')

    def __call__(self, argv, env=None):
        self.calls.append(argv)
        if argv[0] == '/usr/bin/systemctl':
            if argv[1] == 'show-environment':
                return b''
            if '-pExecStart' in argv:
                unit = argv[2]
                raw = 'DropInPaths=\nFragmentPath='+str(self.host.path('/etc/systemd/system/'+unit))+'\n'
                if unit == transition.SERVICE:
                    raw += ('User=root\nGroup=root\nType=oneshot\nEnvironment=\nEnvironmentFiles=\n'
                            'ExecStart={ path='+str(self.host.installed)+' ; argv[]='+str(self.host.installed)+' ; }\n')
                return raw.encode()
            if argv[1:3] == ['stop', transition.TIMER]:
                if self.started and self.fail == 'stop-after-start':
                    raise Refused('COMMAND_TIMEOUT')
                self.timer = 'inactive'
                return b''
            if argv[1:3] == ['start', transition.TIMER]:
                self.timer = 'active'
                self.started = True
                if self.fail == 'start-ack-lost':
                    raise Refused('COMMAND_TIMEOUT')
                return b''
            if argv[1] == 'list-jobs':
                return transition.SERVICE.encode() if (
                    self.queued_before_start or (self.started and self.renewal_active)
                ) else b''
            if argv[2] == transition.TIMER:
                if self.started and self.fail == 'timer-observation':
                    raise Refused('COMMAND_TIMEOUT')
                return ('ActiveState='+self.timer+'\nUnitFileState=enabled\n').encode()
            if self.started and self.renewal_active:
                return b'ActiveState=active\nMainPID=123\n'
            return b'ActiveState=inactive\nMainPID=0\n'
        if argv[0] == '/usr/bin/git':
            if self.fail == 'git-timeout':
                raise Refused('COMMAND_TIMEOUT')
            args = argv[argv.index('-C')+2:]
            if args[:3] == ['remote', 'get-url', 'origin']:
                return transition.ORIGIN.encode()
            if args == ['rev-parse', 'HEAD']:
                return self.host.p['source_sha'].encode()
            if args[:1] == ['ls-remote']:
                return (self.host.p['main_sha']+'\trefs/heads/main\n').encode()
            if args[:1] == ['show']:
                return (self.host.source/args[1].split(':', 1)[1]).read_bytes()
            if args[:1] == ['ls-tree']:
                return b'100644 blob 0000000000000000000000000000000000000000\tfixture\n'
            return b''
        if argv[0] == '/usr/bin/docker':
            args = argv[1:]
            if args == ['ps', '-aq']:
                return ' '.join(c['Id'] for c in self.documents).encode()
            if args[0] == 'inspect':
                return json.dumps(self.documents).encode()
            if args[:2] == ['image', 'inspect']:
                return json.dumps([{'Id': self.documents[0]['Image'],
                                    'Config': {'Env': ['PATH=/usr/bin']}}]).encode()
            if args[:2] == ['compose', 'version']:
                return b'fixture-version\n'
            if args[0] == 'compose':
                if self.fail == 'compose-error':
                    raise RuntimeError('must-not-escape-fixture-secret')
                resolved = self.resolved or self.environment()
                return json.dumps({'services': {'backend': {
                    'environment': resolved, 'image': self.documents[0]['Config']['Image']}}}).encode()
        raise AssertionError('unexpected fixture command')

    def environment(self):
        data = parse((self.host.runtime/'backend.env').read_bytes())
        return {**data, 'SPRING_PROFILES_ACTIVE': 'production'}


class FixtureTransition(transition.Transition):
    def recovery_runner(self):
        return self.run

    def no_writer(self):
        # Production /proc scan is separately tested; synthetic root is private.
        pass

    def invariants(self, before_protection=False):
        return b'public-fixture-cert', {'https-enabled': b'enabled'}, ['fixture-volume']


class SupervisedIntegration(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='g0-t2a-integrated-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.runner = FakeRunner()
        self.entries = {
            'PAWCYCLE_DATASOURCE_HOST': 'db.example.test',
            'PAWCYCLE_DATASOURCE_PORT': '3306',
            'PAWCYCLE_DATASOURCE_SSL_MODE': 'REQUIRED',
            'SPRING_DATASOURCE_URL': 'jdbc:mysql://db.example.test:3306/fixture?sslMode=REQUIRED',
            'SPRING_DATASOURCE_USERNAME': 'fixture-user',
            'SPRING_DATASOURCE_PASSWORD': "fixture'password",
            AUTOMATION[0]: 'true', AUTOMATION[1]: '7', AUTOMATION[2]: '12345',
        }
        packet = {'source_sha': 'a'*40, 'id': 'fixture'}
        self.host = FixtureTransition(packet, root=self.root, runner=self.runner)
        self.runner.host = self.host
        h = self.host
        for path in [h.control, h.state, h.runtime, h.staging, h.staging/'.git',
                     h.installed.parent, h.renewal.parent, h.base/'source/repo/infra/production']:
            path.mkdir(parents=True, exist_ok=True, mode=0o700)
        for name in ('docker', 'git', 'openssl', 'systemctl'):
            self.write(h.path('/usr/bin/'+name), b'fixture-not-executable', 0o755)
        here = Path(__file__).parent
        self.write(h.staging/transition.COMPOSE, (here/'compose.yaml').read_bytes(), 0o644)
        self.write(h.staging/transition.RENEW, b'#!/bin/bash\nexec 9<>"$LOCK_FILE"\n', 0o755)
        for unit in (transition.SERVICE, transition.TIMER):
            data = (here/'systemd'/unit).read_bytes()
            self.write(h.staging/'infra/production/systemd'/unit, data, 0o644)
            self.write(h.path('/etc/systemd/system/'+unit), data, 0o644)
        self.write(h.base/'source/repo'/transition.COMPOSE,
                   (here/'compose.yaml').read_bytes(), 0o644)
        self.write(h.installed, b'#!/bin/bash\nexec 9>"$LOCK_FILE"\n', 0o755)
        self.write(h.runtime/'backend.env', serialize(self.entries), 0o600)
        self.write(h.runtime/'nginx-https.conf', b'fixture nginx public bytes', 0o600)
        self.write(h.deploy, b'deploy-sentinel', 0o600)
        self.write(h.renewal, b'renewal-sentinel', 0o644)
        for path in h.allowed_changes():
            os.chown(path, 1000, 1000)
        os.chown(h.runtime/'backend.env', 1000, 1000)
        self.documents = []
        for index, role in enumerate(('backend', 'frontend', 'proxy', 'sibling')):
            self.documents.append({
                'Id': str(index)*64, 'Image': 'sha256:'+str(index+1)*64,
                'Config': {'Image': 'ghcr.io/fixture/'+role+':'+'b'*40,
                           'Env': ['PATH=/usr/bin', *[k+'='+v for k, v in
                                   {**self.entries, 'SPRING_PROFILES_ACTIVE': 'production'}.items()]],
                           'Labels': {'com.docker.compose.project':
                                      'pawcycle-production' if role != 'sibling' else 'observability',
                                      'com.docker.compose.service': role,
                                      'com.docker.compose.project.config_files':
                                      str(h.base/'source/repo'/transition.COMPOSE)}},
                'State': {'StartedAt': 'fixture-start', 'Status': 'running', 'Health': {'Status': 'healthy'}},
                'RestartCount': 0, 'Mounts': [], 'NetworkSettings': {'Networks': {}}})
        self.runner.documents = self.documents
        now = int(time.time())
        packet.update({
            'schema': 1, 'main_sha': 'a'*40, 'release_sha': 'b'*40,
            'compose_sha256': hashlib.sha256((h.staging/transition.COMPOSE).read_bytes()).hexdigest(),
            'renewal_sha256': hashlib.sha256((h.staging/transition.RENEW).read_bytes()).hexdigest(),
            'installed_sha256': hashlib.sha256(h.installed.read_bytes()).hexdigest(),
            'nginx_sha256': hashlib.sha256((h.runtime/'nginx-https.conf').read_bytes()).hexdigest(),
            'not_before_unix': now-1, 'expires_unix': now+300,
            'command_timeout_seconds': 2, 'recovery_seconds': 30,
            'certificate_not_after_unix': now+200000, 'minimum_certificate_seconds': 86400,
            'quiescence_confirmed': True, 'timer': {'ActiveState': 'active', 'UnitFileState': 'enabled'},
            'changes': {p: list(signature(os.lstat(p))) for p in h.allowed_changes()},
            'env_identity': list(signature((h.runtime/'backend.env').stat())),
            'deploy_identity': list(signature(h.deploy.stat())),
            'renewal_identity': list(signature(h.renewal.stat())),
            'installed_identity': list(signature(h.installed.stat())),
            'snapshot': h.snapshot(), 'compose_version': 'fixture-version',
            'backend_image': 'ghcr.io/fixture/backend', 'frontend_image': 'ghcr.io/fixture/frontend',
            'exclusions': [], 'approval_reference': 'fixture-approval',
            'tool_hashes': {name: hashlib.sha256((here/name).read_bytes()).hexdigest()
                            for name in ('trust_transition.py', 'production_env.py')},
        })

    def write(self, path, data, mode):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        path.chmod(mode)

    def stopped(self):
        self.assertFalse((self.host.runtime/'current').exists())
        self.assertFalse((self.host.bundle/'.complete').exists())

    def test_real_supervisor_success_and_consumer_projections(self):
        old = self.host.renewal.stat().st_ino
        original = (self.host.runtime/'backend.env').read_bytes()
        self.host.apply()
        self.assertEqual(self.host.renewal.stat().st_ino, old)
        self.assertEqual(self.host.renewal.read_bytes(), b'renewal-sentinel')
        self.assertEqual((self.host.runtime/'backend.env').read_bytes(), original)
        self.assertEqual(self.runner.timer, 'active')
        self.assertEqual(len(parse((self.host.bundle/'auth.env').read_bytes(),
                                  keys=transition.projection(self.entries, 'auth').keys())), 6)
        self.assertEqual((self.host.runtime/'current').resolve(), self.host.bundle)
        self.assertEqual(json.loads(self.host.journal.read_bytes())['phase'], 'complete')
        self.assertEqual(self.host.fds, [])

    def test_umask077_preserves_installed_script0755_and_private_files0600(self):
        previous = os.umask(0o077)
        try:
            self.host.apply()
        finally:
            os.umask(previous)
        self.assertEqual(stat.S_IMODE(self.host.installed.stat().st_mode), 0o755)
        self.assertEqual(stat.S_IMODE((self.host.bundle/'backend.env').stat().st_mode), 0o600)

    def test_no_queued_renewal_job_allows_read_only_preflight(self):
        self.host.validate_packet()
        self.assertEqual(self.runner.timer, 'active')
        self.assertNotIn(['/usr/bin/systemctl', 'stop', transition.TIMER], self.runner.calls)
        self.assertFalse(self.host.journal.exists())
        self.stopped()

    def test_queued_renewal_job_stops_before_timer_mutation(self):
        self.runner.queued_before_start = True
        with self.assertRaisesRegex(Refused, '^RENEWAL_BUSY$'):
            self.host.apply()
        self.assertEqual(self.runner.timer, 'active')
        self.assertNotIn(['/usr/bin/systemctl', 'stop', transition.TIMER], self.runner.calls)
        self.assertFalse(self.host.journal.exists())
        self.stopped()

    def test_invalid_canonical_stops_before_timer(self):
        self.write(self.host.runtime/'backend.env', b'UNKNOWN=not-secret\n', 0o600)
        os.chown(self.host.runtime/'backend.env', 1000, 1000)
        self.host.p['env_identity'] = list(signature((self.host.runtime/'backend.env').stat()))
        with self.assertRaises(Refused):
            self.host.apply()
        self.assertEqual(self.runner.timer, 'active')
        self.assertFalse(self.host.journal.exists())
        self.stopped()

    def test_actual_effective_env_mismatch_stops_marker(self):
        self.documents[0]['Config']['Env'][-1] = 'SPRING_PROFILES_ACTIVE=unsafe-fixture'
        self.host.p['snapshot'] = self.host.snapshot()
        with self.assertRaisesRegex(Refused, '^INCIDENT$'):
            self.host.apply()
        self.stopped()
        self.assertEqual(self.runner.timer, 'inactive')

    def test_compose_override_rejected_even_when_running_agrees(self):
        self.runner.resolved = {**self.runner.environment(), AUTOMATION[0]: 'false'}
        with self.assertRaisesRegex(Refused, '^INCIDENT$'):
            self.host.apply()
        self.stopped()

    def test_private_subprocess_exception_redacted(self):
        self.runner.fail = 'compose-error'
        with self.assertRaisesRegex(Refused, '^INCIDENT$'):
            self.host.apply()
        self.assertNotIn('must-not-escape', self.host.journal.read_text())
        self.stopped()

    def test_source_timeout_releases_real_supervisor_locks(self):
        self.runner.fail = 'git-timeout'
        with self.assertRaisesRegex(Refused, '^INCIDENT$'):
            self.host.apply()
        self.assertEqual(self.host.fds, [])
        self.assertEqual(stat.S_IMODE(self.host.renewal.stat().st_mode), 0o644)
        self.stopped()

    def test_packet_expired_does_not_mutate(self):
        self.host.p['expires_unix'] = 0
        with self.assertRaises(Refused):
            self.host.apply()
        self.assertEqual(self.runner.timer, 'active')
        self.assertFalse(self.host.journal.exists())

    def test_timer_start_then_state_failure_stops_timer_and_records_handoff(self):
        original = self.host.unit_state
        count = 0
        def fail_after_start():
            nonlocal count
            count += 1
            if count > 1:
                raise RuntimeError('fixture-private-error')
            return original()
        with patch.object(self.host, 'unit_state', side_effect=fail_after_start):
            with self.assertRaisesRegex(Refused, '^INCIDENT$'):
                self.host.apply()
        self.assertEqual(self.runner.timer, 'inactive')
        record = json.loads(self.host.journal.read_bytes())
        self.assertEqual(record['phase'], 'incident')
        self.assertEqual(record['timer_handoff']['timer'], 'inactive')
        self.assertEqual(self.host.fds, [])
        self.assertTrue((self.host.runtime/'current').is_symlink())

    def test_timer_start_then_complete_journal_failure_stops_timer(self):
        original = self.host.journal_phase
        def fail_complete(phase, *args, **kwargs):
            if phase == 'complete':
                raise OSError('fixture journal fsync')
            return original(phase, *args, **kwargs)
        with patch.object(self.host, 'journal_phase', side_effect=fail_complete):
            with self.assertRaisesRegex(Refused, '^INCIDENT$'):
                self.host.apply()
        self.assertEqual(self.runner.timer, 'inactive')
        self.assertEqual(json.loads(self.host.journal.read_bytes())['phase'], 'incident')

    def fail_timer_completion(self):
        original = self.host.journal_phase
        def fail_complete(phase, *args, **kwargs):
            original(phase, *args, **kwargs)
            if phase == 'complete':
                raise OSError('fixture directory fsync after replace')
        with patch.object(self.host, 'journal_phase', side_effect=fail_complete):
            with self.assertRaisesRegex(Refused, '^INCIDENT$'):
                self.host.apply()
        return json.loads(self.host.journal.read_bytes())

    def test_timer_stop_failure_records_active_incident_without_success(self):
        self.runner.fail = 'stop-after-start'
        record = self.fail_timer_completion()
        self.assertEqual(record['phase'], 'incident')
        self.assertEqual(record['timer_handoff']['timer'], 'active')
        self.assertEqual(record['timer_handoff']['stop'], 'unknown')
        self.assertEqual(self.host.fds, [])
        self.assertTrue((self.host.runtime/'current').is_symlink())

    def test_immediate_renewal_observed_not_killed_or_rolled_back(self):
        self.runner.renewal_active = True
        record = self.fail_timer_completion()
        self.assertEqual(record['timer_handoff']['timer'], 'inactive')
        self.assertEqual(record['timer_handoff']['renewal'], 'possibly-running')
        self.assertEqual(record['timer_handoff']['queued'], 'yes')
        self.assertNotIn(['/usr/bin/systemctl', 'stop', transition.SERVICE], self.runner.calls)
        self.assertTrue((self.host.runtime/'current').is_symlink())
        self.assertEqual(self.host.renewal.read_bytes(), b'renewal-sentinel')

    def test_normal_timer_restore_with_immediate_renewal_preserves_contract(self):
        self.runner.renewal_active = True
        self.host.apply()
        self.assertEqual(self.runner.timer, 'active')
        self.assertFalse(self.host.timer_stopped)
        self.assertEqual(json.loads(self.host.journal.read_bytes())['phase'], 'complete')
        self.assertEqual(self.host.fds, [])
        self.assertNotIn(['/usr/bin/systemctl', 'stop', transition.SERVICE], self.runner.calls)

    def test_unknown_timer_observation_never_fabricates_inactive(self):
        self.runner.fail = 'timer-observation'
        with self.assertRaisesRegex(Refused, '^INCIDENT$'):
            self.host.apply()
        record = json.loads(self.host.journal.read_bytes())
        self.assertEqual(record['timer_handoff']['timer'], 'unknown')
        self.assertEqual(record['timer_handoff']['stop'], 'acknowledged')

    def test_lost_start_acknowledgement_is_contained(self):
        self.runner.fail = 'start-ack-lost'
        with self.assertRaisesRegex(Refused, '^INCIDENT$'):
            self.host.apply()
        self.assertEqual(self.runner.timer, 'inactive')
        self.assertEqual(json.loads(self.host.journal.read_bytes())['phase'], 'incident')

    def test_supervisor_escaped_child_pipe_has_finite_incident_boundary(self):
        pidfile = self.root/'escaped-child.pid'
        child_code = ('import os,sys,time;'
                      'open(sys.argv[1],"w").write(str(os.getpid()));time.sleep(30)')
        parent_code = ('import subprocess,sys;subprocess.Popen([sys.executable,"-c",'
                       +repr(child_code)+',sys.argv[1]],start_new_session=True)')
        def escaped():
            transition.Runner(time.monotonic()+5, 0.1)(
                [sys.executable, '-c', parent_code, str(pidfile)])
        started = time.monotonic()
        try:
            with patch.object(self.host, 'prepare_source', side_effect=escaped):
                with self.assertRaisesRegex(Refused, '^INCIDENT$'):
                    self.host.apply()
            self.assertLess(time.monotonic()-started, 6)
            self.assertEqual(self.host.fds, [])
            self.assertEqual(json.loads(self.host.journal.read_bytes())['code'], 'PROCESS_UNCERTAIN')
            self.assertEqual(self.runner.timer, 'inactive')
            self.stopped()
        finally:
            if pidfile.exists():
                with __import__('contextlib').suppress(ProcessLookupError):
                    os.kill(int(pidfile.read_text()), signal.SIGKILL)

    def test_runner_never_uses_unbounded_communicate_after_timeout(self):
        from unittest.mock import Mock
        proc = Mock(pid=123456789)
        proc.stdout, proc.stderr = io.BytesIO(), io.BytesIO()
        proc.poll.return_value = 0
        def blocked(timeout=None):
            self.assertIsNotNone(timeout, 'unbounded pipe wait')
            raise subprocess.TimeoutExpired('fixture', timeout)
        proc.communicate.side_effect = blocked
        with patch.object(transition.subprocess, 'Popen', return_value=proc):
            with patch.object(transition.os, 'killpg', side_effect=ProcessLookupError):
                with self.assertRaisesRegex(Refused, '^PROCESS_UNCERTAIN$'):
                    transition.Runner(time.monotonic()+5, 0.05)(['fixture'])
        self.assertTrue(proc.stdout.closed and proc.stderr.closed)

    def test_unit_drift_prevents_timer_stop(self):
        self.write(self.host.path('/etc/systemd/system/'+transition.SERVICE),
                   b'fixture unsupported unit', 0o644)
        with self.assertRaisesRegex(Refused, '^UNIT_BYTES$'):
            self.host.apply()
        self.assertEqual(self.runner.timer, 'active')
        self.assertFalse(self.host.journal.exists())

    def test_real_supervisor_lock_contention(self):
        fd = os.open(self.host.renewal, os.O_RDWR)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaisesRegex(Refused, '^INCIDENT$'):
                self.host.apply()
        finally:
            os.close(fd)
        self.assertEqual(self.host.fds, [])
        self.stopped()

    def test_real_supervisor_inode_drift(self):
        original = self.host.validate_source
        def drift():
            original()
            self.host.renewal.rename(self.host.renewal.with_suffix('.old'))
            self.write(self.host.renewal, b'replacement', 0o644)
        with patch.object(self.host, 'validate_source', side_effect=drift):
            with self.assertRaisesRegex(Refused, '^INCIDENT$'):
                self.host.apply()
        self.stopped()

    def test_real_supervisor_interruption_cleanup(self):
        def interrupted():
            raise Refused('INTERRUPTED')
        with patch.object(self.host, 'make_bundle', side_effect=interrupted):
            with self.assertRaisesRegex(Refused, '^INCIDENT$'):
                self.host.apply()
        self.assertEqual(self.host.fds, [])
        self.stopped()

    def test_marker_crash_window_retains_incident_and_disconnects_known_current(self):
        real = self.host.postflight
        def partial():
            real()
            raise OSError('fixture fsync uncertainty')
        with patch.object(self.host, 'postflight', side_effect=partial):
            with self.assertRaisesRegex(Refused, '^INCIDENT$'):
                self.host.apply()
        self.assertFalse((self.host.runtime/'current').exists())
        self.assertTrue((self.host.bundle/'.complete').exists())
        self.assertEqual(json.loads(self.host.journal.read_bytes())['phase'], 'incident')
        with self.assertRaises(Refused):
            self.host.apply()  # No resume/retry through a durable incident.

    def test_symlink_and_acl_contract(self):
        self.host.runtime.rename(self.host.runtime.with_name('runtime-old'))
        self.host.runtime.symlink_to(self.host.runtime.with_name('runtime-old'))
        with self.assertRaises(Refused):
            self.host.validate_packet()

    def test_acl_rejected_before_mutation(self):
        with patch.object(transition.os, 'getxattr', return_value=b'fixture ACL'):
            with self.assertRaisesRegex(Refused, '^PATH_ACL$'):
                self.host.validate_packet()
        self.assertEqual(self.runner.timer, 'active')

    def test_real_fsync_partial_failure_is_incident(self):
        old = transition.os.fsync
        def fail_on_renewal(fd):
            if os.fstat(fd).st_ino == self.host.renewal.stat().st_ino:
                raise OSError('fixture EIO')
            return old(fd)
        with patch.object(transition.os, 'fsync', side_effect=fail_on_renewal):
            with self.assertRaisesRegex(Refused, '^INCIDENT$'):
                self.host.apply()
        self.assertEqual(stat.S_IMODE(self.host.renewal.stat().st_mode), 0o600)
        self.assertEqual(self.host.renewal.read_bytes(), b'renewal-sentinel')
        self.assertEqual(self.host.fds, [])
        self.assertEqual(self.runner.timer, 'inactive')
        self.stopped()

    def test_runner_timeout_reaps_process_group(self):
        runner = transition.Runner(time.monotonic()+5, 0.05)
        with self.assertRaisesRegex(Refused, '^COMMAND_TIMEOUT$'):
            runner([sys.executable, '-c', 'import time; time.sleep(30)'])

    def test_runner_rejects_child_left_by_successful_parent(self):
        runner = transition.Runner(time.monotonic()+5, 0.2)
        code = ("import subprocess; subprocess.Popen(['sleep','30'],"
                "stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)")
        with self.assertRaisesRegex(Refused, '^CHILD_REMAINS$'):
            runner([sys.executable, '-c', code])

    def exercise_signal(self, sig):
        pidfile = self.root/'child.pid'
        source = Path(__file__).parent
        code = '''
import sys,signal,time
from pathlib import Path
sys.path.insert(0,sys.argv[1])
from trust_transition import Runner,Refused
def cancel(*_): raise Refused('INTERRUPTED')
signal.signal(signal.SIGTERM,cancel)
signal.signal(signal.SIGINT,cancel)
try:
    Runner(time.monotonic()+10,5)([sys.executable,'-c',
        'import os,sys,time;open(sys.argv[1],"w").write(str(os.getpid()));time.sleep(30)',sys.argv[2]])
except Refused:
    print('FAIL:INTERRUPTED')
    sys.exit(3)
'''
        with subprocess.Popen([sys.executable, '-c', code, str(source), str(pidfile)],
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE) as child:
            try:
                deadline = time.monotonic()+5
                while not pidfile.exists() and time.monotonic() < deadline:
                    time.sleep(0.01)
                self.assertTrue(pidfile.exists())
                child.send_signal(sig)
                out, err = child.communicate(timeout=5)
                self.assertEqual(child.returncode, 3)
                self.assertEqual(out, b'FAIL:INTERRUPTED\n')
                self.assertEqual(err, b'')
                with self.assertRaises(ProcessLookupError):
                    os.kill(int(pidfile.read_text()), 0)
            finally:
                if child.poll() is None:
                    child.kill()
                    child.wait()

    def test_real_sigterm_reaps_child_without_raw_output(self):
        self.exercise_signal(signal.SIGTERM)

    def test_real_sigint_reaps_child_without_raw_output(self):
        self.exercise_signal(signal.SIGINT)

    def test_open_writer_detected_without_reading_command_or_env(self):
        original = Path.iterdir
        def current_process(path):
            return iter([Path('/proc')/str(os.getpid())]) if path == Path('/proc') else original(path)
        with (self.host.runtime/'backend.env').open('ab'):
            with patch.object(Path, 'iterdir', current_process):
                with self.assertRaisesRegex(Refused, '^ENV_WRITER$'):
                    transition.Transition.no_writer(self.host)

    def test_source_alternate_rejected(self):
        alternate = self.host.staging/'.git/objects/info/alternates'
        self.write(alternate, b'/untrusted-fixture-path\n', 0o600)
        with self.assertRaisesRegex(Refused, '^INCIDENT$'):
            self.host.apply()
        self.stopped()

    def test_parser_quote_escape_duplicate_unknown_missing(self):
        good = serialize(self.entries)
        self.assertEqual(parse(good), self.entries)
        for bad in (good+good.splitlines(keepends=True)[0], good+b"UNKNOWN='x'\n",
                    good.replace(b"\\'", b"\\n"), good.replace(b"'true'", b'true'),
                    b'\xef\xbb\xbf'+good, good.replace(b'\n', b'\r\n'),
                    b'\n'.join(good.splitlines()[:-1])):
            with self.subTest(case=bad[:0]):
                with self.assertRaises(Refused):
                    parse(bad)

    def test_database_projection_never_infers_or_enables_automation(self):
        for consumer in ('auth', 'release', 'catalog'):
            p = projection(self.entries, consumer)
            self.assertEqual(p[AUTOMATION[0]], 'true')
            self.assertEqual(p['SPRING_DATASOURCE_PASSWORD'], "fixture'password")
        bad = dict(self.entries, PAWCYCLE_DATASOURCE_PORT='3307')
        with self.assertRaises(Refused):
            parse(serialize(bad))

    def test_existing_catalog_and_auth_validators_consume_real_projections(self):
        # Execute only pure existing validator/stream functions, never entrypoints.
        for consumer, filename, first, validate in (
            ('catalog', 'import-demo-catalog.sh', 'decode_backend_env_value()', 'validate_backend_env'),
            ('auth', 'create-production-auth-smoke-member.sh', 'validate_backend_env_contract()',
             'validate_backend_env_contract')):
            path = self.root/(consumer+'.env')
            path.write_bytes(serialize(projection(self.entries, consumer)))
            script = Path(__file__).with_name(filename).read_text()
            functions = script[script.index(first):script.index('resolve_database_egress_network()')]
            commands = ('set -Eeuo pipefail\ndie() { exit 2; }\n'
                        'IDENTITY_MODE=release-state\nBACKEND_ENV_FILE="$1"\n'+functions+
                        '\n'+validate+'\nstream_backend_env\n')
            r = subprocess.run(['bash', '-s', str(path)], input=commands.encode(), capture_output=True)
            self.assertEqual(r.returncode, 0, 'pure consumer validator rejected projection')
            values = dict(line.split('=', 1) for line in r.stdout.decode().splitlines())
            self.assertEqual(values[AUTOMATION[0]], 'false')
            self.assertNotIn(AUTOMATION[1], values)
            self.assertNotIn(AUTOMATION[2], values)
            self.assertEqual(values['SPRING_DATASOURCE_PASSWORD'], self.entries['SPRING_DATASOURCE_PASSWORD'])

    def test_release_initial_validates_canonical_and_release_projection(self):
        self.host.apply()
        for name, data in [('https-domain', b'pawcycle.duckdns.org\n'), ('https-enabled', b'enabled\n')]:
            self.write(self.host.state/name, data, 0o600)
        spec = importlib.util.spec_from_file_location('release_projection_test',
                                                    Path(__file__).with_name('oci-release.py'))
        release = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(release)
        host = release.Host(self.host.source, self.host.state, self.host.runtime, self.host.renewal)
        def git(*args):
            if args[:2] == ('remote', 'get-url'):
                return transition.ORIGIN.encode()
            if args[:1] == ('ls-remote',):
                return ('a'*40+'\trefs/heads/main\n').encode()
            return ('a'*40).encode()
        with patch.object(host, 'git', side_effect=git):
            host.initial()
            self.assertEqual(host.automation[AUTOMATION[0]], 'true')
            self.write(self.host.bundle/'release.env', b'bad-projection', 0o600)
            with self.assertRaises(release.Refused):
                host.initial()

    def test_cli_bad_arguments_redact_input(self):
        out, err = io.StringIO(), io.StringIO()
        with patch.object(sys, 'argv', ['tool', 'unexpected-fixture-secret']):
            with __import__('contextlib').redirect_stdout(out), __import__('contextlib').redirect_stderr(err):
                self.assertEqual(transition.main(), 2)
        self.assertEqual(out.getvalue(), 'FAIL:CLI\n')
        self.assertEqual(err.getvalue(), '')

    def test_duplicate_packet_keys_rejected(self):
        path = self.host.control/'approval.json'
        self.write(path, b'{"schema":1,"schema":2}', 0o600)
        with self.assertRaisesRegex(Refused, '^PACKET_SCHEMA$'):
            transition.load_packet(path)

    def test_effective_env_duplicate_and_unknown_exclusion(self):
        with self.assertRaises(Refused):
            verify_effective(self.entries, self.entries, [], ['KEY=x', 'KEY=y'])
        with self.assertRaises(Refused):
            verify_effective(self.entries, self.entries, [], [], ['SPRING_DATASOURCE_PASSWORD'])

    def test_stable_fd_rejects_path_replacement(self):
        path = self.host.runtime/'backend.env'
        fd = os.open(path, os.O_RDONLY)
        try:
            path.rename(path.with_suffix('.old'))
            self.write(path, serialize(self.entries), 0o600)
            with self.assertRaises(Refused):
                stable_read(fd, path, uid=1000)
        finally:
            os.close(fd)

    def test_real_compose_config_parser_without_daemon(self):
        import shutil
        binary = shutil.which('docker')
        if not binary:
            self.skipTest('Docker Compose CLI unavailable; not a parser PASS')
        env_path = self.root/'compose.env'
        env_path.write_bytes(serialize(self.entries))
        env = {'PATH': os.environ.get('PATH', '/usr/bin:/bin'),
               'RELEASE_SHA': 'b'*40, 'BACKEND_IMAGE': 'ghcr.io/fixture/backend',
               'FRONTEND_IMAGE': 'ghcr.io/fixture/frontend',
               'PAWCYCLE_BACKEND_ENV_FILE': str(env_path),
               **{k: self.entries[k] for k in AUTOMATION}}
        result = subprocess.run([binary, 'compose', '--env-file', '/dev/null', '--file',
                                 str(Path(__file__).with_name('compose.yaml')), 'config',
                                 '--format', 'json'], env=env, capture_output=True, timeout=30)
        self.assertEqual(result.returncode, 0, 'synthetic Compose config failed (output redacted)')
        resolved = json.loads(result.stdout)['services']['backend']['environment']
        verify_effective(self.entries, resolved, [], [k+'='+v for k,v in resolved.items()])


if __name__ == "__main__":
    if os.geteuid() != 0:
        raise SystemExit("STOP: disposable Linux root environment required")
    unittest.main()

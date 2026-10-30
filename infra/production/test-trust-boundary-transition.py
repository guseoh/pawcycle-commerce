#!/usr/bin/env python3
"""Disposable-root rehearsal ONLY; no host paths, Docker, SSH, or apply CLI.

The models below test the Runbook's failure contracts, not a production
supervisor, dotenv implementation, or live Compose/effective-env verification.
"""
import fcntl
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


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


if __name__ == "__main__":
    if os.geteuid() != 0:
        raise SystemExit("STOP: disposable Linux root environment required")
    unittest.main()

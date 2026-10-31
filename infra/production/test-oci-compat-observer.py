#!/usr/bin/env python3
"""Offline observer contract tests; no production DB or Docker access."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('compat', HERE / 'oci-compat-observer.py')
compat = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compat)


class ObserverTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / 'runtime' / '.bundle.test'
        self.bundle.mkdir(parents=True)
        (self.root / 'runtime' / 'current').symlink_to(self.bundle)
        (self.bundle / 'backend.env').write_bytes(b'non-secret-fixture\n')
        (self.bundle / '.complete').write_bytes(b'g0-t2a-canonical-v1\n')
        self.current, self.target = 'a'*40, 'b'*40
        self.now = int(time.time())
        self.names = ['V1__first.sql', 'V2__second.sql']
        self.snapshot = compat.digest(b'fake MySQL identity and flyway metadata')
        self.changed = ['frontend/src/app/globals.css']
        self.artifact_path = self.root / 'compat-evidence' / 'pair.json'
        self.artifact_path.parent.mkdir()
        self.report = {'format': 1, 'current': self.current, 'target': self.target,
                       'isolation': 'disposable-mysql-8.4', 'forward_pass': True,
                       'reverse_pass': True, 'reviewed': True,
                       'checks': {name: {'result': 'pass', 'reason': 'validated in an isolated pair',
                                         'test_reference': 'isolation/test-case'}
                                  for name in compat.REQUIRED}}
        self.write_artifact()
        self.attest = {'format': 1, 'current': self.current, 'target': self.target,
                       'runtime_env_sha256': compat.digest(b'non-secret-fixture\n'),
                       'db_epoch': self.snapshot, 'db_snapshot_sha256': self.snapshot,
                       'migration_tree': 'c'*40,
                       'changed_paths_sha256': compat.digest(('\n'.join(self.changed)+'\n').encode()),
                       'verified_unix': self.now, 'expires_unix': self.now + 300,
                       'reviewed_by_operator': True, 'evidence': 'approved/manual-pair-check',
                       'semantic_artifact': {'path': str(self.artifact_path),
                                             'sha256': compat.digest(self.artifact_path.read_bytes())}}
        self.proof = self.root / 'proof.json'
        self.save_proof()
        self.patches = [
            patch.object(compat, 'ROOT', self.root),
            patch.object(compat, 'MIRROR', self.root/'mirror'),
            patch.object(compat, 'MYSQL_OPTIONS', self.root/'credentials.cnf'),
            patch.object(compat, 'PROOF', self.proof),
            patch.object(compat, 'ARTIFACT_DIR', self.artifact_path.parent),
            patch.object(compat, 'trusted', side_effect=lambda p, **kw: Path(p)),
            patch.object(compat.os, 'geteuid', return_value=0),
            patch.object(compat, 'git', side_effect=self.fake_git),
            patch.object(compat, 'run', side_effect=self.fake_run),
            patch.object(compat, 'live_snapshot', return_value=(self.snapshot, set(self.names), 2)),
        ]
        for p in self.patches:
            p.start()
            self.addCleanup(p.stop)

    def save_proof(self):
        self.proof.write_text(json.dumps(self.attest))

    def write_artifact(self):
        self.artifact_path.write_text(json.dumps(self.report))

    def fake_git(self, *parts):
        if parts[0] == 'merge-base': return ''
        if parts[0] == 'rev-parse': return 'c'*40
        if parts[0] == 'ls-tree': return '\n'.join(self.names)
        raise AssertionError('unexpected git command')

    def fake_run(self, args, **kw):
        if 'diff' in args:
            return b'\0'.join(p.encode() for p in self.changed) + b'\0'
        raise AssertionError('unexpected command')

    def test_approved_bidirectional_pair(self):
        proof = compat.observe(self.current, self.target, self.now)
        self.assertTrue(proof['application_data_compatible'])
        self.assertTrue(proof['rollback_safe'])
        self.assertEqual(self.snapshot, proof['db_epoch'])
        self.assertEqual(self.now+90, proof['expires_unix'])

    def test_unapproved_or_stale_proof_fails_closed(self):
        for item, value in [('reviewed_by_operator', False), ('expires_unix', self.now-1),
                            ('db_epoch', 'sha256:'+'0'*64), ('current', 'z'*40)]:
            with self.subTest(item=item):
                original = self.attest[item]
                self.attest[item] = value
                self.save_proof()
                with self.assertRaises(compat.Rejected):
                    compat.observe(self.current, self.target, self.now)
                self.attest[item] = original
                self.save_proof()

    def test_artifact_hash_or_direction_missing_is_blocked(self):
        self.report['reverse_pass'] = False
        self.write_artifact()
        with self.assertRaises(compat.Rejected):
            compat.observe(self.current, self.target, self.now)
        self.attest['semantic_artifact']['sha256'] = compat.digest(self.artifact_path.read_bytes())
        self.save_proof()
        with self.assertRaises(compat.Rejected):
            compat.observe(self.current, self.target, self.now)

    def test_backend_path_or_migration_drift_blocks(self):
        self.changed.append('backend/src/main/java/Changed.java')
        self.attest['changed_paths_sha256'] = compat.digest(('\n'.join(sorted(self.changed))+'\n').encode())
        self.save_proof()
        with self.assertRaises(compat.Rejected):
            compat.observe(self.current, self.target, self.now)
        self.changed.pop()
        self.attest['changed_paths_sha256'] = compat.digest(('\n'.join(self.changed)+'\n').encode())
        self.save_proof()
        with patch.object(compat, 'live_snapshot', return_value=(self.snapshot, {'V1__first.sql'}, 1)):
            with self.assertRaises(compat.Rejected):
                compat.observe(self.current, self.target, self.now)

    def test_live_db_drift_during_pair_check_is_blocked(self):
        with patch.object(compat, 'live_snapshot', side_effect=[
            (self.snapshot, set(self.names), 2),
            (compat.digest(b'changed flyway state'), set(self.names), 2),
        ]):
            with self.assertRaises(compat.Rejected):
                compat.observe(self.current, self.target, self.now)

    def test_frontend_only_scope_blocks_unreviewed_image_inputs(self):
        self.assertTrue(compat.frontend_only_scope([
            'frontend/src/app/page.tsx', 'frontend/public/logo.svg',
            'docs/runbook/OPS-OCI-CD-003-host-pull.md',
        ]))
        denied = [
            'backend/src/main/java/Changed.java',
            'backend/src/test/java/ChangedTest.java',
            'backend/gradle.properties',
            'frontend/package.json',
            'frontend/package-lock.json',
            'frontend/next.config.ts',
            'frontend/scripts/build-helper.js',
            '.dockerignore',
            '.github/workflows/publish-production-images.yml',
            'infra/production/backend.Dockerfile',
            'infra/production/frontend.Dockerfile.dockerignore',
            'infra/production/compose.yaml',
            'infra/production/nginx.https.conf',
        ]
        for path in denied:
            with self.subTest(path=path):
                self.changed.append(path)
                self.attest['changed_paths_sha256'] = compat.digest(
                    ('\n'.join(sorted(self.changed)) + '\n').encode())
                self.save_proof()
                with self.assertRaisesRegex(compat.Rejected, 'separate observer'):
                    compat.observe(self.current, self.target, self.now)
                self.changed.pop()

    def test_mysql_command_has_no_secret_arguments(self):
        with patch.object(compat, 'run', return_value=b'v1\n') as executed:
            self.assertEqual(['v1'], compat.mysql('SELECT 1'))
            argv = executed.call_args[0][0]
            self.assertEqual('/usr/bin/mysql', argv[0])
            self.assertTrue(argv[1].startswith('--defaults-file='))
            self.assertEqual('--execute=SELECT 1', argv[-1])
            self.assertNotIn('password', ' '.join(argv).lower())


if __name__ == '__main__':
    unittest.main()

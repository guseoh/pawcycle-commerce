#!/usr/bin/env python3
"""Offline observer contract tests; no production DB or Docker access."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
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

    def test_frontend_only_scope_rejects_all_other_paths(self):
        self.assertTrue(compat.frontend_only_scope([
            'frontend/src/app/page.tsx', 'frontend/public/logo.svg',
        ]))
        self.assertFalse(compat.frontend_only_scope([]))
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
            '.github/workflows/validate-conventions.yml',
            '.github/workflows/request-coderabbit-review.yml',
            'infra/production/backend.Dockerfile',
            'infra/production/frontend.Dockerfile.dockerignore',
            'infra/production/compose.yaml',
            'infra/production/nginx.https.conf',
            'infra/production/oci-cd.py',
            'scripts/classify-production-image-changes.py',
            'docs/runbook/OPS-OCI-CD-003-host-pull.md',
            'qa/fixture.json',
            'unknown/new-file.txt',
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

    def test_mysql_requires_tcp_and_verified_tls_identity(self):
        with patch.object(compat, 'run', return_value=b'v1\n') as executed:
            self.assertEqual(['v1'], compat.mysql('SELECT 1'))
            argv = executed.call_args[0][0]
            self.assertEqual('/usr/bin/mysql', argv[0])
            self.assertTrue(argv[1].startswith('--defaults-file='))
            self.assertIn('--protocol=TCP', argv)
            self.assertIn('--ssl-mode=VERIFY_IDENTITY', argv)
            self.assertEqual('--execute=SELECT 1', argv[-1])
            self.assertLess(argv.index('--ssl-mode=VERIFY_IDENTITY'),
                            len(argv) - 1)
            self.assertNotIn('password', ' '.join(argv).lower())

    def test_mysql_tls_verification_failure_fails_closed(self):
        with patch.object(compat, 'run',
                          side_effect=compat.Rejected('observer dependency failed or oversized')):
            with self.assertRaisesRegex(compat.Rejected, 'dependency failed'):
                compat.mysql('SELECT 1')


class AutomaticObserverTests(unittest.TestCase):
    # Reuse only the disposable runtime/DB fixture, not manual-pair test cases.
    write_artifact = ObserverTests.write_artifact
    save_proof = ObserverTests.save_proof
    fake_git = ObserverTests.fake_git
    fake_run = ObserverTests.fake_run

    def setUp(self):
        ObserverTests.setUp(self)
        self.source = self.root / 'source'
        self.source.mkdir()
        self.git_cmd(self.source, 'init', '-q')
        self.git_cmd(self.source, 'config', 'user.email', 'isolated@example.invalid')
        self.git_cmd(self.source, 'config', 'user.name', 'isolated')
        self.css = b'.customer-theme { --accent:#f3b88f; --accent-soft:#fbede3; color:var(--accent); }\n'
        for name in self.names:
            self.write('backend/src/main/resources/db/migration/' + name, b'SELECT 1;\n')
        self.write(compat.CSS_PATH, self.css)
        self.write('frontend/src/app/page.tsx', b'unchanged business logic\n')
        self.baseline = self.commit()
        self.mirror = self.root / 'mirror'
        self.git_cmd(self.root, 'clone', '--bare', '-q', str(self.source), str(self.mirror))
        self.git_cmd(self.mirror, 'remote', 'set-url', 'origin', 'https://github.com/guseoh/pawcycle-commerce.git')
        self.current = self.baseline
        self.write(compat.CSS_PATH, self.css.replace(b'#f3b88f', b'#f2b88f'))
        self.target = self.commit()
        self.policy = {'format': 1, 'enabled': True, 'target': 'oci-app01',
                       'repository': 'https://github.com/guseoh/pawcycle-commerce.git',
                       'approval_evidence': 'isolated/initial-scope', 'expires_unix': self.now + 300,
                       'compatibility_mode': compat.AUTO_MODE, 'automatic_baseline_sha': self.baseline,
                       'allowed_prefixes': [compat.CSS_PATH], 'allowed_risks': ['application'],
                       'runtime_env_sha256': self.attest['runtime_env_sha256'], 'db_epoch': self.snapshot,
                       'migration_tree': self.git_cmd(self.mirror, 'rev-parse', self.baseline + ':backend/src/main/resources/db/migration')}
        self.auto_path = self.root / 'auto-policy.json'
        self.save_auto()
        for p in (patch.object(compat, 'git', side_effect=lambda *args: self.git_cmd(self.mirror, *args)),
                  patch.object(compat, 'run', side_effect=self.real_git_run)):
            p.start()
            self.addCleanup(p.stop)
        # Automatic mode must succeed with no manual proof or pair artifact.
        self.proof.unlink()
        self.artifact_path.unlink()

    def git_cmd(self, path, *args):
        return subprocess.check_output(['git', '-C', str(path), *args], stderr=subprocess.DEVNULL).decode().strip()

    def real_git_run(self, argv, **kwargs):
        self.assertEqual('/usr/bin/git', argv[0])
        result = subprocess.run(argv, capture_output=True, timeout=10)
        compat.check(result.returncode == 0 and len(result.stdout) <= 131072, 'isolated Git refused')
        return result.stdout

    def write(self, path, data):
        target = self.source / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)

    def commit(self):
        self.git_cmd(self.source, 'add', '.')
        self.git_cmd(self.source, 'commit', '-qm', 'isolated')
        sha = self.git_cmd(self.source, 'rev-parse', 'HEAD')
        if hasattr(self, 'mirror'):
            self.git_cmd(self.mirror, 'fetch', '-q', str(self.source), 'HEAD')
            self.git_cmd(self.mirror, 'update-ref', 'refs/remotes/origin/main', sha)
        return sha

    def save_auto(self):
        self.auto_path.write_text(json.dumps(self.policy))

    def observe(self):
        return compat.observe(self.current, self.target, self.now, auto_policy=self.auto_path)

    def test_automatic_color_proof_without_manual_artifacts_and_with_docs_tail(self):
        self.write('docs/note.md', b'documentation only\n')
        tip = self.commit()
        proof = self.observe()
        self.assertEqual(tip, proof['observations']['main'])
        self.assertEqual(self.snapshot, proof['db_epoch'])
        self.assertEqual(compat.AUTO_MODE, proof['observations']['mode'])
        self.assertTrue(proof['rollback_safe'])
        self.assertTrue(proof['application_data_compatible'])

    def test_css_injection_layout_and_cumulative_budget_block(self):
        for data in (self.css + b'button { display:none }',
                     self.css.replace(b'#f3b88f', b'url(https://example.invalid)'),
                     self.css.replace(b'--accent:', b'--other:'),
                     self.css.replace(b'#f3b88f', b'#00b88f'),
                     self.css + b'/* extra */'):
            with self.subTest(data=data):
                with self.assertRaises(compat.Rejected):
                    compat.cosmetic_css(self.css, data)
        # A series of individually small changes cannot move the baseline.
        self.write(compat.CSS_PATH, self.css.replace(b'#f3b88f', b'#e3b88f'))
        self.target = self.commit()
        self.observe()
        self.current = self.target
        self.write(compat.CSS_PATH, self.css.replace(b'#f3b88f', b'#e2b88f'))
        self.target = self.commit()
        with self.assertRaisesRegex(compat.Rejected, 'budget'):
            self.observe()

    def test_unsafe_intermediate_reverted_change_and_non_deploy_tail_block(self):
        self.write('backend/build.gradle', b'unsafe intermediate\n')
        self.commit()
        (self.source / 'backend/build.gradle').unlink()
        self.commit()
        self.write(compat.CSS_PATH, self.css.replace(b'#f3b88f', b'#f1b88f'))
        self.target = self.commit()
        with self.assertRaisesRegex(compat.Rejected, 'baseline alignment'):
            self.observe()

    def test_every_non_cosmetic_area_blocks_even_when_classifier_ignores_it(self):
        paths = ['frontend/src/app/page.tsx', 'frontend/public/logo.svg', 'frontend/package.json',
                 'backend/src/main/java/Changed.java', 'backend/src/test/Changed.java',
                 'backend/src/main/resources/db/migration/V3__new.sql', '.github/workflows/ci.yml',
                 'infra/production/oci-release.py', 'scripts/classify-production-image-changes.py', 'unknown.txt']
        for path in paths:
            with self.subTest(path=path):
                case = AutomaticObserverTests()
                try:
                    case.setUp()
                    case.write(path, b'unsafe\n')
                    case.commit()
                    with self.assertRaises(compat.Rejected):
                        case.observe()
                finally:
                    case.doCleanups()

    def test_policy_db_runtime_and_history_identity_block(self):
        for field, value in [('enabled', False), ('expires_unix', self.now),
                             ('runtime_env_sha256', compat.digest(b'drift')),
                             ('db_epoch', compat.digest(b'drift')), ('migration_tree', 'f'*40),
                             ('automatic_baseline_sha', 'f'*40), ('allowed_prefixes', ['frontend/'])]:
            with self.subTest(field=field):
                old = self.policy[field]
                self.policy[field] = value
                self.save_auto()
                with self.assertRaises(compat.Rejected):
                    self.observe()
                self.policy[field] = old
                self.save_auto()
        self.current, self.target = self.target, self.current
        with self.assertRaises(compat.Rejected):
            self.observe()

    def test_superseded_target_is_not_deployed_in_reverse(self):
        self.write(compat.CSS_PATH, self.css.replace(b'#f3b88f', b'#f1b88f'))
        self.commit()
        with self.assertRaisesRegex(compat.Rejected, 'superseded'):
            self.observe()


if __name__ == '__main__':
    unittest.main()

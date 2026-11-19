"""Generator safety guard and determinism contract; no Docker needed."""
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('perfgen', Path(__file__).with_name('perfgen.py'))
perfgen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(perfgen)


class Completed:
    def __init__(self, stdout=b'', returncode=0):
        self.stdout, self.returncode, self.stderr = stdout, returncode, b''


def inspect(label='perf-synthetic-data', mount=None, ports=None):
    mount = mount or {'Type': 'volume', 'Name': 'a' * 64, 'Destination': '/var/lib/mysql'}
    return Completed(json.dumps([{'Config': {'Labels': {'pawcycle.purpose': label} if label else {}},
                                  'Mounts': [mount], 'HostConfig': {'PortBindings': ports or {}, 'Tmpfs': None}}]).encode())


class PerfgenTests(unittest.TestCase):
    def test_names_outside_perfgen_namespace_are_refused_before_docker(self):
        for name in ['pawcycle-local-integration-mysql-1', 'mysql', 'pawcycle-perfgen-', 'pawcycle-perfgen-UPPER']:
            with self.subTest(name=name), patch.object(perfgen, 'run') as run:
                with self.assertRaises(perfgen.Stop):
                    perfgen.guard(name)
                run.assert_not_called()

    def test_label_named_volume_and_published_port_are_refused(self):
        cases = [inspect(label=None),
                 inspect(mount={'Type': 'volume', 'Name': 'pawcycle-local-integration-mysql-data',
                                'Destination': '/var/lib/mysql'}),
                 inspect(mount={'Type': 'bind', 'Source': '/data', 'Destination': '/var/lib/mysql'}),
                 inspect(ports={'3306/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '3306'}]})]
        for case in cases:
            with self.subTest(case=case.stdout[:80]), patch.object(perfgen, 'run', return_value=case):
                with self.assertRaises(perfgen.Stop):
                    perfgen.guard('pawcycle-perfgen-test')
        with patch.object(perfgen, 'run', return_value=inspect()):
            perfgen.guard('pawcycle-perfgen-test')

    def test_scale_ratios_are_fixed(self):
        for scale, n in perfgen.SCALES.items():
            counts = perfgen.counts_for(n)
            with self.subTest(scale=scale):
                self.assertEqual(counts['members'], n)
                self.assertEqual(counts['orders'], n)
                self.assertEqual(counts['products'], n // 10)
                self.assertEqual(counts['skus'], n // 5)
                self.assertEqual(counts['subscriptions'], n // 2)
                self.assertEqual(counts['subscription_plans'], n // 1000)

    def test_chunks_cover_every_key_once(self):
        for total in [1, perfgen.CHUNK, perfgen.CHUNK + 1, 1_000_000]:
            ranges = list(perfgen.chunks(total))
            self.assertEqual(ranges[0][0], 1)
            self.assertEqual(ranges[-1][1], total)
            self.assertTrue(all(b + 1 == c for (_, b), (c, _) in zip(ranges, ranges[1:])))

    def test_generation_sql_is_a_pure_function_of_inputs(self):
        first = list(perfgen.steps(42, 10_000, '2026-11-01 00:00:00', '2030-01-01'))
        again = list(perfgen.steps(42, 10_000, '2026-11-01 00:00:00', '2030-01-01'))
        other = list(perfgen.steps(43, 10_000, '2026-11-01 00:00:00', '2030-01-01'))
        self.assertEqual(first, again)
        self.assertNotEqual(first, other)
        text = '\n'.join(sql for _, sql in first)
        self.assertNotRegex(text, r'(?i)\b(RAND|UUID|NOW|CURRENT_TIMESTAMP|SYSDATE)\s*\(')
        self.assertNotIn('{s}', text)
        self.assertNotIn('PAYMENT_PENDING', text)

    def test_no_migration_or_ddl_on_application_tables(self):
        text = '\n'.join(sql for _, sql in perfgen.steps(42, 10_000, '2026-11-01 00:00:00', '2030-01-01'))
        self.assertNotRegex(text, r'(?i)\b(CREATE|ALTER|DROP|TRUNCATE)\b')
        self.assertFalse(list(Path(__file__).parent.glob('V*__*.sql')))

    def test_due_cohort_only_moves_first_active_subscriptions(self):
        sql = perfgen.due_cohort_sql('2026-11-01 00:00:00', 250)
        self.assertIn("WHERE status = 'ACTIVE' ORDER BY id LIMIT 250", sql)
        self.assertIn("DATE('2026-11-01 00:00:00')", sql)
        self.assertNotRegex(sql, r'(?i)(RAND|UUID|NOW|CREATE|ALTER|DROP|DELETE|INSERT)')
        self.assertEqual(sql, perfgen.due_cohort_sql('2026-11-01 00:00:00', 250))

    def test_flyway_schema_must_be_complete_at_latest_version(self):
        def answers(*outputs):
            return patch.object(perfgen, 'mysql', side_effect=list(outputs))
        with answers('0'):
            self.assertEqual(perfgen.schema_source('pawcycle-perfgen-t', 28), 'raw')
        with answers('1', '0	28'):
            self.assertEqual(perfgen.schema_source('pawcycle-perfgen-t', 28), 'flyway')
        for history in ['0	27', '1	28', 'NULL	NULL']:
            with self.subTest(history=history), answers('1', history):
                with self.assertRaises(perfgen.Stop):
                    perfgen.schema_source('pawcycle-perfgen-t', 28)

    def test_invariants_are_read_only(self):
        sql = perfgen.INVARIANTS.read_text(encoding='utf-8')
        self.assertNotRegex(sql, r'(?i)\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE|REPLACE)\b')


if __name__ == '__main__':
    unittest.main()

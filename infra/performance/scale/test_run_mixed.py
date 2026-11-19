"""Mixed-load runner: metric parsing, window deltas and gates; no Docker or k6 needed.

  python infra/performance/scale/test_run_mixed.py
"""
import importlib.util
from pathlib import Path
import re
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('run_mixed', HERE / 'run-mixed.py')
run_mixed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(run_mixed)
K6 = (HERE.parents[1] / 'performance/k6/scale-mixed.js').read_text(encoding='utf-8')

BEFORE = """# HELP hikaricp_connections_timeout_total x
# TYPE hikaricp_connections_timeout_total counter
hikaricp_connections_timeout_total{pool="HikariPool-1"} 2.0
hikaricp_connections_pending{pool="HikariPool-1"} 0.0
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 100.0
jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 999.0
http_server_requests_seconds_count{method="GET",status="200",uri="/api/products"} 10.0
http_server_requests_seconds_count{method="GET",status="500",uri="/api/cart"} 1.0
pawcycle_job_executions_total{job_name="metrics.commerce",outcome="success"} 5.0
pawcycle_job_last_success_seconds{job_name="checkout.expiration"} NaN
"""
AFTER = BEFORE.replace('timeout_total{pool="HikariPool-1"} 2.0', 'timeout_total{pool="HikariPool-1"} 3.0') \
    .replace('status="200",uri="/api/products"} 10.0', 'status="200",uri="/api/products"} 40.0') \
    .replace('status="500",uri="/api/cart"} 1.0', 'status="500",uri="/api/cart"} 4.0') \
    .replace('outcome="success"} 5.0', 'outcome="success"} 9.0')
PEAK = BEFORE.replace('pending{pool="HikariPool-1"} 0.0', 'pending{pool="HikariPool-1"} 7.0') \
    .replace('id="G1 Eden Space"} 100.0', 'id="G1 Eden Space"} 300.0')


class RunMixedTests(unittest.TestCase):
    def test_parse_skips_comments_and_nan(self):
        samples = run_mixed.parse_prometheus(BEFORE)
        names = {n for n, _, _ in samples}
        self.assertNotIn('pawcycle_job_last_success_seconds', names)   # NaN = never succeeded, not 0
        self.assertEqual(run_mixed.total(samples, 'jvm_memory_used_bytes', {'area': 'heap'}), 100.0)

    def test_window_deltas_and_peaks(self):
        before, after, peak = (run_mixed.parse_prometheus(t) for t in (BEFORE, AFTER, PEAK))
        summary = run_mixed.server_summary(before, after, [before, peak, after])
        self.assertEqual(summary['hikari_timeouts'], 1.0)
        self.assertEqual(summary['http_requests'], 33.0)
        self.assertEqual(summary['http_5xx'], 3.0)
        self.assertEqual(summary['hikari_pending_max'], 7.0)
        self.assertEqual(summary['heap_used_max_bytes'], 300.0)
        self.assertEqual(summary['jobs'], {'metrics.commerce:success': 4})

    def test_gates(self):
        def report(passed=True, dropped=0, interrupted=0, invariants='PASS', exit_code=0):
            return {'k6': {'steady': {'verdict': {'passed': passed}, 'accounting': {'droppedIterations': dropped},
                                      'interrupted_iterations': interrupted, 'k6_exit': exit_code}},
                    'invariants': invariants}
        self.assertEqual(run_mixed.gate_failures(report()), [])
        self.assertEqual(run_mixed.gate_failures(report(passed=False, exit_code=99)), ['k6_exit', 'k6_contract'])
        self.assertEqual(run_mixed.gate_failures(report(exit_code=107)), ['k6_exit'])
        self.assertEqual(run_mixed.gate_failures(report(dropped=3)), ['dropped_iterations'])
        self.assertEqual(run_mixed.gate_failures(report(invariants='FAIL')), ['invariants'])
        self.assertEqual(run_mixed.gate_failures(report(interrupted=1)), ['interrupted_iterations'])

    def test_existing_environment_is_refused_and_never_removed(self):
        class Args:
            run, port, out, scale, seed, rps, warmup, steady, keep = 'kept', 18090, None, '10k', 42, 20, 1, 1, False

        class Found:
            returncode = 0

        with patch.object(run_mixed.subprocess, 'run', return_value=Found()) as run:
            with patch.object(run_mixed.run_scale, 'down') as down:
                with self.assertRaises(run_mixed.Stop):
                    run_mixed.run_mixed(Args())
        down.assert_not_called()
        self.assertEqual(run.call_count, 1)   # only the docker inspect; run-scale never started

    def test_k6_parses_json_without_throwing(self):
        self.assertNotRegex(K6[K6.index('// ---- 5. Bundles'):K6.index('// ---- 6.')], r'\.json\(')

    def test_k6_counts_interrupted_iterations(self):
        self.assertIn('started_iterations', K6)
        self.assertRegex(K6, r"started\.add\(1\);\s*body\(\);\s*metrics\[bundle\]\.finished\.add\(1\)")

    def test_runner_and_k6_agree_on_bundles_and_vus(self):
        mix = re.search(r'export const MIX = \{(.*?)\};', K6).group(1)
        self.assertEqual(sorted(re.findall(r'(\w+):', mix)), sorted(run_mixed.VUS))
        self.assertLessEqual(sum(run_mixed.VUS.values()), run_mixed.COHORT // 2)

    def test_k6_targets_loopback_and_writes_no_secret(self):
        self.assertIn(r"/^http:\/\/127\.0\.0\.1:\d{2,5}$/", K6)
        summary = K6[K6.index('export function handleSummary'):]
        self.assertNotIn('PASSWORD', summary)
        self.assertNotIn('SESSIONS', summary)


if __name__ == '__main__':
    unittest.main()

"""Fail-closed target gates and read-only digest delta regression; no Docker needed."""
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('checkout_runner', Path(__file__).with_name('run-checkout.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class CheckoutRunnerTests(unittest.TestCase):
    def runtime(self):
        return {'Config': {'Labels': {'com.docker.compose.project': 'pawcycle-local-integration'},
                           'Env': ['SPRING_PROFILES_ACTIVE=local-integration',
                                   'SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/local']},
                'HostConfig': {'PortBindings': {'8080/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '8080'}]},
                               'Memory': 0, 'NanoCpus': 0, 'PidsLimit': None},
                'State': {'StartedAt': 'local', 'OOMKilled': False, 'Running': True},
                'Image': 'local-image', 'RestartCount': 0,
                'Mounts': [{'Name': 'pawcycle-local-integration-mysql-data', 'Destination': '/var/lib/mysql'}]}

    def inspect(self, runtime, service='backend'):
        with patch.object(runner, 'command', side_effect=['local-container', json.dumps([runtime])]):
            return runner.inspect(service)

    def test_local_runtime_allowed(self):
        self.assertEqual(self.inspect(self.runtime())['restarts'], 0)

    def test_wrong_project_profile_datasource_and_toss_rejected(self):
        for env in ['SPRING_PROFILES_ACTIVE=production',
                    'SPRING_DATASOURCE_URL=jdbc:mysql://remote:3306/local',
                    'PAWCYCLE_TOSS_TEST_ENABLED=true',
                    'PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS=true']:
            runtime = self.runtime()
            key = env.split('=')[0]
            runtime['Config']['Env'] = [e for e in runtime['Config']['Env'] if not e.startswith(key + '=')] + [env]
            with self.subTest(env=key), self.assertRaises(RuntimeError):
                self.inspect(runtime)
        runtime = self.runtime()
        runtime['Config']['Labels']['com.docker.compose.project'] = 'other-project'
        with self.assertRaises(RuntimeError):
            self.inspect(runtime)

    def test_non_loopback_binding_and_wrong_mysql_volume_rejected(self):
        runtime = self.runtime()
        runtime['HostConfig']['PortBindings']['8080/tcp'][0]['HostIp'] = '0.0.0.0'
        with self.assertRaises(RuntimeError):
            self.inspect(runtime)
        runtime['Mounts'][0]['Name'] = 'another-qa-volume'
        with self.assertRaises(RuntimeError):
            self.inspect(runtime, 'mysql')

    def test_digest_delta_uses_only_measurement_and_omits_collector_sql(self):
        old = dict.fromkeys(runner.FIELDS, 0)
        old.update(count=100, timer_ps=1000000000)
        latest = {**old, 'count': 102, 'timer_ps': 5000000000, 'sql': 'SELECT * FROM carts WHERE id = ?'}
        collector = {**latest, 'sql': 'SELECT * FROM performance_schema.data_lock_waits'}
        result = runner.mysql_delta({'digests': {'d': old}, 'locks': {}, 'transactions': ''},
                                    {'digests': {'d': latest, 'c': collector}, 'locks': {}, 'transactions': ''})
        self.assertEqual(len(result['digests']), 1)
        self.assertEqual(result['digests'][0]['count'], 2)
        self.assertEqual(result['digests'][0]['average_ms'], 2)
        self.assertIsNone(result['innodb_deadlocks_delta'])

    def test_mysql_delta_requires_a_captured_innodb_deadlock_counter(self):
        old = {'digests': {}, 'locks': {}, 'innodb_deadlocks': 7, 'transactions': ''}
        latest = {'digests': {}, 'locks': {}, 'innodb_deadlocks': 7, 'transactions': ''}
        self.assertEqual(runner.mysql_delta(old, latest)['innodb_deadlocks_delta'], 0)
        latest['innodb_deadlocks'] = 8
        self.assertEqual(runner.mysql_delta(old, latest)['innodb_deadlocks_delta'], 1)
        latest['innodb_deadlocks'] = None
        self.assertIsNone(runner.mysql_delta(old, latest)['innodb_deadlocks_delta'])

    def test_cleanup_rejects_invalid_marker_before_any_database_command(self):
        with patch.object(runner, 'sql') as sql:
            with self.assertRaises(RuntimeError):
                runner.cleanup('shared-qa')
            sql.assert_not_called()

    def test_checkout_pool_is_120_and_cleanup_uses_exact_namespace(self):
        self.assertEqual(runner.POOL_SIZE, 120)
        with patch.object(runner, 'sql', side_effect=['', '0']) as sql:
            runner.cleanup('pc001-123456abcdef')
            query = sql.call_args_list[0].args[0]
            self.assertIn("'pc001-123456abcdef-120@local.invalid'", query)
            self.assertIn(f"'pc001-123456abcdef-{runner.POOL_SIZE}@local.invalid'", query)
            self.assertNotIn(' LIKE ', query)
            self.assertNotIn('TRUNCATE', query)
            self.assertNotIn('FOREIGN_KEY_CHECKS', query)

    def test_commit_timeline_uses_sanitised_digest_counters(self):
        summary = {'digests': {'d': {'sql': 'COMMIT', 'count': 4, 'timer_ps': 7500000000}}}
        self.assertEqual(runner.commit_counters(summary), {'count': 4, 'timer_ps': 7500000000})
        self.assertEqual(runner.commit_counters({'digests': {}}), {'count': 0, 'timer_ps': 0})

    def test_metric_count_handles_absent_k6_dropped_metric(self):
        self.assertEqual(runner.metric_count({'metrics': {'dropped_iterations': {'values': {'count': 3}}}},
                                             'dropped_iterations'), 3)
        self.assertEqual(runner.metric_count({'metrics': {}}, 'dropped_iterations'), 0)

    def test_dropped_run_is_capacity_candidate_only_when_all_measurement_gates_pass(self):
        args = (18, 0, 0, True, True, True, True, True)
        self.assertEqual(runner.classify_measurement(*args), 'capacity_failure_candidate')
        self.assertEqual(runner.classify_measurement(*args[:5], False, *args[6:]),
                         'invalid_measurement_with_drops')
        self.assertEqual(runner.classify_measurement(0, 0, 0, True, True, True, True, True),
                         'valid_before')

    def test_before_accepts_only_the_approved_correction_on_pinned_main(self):
        with patch.object(runner, 'command', side_effect=[
            'codex/perf-commerce-001', runner.BASE_MAIN_SHA, runner.BASE_MAIN_SHA,
            '\n'.join(sorted(runner.BEFORE_CORRECTNESS_FILES)), '',
        ]):
            runner.require_before_source_state()

    def test_before_rejects_other_backend_changes_or_main_drift(self):
        cases = [
            ['codex/perf-commerce-001', runner.BASE_MAIN_SHA, runner.BASE_MAIN_SHA,
             'backend/src/main/java/Other.java', ''],
            ['codex/perf-commerce-001', runner.BASE_MAIN_SHA, 'new-main-sha', '', ''],
            ['codex/perf-commerce-001', runner.BASE_MAIN_SHA, runner.BASE_MAIN_SHA,
             '\n'.join(sorted(runner.BEFORE_CORRECTNESS_FILES)), 'backend/untracked.java'],
        ]
        for result in cases:
            with self.subTest(result=result), patch.object(runner, 'command', side_effect=result), \
                    self.assertRaises(RuntimeError):
                runner.require_before_source_state()


if __name__ == '__main__':
    unittest.main()

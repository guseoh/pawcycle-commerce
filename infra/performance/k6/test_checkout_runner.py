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

    def test_status_error_counts_keep_http_status_tags(self):
        summary = {'metrics': {
            'checkout_status_errors{status: "409"}': {'values': {'count': 3}},
            'checkout_status_errors{status: "500"}': {'values': {'count': 2}},
            'checkout_status_errors{status=409}': {'values': {'count': 4}},
            'checkout_requests': {'values': {'count': 10}},
        }}
        self.assertEqual(runner.status_error_counts(summary), {'409': 7, '500': 2})

    def test_sql_boundary_summary_distinguishes_shared_sku_lock_and_inventory_update(self):
        rows = [
            {'sql': 'SELECT * FROM `skus` WHERE `id` IN (...) FOR UPDATE', 'count': 8,
             'timer_ps': 2_000_000_000, 'lock_ps': 1_500_000_000},
            {'sql': 'SELECT * FROM `inventories` WHERE `sku_id` = ?', 'count': 8,
             'timer_ps': 800_000_000, 'lock_ps': 0},
            {'sql': 'UPDATE `inventories` SET `version` = `version` + ?', 'count': 8,
             'timer_ps': 1_200_000_000, 'lock_ps': 500_000_000},
            {'sql': 'COMMIT', 'count': 8, 'timer_ps': 4_000_000_000, 'lock_ps': 0},
        ]
        summary = runner.digest_boundary_summaries(rows)
        self.assertEqual(summary['sku_select_for_update']['statement_count'], 8)
        self.assertEqual(summary['sku_select_for_update']['average_ms'], 0.25)
        self.assertEqual(summary['inventory_select']['statement_count'], 8)
        self.assertEqual(summary['inventory_reserve_update']['statement_count'], 8)
        self.assertEqual(summary['commit']['total_ms'], 4)

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

    def stage(self):
        return {'start_utc': '2026-10-06T00:00:00Z', 'end_utc': '2026-10-06T00:02:00Z',
                'target_rps': 5, 'k6': {'metrics': {'checkout_requests': {'values': {'count': 588}}}},
                'status_error_rate': 0, 'dropped_iterations': 0,
                'mysql': {'innodb_deadlocks_delta': 0}, 'runtime_stable': True,
                'fixture_contract_matches_requests': True, 'backend_scrape_healthy': True,
                'digest_complete': True, 'k6_exit': 0}

    def test_ladder_gates_every_failure_and_accepts_exact_98_percent(self):
        self.assertEqual(runner.stage_failures(self.stage()), [])
        changes = [('status_error_rate', 0.01, 'status_errors'), ('dropped_iterations', 1, 'dropped'),
                   ('runtime_stable', False, 'restart_oom'),
                   ('fixture_contract_matches_requests', False, 'fixture_mismatch'),
                   ('backend_scrape_healthy', False, 'backend_scrape'),
                   ('digest_complete', False, 'digest_loss'), ('k6_exit', 99, 'k6_exit')]
        for field, value, expected in changes:
            evidence = self.stage()
            evidence[field] = value
            with self.subTest(gate=field):
                self.assertEqual(runner.stage_failures(evidence), [expected])
        for deadlock in [1, None]:
            evidence = self.stage()
            evidence['mysql']['innodb_deadlocks_delta'] = deadlock
            self.assertEqual(runner.stage_failures(evidence), ['deadlock'])
        evidence = self.stage()
        evidence['k6']['metrics']['checkout_requests']['values']['count'] = 587
        self.assertEqual(runner.stage_failures(evidence), ['actual_rps'])
        self.assertEqual(runner.stage_failures(evidence, check_actual=False), [])

    def test_no_higher_stage_is_run_after_first_failure_including_warmup(self):
        for fail_at in runner.LADDER_RATES:
            for phase in ['warmup', 'measurement']:
                called = []
                def run(rate):
                    called.append(rate)
                    return {'classification': 'hard_failure' if rate == fail_at else 'stable',
                            'phase': phase, 'actual_rps': rate,
                            'gate_failures': ['dropped'] if rate == fail_at else []}
                result = runner.run_ladder(18080, run)
                self.assertEqual(called, list(runner.LADDER_RATES[:runner.LADDER_RATES.index(fail_at) + 1]))
                self.assertEqual(result['not_run_rps'], list(runner.LADDER_RATES[len(called):]))

    def test_resource_json_is_sanitized_and_memory_units_match_existing_pattern(self):
        rows = [{'ID': cid, 'CPUPerc': '12.5%', 'MemUsage': '2MiB / 4GiB',
                 'Name': 'omit-container-name', 'PIDs': '7'} for cid in ['abc', 'def']]
        with patch.object(runner, 'command', return_value='\n'.join(map(json.dumps, rows))) as command, \
                patch.object(runner, 'sql', return_value='0'), \
                patch.object(runner, 'mysql_threads', return_value={'Threads_connected': 12, 'Threads_running': 1}):
            sample = runner.resource_sample({'backend': 'abcdef', 'mysql': 'defabc'})
        self.assertEqual(command.call_args_list[0].args[0][1:5], ['stats', '--no-stream', '--format', '{{json .}}'])
        self.assertEqual(sample['backend'], {'cpu_percent': 12.5, 'memory_used_bytes': 2 * 1024**2,
                                            'memory_limit_bytes': 4 * 1024**3})
        self.assertNotIn('Name', str(sample))
        with self.assertRaises(ValueError):
            runner.memory_bytes('unknown')

    def test_pending_one_point_is_distinguished_from_consecutive_queueing(self):
        self.assertEqual(runner.consecutive_positive([{'values': [[0, '0'], [15, '8'], [30, '0']]}]), 1)
        self.assertEqual(runner.consecutive_positive([{'values': [[0, '1'], [15, '2'], [30, '3']]}]), 3)

    def test_catalog_health_expiry_and_metric_scans_are_not_checkout_cost(self):
        for statement in ['SELECT COUNT ( * ) FROM `products` `p` FORCE INDEX ( PRIMARY ) WHERE `p` . `display_status` = ?',
                          'SELECT ( SELECT `s2` . `price` FROM `skus` `s2` WHERE `s2` . `product_id` = `p` . `id` ) FROM `products` `p`',
                          'SELECT COUNT ( * ) FROM `payments` WHERE STATUS IN (...)',
                          'SELECT `p` . `id` FROM `payments` `p` WHERE `p` . `expires_at` <= ? ORDER BY `p` . `id` LIMIT ?',
                          'SELECT * FROM performance_schema.data_lock_waits']:
            self.assertFalse(runner.is_checkout_digest(statement), statement)
        for statement in ['SELECT * FROM `members` `m` WHERE `m` . `id` = ? FOR UPDATE',
                          'SELECT * FROM `skus` `s` WHERE `s` . `id` IN (...) FOR UPDATE',
                          'SELECT * FROM `carts` WHERE member_id = ?',
                          'SELECT * FROM `checkout_idempotency_results` `c` WHERE ( `c` . `idempotency_key` , `c` . `member_id` ) IN ( (...) )',
                          'INSERT INTO `payments` VALUES (...)',
                          'UPDATE `inventories` SET `reserved_quantity` = ? WHERE `sku_id` = ?']:
            self.assertTrue(runner.is_checkout_digest(statement), statement)

    def test_fixture_delta_and_runtime_gates_preserve_boundary(self):
        before = {'orders': 100, 'payments_ready': 100, 'payments': 100, 'idempotency_results': 100,
                  'reservations': 100, 'reserved_quantity': 100, 'available_quantity': 999900,
                  'inventory_version': 100, 'members': 120, 'cart_items': 120,
                  'products': 120, 'skus': 120, 'inventories': 120}
        after = {**{key: value + 600 for key, value in before.items()
                    if key in ['orders', 'payments_ready', 'payments', 'idempotency_results',
                               'reservations', 'reserved_quantity', 'inventory_version']},
                 **{key: value for key, value in before.items()
                    if key not in ['orders', 'payments_ready', 'payments', 'idempotency_results',
                                   'reservations', 'reserved_quantity', 'inventory_version']},
                 'available_quantity': 999300, 'negative_inventories': 0,
                 'duplicate_payment_orders': 0, 'minimum_stock': 9990}
        self.assertTrue(runner.fixture_delta_matches(before, after, 600))
        after['reservations'] += 1
        self.assertFalse(runner.fixture_delta_matches(before, after, 600))
        shared_before = {**before, 'available_quantity': 10000, 'products': 1, 'skus': 1, 'inventories': 1}
        shared_after = {**shared_before, 'orders': 700, 'payments_ready': 700, 'payments': 700,
                        'idempotency_results': 700, 'reservations': 700, 'reserved_quantity': 700,
                        'available_quantity': 9400, 'inventory_version': 700,
                        'negative_inventories': 0, 'duplicate_payment_orders': 0,
                        'minimum_stock': 9400}
        self.assertTrue(runner.fixture_delta_matches(shared_before, shared_after, 600, 'shared'))
        shared_after['inventory_version'] += 1
        self.assertFalse(runner.fixture_delta_matches(shared_before, shared_after, 600, 'shared'))
        state = {'running': True, 'oom': False, 'started': 'start', 'restarts': 0,
                 'image': 'image', 'memory_bytes': 0, 'nano_cpus': 0, 'pids': None}
        self.assertTrue(runner.runtime_unchanged(state, state))
        for field, value in [('restarts', 1), ('image', 'other'), ('oom', True), ('nano_cpus', 1)]:
            self.assertFalse(runner.runtime_unchanged(state, {**state, field: value}))


if __name__ == '__main__':
    unittest.main()

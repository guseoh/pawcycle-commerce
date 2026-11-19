"""Scale environment safety contract; no Docker needed.

  python infra/performance/scale/test_run_scale.py
"""
import importlib.util
from pathlib import Path
import re
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('run_scale', HERE / 'run-scale.py')
run_scale = importlib.util.module_from_spec(spec)
spec.loader.exec_module(run_scale)
COMPOSE = (HERE / 'compose.yaml').read_text(encoding='utf-8')


def service(name):
    match = re.search(rf'^  {name}:\n(.*?)(?=^  [a-z-]+:\n|^networks:)', COMPOSE, re.M | re.S)
    return match.group(1)


class RunScaleTests(unittest.TestCase):
    def test_run_id_maps_into_perfgen_namespace(self):
        project, container = run_scale.names('g1-10k')
        self.assertEqual(project, 'pawcycle-perf-g1-10k')
        self.assertTrue(run_scale.perfgen.NAME_PATTERN.fullmatch(container))
        for bad in ['', 'UPPER', 'a b', '../x', 'x' * 40]:
            with self.subTest(run=bad), self.assertRaises(run_scale.Stop):
                run_scale.names(bad)

    def test_mysql_keeps_the_perfgen_guard_contract(self):
        mysql = service('mysql')
        self.assertIn('container_name: ${PAWCYCLE_SCALE_DB_CONTAINER', mysql)
        self.assertIn('pawcycle.purpose: perf-synthetic-data', mysql)
        self.assertRegex(mysql, r'volumes:\n\s+- /var/lib/mysql\n')
        self.assertNotIn('ports:', mysql)

    def test_backend_is_local_only_with_production_limits(self):
        backend = service('backend')
        self.assertIn('"127.0.0.1:${PAWCYCLE_SCALE_BACKEND_PORT', backend)
        self.assertIn('mem_limit: 640m', backend)
        self.assertIn('cpus: 0.75', backend)
        self.assertIn('SPRING_PROFILES_ACTIVE: local-integration', backend)
        self.assertNotIn('PAWCYCLE_TOSS_TEST', COMPOSE)
        self.assertNotIn('PAWCYCLE_SCHEDULER_ENABLED', COMPOSE)  # not a real property

    def test_switchable_jobs_default_off(self):
        for flag in re.findall(r'(PAWCYCLE_[A-Z_]+_ENABLED): (\S+)', service('backend')):
            name, value = flag
            if name.startswith(('PAWCYCLE_COMMERCE_', 'PAWCYCLE_SUBSCRIPTION_')):
                with self.subTest(flag=name):
                    self.assertRegex(value, r'^("false"|\$\{PAWCYCLE_SCALE_[A-Z_]+:-false\})$')

    def test_failed_login_or_check_is_a_verification_failure(self):
        def summary(verdict='PASS', login=200, outside=401):
            return {'check': {'verdict': verdict},
                    'cohort': {'login': {'1': {'login': login, 'me': 200}}, 'outside_cohort_login': outside}}
        self.assertEqual(run_scale.verification_failures(summary()), [])
        self.assertEqual(run_scale.verification_failures(summary(verdict='FAIL')), ['invariants'])
        self.assertEqual(run_scale.verification_failures(summary(login=401)), ['cohort_login'])
        self.assertEqual(run_scale.verification_failures(summary(outside=200)), ['outside_cohort_login'])

    def test_environment_without_secrets_uses_placeholders(self):
        env = run_scale.environment('g1-10k', 18090, {})
        self.assertEqual(env['PAWCYCLE_SCALE_DB_PASSWORD'], 'unused')
        self.assertEqual(env['PAWCYCLE_SCALE_SUBSCRIPTION_AUTOMATION'], 'false')


if __name__ == '__main__':
    unittest.main()

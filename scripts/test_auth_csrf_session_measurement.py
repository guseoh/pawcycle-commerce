from __future__ import annotations

import copy
import importlib.util
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = (
    ROOT / "infra" / "performance" / "auth-csrf-session" / "measure_auth_csrf_sessions.py"
)
SPEC = importlib.util.spec_from_file_location("auth_csrf_session_measurement", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
measurement = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = measurement
SPEC.loader.exec_module(measurement)


class AuthCsrfSessionMeasurementTests(unittest.TestCase):
    def test_synthetic_contracts_pass(self) -> None:
        measurement.validate_repository_contract()
        measurement.run_synthetic_validation()

    def test_missing_session_metric_fails_closed(self) -> None:
        samples = measurement.parse_prometheus_samples(
            "tomcat_sessions_active_current_sessions 1\n"
        )

        with self.assertRaisesRegex(ValueError, "missing or ambiguous"):
            measurement.discover_session_metric_names(samples)

    def test_missing_required_process_cpu_metric_fails_closed(self) -> None:
        samples = measurement.parse_prometheus_samples(
            'jvm_memory_used_bytes{area="heap"} 100\n'
        )

        with self.assertRaisesRegex(ValueError, "process_cpu_usage"):
            measurement.metric_sum(samples, "process_cpu_usage")

    def test_sensitive_evidence_fails_closed(self) -> None:
        summary = measurement.synthetic_summary()
        summary["csrfToken"] = "sentinel-sensitive-value"

        with self.assertRaisesRegex(ValueError, "Sensitive evidence key"):
            measurement.validate_measurement_summary(summary)

    def test_cohort_size_or_delta_drift_fails_closed(self) -> None:
        mismatched = copy.deepcopy(measurement.synthetic_summary())
        mismatched["cohorts"]["reused"]["requestCount"] = 9
        with self.assertRaisesRegex(ValueError, "same request count"):
            measurement.validate_measurement_summary(mismatched)

        wrong_delta = copy.deepcopy(measurement.synthetic_summary())
        wrong_delta["cohorts"]["fresh"]["sessionCreatedDelta"] = 9
        with self.assertRaisesRegex(ValueError, "does not match"):
            measurement.validate_measurement_summary(wrong_delta)

    def test_measurement_setting_is_absent_from_production_defaults(self) -> None:
        measurement.validate_repository_contract()
        default_properties = measurement.PRODUCTION_PROPERTIES.read_text(encoding="utf-8")
        self.assertNotIn("server.tomcat.mbeanregistry.enabled", default_properties)

    def test_compose_model_rejects_non_loopback_publication(self) -> None:
        model = measurement.synthetic_compose_model()
        model["services"]["backend"]["ports"][0]["host_ip"] = "0.0.0.0"

        with self.assertRaisesRegex(ValueError, "loopback-only"):
            measurement.validate_compose_model(model)


if __name__ == "__main__":
    unittest.main()

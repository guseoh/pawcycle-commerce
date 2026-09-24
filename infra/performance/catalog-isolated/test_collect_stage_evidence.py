import datetime as dt
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock


MODULE_PATH = Path(__file__).with_name("collect-stage-evidence.py")
SPEC = importlib.util.spec_from_file_location("collect_stage_evidence", MODULE_PATH)
collector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(collector)


class EvidenceTest(unittest.TestCase):
    def test_oci_projection_discards_resource_identity(self):
        response = {"data": [{"dimensions": {"resourceID": "resource-marker"},
                              "aggregated-datapoints": [
                                  {"timestamp": "2026-09-24T00:01:00Z", "value": 42}]}]}
        with mock.patch.dict(collector.os.environ, {
            "PAWCYCLE_PERF_OCI_COMPARTMENT_ID": "ocid1." + "compartment.fixture",
            "PAWCYCLE_PERF_OCI_DB_SYSTEM_ID": "ocid1." + "mysqldbsystem.fixture"}), \
             mock.patch.object(collector, "command", return_value=json.dumps(response)):
            points = collector.oci_points("CPUUtilization", "mean",
                                          "2026-09-24T00:00:00Z", "2026-09-24T00:02:00Z")
        self.assertEqual(points, [{"timestampUtc": "2026-09-24T00:01:00Z", "value": 42}])

    def test_allowlist_excludes_unrelated_metrics_and_requires_runtime_metrics(self):
        lines = []
        for name in collector.METRICS:
            labels = '{area="heap"}' if name.startswith("jvm_memory_") else ""
            lines.append(f"{name}{labels} 1")
        lines.extend(["jvm_memory_used_bytes{area=\"nonheap\"} 2",
                      "some_secret_metric{password=\"must-not-persist\"} 99"])
        parsed = collector.parse_metrics("\n".join(lines))
        self.assertNotIn("some_secret_metric", parsed)
        self.assertEqual(parsed["jvmMemoryUsedNonHeap"], 2)
        with self.assertRaises(ValueError):
            collector.parse_metrics("process_cpu_usage 0.1")

    def test_assemble_keeps_only_measurement_window_and_rejects_missing_oci(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            start = dt.datetime(2026, 9, 24, 0, 0, tzinfo=dt.timezone.utc)
            end = start + dt.timedelta(seconds=120)
            summary = {"datasetId": "catalog-core-control-v1", "targetRps": 25,
                       "measurementStartUtc": start.isoformat(),
                       "measurementEndUtc": end.isoformat()}
            (root / "summary.json").write_text(json.dumps(summary))
            samples = []
            for seconds in range(-5, 126, 5):
                timestamp = (start + dt.timedelta(seconds=seconds)).isoformat()
                samples.append(json.dumps({"timestampUtc": timestamp,
                                           "container": {"restartCount": 0, "oomKilled": False,
                                                         "health": "healthy"}}))
            (root / "samples.jsonl").write_text("\n".join(samples))
            args = type("Args", (), {"summary": root / "summary.json",
                                     "host_samples": root / "samples.jsonl",
                                     "output": root / "evidence.json"})()
            points = [{"timestampUtc": (start + dt.timedelta(seconds=60)).isoformat(),
                       "value": 1.0}]
            with mock.patch.object(collector, "oci_points", return_value=points):
                collector.assemble(args)
            result = json.loads(args.output.read_text())
            self.assertEqual(len(result["hostJvmTomcatHikariSamples"]), 25)
            self.assertEqual(set(result["ociMysql"]), set(collector.OCI_METRICS))
            self.assertNotIn("ocid", args.output.read_text())
            args.output = root / "missing.json"
            with mock.patch.object(collector, "oci_points", return_value=[]), \
                 mock.patch.object(collector.time, "sleep"):
                with self.assertRaisesRegex(ValueError, "no measurement-window datapoint"):
                    collector.assemble(args)
            args.output = root / "retried.json"
            calls = {name: 0 for name in collector.OCI_METRICS}

            def delayed(metric, _statistic, _start, _end):
                calls[metric] += 1
                return [] if calls[metric] == 1 else points

            with mock.patch.object(collector, "oci_points", side_effect=delayed), \
                 mock.patch.object(collector.time, "sleep") as sleep:
                collector.assemble(args)
            retried = json.loads(args.output.read_text())
            self.assertEqual(set(retried["ociMysql"]), set(calls))
            self.assertTrue(all(count == 2 for count in calls.values()))
            sleep.assert_called_once_with(20)


if __name__ == "__main__":
    unittest.main()

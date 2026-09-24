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
    def test_swap_activity_is_an_interval_delta_and_fails_closed(self):
        before = collector.parse_swap_counters("pswpin 12\npswpout 30\n")
        after = collector.parse_swap_counters("pswpin 15\npswpout 31\n")
        with mock.patch.object(collector.os, "sysconf", return_value=4096, create=True):
            self.assertEqual(collector.swap_activity(before, after), {
                "swapInActivityPages": 3,
                "swapOutActivityPages": 1,
                "swapInActivityBytes": 12288,
                "swapOutActivityBytes": 4096,
            })
            with self.assertRaisesRegex(ValueError, "moved backwards"):
                collector.swap_activity(after, before)
        for malformed in (
            "pswpin 12\n",
            "pswpin nope\npswpout 30\n",
            "pswpin 12\npswpin 13\npswpout 30\n",
        ):
            with self.assertRaisesRegex(ValueError, "swap activity counters"):
                collector.parse_swap_counters(malformed)

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
            end = start + dt.timedelta(seconds=120, milliseconds=500)
            summary = {"datasetId": "catalog-core-control-v1", "targetRps": 25,
                       "measurementStartUtc": start.isoformat(),
                       "measurementEndUtc": end.isoformat(), "maxMs": 500}
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
            too_wide = dict(summary, measurementEndUtc=(start + dt.timedelta(seconds=122)).isoformat())
            (root / "too-wide.json").write_text(json.dumps(too_wide))
            too_wide_args = type("Args", (), {"summary": root / "too-wide.json",
                                               "host_samples": root / "samples.jsonl",
                                               "output": root / "too-wide-evidence.json"})()
            with self.assertRaisesRegex(ValueError, "invalid k6 measurement window"):
                collector.assemble(too_wide_args)
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

    def test_k6_evidence_clock_spans_request_start_through_completion(self):
        js = (MODULE_PATH.parents[1] / "k6" / "lib" / "isolated-capacity.js").read_text()
        request = js[js.index("export function request(measurement)"):]
        start = request.index("const requestStartedAt = Date.now();")
        call = request.index("const response = http.get(")
        completion = request.index("const requestCompletedAt = Date.now();")
        clock_start = request.index("measurementClock.add(requestStartedAt);")
        clock_end = request.index("measurementClock.add(requestCompletedAt);")
        self.assertLess(start, call)
        self.assertLess(call, completion)
        self.assertLess(completion, clock_start)
        self.assertLess(clock_start, clock_end)
        self.assertIn("measurementStartUtc: clock.min", request)
        self.assertIn("measurementEndUtc: clock.max", request)


if __name__ == "__main__":
    unittest.main()

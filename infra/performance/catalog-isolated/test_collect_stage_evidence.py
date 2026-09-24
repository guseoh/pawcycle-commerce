import datetime as dt
import contextlib
import io
import importlib.util
import json
from pathlib import Path
import sys
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
        self.assertEqual(points, [{"windowStartUtc": "2026-09-24T00:00:00Z",
                                   "windowEndUtc": "2026-09-24T00:01:00Z", "value": 42}])

    def test_oci_measurement_window_uses_bucket_overlap_and_exclusive_query_end(self):
        start = collector.parse_utc("2026-09-24T12:00:30Z")
        end = collector.parse_utc("2026-09-24T12:02:30Z")
        points = [collector.oci_bucket_point(f"2026-09-24T12:{minute:02d}:00Z", minute)
                  for minute in range(0, 5)]

        selected = collector.select_oci_points(points, start, end)
        self.assertEqual([point["windowEndUtc"] for point in selected], [
            "2026-09-24T12:01:00Z",
            "2026-09-24T12:02:00Z",
            "2026-09-24T12:03:00Z",
        ])
        query_start, query_end = collector.oci_query_bounds(start, end)
        self.assertEqual(query_start, "2026-09-24T11:59:30Z")
        self.assertEqual(query_end, "2026-09-24T12:03:30Z")
        self.assertLess(collector.parse_utc("2026-09-24T12:03:00Z"),
                        collector.parse_utc(query_end))
        self.assertGreaterEqual(collector.parse_utc("2026-09-24T12:04:00Z"),
                                collector.parse_utc(query_end))

    def test_allowlist_excludes_unrelated_metrics_and_requires_runtime_metrics(self):
        lines = []
        for name in collector.METRICS:
            if name in collector.GC_PAUSE_METRICS:
                continue
            labels = '{area="heap"}' if name.startswith("jvm_memory_") else ""
            lines.append(f"{name}{labels} 1")
        lines.extend(["jvm_memory_used_bytes{area=\"nonheap\"} 2",
                      "some_secret_metric{password=\"must-not-persist\"} 99"])
        parsed = collector.parse_metrics("\n".join(lines))
        self.assertNotIn("some_secret_metric", parsed)
        self.assertEqual(parsed["jvmMemoryUsedNonHeap"], 2)
        self.assertEqual(parsed["gcPauseCount"], 0.0)
        self.assertEqual(parsed["gcPauseSeconds"], 0.0)
        without_tomcat = [line for line in lines
                          if not line.startswith("tomcat_threads_busy_threads ")]
        with self.assertRaisesRegex(ValueError, "missing required allowlisted metrics"):
            collector.parse_metrics("\n".join(without_tomcat))
        with self.assertRaises(ValueError):
            collector.parse_metrics("process_cpu_usage 0.1")
        for invalid_gc in ("jvm_gc_pause_seconds_count NaN",
                           "jvm_gc_pause_seconds_sum 1.0e999",
                           "jvm_gc_pause_seconds_count {bad-label 1"):
            with self.assertRaisesRegex(ValueError, "GC pause metric"):
                collector.parse_metrics("\n".join(lines + [invalid_gc]))

    def test_k6_summary_validation_fails_closed_without_tracebacks(self):
        valid = {"measurementStartUtc": "2026-09-24T12:00:00Z",
                 "measurementEndUtc": "2026-09-24T12:02:00Z", "maxMs": 500}
        invalid_summaries = []
        for field in ("measurementStartUtc", "measurementEndUtc", "maxMs"):
            missing = dict(valid)
            del missing[field]
            invalid_summaries.append(missing)
            invalid_summaries.append(dict(valid, **{field: None}))
            invalid_summaries.append(dict(valid, **{field: ""}))
        invalid_summaries.extend([
            dict(valid, measurementStartUtc="2026-09-24T12:00:00"),
            dict(valid, measurementEndUtc="not-a-time"),
            dict(valid, maxMs=True), dict(valid, maxMs=float("nan")),
            dict(valid, maxMs=float("inf")), dict(valid, maxMs=-1),
            dict(valid, maxMs="500"), dict(valid, maxMs=10 ** 400),
        ])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            samples = root / "samples.jsonl"
            samples.write_text("")
            for index, summary in enumerate(invalid_summaries):
                path = root / f"summary-{index}.json"
                path.write_text(json.dumps(summary))
                args = type("Args", (), {"summary": path, "host_samples": samples,
                                          "output": root / f"evidence-{index}.json"})()
                with self.assertRaises(ValueError):
                    collector.assemble(args)

            malformed = root / "summary-null.json"
            malformed.write_text("null")
            with mock.patch.object(sys, "argv", ["collect-stage-evidence.py", "assemble",
                                                   "--summary", str(malformed), "--host-samples",
                                                   str(samples), "--output", str(root / "out.json")]), \
                 contextlib.redirect_stderr(io.StringIO()) as stderr:
                self.assertEqual(collector.main(), 1)
            self.assertIn("evidence_collection=FAIL reason=", stderr.getvalue())
            self.assertNotIn("Traceback", stderr.getvalue())

    def test_assemble_keeps_only_measurement_window_and_rejects_missing_oci(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            start = dt.datetime(2026, 9, 24, 12, 0, 30, tzinfo=dt.timezone.utc)
            end = start + dt.timedelta(seconds=120)
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
            points = [collector.oci_bucket_point((start + dt.timedelta(seconds=offset)).isoformat(),
                                                  float(offset))
                      for offset in (-30, 30, 90, 150, 210)]
            events = []
            with mock.patch.object(collector, "wait_for_last_oci_bucket",
                                   side_effect=lambda _ends: events.append("wait")), \
                 mock.patch.object(collector, "oci_points",
                                  side_effect=lambda *args: (events.append("query"), points)[1]) as query:
                collector.assemble(args)
            self.assertEqual(events[0], "wait")
            self.assertEqual(events[1], "query")
            result = json.loads(args.output.read_text())
            self.assertEqual(len(result["hostJvmTomcatHikariSamples"]), 25)
            self.assertEqual(set(result["ociMysql"]), set(collector.OCI_METRICS))
            query_start, query_end = collector.oci_query_bounds(start, end)
            self.assertEqual(query.call_args_list[0].args[2:], (query_start, query_end))
            self.assertEqual([point["windowEndUtc"] for point in result["ociMysql"]["CPUUtilization"]], [
                "2026-09-24T12:01:00Z",
                "2026-09-24T12:02:00Z",
                "2026-09-24T12:03:00Z",
            ])
            self.assertNotIn("ocid", args.output.read_text())
            too_wide = dict(summary, measurementEndUtc=(start + dt.timedelta(seconds=122)).isoformat())
            (root / "too-wide.json").write_text(json.dumps(too_wide))
            too_wide_args = type("Args", (), {"summary": root / "too-wide.json",
                                               "host_samples": root / "samples.jsonl",
                                               "output": root / "too-wide-evidence.json"})()
            with self.assertRaisesRegex(ValueError, "invalid k6 measurement window"):
                collector.assemble(too_wide_args)
            args.output = root / "missing.json"
            with mock.patch.object(collector, "wait_for_last_oci_bucket"), \
                 mock.patch.object(collector, "oci_points", return_value=[]), \
                 mock.patch.object(collector.time, "sleep"):
                with self.assertRaisesRegex(ValueError, "no complete expected bucket set"):
                    collector.assemble(args)
            args.output = root / "retried.json"
            calls = {name: 0 for name in collector.OCI_METRICS}
            partial_points = [point for point in points
                              if point["windowEndUtc"] != "2026-09-24T12:03:00Z"]

            def delayed(metric, _statistic, _start, _end):
                calls[metric] += 1
                return partial_points if calls[metric] == 1 else points

            with mock.patch.object(collector, "wait_for_last_oci_bucket"), \
                 mock.patch.object(collector, "oci_points", side_effect=delayed), \
                 mock.patch.object(collector.time, "sleep") as sleep:
                collector.assemble(args)
            retried = json.loads(args.output.read_text())
            self.assertEqual(set(retried["ociMysql"]), set(calls))
            self.assertTrue(all(count == 2 for count in calls.values()))
            sleep.assert_called_once_with(20)

            args.output = root / "exhausted.json"
            calls = {name: 0 for name in collector.OCI_METRICS}
            def always_partial(metric, _statistic, _start, _end):
                calls[metric] += 1
                return partial_points

            with mock.patch.object(collector, "wait_for_last_oci_bucket"), \
                 mock.patch.object(collector, "oci_points", side_effect=always_partial), \
                 mock.patch.object(collector.time, "sleep") as sleep:
                with self.assertRaisesRegex(ValueError, "no complete expected bucket set"):
                    collector.assemble(args)
            self.assertEqual(calls, {name: 7 for name in collector.OCI_METRICS})
            self.assertEqual(sleep.call_count, 6)
            sleep.assert_called_with(collector.OCI_PUBLICATION_RETRY_INTERVAL_SECONDS)

    def test_wait_for_last_expected_oci_bucket_before_query(self):
        start = collector.parse_utc("2026-09-24T12:00:30Z")
        end = collector.parse_utc("2026-09-24T12:02:30Z")
        buckets = collector.expected_oci_bucket_ends(start, end)
        self.assertEqual([collector.format_utc(bucket) for bucket in buckets], [
            "2026-09-24T12:01:00Z", "2026-09-24T12:02:00Z", "2026-09-24T12:03:00Z"])
        with mock.patch.object(collector.time, "sleep") as sleep:
            collector.wait_for_last_oci_bucket(buckets,
                                               now=collector.parse_utc("2026-09-24T12:02:40Z"))
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

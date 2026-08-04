from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RUNNER = ROOT / "run-agent-benchmark.py"
VALIDATOR = ROOT / "validate-agent-benchmark.py"
RENDERER = ROOT / "render-agent-benchmark-charts.py"


def record(scenario: str, run: int, duration: float | None = 1.0, tools: int = 1) -> dict:
    return {
        "schema_version": "3.0",
        "record_type": "result",
        "task_id": "HARNESS-AGENT-TEST",
        "comparison_arm": "test_arm",
        "scenario": scenario,
        "run": run,
        "duration_seconds": duration,
        "tool_calls": tools,
        "failed_tool_calls": 0,
        "user_additional_explanations": 0,
        "user_corrections": 0,
        "user_intervention_measurement": "measured",
        "accuracy": "pass",
        "scope_violation": False,
        "evidence_missing": False,
        "cache_reuse": False,
        "independent_evidence_read": True,
        "counts_toward_independent_repetition": True,
        "production_execution": "none",
    }


class AgentBenchmarkToolsTest(unittest.TestCase):
    def write_valid(self, path: Path) -> None:
        tool_counts = {"A": 1, "B": 1, "C": 6, "D": 3}
        rows = [
            record(scenario, run, None if scenario in {"C", "D"} else float(run), tool_counts[scenario])
            for scenario in "ABCD"
            for run in (1, 2, 3)
        ]
        path.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")

    def test_validate_and_render(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "results.jsonl"
            output = Path(temp) / "chart.svg"
            self.write_valid(source)
            validated = subprocess.run(
                [sys.executable, str(VALIDATOR), str(source), "--expected-arm", "test_arm"],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(0, validated.returncode, validated.stderr)
            rendered = subprocess.run(
                [sys.executable, str(RENDERER), str(source), "--output", str(output)],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(0, rendered.returncode, rendered.stderr)
            svg = output.read_text(encoding="utf-8")
            self.assertIn("N/A", svg)
            self.assertIn("Accuracy pass count", svg)

    def test_validator_rejects_cache_as_independent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "bad.jsonl"
            rows = [record(scenario, run) for scenario in "ABCD" for run in (1, 2, 3)]
            rows[-1]["cache_reuse"] = True
            source.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(VALIDATOR), str(source)],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(1, result.returncode)
            self.assertIn("result must be independent", result.stderr)

    def test_runner_start_finish(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            state = Path(temp) / "state.json"
            output = Path(temp) / "out.jsonl"
            start = subprocess.run(
                [
                    sys.executable,
                    str(RUNNER),
                    "start",
                    "--state",
                    str(state),
                    "--task-id",
                    "HARNESS-AGENT-TEST",
                    "--arm",
                    "test_arm",
                    "--scenario",
                    "A",
                    "--run",
                    "1",
                    "--target",
                    "PR #1",
                    "--prompt",
                    "test",
                ],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(0, start.returncode, start.stderr)
            time.sleep(0.01)
            finish = subprocess.run(
                [
                    sys.executable,
                    str(RUNNER),
                    "finish",
                    "--state",
                    str(state),
                    "--output",
                    str(output),
                    "--tool-calls",
                    "1",
                    "--accuracy",
                    "pass",
                    "--user-intervention-measurement",
                    "measured",
                    "--user-additional-explanations",
                    "0",
                    "--user-corrections",
                    "0",
                    "--independent",
                ],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(0, finish.returncode, finish.stderr)
            row = json.loads(output.read_text(encoding="utf-8"))
            self.assertGreater(row["duration_seconds"], 0)
            self.assertEqual("none", row["production_execution"])


if __name__ == "__main__":
    unittest.main()

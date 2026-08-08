#!/usr/bin/env python3
"""Validate PawCycle agent benchmark JSONL schema and independent repetition contract."""

from __future__ import annotations

import argparse
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

SCENARIOS = {"A", "B", "C", "D"}
REQUIRED = {
    "schema_version", "record_type", "task_id", "comparison_arm", "phase", "scenario", "run",
    "duration_seconds", "tool_calls", "failed_tool_calls", "user_additional_explanations",
    "user_corrections", "user_intervention_measurement", "accuracy", "scope_violation",
    "evidence_missing", "cache_reuse", "independent_evidence_read",
    "counts_toward_independent_repetition", "production_execution", "model",
    "reasoning_level", "success",
}
LEGACY_OPTIONAL = {"production_execution", "model", "reasoning_level", "success"}


def load(paths: list[Path]) -> list[dict]:
    records: list[dict] = []
    for path in paths:
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_number}: invalid JSON: {exc.msg}") from exc
            record["_source"] = f"{path}:{line_number}"
            records.append(record)
    return records


def non_negative_int(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value >= 0


def validate(records: list[dict], expected_arm: str | None, allow_legacy_schema_3: bool) -> list[str]:
    errors: list[str] = []
    independent: dict[str, list[dict]] = defaultdict(list)
    keys: set[tuple[str, int]] = set()
    for record in records:
        source = record.get("_source", "record")
        missing = sorted(REQUIRED - record.keys())
        required_missing = [
            field for field in missing
            if not allow_legacy_schema_3 or field not in LEGACY_OPTIONAL
        ]
        if required_missing:
            errors.append(f"{source}: missing fields: {', '.join(required_missing)}")
            continue
        if record["schema_version"] != "3.0":
            errors.append(f"{source}: schema_version must be 3.0")
        phase = record["phase"]
        if phase not in ("benchmark", "pilot"):
            errors.append(f"{source}: invalid phase")
        elif phase == "benchmark":
            if record["scenario"] not in SCENARIOS:
                errors.append(f"{source}: benchmark scenario must be A, B, C or D")
            if record["run"] not in (1, 2, 3):
                errors.append(f"{source}: benchmark run must be 1, 2 or 3")
        elif not isinstance(record["scenario"], str) or not record["scenario"].strip():
            errors.append(f"{source}: pilot scenario must be a non-empty work item")
        elif not non_negative_int(record["run"]) or record["run"] < 1:
            errors.append(f"{source}: pilot run must be a positive integer")
        if expected_arm and record["comparison_arm"] != expected_arm:
            errors.append(f"{source}: unexpected comparison_arm")
        if record["record_type"] not in ("result", "exploratory_result"):
            errors.append(f"{source}: invalid record_type")
        for field in ("tool_calls", "failed_tool_calls"):
            if not non_negative_int(record[field]):
                errors.append(f"{source}: {field} must be a non-negative integer")
        duration = record["duration_seconds"]
        if duration is not None and not (
            isinstance(duration, (int, float)) and not isinstance(duration, bool) and duration >= 0
        ):
            errors.append(f"{source}: duration_seconds must be null or non-negative")
        measurement = record["user_intervention_measurement"]
        if measurement == "measured":
            if not non_negative_int(record["user_additional_explanations"]) or not non_negative_int(record["user_corrections"]):
                errors.append(f"{source}: measured user intervention requires non-negative counts")
        elif measurement == "not_measured":
            if record["user_additional_explanations"] is not None or record["user_corrections"] is not None:
                errors.append(f"{source}: not_measured user intervention requires null counts")
        else:
            errors.append(f"{source}: invalid user_intervention_measurement")
        if record["accuracy"] not in ("pass", "fail", "not_scored"):
            errors.append(f"{source}: invalid accuracy")
        if "production_execution" in record and record["production_execution"] != "none":
            errors.append(f"{source}: production_execution must be none")
        model = record.get("model")
        reasoning_level = record.get("reasoning_level")
        if (model is None) != (reasoning_level is None):
            errors.append(f"{source}: model and reasoning_level must be recorded together")
        elif model is not None and (
            not isinstance(model, str) or not model.strip()
            or not isinstance(reasoning_level, str) or not reasoning_level.strip()
        ):
            errors.append(f"{source}: model and reasoning_level must be non-empty strings")
        if "success" in record and not isinstance(record["success"], bool):
            errors.append(f"{source}: success must be boolean")
        if record["scope_violation"]:
            errors.append(f"{source}: scope violation")
        if record["record_type"] == "result":
            if not record["independent_evidence_read"] or record["cache_reuse"] or not record["counts_toward_independent_repetition"]:
                errors.append(f"{source}: result must be independent, non-cache and countable")
            if phase == "benchmark":
                key = (record["scenario"], record["run"])
                if key in keys:
                    errors.append(f"{source}: duplicate independent scenario/run {key}")
                keys.add(key)
                independent[record["scenario"]].append(record)
        elif record["counts_toward_independent_repetition"]:
            errors.append(f"{source}: exploratory result cannot count as independent")
    if any(record.get("phase") == "benchmark" for record in records):
        for scenario in sorted(SCENARIOS):
            runs = sorted(record["run"] for record in independent[scenario])
            if runs != [1, 2, 3]:
                errors.append(f"scenario {scenario}: independent runs must be [1, 2, 3], got {runs}")
    return errors


def summary(records: list[dict]) -> None:
    for scenario in sorted(SCENARIOS):
        rows = [
            record for record in records
            if record.get("phase") == "benchmark" and record.get("scenario") == scenario
            and record.get("record_type") == "result"
        ]
        durations = [float(record["duration_seconds"]) for record in rows if record.get("duration_seconds") is not None]
        duration = f"{statistics.median(durations):.3f}s" if durations else "N/A"
        tools = statistics.median([record["tool_calls"] for record in rows]) if rows else 0
        passed = sum(record.get("accuracy") == "pass" for record in rows)
        print(f"{scenario}: duration={duration}, tool_calls_median={tools:g}, accuracy={passed}/{len(rows)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", type=Path)
    parser.add_argument("--expected-arm")
    parser.add_argument(
        "--allow-legacy-schema-3",
        action="store_true",
        help="allow schema 3.0 records created before additive execution fields",
    )
    args = parser.parse_args()
    try:
        records = load(args.inputs)
        errors = validate(records, args.expected_arm, args.allow_legacy_schema_3)
        if errors:
            for error in errors:
                print(error, file=sys.stderr)
            return 1
        summary(records)
        return 0
    except (OSError, ValueError) as exc:
        print(f"benchmark validation error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

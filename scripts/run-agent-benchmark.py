#!/usr/bin/env python3
"""Start and finish an externally executed agent benchmark without running the task itself."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

SCENARIOS = {"A", "B", "C", "D"}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def atomic_write(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temp, path)


def start(args: argparse.Namespace) -> int:
    state = Path(args.state)
    if state.exists() and not args.overwrite:
        raise ValueError(f"state already exists: {state}")
    payload = {
        "schema_version": "3.0",
        "record_type": "benchmark_state",
        "task_id": args.task_id,
        "model": args.model,
        "reasoning_level": args.reasoning_level,
        "comparison_arm": args.arm,
        "scenario": args.scenario,
        "run": args.run,
        "target": args.target,
        "prompt": args.prompt,
        "started_at": utc_now(),
        "started_epoch_ns": time.time_ns(),
        "production_execution": "none",
        "status": "running",
    }
    atomic_write(state, payload)
    print(state)
    return 0


def finish(args: argparse.Namespace) -> int:
    state_path = Path(args.state)
    if not state_path.is_file():
        raise ValueError(f"state not found: {state_path}")
    state = json.loads(state_path.read_text(encoding="utf-8"))
    if state.get("status") != "running":
        raise ValueError("state is not running")
    ended_epoch_ns = time.time_ns()
    duration = (ended_epoch_ns - int(state["started_epoch_ns"])) / 1_000_000_000
    measured = args.user_intervention_measurement == "measured"
    if measured:
        if args.user_additional_explanations is None or args.user_corrections is None:
            raise ValueError("measured user intervention requires both counts")
    else:
        if args.user_additional_explanations is not None or args.user_corrections is not None:
            raise ValueError("not_measured user intervention requires null counts")
    record_type = "result" if args.independent else "exploratory_result"
    record = {
        "schema_version": "3.0",
        "record_type": record_type,
        "task_id": state["task_id"],
        "model": state["model"],
        "reasoning_level": state["reasoning_level"],
        "comparison_arm": state["comparison_arm"],
        "phase": args.phase,
        "scenario": state["scenario"],
        "run": state["run"],
        "target": state.get("target"),
        "prompt": state.get("prompt"),
        "started_at": state["started_at"],
        "ended_at": utc_now(),
        "duration_seconds": round(duration, 6),
        "duration_measurement": "external_wall_clock",
        "tool_calls": args.tool_calls,
        "failed_tool_calls": args.failed_tool_calls,
        "user_additional_explanations": args.user_additional_explanations,
        "user_corrections": args.user_corrections,
        "user_intervention_measurement": args.user_intervention_measurement,
        "accuracy": args.accuracy,
        "success": args.success,
        "scope_violation": args.scope_violation,
        "evidence_missing": args.evidence_missing,
        "cache_reuse": args.cache_reuse,
        "independent_evidence_read": args.independent,
        "counts_toward_independent_repetition": args.independent,
        "production_execution": "none",
        "notes": args.notes,
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("a", encoding="utf-8", newline="\n") as handle:
        handle.write(json.dumps(record, ensure_ascii=False) + "\n")
    state["status"] = "finished"
    state["ended_at"] = record["ended_at"]
    state["output"] = str(output)
    atomic_write(state_path, state)
    print(output)
    return 0


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    sub = root.add_subparsers(dest="command", required=True)
    begin = sub.add_parser("start")
    begin.add_argument("--state", required=True)
    begin.add_argument("--task-id", required=True)
    begin.add_argument("--model", required=True)
    begin.add_argument("--reasoning-level", required=True)
    begin.add_argument("--arm", required=True)
    begin.add_argument("--scenario", choices=sorted(SCENARIOS), required=True)
    begin.add_argument("--run", type=int, choices=(1, 2, 3), required=True)
    begin.add_argument("--target", required=True)
    begin.add_argument("--prompt", required=True)
    begin.add_argument("--overwrite", action="store_true")
    begin.set_defaults(func=start)

    end = sub.add_parser("finish")
    end.add_argument("--state", required=True)
    end.add_argument("--output", required=True)
    end.add_argument("--phase", default="benchmark")
    end.add_argument("--tool-calls", type=int, required=True)
    end.add_argument("--failed-tool-calls", type=int, default=0)
    end.add_argument("--accuracy", choices=("pass", "fail", "not_scored"), required=True)
    end.add_argument("--success", action=argparse.BooleanOptionalAction, required=True)
    end.add_argument("--user-intervention-measurement", choices=("measured", "not_measured"), required=True)
    end.add_argument("--user-additional-explanations", type=int)
    end.add_argument("--user-corrections", type=int)
    end.add_argument("--independent", action="store_true")
    end.add_argument("--cache-reuse", action="store_true")
    end.add_argument("--scope-violation", action="store_true")
    end.add_argument("--evidence-missing", action="store_true")
    end.add_argument("--notes", default="")
    end.set_defaults(func=finish)
    return root


def main() -> int:
    try:
        args = parser().parse_args()
        for field in ("tool_calls", "failed_tool_calls", "user_additional_explanations", "user_corrections"):
            value = getattr(args, field, None)
            if value is not None and value < 0:
                raise ValueError(f"{field} must be non-negative")
        if getattr(args, "independent", False) and getattr(args, "cache_reuse", False):
            raise ValueError("independent execution cannot reuse cache")
        return args.func(args)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"benchmark runner error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

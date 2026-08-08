#!/usr/bin/env python3
"""Render a dependency-free SVG summary from validated agent benchmark JSONL."""

from __future__ import annotations

import argparse
import html
import json
import statistics
from collections import defaultdict
from pathlib import Path

SCENARIOS = ("A", "B", "C", "D")
ARM_LABELS = {
    "chatgpt_connector_pilot": "ChatGPT Connector",
    "codex_github_mcp": "Codex GitHub MCP",
}
COLORS = ("#6f7782", "#2563eb", "#16a34a", "#dc2626")


def load(paths: list[Path]) -> dict[str, dict[str, list[dict]]]:
    selected: dict[str, dict[str, dict[int, dict]]] = defaultdict(lambda: defaultdict(dict))
    for path in paths:
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            row = json.loads(line)
            if row.get("record_type") == "result" and row.get("counts_toward_independent_repetition"):
                selected[row["comparison_arm"]][row["scenario"]][row["run"]] = row
    return {
        arm: {
            scenario: [runs[run] for run in sorted(runs)]
            for scenario, runs in scenarios.items()
        }
        for arm, scenarios in selected.items()
    }


def text(x: int, y: int, value: str, size: int = 14, weight: str = "normal") -> str:
    return f'<text x="{x}" y="{y}" font-family="Arial, sans-serif" font-size="{size}" font-weight="{weight}">{html.escape(value)}</text>'


def render(grouped: dict[str, dict[str, list[dict]]], output: Path, title: str) -> None:
    arms = sorted(grouped)
    width = max(1180, 80 + len(arms) * 250)
    arm_step = 16
    bar_height = 12
    scenario_stride = max(1, len(arms)) * arm_step + 16
    section_body_height = len(SCENARIOS) * scenario_stride
    duration_heading = 135
    duration_start = duration_heading + 40
    tools_heading = duration_start + section_body_height + 35
    tools_start = tools_heading + 40
    accuracy_heading = tools_start + section_body_height + 35
    accuracy_start = accuracy_heading + 40
    height = accuracy_start + section_body_height + 20
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="white"/>',
        text(40, 45, title, 24, "bold"),
        text(40, 72, "Independent records grouped by comparison arm; missing duration is shown as N/A", 13),
    ]
    for index, arm in enumerate(arms):
        color = COLORS[index % len(COLORS)]
        x = 40 + index * 250
        parts.append(f'<rect x="{x}" y="88" width="18" height="12" fill="{color}"/>')
        parts.append(text(x + 26, 99, ARM_LABELS.get(arm, arm), 13, "bold"))
    sections = [
        (duration_heading, "Duration median (seconds)"),
        (tools_heading, "Tool calls median"),
        (accuracy_heading, "Accuracy pass count"),
    ]
    duration_values: dict[str, dict[str, float | None]] = defaultdict(dict)
    tool_values: dict[str, dict[str, float]] = defaultdict(dict)
    accuracy_values: dict[str, dict[str, float]] = defaultdict(dict)
    for arm in arms:
        for scenario in SCENARIOS:
            rows = grouped[arm].get(scenario, [])
            durations = [float(row["duration_seconds"]) for row in rows if row.get("duration_seconds") is not None]
            duration_values[arm][scenario] = statistics.median(durations) if durations else None
            tool_values[arm][scenario] = statistics.median([row["tool_calls"] for row in rows]) if rows else 0
            accuracy_values[arm][scenario] = sum(row.get("accuracy") == "pass" for row in rows)
    for y, heading in sections:
        parts.append(text(40, y, heading, 18, "bold"))
        parts.append(f'<line x1="40" y1="{y + 15}" x2="{width - 40}" y2="{y + 15}" stroke="#bbbbbb"/>')
    max_duration = max(
        (value for values in duration_values.values() for value in values.values() if value is not None),
        default=1,
    )
    max_tools = max((value for values in tool_values.values() for value in values.values()), default=1)
    for index, scenario in enumerate(SCENARIOS):
        duration_y = duration_start + index * scenario_stride
        tools_y = tools_start + index * scenario_stride
        accuracy_y = accuracy_start + index * scenario_stride
        parts.append(text(55, duration_y + 11, scenario, 14, "bold"))
        parts.append(text(55, tools_y + 11, scenario, 14, "bold"))
        parts.append(text(55, accuracy_y + 11, scenario, 14, "bold"))
        for arm_index, arm in enumerate(arms):
            color = COLORS[arm_index % len(COLORS)]
            offset = arm_index * arm_step
            value = duration_values[arm][scenario]
            if value is None:
                parts.append(text(115, duration_y + offset + 11, "N/A", 12))
            else:
                bar = int(760 * value / max_duration)
                parts.append(
                    f'<rect data-role="bar" data-section="duration" data-scenario="{scenario}" data-arm="{html.escape(arm)}" '
                    f'x="115" y="{duration_y + offset}" width="{bar}" height="{bar_height}" fill="{color}"/>'
                )
                parts.append(text(125 + bar, duration_y + offset + 11, f"{value:.3f}", 12))
            tool_value = tool_values[arm][scenario]
            tool_bar = int(760 * tool_value / max_tools) if max_tools else 0
            parts.append(
                f'<rect data-role="bar" data-section="tools" data-scenario="{scenario}" data-arm="{html.escape(arm)}" '
                f'x="115" y="{tools_y + offset}" width="{tool_bar}" height="{bar_height}" fill="{color}"/>'
            )
            parts.append(text(125 + tool_bar, tools_y + offset + 11, f"{tool_value:g}", 12))
            accuracy_value = accuracy_values[arm][scenario]
            accuracy_bar = int(760 * accuracy_value / 3)
            parts.append(
                f'<rect data-role="bar" data-section="accuracy" data-scenario="{scenario}" data-arm="{html.escape(arm)}" '
                f'x="115" y="{accuracy_y + offset}" width="{accuracy_bar}" height="{bar_height}" fill="{color}"/>'
            )
            parts.append(text(125 + accuracy_bar, accuracy_y + offset + 11, f"{int(accuracy_value)}/3", 12))
    parts.append("</svg>")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(parts) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--title", default="Agent Benchmark Control Baseline")
    args = parser.parse_args()
    render(load(args.inputs), args.output, args.title)
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

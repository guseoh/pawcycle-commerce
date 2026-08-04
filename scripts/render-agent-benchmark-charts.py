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


def load(paths: list[Path]) -> dict[str, list[dict]]:
    grouped: dict[str, list[dict]] = defaultdict(list)
    for path in paths:
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            row = json.loads(line)
            if row.get("record_type") == "result" and row.get("counts_toward_independent_repetition"):
                grouped[row["scenario"]].append(row)
    return grouped


def text(x: int, y: int, value: str, size: int = 14, weight: str = "normal") -> str:
    return f'<text x="{x}" y="{y}" font-family="Arial, sans-serif" font-size="{size}" font-weight="{weight}">{html.escape(value)}</text>'


def render(grouped: dict[str, list[dict]], output: Path, title: str) -> None:
    width, height = 920, 700
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="white"/>',
        text(40, 45, title, 24, "bold"),
        text(40, 72, "Independent records only; missing duration is shown as N/A", 13),
    ]
    sections = [(110, "Duration median (seconds)"), (310, "Tool calls median"), (510, "Accuracy pass count")]
    duration_values: dict[str, float | None] = {}
    tool_values: dict[str, float] = {}
    accuracy_values: dict[str, float] = {}
    for scenario in SCENARIOS:
        rows = grouped.get(scenario, [])
        durations = [float(row["duration_seconds"]) for row in rows if row.get("duration_seconds") is not None]
        duration_values[scenario] = statistics.median(durations) if durations else None
        tool_values[scenario] = statistics.median([row["tool_calls"] for row in rows]) if rows else 0
        accuracy_values[scenario] = sum(row.get("accuracy") == "pass" for row in rows)
    for y, heading in sections:
        parts.append(text(40, y, heading, 18, "bold"))
        parts.append(f'<line x1="40" y1="{y + 15}" x2="880" y2="{y + 15}" stroke="#bbbbbb"/>')
    max_duration = max((value for value in duration_values.values() if value is not None), default=1)
    max_tools = max(tool_values.values(), default=1)
    for index, scenario in enumerate(SCENARIOS):
        row_y = 150 + index * 35
        parts.append(text(55, row_y + 16, scenario, 14, "bold"))
        value = duration_values[scenario]
        if value is None:
            parts.append(text(115, row_y + 16, "N/A", 14))
        else:
            bar = int(600 * value / max_duration)
            parts.append(f'<rect x="115" y="{row_y}" width="{bar}" height="22" fill="#6f7782"/>')
            parts.append(text(125 + bar, row_y + 16, f"{value:.3f}", 13))
        row_y2 = 350 + index * 35
        parts.append(text(55, row_y2 + 16, scenario, 14, "bold"))
        bar2 = int(600 * tool_values[scenario] / max_tools) if max_tools else 0
        parts.append(f'<rect x="115" y="{row_y2}" width="{bar2}" height="22" fill="#6f7782"/>')
        parts.append(text(125 + bar2, row_y2 + 16, f"{tool_values[scenario]:g}", 13))
        row_y3 = 550 + index * 35
        parts.append(text(55, row_y3 + 16, scenario, 14, "bold"))
        bar3 = int(600 * accuracy_values[scenario] / 3)
        parts.append(f'<rect x="115" y="{row_y3}" width="{bar3}" height="22" fill="#6f7782"/>')
        parts.append(text(125 + bar3, row_y3 + 16, f"{int(accuracy_values[scenario])}/3", 13))
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

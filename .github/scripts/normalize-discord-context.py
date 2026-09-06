#!/usr/bin/env python3
"""Normalize Discord context against the current lean PR/task contract."""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.task_id import extract_task_id  # noqa: E402


COLLECTOR_PATH = Path(__file__).with_name("collect-discord-context.py")
COLLECTOR_SPEC = importlib.util.spec_from_file_location("collect_discord_context", COLLECTOR_PATH)
if COLLECTOR_SPEC is None or COLLECTOR_SPEC.loader is None:
    raise RuntimeError("Discord collector를 불러올 수 없음")
COLLECTOR = importlib.util.module_from_spec(COLLECTOR_SPEC)
COLLECTOR_SPEC.loader.exec_module(COLLECTOR)

MISSING = COLLECTOR.MISSING


def _section_map(body: str) -> dict[str, str]:
    visible = COLLECTOR.strip_fenced_code_blocks(COLLECTOR.strip_automatic_summary(body or ""))
    matches = list(re.finditer(r"(?m)^##\s+(.+?)\s*$", visible))
    sections: dict[str, str] = {}
    for index, heading in enumerate(matches):
        name = heading.group(1).strip()
        start = heading.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(visible)
        sections.setdefault(name, visible[start:end].strip())
    return sections


def _label(section: str, label: str) -> str | None:
    match = re.search(
        rf"(?im)^\s*(?:[-*]\s*)?{re.escape(label)}\s*:\s*(.+?)\s*$",
        section or "",
    )
    if not match:
        return None
    value = COLLECTOR.clean_text(match.group(1), 500)
    return None if value == MISSING else value


def _pr_from_payload(payload: dict[str, Any]) -> dict[str, Any] | None:
    pr = payload.get("pull_request")
    return pr if isinstance(pr, dict) else None


def _fetch_pr_if_needed(
    context: dict[str, Any], repository: str, api: Any | None
) -> dict[str, Any] | None:
    number = context.get("number")
    if api is None or not isinstance(number, int):
        return None
    result = api.pull_request(number)
    return result if isinstance(result, dict) else None


def normalize_context(
    context: dict[str, Any],
    payload: dict[str, Any],
    repository: str,
    api: Any | None = None,
) -> dict[str, Any]:
    """Apply generic task-id parsing and the four-section PR contract."""
    pr = _pr_from_payload(payload) or _fetch_pr_if_needed(context, repository, api)
    body = str((pr or {}).get("body") or "")
    title = str((pr or {}).get("title") or context.get("title") or "")
    head = str(((pr or {}).get("head") or {}).get("ref") or context.get("head") or "")
    issue = payload.get("issue") if isinstance(payload.get("issue"), dict) else {}
    issue_text = f"{issue.get('title', '')}\n{issue.get('body', '')}"
    existing = str(context.get("task_id") or "")

    task_id = extract_task_id(body, title, head, issue_text, existing)
    context["task_id"] = task_id or MISSING

    if not body:
        return context

    sections = _section_map(body)
    change = sections.get("변경")
    if change is not None:
        purpose = _label(change, "목적")
        included = _label(change, "포함 범위")
        excluded = _label(change, "제외 범위")
        if purpose:
            context["purpose"] = purpose
        scope_lines = []
        if included:
            scope_lines.append(f"포함 범위: {included}")
        if excluded:
            scope_lines.append(f"제외 범위: {excluded}")
        if scope_lines:
            context["changes"] = COLLECTOR.clean_text("\n".join(scope_lines), 650)

    validation = sections.get("검증")
    if validation is not None:
        executed = _label(validation, "실행 결과")
        not_run = _label(validation, "실패·미실행")
        lines = []
        if executed:
            lines.append(f"실행 결과: {executed}")
        if not_run:
            lines.append(f"실패·미실행: {not_run}")
        if lines:
            context["validation"] = COLLECTOR.clean_text("\n".join(lines), 650)

    risk = sections.get("위험")
    if risk is not None:
        remaining = _label(risk, "남은 위험")
        rollback = _label(risk, "복구 경계")
        lines = []
        if remaining:
            lines.append(f"남은 위험: {remaining}")
        if rollback:
            lines.append(f"복구 경계: {rollback}")
        if lines:
            context["risks"] = COLLECTOR.clean_text("\n".join(lines), 650)

    return context


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--context-file", required=True)
    result.add_argument("--event-path", required=True)
    result.add_argument("--repository", required=True)
    return result


def main() -> int:
    args = parser().parse_args()
    context_path = Path(args.context_file)
    context = json.loads(context_path.read_text(encoding="utf-8"))
    payload = json.loads(Path(args.event_path).read_text(encoding="utf-8"))
    api = COLLECTOR.GitHubApi(
        args.repository,
        os.environ.get("GITHUB_TOKEN", ""),
        os.environ.get("GITHUB_API_URL", "https://api.github.com"),
    )
    normalized = normalize_context(context, payload, args.repository, api)
    context_path.write_text(
        json.dumps(normalized, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Discord context 정규화 완료: task_id={normalized.get('task_id', MISSING)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

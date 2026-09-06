#!/usr/bin/env python3
"""Generic PawCycle task-id parsing without a CI-maintained family allowlist."""

from __future__ import annotations

import re


TASK_ID_PATTERN = r"(?:[A-Z][A-Z0-9]*-)+[0-9]{3}[A-Z]?"
_BOUNDARY = r"A-Za-z0-9_\-\x80-\U0010FFFF"
TASK_ID_RE = re.compile(
    rf"(?<![{_BOUNDARY}])({TASK_ID_PATTERN})(?![{_BOUNDARY}])",
    re.IGNORECASE,
)
TASK_LINE_RE = re.compile(
    rf"(?im)^\s*(?:[-*]\s*)?작업\s*ID\s*:\s*`?({TASK_ID_PATTERN})`?\s*$",
    re.IGNORECASE,
)


def normalize_task_id(value: str) -> str | None:
    candidate = (value or "").strip().strip("`")
    if re.fullmatch(TASK_ID_PATTERN, candidate, re.IGNORECASE):
        return candidate.upper()
    return None


def extract_task_id(body: str = "", *fallbacks: str) -> str | None:
    """Prefer an explicit `작업 ID:` line, then scan bounded fallback text."""
    explicit = TASK_LINE_RE.search(body or "")
    if explicit:
        return explicit.group(1).upper()

    for value in fallbacks:
        match = TASK_ID_RE.search(value or "")
        if match:
            return match.group(1).upper()
    return None

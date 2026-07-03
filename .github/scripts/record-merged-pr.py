#!/usr/bin/env python3
"""Create an Obsidian-friendly Markdown record for a merged pull request."""

from __future__ import annotations

import argparse
import json
import os
import re
import textwrap
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


REPO = "guseoh/pawcycle-commerce"
TASK_ID_PREFIXES = "BOOTSTRAP|PS|ARCH|FOUNDATION|BUG|PERF|OPS|SEC|DOMAIN|API"


def safe_text(value: Any, default: str = "기록 없음") -> str:
    if value is None or value == "":
        return default
    text = str(value).replace("\r\n", "\n").replace("\r", "\n").strip()
    text = text.replace("```", "'''")
    return text or default


def one_line(value: Any, limit: int = 180) -> str:
    text = safe_text(value).replace("\n", " ")
    return text[: limit - 1] + "…" if len(text) > limit else text


def slugify(title: str) -> str:
    slug = title.lower()
    slug = re.sub(r"[^a-z0-9]+", "-", slug)
    slug = re.sub(r"-+", "-", slug).strip("-")
    return slug[:48] or "pull-request"


def task_id_from_text(*values: str) -> str:
    joined = "\n".join(values)
    match = re.search(rf"\b({TASK_ID_PREFIXES})-\d{{3}}\b", joined)
    return match.group(0) if match else "기록 없음"


def github_api(path: str, token: str) -> Any:
    if not token:
        return None
    req = urllib.request.Request(
        f"https://api.github.com{path}",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return json.loads(response.read().decode("utf-8"))
    except (urllib.error.HTTPError, urllib.error.URLError):
        return None


def list_names(items: Any, key: str, limit: int = 20) -> str:
    if not isinstance(items, list) or not items:
        return "기록 없음"
    names = [str(item.get(key, "")) for item in items if isinstance(item, dict) and item.get(key)]
    if not names:
        return "기록 없음"
    rendered = names[:limit]
    suffix = f"\n- 외 {len(names) - limit}개" if len(names) > limit else ""
    return "\n".join(f"- {name}" for name in rendered) + suffix


def summarize_reviews(reviews: Any) -> str:
    if not isinstance(reviews, list) or not reviews:
        return "기록 없음"
    counts: dict[str, int] = {}
    for review in reviews:
        state = str(review.get("state", "UNKNOWN"))
        counts[state] = counts.get(state, 0) + 1
    return "\n".join(f"- {state}: {count}" for state, count in sorted(counts.items()))


def summarize_checks(check_runs: Any) -> str:
    runs = check_runs.get("check_runs") if isinstance(check_runs, dict) else None
    if not runs:
        return "기록 없음"
    lines = []
    for run in runs[:20]:
        name = run.get("name", "unknown")
        conclusion = run.get("conclusion") or run.get("status") or "unknown"
        lines.append(f"- {name}: {conclusion}")
    if len(runs) > 20:
        lines.append(f"- 외 {len(runs) - 20}개")
    return "\n".join(lines)


def build_markdown(event: dict[str, Any], token: str) -> tuple[str, Path]:
    pr = event["pull_request"]
    number = pr["number"]
    title = safe_text(pr.get("title"))
    body = safe_text(pr.get("body"))
    author = safe_text(pr.get("user", {}).get("login"))
    base = safe_text(pr.get("base", {}).get("ref"))
    head = safe_text(pr.get("head", {}).get("ref"))
    merged_at = safe_text(pr.get("merged_at"))
    merge_sha = safe_text(pr.get("merge_commit_sha"))
    url = safe_text(pr.get("html_url"))
    task_id = task_id_from_text(title, body, head)
    year = (merged_at[:4] if re.match(r"\d{4}", merged_at) else str(datetime.now(timezone.utc).year))
    path = Path("docs") / "learning" / "pull-requests" / year / f"PR-{number}-{slugify(title)}.md"

    files = github_api(f"/repos/{REPO}/pulls/{number}/files", token)
    reviews = github_api(f"/repos/{REPO}/pulls/{number}/reviews", token)
    checks = github_api(f"/repos/{REPO}/commits/{merge_sha}/check-runs", token) if merge_sha != "기록 없음" else None

    labels = pr.get("labels") or []
    label_names = [label.get("name") for label in labels if isinstance(label, dict) and label.get("name")]
    label_yaml = "\n".join(f"  - {label}" for label in label_names) if label_names else "  - 기록 없음"

    purpose = "기록 없음"
    changes = "기록 없음"
    follow_up = "기록 없음"
    if body != "기록 없음":
        purpose = one_line(body, 500)

    markdown = f"""---
type: pull-request
repository: {REPO}
pr: {number}
status: merged
taskId: {task_id}
author: {author}
base: {base}
head: {head}
mergedAt: {merged_at}
mergeCommit: {merge_sha}
labels:
{label_yaml}
tags:
  - pawcycle
  - pull-request
  - learning
---

# PR #{number} {title}

## 작업 목적

{purpose}

## 주요 변경

{changes}

## 변경 파일

{list_names(files, "filename")}

## 리뷰 결과

{summarize_reviews(reviews)}

## CI 및 검증

{summarize_checks(checks)}

## 주요 결정

기록 없음

## 알려진 위험

기록 없음

## 후속 작업

{follow_up}

## 연결된 Issue

기록 없음

## GitHub 링크

{url}
"""
    return textwrap.dedent(markdown), path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event-path", default=os.environ.get("GITHUB_EVENT_PATH", ""))
    parser.add_argument("--output-root", default=".")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not args.event_path:
        raise SystemExit("GITHUB_EVENT_PATH 또는 --event-path가 필요합니다.")

    with open(args.event_path, "r", encoding="utf-8") as handle:
        event = json.load(handle)

    pr = event.get("pull_request", {})
    if not pr.get("merged"):
        print("PR이 병합되지 않아 기록을 생성하지 않습니다.")
        return 0

    markdown, relative_path = build_markdown(event, os.environ.get("GITHUB_TOKEN", ""))
    output = Path(args.output_root) / relative_path
    if output.exists():
        print(f"이미 기록이 존재합니다: {output}")
        return 0

    if args.dry_run:
        print(markdown)
        return 0

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(markdown, encoding="utf-8")
    print(output.as_posix())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

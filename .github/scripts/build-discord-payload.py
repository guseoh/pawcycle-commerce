#!/usr/bin/env python3
"""Build a bounded Discord report payload from normalized collaboration context."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


MISSING = "기록 없음"
UNKNOWN = "확인 불가"
COLORS = {
    "pr_ready": 0x3498DB,
    "pr_draft": 0x95A5A6,
    "pr_synchronize": 0x3498DB,
    "pr_review_requested": 0x3498DB,
    "review_approved": 0x2ECC71,
    "changes_requested": 0xE67E22,
    "ci_success": 0x2ECC71,
    "ci_failure": 0xE74C3C,
    "pr_merged": 0x9B59B6,
    "issue_opened": 0x1ABC9C,
    "issue_closed": 0x95A5A6,
    "main_updated": 0x9B59B6,
    "connection_test": 0x3498DB,
}
TITLES = {
    "pr_ready": "PR 리뷰 준비",
    "pr_draft": "Draft PR 등록",
    "pr_synchronize": "PR 새 커밋 반영",
    "pr_review_requested": "PR 리뷰어 지정",
    "review_approved": "PR 리뷰 승인",
    "changes_requested": "PR 변경 요청",
    "ci_success": "Repository Validation 성공",
    "ci_failure": "Repository Validation 미통과",
    "pr_merged": "PR 병합 완료",
    "issue_opened": "Issue 등록",
    "issue_closed": "Issue 완료",
    "main_updated": "main 반영 완료",
    "connection_test": "Discord 협업 알림 Preview",
}
CONTROL = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")


def clean(value: Any, limit: int = 700, multiline: bool = True) -> str:
    if value is None or value == "":
        return MISSING
    text = CONTROL.sub("", str(value)).replace("@everyone", "@\u200beveryone").replace("@here", "@\u200bhere")
    if not multiline:
        text = " ".join(text.split())
    else:
        text = "\n".join(line.rstrip() for line in text.replace("\r", "").split("\n")).strip()
    if not text:
        return MISSING
    return text if len(text) <= limit else text[: limit - 1] + "…"


def display(value: Any, limit: int = 700) -> str:
    if isinstance(value, bool):
        return "예" if value else "아니요"
    return clean(value, limit)


def short_sha(value: Any) -> str:
    text = display(value, 80)
    return text if text in (MISSING, UNKNOWN) else text[:7]


def field(name: str, value: Any, inline: bool = False, limit: int = 700) -> dict[str, Any]:
    return {"name": clean(name, 80, False), "value": display(value, limit), "inline": inline}


def format_change(context: dict[str, Any]) -> str:
    return f"파일 {display(context.get('changed_files'), 30)} · +{display(context.get('additions'), 30)} / -{display(context.get('deletions'), 30)}"


def format_jobs(value: Any) -> str:
    if not isinstance(value, list):
        return display(value, 620)
    if not value:
        return MISSING
    selected = value[:10]
    lines = [f"{'✅' if item.get('conclusion') in ('success', 'skipped', 'neutral') else '❌'} {clean(item.get('name'), 90, False)}: {clean(item.get('conclusion'), 30, False)}" for item in selected]
    if len(value) > len(selected):
        lines.append(f"… 외 {len(value) - len(selected)}건")
    return clean("\n".join(lines), 620)


def format_failures(value: Any) -> str:
    if not isinstance(value, list):
        return display(value, 620)
    if not value:
        return "없음"
    selected = value[:6]
    lines = [f"{clean(item.get('name'), 80, False)} · {clean(item.get('step'), 100, False)} ({clean(item.get('conclusion'), 30, False)})" for item in selected]
    if len(value) > len(selected):
        lines.append(f"… 외 {len(value) - len(selected)}건")
    return clean("\n".join(lines), 620)


def format_reviews(value: Any) -> str:
    if not isinstance(value, list):
        return display(value, 620)
    if not value:
        return MISSING
    selected = value[:10]
    lines = [f"{clean(item.get('reviewer') or item.get('user'), 80, False)}: {clean(item.get('state'), 40, False)}" for item in selected]
    if len(value) > len(selected):
        lines.append(f"… 외 {len(value) - len(selected)}건")
    return clean("\n".join(lines), 620)


def embed(title: str, color: int, fields: list[dict[str, Any]], context: dict[str, Any], description: Any = None) -> dict[str, Any]:
    result: dict[str, Any] = {
        "title": clean(title, 180, False),
        "color": color,
        "fields": fields,
        "footer": {"text": "PawCycle Commerce · Collaboration Report"},
        "timestamp": clean(context.get("timestamp") or datetime.now(timezone.utc).isoformat(), 40, False),
    }
    url = context.get("url")
    if url and url not in (MISSING, UNKNOWN):
        result["url"] = clean(url, 500, False)
    if description:
        result["description"] = display(description, 500)
    return result


def build_payload(context: dict[str, Any]) -> dict[str, Any]:
    event = str(context.get("event") or "connection_test")
    if event not in TITLES:
        raise ValueError(f"지원하지 않는 event: {event}")
    color = COLORS[event]
    number = context.get("number")
    suffix = "" if number in (None, "", MISSING, UNKNOWN) else f" · #{clean(number, 20, False)}"
    preview = "[PREVIEW] " if context.get("preview") else ""
    summary = [
        field("작업 ID", context.get("task_id"), True, 80),
        field("역할", context.get("role"), True, 80),
        field("작업자", context.get("actor"), True, 80),
        field("브랜치", f"{display(context.get('head'), 90)} → {display(context.get('base'), 90)}", True, 190),
        field("커밋", short_sha(context.get("sha")), True, 30),
        field("변경 규모", format_change(context), True, 120),
        field("작업 목적", context.get("purpose"), False, 620),
    ]
    embeds = [embed(f"{preview}{TITLES[event]}{suffix}", color, summary, context, context.get("title"))]

    detailed = event in ("pr_ready", "pr_merged", "review_approved", "changes_requested") or event.startswith("ci_")
    if detailed:
        detail_fields = [
            field("주요 변경", context.get("changes"), False, 620),
            field("처리 과정", context.get("process"), False, 620),
            field("검증 결과", context.get("validation"), False, 620),
            field("QA 결과", context.get("qa"), False, 420),
        ]
        if event.startswith("ci_"):
            detail_fields.extend([field("Job 결과", format_jobs(context.get("ci_jobs")), False, 620), field("실패 Job / Step", format_failures(context.get("failed_jobs")), False, 620)])
        if event in ("review_approved", "changes_requested") and context.get("review_body"):
            detail_fields.append(field("리뷰 의견", context.get("review_body"), False, 420))
        if event in ("review_approved", "changes_requested"):
            detail_fields.append(field("CI 상태", context.get("ci_status"), True, 80))
        detail_fields.extend([field("리뷰 상태", format_reviews(context.get("reviews")), False, 500), field("미해결 스레드", context.get("unresolved_threads"), True, 40)])
        embeds.append(embed("처리·검증·리뷰", color, detail_fields, context))

        action_fields = [
            field("현재 상태", context.get("status"), True, 100),
            field("병합 가능", context.get("mergeable"), True, 60),
            field("남은 위험", context.get("risks"), False, 500),
            field("다음 작업", context.get("next_action"), False, 500),
        ]
        actions_url = context.get("actions_url")
        if actions_url and actions_url not in (MISSING, UNKNOWN):
            action_fields.append(field("Actions", actions_url, False, 500))
        embeds.append(embed("상태와 다음 작업", color, action_fields, context))
    else:
        embeds[0]["fields"].extend([field("현재 상태", context.get("status"), True, 100), field("다음 작업", context.get("next_action"), False, 500)])

    payload = {"username": "PawCycle Bot", "allowed_mentions": {"parse": []}, "embeds": embeds}
    if len(json.dumps(payload, ensure_ascii=False)) > 6000:
        for item in payload["embeds"]:
            for item_field in item.get("fields", []):
                item_field["value"] = clean(item_field["value"], 280)
            if "description" in item:
                item["description"] = clean(item["description"], 280)
    if len(json.dumps(payload, ensure_ascii=False)) > 6000:
        raise ValueError("Discord payload 6000자 제한을 만족할 수 없음")
    return payload


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--context-file", required=True, type=Path)
    args = parser.parse_args()
    context = json.loads(args.context_file.read_text(encoding="utf-8"))
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    print(json.dumps(build_payload(context), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

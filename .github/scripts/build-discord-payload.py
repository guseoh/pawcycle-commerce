#!/usr/bin/env python3
"""Build a Discord webhook payload for PawCycle collaboration events."""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone


COLORS = {
    "info": 0x3498DB,
    "approved": 0x2ECC71,
    "changes_requested": 0xE67E22,
    "success": 0x2ECC71,
    "failure": 0xE74C3C,
    "merged": 0x9B59B6,
    "issue_opened": 0x1ABC9C,
    "issue_closed": 0x95A5A6,
    "warning": 0xF1C40F,
}

EVENTS = {
    "pr_opened": {
        "title": "🔍 PR 리뷰 요청",
        "description": "변경 사항이 리뷰를 기다리고 있습니다.",
        "color": "info",
        "footer": "PawCycle Commerce · Pull Request",
    },
    "review_approved": {
        "title": "✅ PR 리뷰 승인",
        "description": "PR이 리뷰 승인을 받았습니다.",
        "color": "approved",
        "footer": "PawCycle Commerce · Pull Request",
    },
    "changes_requested": {
        "title": "🛠️ 수정 요청",
        "description": "PR에 변경 요청이 등록되었습니다.",
        "color": "changes_requested",
        "footer": "PawCycle Commerce · Pull Request",
    },
    "review_commented": {
        "title": "💬 PR 리뷰 코멘트",
        "description": "PR에 리뷰 코멘트가 등록되었습니다.",
        "color": "info",
        "footer": "PawCycle Commerce · Pull Request",
    },
    "ci_success": {
        "title": "✅ CI 검증 성공",
        "description": "모든 검증 단계가 통과했습니다.",
        "color": "success",
        "footer": "PawCycle Commerce · GitHub Actions",
    },
    "ci_failure": {
        "title": "❌ CI 검증 실패",
        "description": "검증 중 오류가 발생했습니다.",
        "color": "failure",
        "footer": "PawCycle Commerce · GitHub Actions",
    },
    "pr_merged": {
        "title": "🚀 PR 병합 완료",
        "description": "변경 사항이 main에 반영되었습니다.",
        "color": "merged",
        "footer": "PawCycle Commerce · Pull Request",
    },
    "pr_closed": {
        "title": "⚪ PR 종료",
        "description": "PR이 병합 없이 종료되었습니다.",
        "color": "warning",
        "footer": "PawCycle Commerce · Pull Request",
    },
    "issue_opened": {
        "title": "📌 새 Issue 등록",
        "description": "새로운 작업 또는 결정 사항이 등록되었습니다.",
        "color": "issue_opened",
        "footer": "PawCycle Commerce · Issue",
    },
    "issue_closed": {
        "title": "🏁 Issue 완료",
        "description": "Issue가 완료 상태로 변경되었습니다.",
        "color": "issue_closed",
        "footer": "PawCycle Commerce · Issue",
    },
    "main_updated": {
        "title": "📦 main 반영 완료",
        "description": "새 커밋이 기준 브랜치에 반영되었습니다.",
        "color": "merged",
        "footer": "PawCycle Commerce · GitHub Actions",
    },
    "test": {
        "title": "🧪 PawCycle Commerce 알림 연결 테스트",
        "description": "Discord Webhook 연결 테스트입니다.",
        "color": "info",
        "footer": "PawCycle Commerce · GitHub Actions",
    },
}


def now_utc() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def clean(value: str | None, limit: int = 240) -> str:
    if value is None or value == "":
        return "기록 없음"
    value = str(value).replace("\r", " ").replace("\n", " ").strip()
    value = value.replace("@everyone", "@\u200beveryone").replace("@here", "@\u200bhere")
    if len(value) > limit:
        return value[: max(0, limit - 1)] + "…"
    return value


def short_sha(value: str | None) -> str:
    value = clean(value, 80)
    if value == "기록 없음":
        return value
    return value[:7]


def add_field(fields: list[dict[str, object]], name: str, value: str | None, inline: bool = True) -> None:
    fields.append({"name": name, "value": clean(value), "inline": inline})


def build_payload(args: argparse.Namespace) -> dict[str, object]:
    event = EVENTS[args.event]
    number = clean(args.number, 40)
    title = event["title"]
    if number != "기록 없음":
        title = f"{title} · #{number}" if args.event.startswith(("pr_", "issue_")) else f"{title} · {number}"

    fields: list[dict[str, object]] = []
    add_field(fields, "저장소", args.repository)
    add_field(fields, "작업 ID", args.task_id)
    add_field(fields, "작업자", args.actor)
    add_field(fields, "브랜치", args.branch)
    add_field(fields, "대상", args.target)
    add_field(fields, "커밋", short_sha(args.sha))
    add_field(fields, "상태", args.status)
    add_field(fields, "검증", args.validation, inline=False)

    if args.extra:
        add_field(fields, "추가 정보", args.extra, inline=False)

    return {
        "username": "PawCycle Bot",
        "allowed_mentions": {"parse": []},
        "embeds": [
            {
                "title": title,
                "url": clean(args.url, 500),
                "description": clean(args.summary or event["description"], 320),
                "color": COLORS[event["color"]],
                "fields": fields[:8],
                "footer": {"text": event["footer"]},
                "timestamp": args.timestamp or now_utc(),
            }
        ],
    }


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--event", required=True, choices=sorted(EVENTS))
    p.add_argument("--repository", default="guseoh/pawcycle-commerce")
    p.add_argument("--number", default="")
    p.add_argument("--task-id", default="")
    p.add_argument("--actor", default="")
    p.add_argument("--branch", default="")
    p.add_argument("--target", default="")
    p.add_argument("--sha", default="")
    p.add_argument("--status", default="")
    p.add_argument("--validation", default="")
    p.add_argument("--summary", default="")
    p.add_argument("--extra", default="")
    p.add_argument("--url", default="")
    p.add_argument("--timestamp", default="")
    return p


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    args = parser().parse_args()
    print(json.dumps(build_payload(args), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

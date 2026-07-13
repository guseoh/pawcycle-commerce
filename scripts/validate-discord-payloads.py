#!/usr/bin/env python3
"""Validate normalized Discord fixtures and the single CI notification path."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
FIXTURE_DIR = ROOT / ".github" / "fixtures" / "discord"
BUILDER = ROOT / ".github" / "scripts" / "build-discord-payload.py"
REQUIRED = {
    "pr-opened.json", "pr-draft.json", "pr-synchronize.json", "review-approved.json",
    "changes-requested.json", "ci-success.json", "ci-failure.json", "pr-merged.json",
    "issue-opened.json", "issue-closed.json", "main-updated.json", "manual-test.json",
    "missing-task.json", "long-input.json", "long-review.json", "api-fallback.json",
}


def validate_payload(payload: dict[str, Any], fixture: Path, context: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    raw = json.dumps(payload, ensure_ascii=False)
    if payload.get("allowed_mentions") != {"parse": []}:
        errors.append("allowed_mentions 제한이 없음")
    embeds = payload.get("embeds")
    if not isinstance(embeds, list) or not 1 <= len(embeds) <= 3:
        errors.append("embed 개수가 1~3 범위를 벗어남")
        return [f"{fixture.name}: {error}" for error in errors]
    event = str(context.get("event", ""))
    detailed = event in ("pr_ready", "pr_merged", "review_approved", "changes_requested") or event.startswith("ci_")
    if detailed and len(embeds) != 3:
        errors.append("상세 이벤트가 3개 embed 보고서를 만들지 않음")
    for index, embed in enumerate(embeds):
        if not isinstance(embed, dict):
            errors.append(f"embed {index + 1} 형식 오류")
            continue
        for key in ("title", "color", "fields", "footer", "timestamp"):
            if key not in embed:
                errors.append(f"embed {index + 1} 필수 키 누락: {key}")
        fields = embed.get("fields")
        if not isinstance(fields, list) or not 1 <= len(fields) <= 25:
            errors.append(f"embed {index + 1} field 개수 오류")
        elif any(len(str(item.get("name", ""))) > 256 or len(str(item.get("value", ""))) > 1024 for item in fields if isinstance(item, dict)):
            errors.append(f"embed {index + 1} field 길이 초과")
        if len(str(embed.get("title", ""))) > 256 or len(str(embed.get("description", ""))) > 4096:
            errors.append(f"embed {index + 1} title 또는 description 길이 초과")
    names = {str(item.get("name")) for embed in embeds for item in embed.get("fields", []) if isinstance(item, dict)}
    common = {"작업 ID", "역할", "작업자", "브랜치", "커밋", "변경 규모", "작업 목적", "현재 상태", "다음 작업"}
    required_names = common | ({"주요 변경", "처리 과정", "검증 결과", "리뷰 상태", "미해결 스레드", "병합 가능", "남은 위험"} if detailed else set())
    if event.startswith("ci_"):
        required_names |= {"Job 결과", "실패 Job / Step", "Actions"}
    if event in ("review_approved", "changes_requested"):
        required_names |= {"리뷰 의견", "CI 상태"}
    if missing_names := required_names - names:
        errors.append("필수 section 누락: " + ", ".join(sorted(missing_names)))
    for required_text in context.get("_expect", {}).get("contains", []):
        if required_text not in raw:
            errors.append(f"필수 텍스트 누락: {required_text}")
    if "discord.com/api/webhooks" in raw or "DISCORD_WEBHOOK_URL" in raw or "github_pat_" in raw or "ghp_" in raw:
        errors.append("Webhook 정보가 payload에 포함됨")
    if "@everyone" in raw or "@here" in raw:
        errors.append("멘션이 제한 없이 포함됨")
    if len(raw) > 6000:
        errors.append("payload가 6000자 제한을 초과함")
    return [f"{fixture.name}: {error}" for error in errors]


def validate_workflow() -> list[str]:
    workflows = list((ROOT / ".github" / "workflows").glob("notify-*.yml"))
    count = sum(path.read_text(encoding="utf-8").count("workflow_run:") for path in workflows)
    errors = []
    if count != 1:
        errors.append(f"Repository Validation 알림 workflow_run 경로가 {count}개임")
    if (ROOT / ".github" / "workflows" / "notify-ci-result.yml").exists():
        errors.append("중복 notify-ci-result.yml이 남아 있음")
    return errors


def main() -> int:
    errors = validate_workflow()
    fixtures = sorted(FIXTURE_DIR.glob("*.json"))
    missing = REQUIRED - {path.name for path in fixtures}
    if missing:
        errors.append("필수 fixture 누락: " + ", ".join(sorted(missing)))
    for fixture in fixtures:
        context = json.loads(fixture.read_text(encoding="utf-8"))
        result = subprocess.run([sys.executable, str(BUILDER), "--context-file", str(fixture)], cwd=ROOT, text=True, encoding="utf-8", capture_output=True)
        if result.returncode:
            errors.append(f"{fixture.name}: builder 실패: {result.stderr.strip()}")
            continue
        errors.extend(validate_payload(json.loads(result.stdout), fixture, context))
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Discord normalized payload fixtures OK: {len(fixtures)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

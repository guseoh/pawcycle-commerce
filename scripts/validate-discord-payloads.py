#!/usr/bin/env python3
"""Validate normalized Discord fixtures and the single CI notification path."""

from __future__ import annotations

import importlib.util
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
FIXTURE_DIR = ROOT / ".github" / "fixtures" / "discord"
BUILDER = ROOT / ".github" / "scripts" / "build-discord-payload.py"
LIMITS_PATH = ROOT / ".github" / "scripts" / "discord-payload-limits.py"
LIMITS_SPEC = importlib.util.spec_from_file_location("discord_payload_limits", LIMITS_PATH)
if LIMITS_SPEC is None or LIMITS_SPEC.loader is None:
    raise RuntimeError("Discord payload limit helper를 불러올 수 없음")
limits = importlib.util.module_from_spec(LIMITS_SPEC)
LIMITS_SPEC.loader.exec_module(limits)
REQUIRED = {
    "pr-opened.json", "pr-draft.json", "pr-synchronize.json", "review-approved.json",
    "changes-requested.json", "ci-success.json", "ci-failure.json", "pr-merged.json",
    "ci-cancelled.json", "ci-timed-out.json", "ci-neutral.json", "ci-skipped.json", "ci-unknown.json",
    "issue-opened.json", "issue-closed.json", "manual-test.json",
    "missing-task.json", "long-input.json", "long-review.json", "api-fallback.json",
}
SENSITIVE_ASSIGNMENT = re.compile(
    r'''(?i)["']?(?:password|token|secret|client[_-]?secret|api[_-]?key|apikey|access[_-]?key|aws_access_key_id|aws_secret_access_key|private_key)["']?\s*[:=]\s*(?P<value>"[^"\r\n]*"|'[^'\r\n]*'|[^\s,;}\]]+)'''
)


def contains_unredacted_secret(raw: str) -> bool:
    normalized = raw.replace(r'\"', '"')
    if re.search(r"https://(?:canary\.)?(?:discord(?:app)?\.com)/api/webhooks/", normalized, re.IGNORECASE):
        return True
    if re.search(r"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b", normalized):
        return True
    if re.search(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b", normalized):
        return True
    if re.search(r"-----BEGIN [^-\r\n]*PRIVATE KEY-----", normalized, re.IGNORECASE):
        return True
    return any(
        not match.group("value").strip("\"'").startswith("[REDACTED")
        for match in SENSITIVE_ASSIGNMENT.finditer(normalized)
    )


def validate_payload(payload: dict[str, Any], fixture: Path, context: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    raw = json.dumps(payload, ensure_ascii=False)
    if not isinstance(payload, dict):
        return [f"{fixture.name}: payload 최상위 형식 오류"]
    if payload.get("allowed_mentions") != {"parse": []}:
        errors.append("allowed_mentions 제한이 없음")
    embeds = payload.get("embeds")
    if not isinstance(embeds, list):
        errors.append("embeds 형식 오류")
        return [f"{fixture.name}: {error}" for error in errors]
    if not 1 <= len(embeds) <= limits.MAX_EMBEDS:
        errors.append(f"embed 개수가 1~{limits.MAX_EMBEDS} 범위를 벗어남")
    event = str(context.get("event", ""))
    detailed = event in ("pr_ready", "pr_merged", "review_approved", "changes_requested") or event.startswith("ci_")
    if detailed and len(embeds) != 3:
        errors.append("상세 이벤트가 3개 embed 보고서를 만들지 않음")
    if detailed and isinstance(embeds, list) and len(embeds) == 3:
        titles = [item.get("title") for item in embeds if isinstance(item, dict)]
        if titles[1:] != ["🔍 처리·검증·리뷰", "🚦 상태와 다음 작업"]:
            errors.append("상세 embed title 이모지 계약 불일치")
        if len(titles) != len(set(titles)):
            errors.append("embed title 중복")
    for index, embed in enumerate(embeds):
        if not isinstance(embed, dict):
            errors.append(f"embed {index + 1} 형식 오류")
            continue
        for key in ("title", "color", "fields", "footer", "timestamp"):
            if key not in embed:
                errors.append(f"embed {index + 1} 필수 키 누락: {key}")
        fields = embed.get("fields")
        if not isinstance(fields, list):
            errors.append(f"embed {index + 1} fields 형식 오류")
            fields = []
        elif not 1 <= len(fields) <= limits.MAX_FIELDS:
            errors.append(f"embed {index + 1} field 개수 오류")
        for field_index, item in enumerate(fields):
            if not isinstance(item, dict):
                errors.append(f"embed {index + 1} field {field_index + 1} 형식 오류")
                continue
            if len(str(item.get("name", ""))) > limits.MAX_FIELD_NAME:
                errors.append(f"embed {index + 1} field {field_index + 1} name 길이 초과")
            if len(str(item.get("value", ""))) > limits.MAX_FIELD_VALUE:
                errors.append(f"embed {index + 1} field {field_index + 1} value 길이 초과")
        if len(str(embed.get("title", ""))) > limits.MAX_TITLE:
            errors.append(f"embed {index + 1} title 길이 초과")
        if len(str(embed.get("description", ""))) > limits.MAX_DESCRIPTION:
            errors.append(f"embed {index + 1} description 길이 초과")
        footer = embed.get("footer")
        if not isinstance(footer, dict):
            errors.append(f"embed {index + 1} footer 형식 오류")
        elif len(str(footer.get("text", ""))) > limits.MAX_FOOTER_TEXT:
            errors.append(f"embed {index + 1} footer text 길이 초과")
        author = embed.get("author")
        if author is not None and not isinstance(author, dict):
            errors.append(f"embed {index + 1} author 형식 오류")
        elif isinstance(author, dict) and len(str(author.get("name", ""))) > limits.MAX_AUTHOR_NAME:
            errors.append(f"embed {index + 1} author name 길이 초과")
    names = {
        str(item.get("name"))
        for embed in embeds if isinstance(embed, dict)
        for item in (embed.get("fields") if isinstance(embed.get("fields"), list) else [])
        if isinstance(item, dict)
    }
    common = {"🆔 작업 ID", "🧑‍💻 역할", "👤 작업자", "🌿 브랜치", "🔖 커밋", "📊 변경 규모", "🎯 작업 목적", "📌 현재 상태", "➡️ 다음 작업"}
    required_names = common | ({"🧩 주요 변경", "🛠️ 처리 과정", "🧪 검증 결과", "👀 리뷰 상태", "💬 미해결 스레드", "🔀 병합 가능", "⚠️ 남은 위험"} if detailed else set())
    if event.startswith("ci_"):
        required_names |= {"📋 Job 결과", "❌ 실패 Job / Step", "🔗 Actions"}
    if event in ("review_approved", "changes_requested"):
        required_names |= {"리뷰 의견", "CI 상태"}
    if missing_names := required_names - names:
        errors.append("필수 section 누락: " + ", ".join(sorted(missing_names)))
    for required_text in context.get("_expect", {}).get("contains", []):
        if required_text not in raw:
            errors.append(f"필수 텍스트 누락: {required_text}")
    if contains_unredacted_secret(raw):
        errors.append("마스킹되지 않은 Secret 의심 값이 payload에 포함됨")
    if "@everyone" in raw or "@here" in raw:
        errors.append("멘션이 제한 없이 포함됨")
    if limits.payload_text_length(payload) > limits.MAX_TOTAL_TEXT:
        errors.append(f"embed 텍스트 합계가 {limits.MAX_TOTAL_TEXT}자 제한을 초과함")
    return [f"{fixture.name}: {error}" for error in errors]


def yaml_top_level_on_entries(text: str) -> list[str]:
    lines = text.splitlines()
    entries: list[str] = []
    in_on = False
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(line) - len(line.lstrip(" "))
        if indent == 0:
            if in_on:
                break
            in_on = stripped == "on:"
            continue
        if in_on and indent == 2 and stripped.endswith(":"):
            entries.append(stripped[:-1])
    return entries


def yaml_step_blocks(text: str) -> list[list[str]]:
    lines = text.splitlines()
    blocks: list[list[str]] = []
    current: list[str] = []
    step_indent: int | None = None
    for line in lines:
        stripped = line.lstrip(" ")
        indent = len(line) - len(stripped)
        if stripped.startswith("- name:") or stripped.startswith("- uses:"):
            if current:
                blocks.append(current)
            current = [line]
            step_indent = indent
        elif current:
            if stripped and not stripped.startswith("#") and step_indent is not None and indent <= step_indent:
                blocks.append(current)
                current = []
                step_indent = None
            else:
                current.append(line)
    if current:
        blocks.append(current)
    return blocks


def validate_workflow() -> list[str]:
    workflows = list((ROOT / ".github" / "workflows").glob("notify-*.yml"))
    errors = []
    workflow_run_paths = [
        path.name for path in workflows
        if yaml_top_level_on_entries(path.read_text(encoding="utf-8")).count("workflow_run")
    ]
    collaboration_path = ROOT / ".github" / "workflows" / "notify-collaboration.yml"
    collaboration = collaboration_path.read_text(encoding="utf-8")
    if yaml_top_level_on_entries(collaboration).count("workflow_run") != 1:
        errors.append("notify-collaboration.yml top-level on에 workflow_run이 정확히 하나가 아님")
    if workflow_run_paths != ["notify-collaboration.yml"]:
        errors.append("Repository Validation workflow_run trigger가 단일 협업 알림 경로가 아님")
    if (ROOT / ".github" / "workflows" / "notify-ci-result.yml").exists():
        errors.append("중복 notify-ci-result.yml이 남아 있음")
    checkout_blocks = [block for block in yaml_step_blocks(collaboration) if any("uses: actions/checkout@" in line for line in block)]
    trusted_ref = "ref: ${{ github.event.repository.default_branch }}"
    if len(checkout_blocks) != 1 or not any(trusted_ref in line.strip() for line in checkout_blocks[0]):
        errors.append("실제 checkout step이 신뢰된 기본 브랜치를 사용하지 않음")
    if any("github.ref" in line for block in checkout_blocks for line in block):
        errors.append("checkout step에 github.ref 조건이 남아 있음")
    collector_blocks = [block for block in yaml_step_blocks(collaboration) if any("collect-discord-context.py" in line for line in block)]
    if not collector_blocks or not any(
        "if [ ! -f .github/scripts/collect-discord-context.py ]" in "\n".join(block) and "exit 1" in "\n".join(block)
        for block in collector_blocks
    ):
        errors.append("collector 누락이 실제 command step에서 실패로 처리되지 않음")
    contract_blocks = [block for block in yaml_step_blocks(collaboration) if any("discord-message-contract.py" in line for line in block)]
    sender_blocks = [block for block in yaml_step_blocks(collaboration) if any("send-discord-notification.py" in line for line in block)]
    if not contract_blocks or not sender_blocks:
        errors.append("payload 계약 검증 또는 sender step이 없음")
    elif yaml_step_blocks(collaboration).index(contract_blocks[0]) > yaml_step_blocks(collaboration).index(sender_blocks[0]):
        errors.append("payload 계약 검증이 sender보다 먼저 실행되지 않음")
    if not sender_blocks or not all(value in "\n".join(sender_blocks[0]) for value in ("--wait-for-message", "--context-file")):
        errors.append("sender가 wait mode와 context file을 사용하지 않음")
    if sender_blocks and "DISCORD_WEBHOOK_URL" not in "\n".join(sender_blocks[0]):
        errors.append("sender step에 Discord Webhook Secret이 없음")
    non_sender_secret_blocks = [block for block in yaml_step_blocks(collaboration) if block not in sender_blocks and "DISCORD_WEBHOOK_URL" in "\n".join(block)]
    if non_sender_secret_blocks:
        errors.append("Discord Webhook Secret이 sender 외 step에 전달됨")
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

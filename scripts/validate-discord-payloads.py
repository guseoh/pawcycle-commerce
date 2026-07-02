#!/usr/bin/env python3
"""Validate generated Discord payloads from local fixtures."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FIXTURE_DIR = ROOT / ".github" / "fixtures" / "discord"
BUILDER = ROOT / ".github" / "scripts" / "build-discord-payload.py"


def validate_payload(payload: dict[str, object], fixture: Path) -> list[str]:
    errors: list[str] = []
    if payload.get("allowed_mentions") != {"parse": []}:
        errors.append("allowed_mentions 제한이 없음")
    embeds = payload.get("embeds")
    if not isinstance(embeds, list) or len(embeds) != 1:
        errors.append("embeds가 정확히 1개가 아님")
        return errors
    embed = embeds[0]
    if not isinstance(embed, dict):
        errors.append("embed 형식이 잘못됨")
        return errors
    for key in ("title", "description", "color", "fields", "footer", "timestamp"):
        if key not in embed:
            errors.append(f"embed 필수 키 누락: {key}")
    if not isinstance(embed.get("color"), int):
        errors.append("color가 정수값이 아님")
    fields = embed.get("fields")
    if not isinstance(fields, list) or not 1 <= len(fields) <= 8:
        errors.append("field 개수가 1~8 범위를 벗어남")
    if "discord.com/api/webhooks" in json.dumps(payload):
        errors.append("Webhook URL이 payload에 포함됨")
    if "@everyone" in json.dumps(payload) or "@here" in json.dumps(payload):
        errors.append("멘션이 제한 없이 포함됨")
    if len(json.dumps(payload, ensure_ascii=False)) > 6000:
        errors.append("payload가 Discord Embed 권장 크기를 초과함")
    return [f"{fixture.name}: {error}" for error in errors]


def main() -> int:
    errors: list[str] = []
    fixtures = sorted(FIXTURE_DIR.glob("*.json"))
    if not fixtures:
        print("Discord fixture가 없습니다.", file=sys.stderr)
        return 1

    for fixture in fixtures:
        data = json.loads(fixture.read_text(encoding="utf-8"))
        args = [sys.executable, str(BUILDER)]
        for key, value in data.items():
            args.extend([f"--{key.replace('_', '-')}", str(value)])
        result = subprocess.run(
            args,
            cwd=ROOT,
            check=True,
            text=True,
            encoding="utf-8",
            capture_output=True,
        )
        payload = json.loads(result.stdout)
        errors.extend(validate_payload(payload, fixture))

    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1

    print(f"Discord payload fixtures OK: {len(fixtures)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

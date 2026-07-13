#!/usr/bin/env python3
"""Validate Discord payload and created-message embed contracts safely."""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
from pathlib import Path
from typing import Any


LIMITS_PATH = Path(__file__).with_name("discord-payload-limits.py")
LIMITS_SPEC = importlib.util.spec_from_file_location("discord_payload_limits_contract", LIMITS_PATH)
if LIMITS_SPEC is None or LIMITS_SPEC.loader is None:
    raise RuntimeError("Discord payload limit helper를 불러올 수 없음")
limits = importlib.util.module_from_spec(LIMITS_SPEC)
LIMITS_SPEC.loader.exec_module(limits)

DETAILED_EVENTS = frozenset({"pr_ready", "pr_merged", "review_approved", "changes_requested"})


def is_detailed_event(event: str) -> bool:
    return event in DETAILED_EVENTS or event.startswith("ci_")


def expected_embed_count(event: str) -> int:
    return 3 if is_detailed_event(event) else 1


def payload_embeds(payload: Any) -> list[dict[str, Any]]:
    if not isinstance(payload, dict):
        raise ValueError("Discord payload가 object가 아님")
    embeds = payload.get("embeds")
    if not isinstance(embeds, list):
        raise ValueError("Discord payload embeds가 list가 아님")
    if not all(isinstance(item, dict) for item in embeds):
        raise ValueError("Discord payload embed가 object가 아님")
    return embeds


def embed_titles(embeds: list[dict[str, Any]]) -> list[str]:
    titles = [item.get("title") for item in embeds]
    if not all(isinstance(title, str) and title.strip() for title in titles):
        raise ValueError("Discord payload embed title이 비어 있음")
    normalized = [title.strip() for title in titles]
    if len(normalized) != len(set(normalized)):
        raise ValueError("Discord payload embed title이 중복됨")
    return normalized


def validate_payload_contract(payload: Any, event: str) -> dict[str, Any]:
    embeds = payload_embeds(payload)
    expected = expected_embed_count(event)
    if len(embeds) != expected:
        raise ValueError(f"Discord payload embed 개수 불일치: expected={expected}, actual={len(embeds)}")
    if payload.get("allowed_mentions") != {"parse": []}:
        raise ValueError("Discord payload mention 보호가 없음")
    titles = embed_titles(embeds)
    for index, embed in enumerate(embeds, start=1):
        if len(str(embed.get("title", ""))) > limits.MAX_TITLE:
            raise ValueError(f"Discord payload embed {index} title 제한 초과")
        fields = embed.get("fields")
        if not isinstance(fields, list) or not 1 <= len(fields) <= limits.MAX_FIELDS:
            raise ValueError(f"Discord payload embed {index} fields 계약 불일치")
        for field_index, item in enumerate(fields, start=1):
            if not isinstance(item, dict):
                raise ValueError(f"Discord payload embed {index} field {field_index}가 object가 아님")
            if len(str(item.get("name", ""))) > limits.MAX_FIELD_NAME:
                raise ValueError(f"Discord payload embed {index} field {field_index} name 제한 초과")
            if len(str(item.get("value", ""))) > limits.MAX_FIELD_VALUE:
                raise ValueError(f"Discord payload embed {index} field {field_index} value 제한 초과")
    text_length = limits.payload_text_length(payload)
    if text_length > limits.MAX_TOTAL_TEXT:
        raise ValueError(f"Discord payload 텍스트 제한 초과: {text_length}/{limits.MAX_TOTAL_TEXT}")
    return {
        "event": event,
        "expected_embed_count": expected,
        "payload_embed_count": len(embeds),
        "payload_embed_titles": titles,
        "payload_text_length": text_length,
    }


def validate_created_message(payload: Any, message: Any, event: str) -> dict[str, int]:
    requested = len(payload_embeds(payload))
    expected = expected_embed_count(event)
    if requested != expected:
        raise ValueError(f"Discord 요청 embed 개수 불일치: expected={expected}, requested={requested}")
    if not isinstance(message, dict):
        raise ValueError("Discord message 응답이 object가 아님")
    if not message.get("id"):
        raise ValueError("Discord message 식별자가 없음")
    created = message.get("embeds")
    if not isinstance(created, list):
        raise ValueError("Discord message 응답 embeds가 list가 아님")
    if len(created) != requested:
        raise ValueError(f"Discord message embed 개수 불일치: requested={requested}, created={len(created)}")
    if len(created) != expected:
        raise ValueError(f"Discord 상세 message 계약 불일치: expected={expected}, created={len(created)}")
    return {"requested_embed_count": requested, "created_embed_count": len(created)}


def format_metadata(metadata: dict[str, Any]) -> str:
    lines = [
        f"Discord event: {metadata['event']}",
        f"Expected embed count: {metadata['expected_embed_count']}",
        f"Payload embed count: {metadata['payload_embed_count']}",
        "Payload embed titles:",
        *(f"- {title}" for title in metadata["payload_embed_titles"]),
        f"Payload text length: {metadata['payload_text_length']}",
    ]
    return "\n".join(lines)


def emit_summary(message: str) -> None:
    print(message)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as handle:
            handle.write(message.rstrip() + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--payload-file", required=True, type=Path)
    parser.add_argument("--context-file", required=True, type=Path)
    args = parser.parse_args()
    payload = json.loads(args.payload_file.read_text(encoding="utf-8"))
    context = json.loads(args.context_file.read_text(encoding="utf-8"))
    try:
        metadata = validate_payload_contract(payload, str(context.get("event") or "connection_test"))
    except ValueError as exc:
        print(f"Discord payload contract failure: {exc}")
        return 1
    emit_summary(format_metadata(metadata))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

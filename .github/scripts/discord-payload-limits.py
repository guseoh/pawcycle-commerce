#!/usr/bin/env python3
"""Shared Discord embed text-limit helpers."""

from __future__ import annotations

from typing import Any


MAX_EMBEDS = 10
MAX_FIELDS = 25
MAX_TITLE = 256
MAX_DESCRIPTION = 4096
MAX_FIELD_NAME = 256
MAX_FIELD_VALUE = 1024
MAX_FOOTER_TEXT = 2048
MAX_AUTHOR_NAME = 256
MAX_TOTAL_TEXT = 6000


def payload_text_length(payload: Any) -> int:
    if not isinstance(payload, dict) or not isinstance(payload.get("embeds"), list):
        return 0
    total = 0
    for embed in payload["embeds"]:
        if not isinstance(embed, dict):
            continue
        total += len(str(embed.get("title") or ""))
        total += len(str(embed.get("description") or ""))
        fields = embed.get("fields")
        if isinstance(fields, list):
            for field in fields:
                if isinstance(field, dict):
                    total += len(str(field.get("name") or ""))
                    total += len(str(field.get("value") or ""))
        footer = embed.get("footer")
        if isinstance(footer, dict):
            total += len(str(footer.get("text") or ""))
        author = embed.get("author")
        if isinstance(author, dict):
            total += len(str(author.get("name") or ""))
    return total

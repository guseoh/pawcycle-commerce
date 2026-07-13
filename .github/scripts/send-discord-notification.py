#!/usr/bin/env python3
"""Send a Discord webhook payload without printing secret values."""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


USER_AGENT = "DiscordBot (https://github.com/guseoh/pawcycle-commerce, 1.0)"
CONTRACT_PATH = Path(__file__).with_name("discord-message-contract.py")
CONTRACT_SPEC = importlib.util.spec_from_file_location("discord_message_contract_sender", CONTRACT_PATH)
if CONTRACT_SPEC is None or CONTRACT_SPEC.loader is None:
    raise RuntimeError("Discord message contract helper를 불러올 수 없음")
contract = importlib.util.module_from_spec(CONTRACT_SPEC)
CONTRACT_SPEC.loader.exec_module(contract)


def summary(message: str) -> None:
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as handle:
            handle.write(message.rstrip() + "\n")
    print(message)


def retry_delay(attempt: int) -> int:
    return min(2 * attempt, 6)


def should_retry(status: int) -> bool:
    return status == 429 or 500 <= status < 600


def build_request(url: str, payload: dict[str, object]) -> urllib.request.Request:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    return urllib.request.Request(
        url,
        data=data,
        headers={
            "Content-Type": "application/json",
            "User-Agent": USER_AGENT,
        },
        method="POST",
    )


def with_wait_query(url: str) -> str:
    parsed = urllib.parse.urlsplit(url)
    query = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
    query = [(key, value) for key, value in query if key.lower() != "wait"]
    query.append(("wait", "true"))
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, parsed.path, urllib.parse.urlencode(query), parsed.fragment))


def parse_created_message(raw: bytes) -> Any:
    try:
        return json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError("Discord message JSON 파싱 실패") from exc


def send(url: str, payload: dict[str, object], retries: int, *, wait_for_message: bool = False, event: str = "connection_test") -> int:
    try:
        metadata = contract.validate_payload_contract(payload, event)
    except ValueError as exc:
        summary(f"Discord payload contract failure: {exc}")
        return 1
    request_url = with_wait_query(url) if wait_for_message else url
    for attempt in range(1, retries + 1):
        try:
            with urllib.request.urlopen(build_request(request_url, payload), timeout=15) as response:
                status = response.getcode()
                raw_response = response.read() if wait_for_message else b""
            if 200 <= status < 300:
                summary(f"Discord 알림 전송 완료: HTTP {status}")
                if wait_for_message:
                    message: Any = None
                    try:
                        message = parse_created_message(raw_response)
                        counts = contract.validate_created_message(payload, message, event)
                    except ValueError as exc:
                        summary("Discord message contract mismatch")
                        summary(f"Requested embed count: {metadata['payload_embed_count']}")
                        created = message.get("embeds") if isinstance(message, dict) else None
                        created_count = len(created) if isinstance(created, list) else "확인 불가"
                        summary(f"Created message embed count: {created_count}")
                        summary(f"Contract failure: {exc}")
                        return 1
                    summary(f"Requested embed count: {counts['requested_embed_count']}")
                    summary(f"Created message embed count: {counts['created_embed_count']}")
                    summary("Discord message contract: success")
                return 0
            if not should_retry(status):
                summary(f"Discord 알림 전송 실패: HTTP {status}")
                return 1
            summary(f"Discord 알림 전송 재시도: HTTP {status}, attempt={attempt}/{retries}")
        except urllib.error.HTTPError as exc:
            status = exc.code
            if not should_retry(status):
                summary(f"Discord 알림 전송 실패: HTTP {status}")
                return 1
            summary(f"Discord 알림 전송 재시도: HTTP {status}, attempt={attempt}/{retries}")
        except urllib.error.URLError:
            summary(f"Discord 알림 전송 재시도: 네트워크 오류, attempt={attempt}/{retries}")

        if attempt < retries:
            time.sleep(retry_delay(attempt))

    summary("Discord 알림 전송 실패: 제한된 재시도 후 포기")
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--payload-file", required=True)
    parser.add_argument("--context-file")
    parser.add_argument("--webhook-url", default=os.environ.get("DISCORD_WEBHOOK_URL", ""))
    parser.add_argument("--retries", type=int, default=3)
    parser.add_argument("--wait-for-message", action="store_true")
    args = parser.parse_args()

    if not args.webhook_url:
        summary("Discord 알림 실패: DISCORD_WEBHOOK_URL Secret이 설정되지 않음")
        return 1

    with open(args.payload_file, "r", encoding="utf-8") as handle:
        payload = json.load(handle)

    event = "connection_test"
    if args.context_file:
        with open(args.context_file, "r", encoding="utf-8") as handle:
            context = json.load(handle)
        event = str(context.get("event") or event)
    elif args.wait_for_message:
        summary("Discord 알림 실패: wait mode에는 --context-file이 필요함")
        return 1

    return send(args.webhook_url, payload, max(1, args.retries), wait_for_message=args.wait_for_message, event=event)


if __name__ == "__main__":
    raise SystemExit(main())

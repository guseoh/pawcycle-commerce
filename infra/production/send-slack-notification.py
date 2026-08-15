#!/usr/bin/env python3
"""Send a Slack Incoming Webhook payload without printing secret values."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


USER_AGENT = "PawCycleBackendStateAlert/1.0"


def build_request(url: str, payload: dict[str, object]) -> urllib.request.Request:
    return urllib.request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": USER_AGENT},
        method="POST",
    )


def send(url: str, payload: dict[str, object]) -> int:
    try:
        with urllib.request.urlopen(build_request(url, payload), timeout=15) as response:
            status = response.getcode()
    except urllib.error.HTTPError as exc:
        print(f"Slack 알림 전송 실패: HTTP {exc.code}")
        return 1
    except urllib.error.URLError:
        print("Slack 알림 전송 실패: 네트워크 오류")
        return 1
    if 200 <= status < 300:
        print(f"Slack Webhook 전송 완료: HTTP {status}")
        return 0
    print(f"Slack 알림 전송 실패: HTTP {status}")
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--payload-file", required=True, type=Path)
    parser.add_argument("--webhook-url", default=os.environ.get("SLACK_WEBHOOK_URL", ""))
    args = parser.parse_args()
    if not args.webhook_url:
        print("Slack 알림 실패: SLACK_WEBHOOK_URL Secret이 설정되지 않음")
        return 1
    try:
        payload = json.loads(args.payload_file.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        print("Slack 알림 실패: payload 파일을 읽을 수 없음")
        return 1
    if not isinstance(payload, dict):
        print("Slack 알림 실패: payload 형식 오류")
        return 1
    return send(args.webhook_url, payload)


if __name__ == "__main__":
    raise SystemExit(main())

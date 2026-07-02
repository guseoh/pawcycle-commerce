#!/usr/bin/env python3
"""Send a Discord webhook payload without printing secret values."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request


def summary(message: str) -> None:
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as handle:
            handle.write(message.rstrip() + "\n")
    print(message)


def send(url: str, payload: dict[str, object], retries: int) -> int:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    for attempt in range(1, retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                status = response.getcode()
            if 200 <= status < 300:
                summary(f"Discord 알림 전송 완료: HTTP {status}")
                return 0
            summary(f"Discord 알림 전송 경고: HTTP {status}, attempt={attempt}")
        except urllib.error.HTTPError as exc:
            status = exc.code
            summary(f"Discord 알림 전송 경고: HTTP {status}, attempt={attempt}")
            if status != 429 and status < 500:
                return 0
        except urllib.error.URLError as exc:
            summary(f"Discord 알림 전송 경고: network error, attempt={attempt}, reason={exc.reason}")

        if attempt < retries:
            time.sleep(min(2 * attempt, 6))

    summary("Discord 알림 전송 실패: 제한된 재시도 후 포기")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--payload-file", required=True)
    parser.add_argument("--webhook-url", default=os.environ.get("DISCORD_WEBHOOK_URL", ""))
    parser.add_argument("--retries", type=int, default=3)
    args = parser.parse_args()

    if not args.webhook_url:
        summary("Discord 알림 건너뜀: DISCORD_WEBHOOK_URL Secret이 설정되지 않음")
        return 0

    with open(args.payload_file, "r", encoding="utf-8") as handle:
        payload = json.load(handle)

    return send(args.webhook_url, payload, max(1, args.retries))


if __name__ == "__main__":
    raise SystemExit(main())

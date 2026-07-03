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


def retry_delay(attempt: int) -> int:
    return min(2 * attempt, 6)


def should_retry(status: int) -> bool:
    return status == 429 or 500 <= status < 600


def build_request(url: str, payload: dict[str, object]) -> urllib.request.Request:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    return urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )


def send(url: str, payload: dict[str, object], retries: int) -> int:
    for attempt in range(1, retries + 1):
        try:
            with urllib.request.urlopen(build_request(url, payload), timeout=15) as response:
                status = response.getcode()
            if 200 <= status < 300:
                summary(f"Discord 알림 전송 완료: HTTP {status}")
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
    parser.add_argument("--webhook-url", default=os.environ.get("DISCORD_WEBHOOK_URL", ""))
    parser.add_argument("--retries", type=int, default=3)
    args = parser.parse_args()

    if not args.webhook_url:
        summary("Discord 알림 실패: DISCORD_WEBHOOK_URL Secret이 설정되지 않음")
        return 1

    with open(args.payload_file, "r", encoding="utf-8") as handle:
        payload = json.load(handle)

    return send(args.webhook_url, payload, max(1, args.retries))


if __name__ == "__main__":
    raise SystemExit(main())

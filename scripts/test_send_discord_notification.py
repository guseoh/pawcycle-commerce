#!/usr/bin/env python3
"""Unit tests for Discord webhook delivery behavior."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".github" / "scripts" / "send-discord-notification.py"
WEBHOOK_URL = "https://example.invalid/webhook/opaque-value"
TEST_WEBHOOK_MARKER = "opaque-value"


def load_module():
    spec = importlib.util.spec_from_file_location("send_discord_notification", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError("send-discord-notification.py를 불러올 수 없음")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


discord = load_module()


class FakeResponse:
    def __init__(self, status: int) -> None:
        self.status = status

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:
        return False

    def getcode(self) -> int:
        return self.status


def http_error(status: int) -> urllib.error.HTTPError:
    return urllib.error.HTTPError(WEBHOOK_URL, status, "status", {}, io.BytesIO(b"hidden body"))


class SendDiscordNotificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.payload = {"content": "test"}

    def test_build_request_sets_required_headers(self) -> None:
        request = discord.build_request(WEBHOOK_URL, self.payload)

        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(request.get_header("Content-type"), "application/json")
        self.assertEqual(request.get_header("User-agent"), discord.USER_AGENT)
        self.assertTrue(discord.USER_AGENT.startswith("DiscordBot ("))
        self.assertIn("https://github.com/guseoh/pawcycle-commerce", discord.USER_AGENT)
        self.assertRegex(discord.USER_AGENT, r", [0-9]+(?:\.[0-9]+)*\)$")

    def test_build_request_headers_do_not_include_webhook_url(self) -> None:
        request = discord.build_request(WEBHOOK_URL, self.payload)
        header_values = "\n".join(str(value) for value in request.header_items())

        self.assertNotIn(WEBHOOK_URL, header_values)
        self.assertNotIn(TEST_WEBHOOK_MARKER, header_values)

    def run_send(self, side_effects, retries: int = 3) -> tuple[int, str, mock.Mock]:
        stdout = io.StringIO()
        with (
            mock.patch.dict(discord.os.environ, {}, clear=True),
            mock.patch.object(discord.urllib.request, "urlopen", side_effect=side_effects) as urlopen,
            mock.patch.object(discord.time, "sleep"),
            contextlib.redirect_stdout(stdout),
        ):
            code = discord.send(WEBHOOK_URL, self.payload, retries)
        return code, stdout.getvalue(), urlopen

    def test_missing_webhook_url_returns_failure(self) -> None:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as handle:
            json.dump(self.payload, handle)
            payload_path = handle.name

        stdout = io.StringIO()
        try:
            with (
                mock.patch.dict(discord.os.environ, {}, clear=True),
                mock.patch.object(sys, "argv", ["send-discord-notification.py", "--payload-file", payload_path]),
                contextlib.redirect_stdout(stdout),
            ):
                code = discord.main()
        finally:
            Path(payload_path).unlink()

        self.assertEqual(code, 1)
        self.assertIn("DISCORD_WEBHOOK_URL Secret이 설정되지 않음", stdout.getvalue())

    def test_http_204_returns_success(self) -> None:
        code, output, _ = self.run_send([FakeResponse(204)])
        self.assertEqual(code, 0)
        self.assertIn("Discord 알림 전송 완료: HTTP 204", output)

    def test_http_400_fails_immediately(self) -> None:
        code, _, urlopen = self.run_send([http_error(400), FakeResponse(204)])
        self.assertEqual(code, 1)
        self.assertEqual(urlopen.call_count, 1)

    def test_http_401_fails_immediately(self) -> None:
        code, _, urlopen = self.run_send([http_error(401), FakeResponse(204)])
        self.assertEqual(code, 1)
        self.assertEqual(urlopen.call_count, 1)

    def test_http_403_fails_immediately(self) -> None:
        code, _, urlopen = self.run_send([http_error(403), FakeResponse(204)])
        self.assertEqual(code, 1)
        self.assertEqual(urlopen.call_count, 1)

    def test_http_404_fails_immediately(self) -> None:
        code, output, urlopen = self.run_send([http_error(404), FakeResponse(204)])
        self.assertEqual(code, 1)
        self.assertEqual(urlopen.call_count, 1)
        self.assertIn("Discord 알림 전송 실패: HTTP 404", output)

    def test_http_429_retries_then_succeeds(self) -> None:
        code, _, urlopen = self.run_send([http_error(429), FakeResponse(204)])
        self.assertEqual(code, 0)
        self.assertEqual(urlopen.call_count, 2)

    def test_http_429_retry_exhaustion_fails(self) -> None:
        code, output, urlopen = self.run_send([http_error(429), http_error(429), http_error(429)])
        self.assertEqual(code, 1)
        self.assertEqual(urlopen.call_count, 3)
        self.assertIn("제한된 재시도 후 포기", output)

    def test_http_500_retries_then_succeeds(self) -> None:
        code, _, urlopen = self.run_send([http_error(500), FakeResponse(204)])
        self.assertEqual(code, 0)
        self.assertEqual(urlopen.call_count, 2)

    def test_http_500_retry_exhaustion_fails(self) -> None:
        code, output, urlopen = self.run_send([http_error(500), http_error(500), http_error(500)])
        self.assertEqual(code, 1)
        self.assertEqual(urlopen.call_count, 3)
        self.assertIn("제한된 재시도 후 포기", output)

    def test_url_error_retries_then_succeeds(self) -> None:
        code, _, urlopen = self.run_send([urllib.error.URLError("network"), FakeResponse(204)])
        self.assertEqual(code, 0)
        self.assertEqual(urlopen.call_count, 2)

    def test_url_error_retry_exhaustion_fails(self) -> None:
        code, output, urlopen = self.run_send(
            [urllib.error.URLError("network"), urllib.error.URLError("network"), urllib.error.URLError("network")]
        )
        self.assertEqual(code, 1)
        self.assertEqual(urlopen.call_count, 3)
        self.assertIn("제한된 재시도 후 포기", output)

    def test_logs_do_not_include_webhook_url(self) -> None:
        code, output, _ = self.run_send([http_error(404)])
        self.assertEqual(code, 1)
        self.assertNotIn(WEBHOOK_URL, output)
        self.assertNotIn("opaque-value", output)


if __name__ == "__main__":
    unittest.main()

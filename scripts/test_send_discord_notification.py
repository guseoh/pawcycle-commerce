#!/usr/bin/env python3
"""Focused tests for safe Discord webhook delivery and message validation."""

from __future__ import annotations

import importlib.util
import contextlib
import io
import json
import sys
import tempfile
import unittest
import urllib.error
import urllib.parse
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".github" / "scripts" / "send-discord-notification.py"
BUILDER = ROOT / ".github" / "scripts" / "build-discord-payload.py"
WEBHOOK_URL = "https://example.invalid/webhook/opaque-value"


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"{name} 모듈을 불러올 수 없음")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


sender = load_module("send_discord_notification", SCRIPT)
builder = load_module("build_discord_payload_for_sender", BUILDER)


class FakeResponse:
    def __init__(self, status, body=b""):
        self.status = status
        self.body = body

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def getcode(self):
        return self.status

    def read(self):
        return self.body


def http_error(status):
    return urllib.error.HTTPError(WEBHOOK_URL, status, "status", {}, io.BytesIO(b"hidden body"))


class DiscordSenderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        context = json.loads((ROOT / ".github" / "fixtures" / "discord" / "pr-merged.json").read_text(encoding="utf-8"))
        cls.payload = builder.build_payload(context)

    def run_send(self, side_effects, retries=3):
        stdout = io.StringIO()
        with (
            mock.patch.dict(sender.os.environ, {}, clear=True),
            mock.patch.object(sender.urllib.request, "urlopen", side_effect=side_effects) as urlopen,
            mock.patch.object(sender.time, "sleep"),
            contextlib.redirect_stdout(stdout),
        ):
            code = sender.send(WEBHOOK_URL, self.payload, retries, event="pr_merged")
        return code, stdout.getvalue(), urlopen

    def test_build_request_sets_required_headers_without_exposing_url(self):
        request = sender.build_request(WEBHOOK_URL, self.payload)
        headers = "\n".join(str(value) for value in request.header_items())
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(request.get_header("Content-type"), "application/json")
        self.assertEqual(request.get_header("User-agent"), sender.USER_AGENT)
        self.assertNotIn(WEBHOOK_URL, headers)
        self.assertNotIn("opaque-value", headers)

    def test_missing_webhook_url_returns_failure(self):
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as handle:
            json.dump(self.payload, handle)
            payload_path = handle.name
        stdout = io.StringIO()
        try:
            with (
                mock.patch.dict(sender.os.environ, {}, clear=True),
                mock.patch.object(sys, "argv", ["send-discord-notification.py", "--payload-file", payload_path]),
                redirect_stdout(stdout),
            ):
                code = sender.main()
        finally:
            Path(payload_path).unlink()
        self.assertEqual(code, 1)
        self.assertIn("DISCORD_WEBHOOK_URL Secret이 설정되지 않음", stdout.getvalue())

    def test_wait_mode_without_context_file_fails_in_main(self):
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as handle:
            json.dump(self.payload, handle)
            payload_path = handle.name
        stdout = io.StringIO()
        try:
            with (
                mock.patch.dict(sender.os.environ, {"DISCORD_WEBHOOK_URL": WEBHOOK_URL}, clear=True),
                mock.patch.object(sys, "argv", ["send-discord-notification.py", "--payload-file", payload_path, "--wait-for-message"]),
                redirect_stdout(stdout),
            ):
                code = sender.main()
        finally:
            Path(payload_path).unlink()
        self.assertEqual(code, 1)
        self.assertIn("wait mode에는 --context-file이 필요함", stdout.getvalue())

    def test_non_retryable_http_errors_fail_immediately(self):
        for status in (400, 401, 403, 404):
            with self.subTest(status=status):
                code, output, urlopen = self.run_send([http_error(status), FakeResponse(204)])
                self.assertEqual(code, 1)
                self.assertEqual(urlopen.call_count, 1)
                self.assertNotIn("opaque-value", output)
                self.assertNotIn("hidden body", output)

    def test_retryable_http_errors_retry_and_can_succeed(self):
        for status in (429, 500):
            with self.subTest(status=status):
                code, _, urlopen = self.run_send([http_error(status), FakeResponse(204)])
                self.assertEqual(code, 0)
                self.assertEqual(urlopen.call_count, 2)

    def test_retryable_http_errors_stop_after_retry_budget(self):
        for status in (429, 500):
            with self.subTest(status=status):
                code, output, urlopen = self.run_send([http_error(status)] * 3)
                self.assertEqual(code, 1)
                self.assertEqual(urlopen.call_count, 3)
                self.assertIn("제한된 재시도 후 포기", output)

    def test_url_error_retries_and_respects_retry_budget(self):
        code, _, urlopen = self.run_send([urllib.error.URLError("network"), FakeResponse(204)])
        self.assertEqual(code, 0)
        self.assertEqual(urlopen.call_count, 2)
        code, output, urlopen = self.run_send([urllib.error.URLError("network")] * 3)
        self.assertEqual(code, 1)
        self.assertEqual(urlopen.call_count, 3)
        self.assertIn("제한된 재시도 후 포기", output)

    def test_wait_query_is_added_and_existing_parameters_are_preserved(self):
        result = sender.with_wait_query("https://example.invalid/hook?thread_id=7")
        query = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(result).query))
        self.assertEqual(query, {"thread_id": "7", "wait": "true"})

    def test_existing_wait_query_is_replaced(self):
        result = sender.with_wait_query("https://example.invalid/hook?wait=false&thread_id=7")
        values = urllib.parse.parse_qs(urllib.parse.urlsplit(result).query)
        self.assertEqual(values["wait"], ["true"])
        self.assertEqual(values["thread_id"], ["7"])

    def test_wait_mode_accepts_http_200_message_with_three_embeds(self):
        message = {"id": "message-id", "embeds": [{}, {}, {}], "content": "sensitive-response"}
        with mock.patch("urllib.request.urlopen", return_value=FakeResponse(200, json.dumps(message).encode())):
            output = io.StringIO()
            with redirect_stdout(output):
                result = sender.send("https://example.invalid/hook", self.payload, 1, wait_for_message=True, event="pr_merged")
        self.assertEqual(result, 0)
        log = output.getvalue()
        self.assertIn("Discord Webhook 응답 수신: HTTP 200", log)
        self.assertIn("Created message embed count: 3", log)
        self.assertIn("Discord message contract: success", log)
        self.assertIn("Discord 알림 전송 완료", log)
        self.assertNotIn("sensitive-response", log)

    def test_wait_mode_rejects_message_embed_mismatch_without_logging_body_or_url(self):
        message = {"id": "message-id", "embeds": [{}], "content": "private-body"}
        webhook = "https://example.invalid/hook/private-token"
        with mock.patch("urllib.request.urlopen", return_value=FakeResponse(200, json.dumps(message).encode())):
            output = io.StringIO()
            with redirect_stdout(output):
                result = sender.send(webhook, self.payload, 1, wait_for_message=True, event="pr_merged")
        self.assertEqual(result, 1)
        log = output.getvalue()
        self.assertIn("Discord Webhook 응답 수신: HTTP 200", log)
        self.assertIn("Discord message contract mismatch", log)
        self.assertIn("Created message embed count: 1", log)
        self.assertNotIn("Discord 알림 전송 완료", log)
        self.assertNotIn("private-body", log)
        self.assertNotIn("private-token", log)

    def test_wait_mode_rejects_invalid_json_http_204_and_missing_message_id(self):
        cases = [
            FakeResponse(200, b"not-json"),
            FakeResponse(204, b""),
            FakeResponse(200, json.dumps({"embeds": [{}, {}, {}]}).encode()),
        ]
        for response in cases:
            with self.subTest(status=response.status, body=response.body):
                with mock.patch("urllib.request.urlopen", return_value=response):
                    output = io.StringIO()
                    with redirect_stdout(output):
                        result = sender.send("https://example.invalid/hook", self.payload, 1, wait_for_message=True, event="pr_merged")
                self.assertEqual(result, 1)
                self.assertNotIn("Discord 알림 전송 완료", output.getvalue())

    def test_non_wait_mode_keeps_http_204_success(self):
        with mock.patch("urllib.request.urlopen", return_value=FakeResponse(204)):
            output = io.StringIO()
            with redirect_stdout(output):
                result = sender.send("https://example.invalid/hook", self.payload, 1, event="pr_merged")
        self.assertEqual(result, 0)
        self.assertIn("Discord Webhook 전송 완료: HTTP 204", output.getvalue())
        self.assertNotIn("Discord message contract: success", output.getvalue())

    def test_payload_count_mismatch_fails_before_network(self):
        single = dict(self.payload)
        single["embeds"] = [self.payload["embeds"][0]]
        with mock.patch("urllib.request.urlopen") as urlopen:
            self.assertEqual(sender.send("https://example.invalid/hook", single, 1, wait_for_message=True, event="pr_merged"), 1)
        urlopen.assert_not_called()

    def test_retry_status_policy_is_preserved(self):
        self.assertTrue(sender.should_retry(429))
        self.assertTrue(sender.should_retry(500))
        self.assertTrue(sender.should_retry(599))
        self.assertFalse(sender.should_retry(400))
        self.assertFalse(sender.should_retry(404))


if __name__ == "__main__":
    unittest.main()

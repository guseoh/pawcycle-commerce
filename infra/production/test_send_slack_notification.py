#!/usr/bin/env python3
"""Focused tests for safe Slack Incoming Webhook delivery."""

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


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "infra" / "production" / "send-slack-notification.py"
WEBHOOK_URL = "https://example.invalid/slack/opaque-value"


def load_module():
    spec = importlib.util.spec_from_file_location("send_slack_notification", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError("Slack sender를 불러올 수 없음")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


sender = load_module()


class FakeResponse:
    def __init__(self, status: int):
        self.status = status

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def getcode(self):
        return self.status


class SlackSenderTests(unittest.TestCase):
    payload = {"text": "PawCycle Backend State Alert\nstatus: UNKNOWN"}

    def test_request_uses_json_headers_without_exposing_url(self):
        request = sender.build_request(WEBHOOK_URL, self.payload)
        headers = "\n".join(str(value) for value in request.header_items())
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(request.get_header("Content-type"), "application/json")
        self.assertEqual(request.get_header("User-agent"), sender.USER_AGENT)
        self.assertNotIn(WEBHOOK_URL, headers)
        self.assertNotIn("opaque-value", headers)

    def test_success_and_failures_do_not_log_webhook_or_response_body(self):
        for effect, expected in ((FakeResponse(200), 0), (urllib.error.HTTPError(WEBHOOK_URL, 403, "forbidden", {}, io.BytesIO(b"private-body")), 1), (urllib.error.URLError("network"), 1)):
            side_effect = effect if isinstance(effect, BaseException) else lambda *_args, response=effect, **_kwargs: response
            with self.subTest(expected=expected), mock.patch.object(sender.urllib.request, "urlopen", side_effect=side_effect), contextlib.redirect_stdout(io.StringIO()) as output:
                self.assertEqual(sender.send(WEBHOOK_URL, self.payload), expected)
                self.assertNotIn("opaque-value", output.getvalue())
                self.assertNotIn("private-body", output.getvalue())

    def test_missing_webhook_and_invalid_payload_fail_closed(self):
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as handle:
            handle.write("not-json")
            payload_path = handle.name
        try:
            with mock.patch.dict(sender.os.environ, {}, clear=True), mock.patch.object(sys, "argv", ["send-slack-notification.py", "--payload-file", payload_path]):
                self.assertEqual(sender.main(), 1)
            with mock.patch.dict(sender.os.environ, {"SLACK_WEBHOOK_URL": WEBHOOK_URL}, clear=True), mock.patch.object(sys, "argv", ["send-slack-notification.py", "--payload-file", payload_path]):
                self.assertEqual(sender.main(), 1)
        finally:
            Path(payload_path).unlink()


if __name__ == "__main__":
    unittest.main()

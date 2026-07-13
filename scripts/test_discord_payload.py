#!/usr/bin/env python3
"""Focused tests for Discord report payload composition."""

from __future__ import annotations

import importlib.util
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".github" / "scripts" / "build-discord-payload.py"
COLLECTOR = ROOT / ".github" / "scripts" / "collect-discord-context.py"
VALIDATOR = ROOT / "scripts" / "validate-discord-payloads.py"


def load_module():
    spec = importlib.util.spec_from_file_location("build_discord_payload", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError("builder를 불러올 수 없음")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


discord = load_module()


def load_path_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"{name} 모듈을 불러올 수 없음")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


collector = load_path_module("collect_discord_context_for_payload", COLLECTOR)
validator = load_path_module("validate_discord_payloads", VALIDATOR)


class FakeApi:
    def unresolved_threads(self, _number):
        return 0

    def review_states(self, _number):
        return []

    def latest_validation(self, _branch):
        return "success"


class DiscordPayloadTests(unittest.TestCase):
    def test_detailed_report_preserves_failure_and_next_action(self):
        context = json.loads((ROOT / ".github" / "fixtures" / "discord" / "ci-failure.json").read_text(encoding="utf-8"))
        payload = discord.build_payload(context)
        raw = json.dumps(payload, ensure_ascii=False)
        self.assertEqual(len(payload["embeds"]), 3)
        self.assertIn("Validate required headings", raw)
        self.assertIn("실패 Job과 Step 확인 후 최소 수정", raw)
        self.assertEqual(payload["allowed_mentions"], {"parse": []})

    def test_long_untrusted_input_is_bounded_and_mentions_disabled(self):
        context = json.loads((ROOT / ".github" / "fixtures" / "discord" / "long-input.json").read_text(encoding="utf-8"))
        payload = discord.build_payload(context)
        raw = json.dumps(payload, ensure_ascii=False)
        self.assertLessEqual(discord.limits.payload_text_length(payload), discord.limits.MAX_TOTAL_TEXT)
        self.assertNotIn("@everyone", raw)
        self.assertNotIn("@here", raw)
        self.assertNotIn("\u0007", raw)

    def test_fenced_secret_never_reaches_final_payload(self):
        pr = {
            "number": 40,
            "title": "OPS-007 안전한 알림",
            "body": "## 작업 목적\n안전 검증\n```env\nAWS_SECRET_ACCESS_KEY=do-not-send\nCLIENT_SECRET=hidden\n```\n## 주요 변경\ncollector 보완",
            "head": {"ref": "ops/sre", "sha": "abc"},
            "base": {"ref": "main"},
            "user": {"login": "author"},
            "draft": False,
        }
        context = collector.collect("pull_request_target", {"action": "opened", "pull_request": pr}, "guseoh/pawcycle-commerce", FakeApi())
        raw_context = json.dumps(context, ensure_ascii=False)
        raw_payload = json.dumps(discord.build_payload(context), ensure_ascii=False)
        for secret in ("do-not-send", "hidden", "AWS_SECRET_ACCESS_KEY", "CLIENT_SECRET"):
            self.assertNotIn(secret, raw_context)
            self.assertNotIn(secret, raw_payload)

    def test_validator_collects_malformed_embed_errors_without_exception(self):
        payload = {
            "allowed_mentions": {"parse": []},
            "embeds": [
                "not-an-embed",
                {"title": "x", "color": 1, "fields": "not-a-list", "footer": "bad", "author": "bad", "timestamp": "now"},
                {"title": "y", "color": 1, "fields": ["not-a-field"], "footer": {"text": "ok"}, "timestamp": "now"},
            ],
        }
        errors = validator.validate_payload(payload, Path("malformed.json"), {"event": "connection_test"})
        joined = "\n".join(errors)
        self.assertIn("embed 1 형식 오류", joined)
        self.assertIn("fields 형식 오류", joined)
        self.assertIn("footer 형식 오류", joined)
        self.assertIn("author 형식 오류", joined)
        self.assertIn("field 1 형식 오류", joined)

    def test_workflow_parser_ignores_comments_and_non_on_blocks(self):
        text = """name: Example
# workflow_run:
on:
  workflow_dispatch:
jobs:
  sample:
    env:
      NOTE: workflow_run:
"""
        self.assertEqual(validator.yaml_top_level_on_entries(text), ["workflow_dispatch"])

    def test_validator_rejects_aws_and_private_key_markers(self):
        payload = {
            "allowed_mentions": {"parse": []},
            "embeds": [{
                "title": "검증",
                "color": 1,
                "fields": [{"name": "로그", "value": "AWS_SECRET_ACCESS_KEY=unsafe-value\n-----BEGIN PRIVATE KEY-----", "inline": False}],
                "footer": {"text": "footer"},
                "timestamp": "2026-07-13T00:00:00Z",
            }],
        }
        errors = validator.validate_payload(payload, Path("secret.json"), {"event": "connection_test"})
        self.assertIn("마스킹되지 않은 Secret 의심 값", "\n".join(errors))


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Focused tests for Discord report payload composition."""

from __future__ import annotations

import importlib.util
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".github" / "scripts" / "build-discord-payload.py"


def load_module():
    spec = importlib.util.spec_from_file_location("build_discord_payload", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError("builder를 불러올 수 없음")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


discord = load_module()


class DiscordPayloadTests(unittest.TestCase):
    def test_detailed_report_preserves_failure_and_next_action(self):
        context = json.loads((ROOT / ".github" / "fixtures" / "discord" / "ci-failure.json").read_text(encoding="utf-8"))
        payload = discord.build_payload(context)
        raw = json.dumps(payload, ensure_ascii=False)
        self.assertEqual(len(payload["embeds"]), 3)
        self.assertIn("Validate required headings", raw)
        self.assertIn("실패 로그 확인과 최소 수정", raw)
        self.assertEqual(payload["allowed_mentions"], {"parse": []})

    def test_long_untrusted_input_is_bounded_and_mentions_disabled(self):
        context = json.loads((ROOT / ".github" / "fixtures" / "discord" / "long-input.json").read_text(encoding="utf-8"))
        payload = discord.build_payload(context)
        raw = json.dumps(payload, ensure_ascii=False)
        self.assertLessEqual(len(raw), 6000)
        self.assertNotIn("@everyone", raw)
        self.assertNotIn("@here", raw)
        self.assertNotIn("\u0007", raw)


if __name__ == "__main__":
    unittest.main()

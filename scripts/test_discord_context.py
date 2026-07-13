#!/usr/bin/env python3
"""Focused tests for normalized Discord collaboration context."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".github" / "scripts" / "collect-discord-context.py"


def load_module():
    spec = importlib.util.spec_from_file_location("collect_discord_context", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError("collector를 불러올 수 없음")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


discord = load_module()


class FakeApi:
    def pull_request(self, _number):
        return None

    def unresolved_threads(self, _number):
        return discord.UNKNOWN

    def review_states(self, _number):
        return None

    def workflow_jobs(self, _run_id):
        return None

    def latest_validation(self, _branch):
        return discord.UNKNOWN


class DiscordContextTests(unittest.TestCase):
    def test_task_id_priority_and_supported_families(self):
        self.assertEqual(discord.extract_task_id("작업 ID:\nAUTH-004", "API-003", "ops/sre"), "AUTH-004")
        self.assertEqual(discord.extract_task_id("", "PRODUCT-001 상품", "feat/be"), "PRODUCT-001")
        self.assertEqual(discord.extract_task_id("", "일반 제목", "api-003-contract"), "API-003")
        self.assertEqual(discord.extract_task_id("", "일반 제목", "feature/no-id"), discord.MISSING)

    def test_branch_role_mapping(self):
        self.assertEqual(discord.role_for_branch("ops/sre"), "Platform/SRE")
        self.assertEqual(discord.role_for_branch("test/qa"), "QA Engineer")
        self.assertEqual(discord.role_for_branch("unknown/branch"), discord.MISSING)

    def test_sections_ignore_automatic_summary_and_sanitize_mentions(self):
        body = "## 작업 목적\n안전한 @everyone 알림\n<!-- bot -->\n## 주요 변경\n변경 A\n## CodeRabbit Summary\n자동 요약"
        sections = discord.extract_sections(body)
        self.assertIn("@\u200beveryone", sections["purpose"])
        self.assertEqual(sections["changes"], "변경 A")
        self.assertNotIn("자동 요약", str(sections))

    def test_workflow_api_failure_uses_explicit_fallback(self):
        payload = {"workflow_run": {"id": 7, "name": "Repository Validation", "conclusion": "failure", "head_branch": "ops/sre", "head_sha": "abc", "html_url": "https://example.invalid/run", "pull_requests": []}}
        with mock.patch.dict("os.environ", {"GITHUB_ACTOR": "runner"}):
            context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", FakeApi())
        self.assertEqual(context["ci_jobs"], discord.UNKNOWN)
        self.assertEqual(context["unresolved_threads"], discord.UNKNOWN)
        self.assertEqual(context["next_action"], "실패 로그 확인과 최소 수정")


if __name__ == "__main__":
    unittest.main()

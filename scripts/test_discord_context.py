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


class StalePrApi(FakeApi):
    def pull_request(self, number):
        return {
            "number": number,
            "title": "OPS-007 최신 변경",
            "body": "## 작업 목적\n최신 목적\n## 주요 변경\n최신 변경",
            "head": {"ref": "ops/sre", "sha": "new-sha"},
            "base": {"ref": "main"},
            "user": {"login": "author"},
        }


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

    def test_qa_heading_does_not_fill_validation(self):
        sections = discord.extract_sections("## QA 검증\n독립 QA 통과")
        self.assertEqual(sections["qa"], "독립 QA 통과")
        self.assertEqual(sections["validation"], discord.MISSING)

    def test_workflow_api_failure_uses_explicit_fallback(self):
        payload = {"workflow_run": {"id": 7, "name": "Repository Validation", "conclusion": "failure", "head_branch": "ops/sre", "head_sha": "abc", "html_url": "https://example.invalid/run", "pull_requests": [{"number": 40}]}}
        with mock.patch.dict("os.environ", {"GITHUB_ACTOR": "runner"}):
            context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", FakeApi())
        self.assertEqual(context["number"], 40)
        self.assertEqual(context["ci_jobs"], discord.UNKNOWN)
        self.assertEqual(context["unresolved_threads"], discord.UNKNOWN)
        self.assertEqual(context["next_action"], "실패 로그 확인과 최소 수정")

    def test_stale_workflow_run_preserves_run_sha_and_marks_context(self):
        payload = {"workflow_run": {"id": 8, "name": "Repository Validation", "conclusion": "success", "head_branch": "ops/sre", "head_sha": "old-sha", "html_url": "https://example.invalid/run", "pull_requests": [{"number": 40}]}}
        context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", StalePrApi())
        self.assertEqual(context["sha"], "old-sha")
        self.assertEqual(context["changed_files"], discord.UNKNOWN)
        self.assertIn("이전 SHA", context["risks"])

    def test_secret_patterns_are_redacted(self):
        source = "https://discord.com/api/webhooks/123/opaque ghp_abcdefghijklmnopqrstuvwxyz password=hunter2"
        cleaned = discord.clean_text(source)
        self.assertNotIn("opaque", cleaned)
        self.assertNotIn("ghp_", cleaned)
        self.assertNotIn("hunter2", cleaned)
        self.assertIn("[REDACTED", cleaned)

    def test_issue_body_is_not_forwarded(self):
        payload = {"action": "opened", "issue": {"number": 9, "title": "OPS-007 문의", "body": "password=do-not-send", "user": {"login": "author"}}}
        context = discord.collect("issues", payload, "guseoh/pawcycle-commerce", FakeApi())
        self.assertNotIn("do-not-send", context["purpose"])


if __name__ == "__main__":
    unittest.main()

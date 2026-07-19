#!/usr/bin/env python3
"""Focused tests for normalized Discord collaboration context."""

from __future__ import annotations

import io
import importlib.util
import json
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


class CurrentPrApi(StalePrApi):
    def __init__(self, unresolved):
        self.unresolved = unresolved

    def unresolved_threads(self, _number):
        return self.unresolved


class PreviewPrApi(FakeApi):
    def __init__(self, pr):
        self.pr = pr

    def pull_request(self, _number):
        return self.pr

    def unresolved_threads(self, _number):
        return 0

    def review_states(self, _number):
        return []

    def latest_validation(self, _branch):
        return "success"


class DiscordContextTests(unittest.TestCase):
    @staticmethod
    def preview_pr(**overrides):
        pr = {
            "number": 40,
            "title": "OPS-007 Discord 알림",
            "body": "## 작업 목적\n상세 알림 검증",
            "head": {"ref": "ops/sre", "sha": "head-sha"},
            "base": {"ref": "main"},
            "user": {"login": "author"},
            "state": "open",
            "draft": False,
            "merged": False,
        }
        pr.update(overrides)
        return pr

    def test_pr_preview_reflects_merged_draft_and_ready_states(self):
        cases = [
            (self.preview_pr(state="closed", merged=True, merge_commit_sha="merge-sha"), "pr_merged", "병합 완료", "merge-sha"),
            (self.preview_pr(draft=True), "pr_draft", "Draft", "head-sha"),
            (self.preview_pr(), "pr_ready", "리뷰 가능", "head-sha"),
        ]
        for pr, expected_event, expected_status, expected_sha in cases:
            with self.subTest(event=expected_event):
                context = discord.collect("workflow_dispatch", {}, "guseoh/pawcycle-commerce", PreviewPrApi(pr), "pr_preview", "40")
                self.assertEqual(context["event"], expected_event)
                self.assertIn(expected_status, context["status"])
                self.assertEqual(context["sha"], expected_sha)
                self.assertTrue(context["preview"])

    def test_pr_preview_api_failure_and_closed_unmerged_are_explicit_fallbacks(self):
        failed = discord.collect("workflow_dispatch", {}, "guseoh/pawcycle-commerce", PreviewPrApi(None), "pr_preview", "40")
        closed = discord.collect("workflow_dispatch", {}, "guseoh/pawcycle-commerce", PreviewPrApi(self.preview_pr(state="closed")), "pr_preview", "40")
        closed_draft = discord.collect("workflow_dispatch", {}, "guseoh/pawcycle-commerce", PreviewPrApi(self.preview_pr(state="closed", draft=True)), "pr_preview", "40")
        self.assertEqual(failed["event"], "connection_test")
        self.assertIn("조회 실패", failed["status"])
        self.assertEqual(closed["event"], "connection_test")
        self.assertIn("미병합", closed["status"])
        self.assertEqual(closed_draft["event"], "connection_test")
        self.assertIn("미병합", closed_draft["status"])
        self.assertNotIn("Draft", closed_draft["status"])

    def test_task_id_priority_and_supported_families(self):
        self.assertEqual(discord.extract_task_id("작업 ID:\nAUTH-004", "API-003", "ops/sre"), "AUTH-004")
        self.assertEqual(discord.extract_task_id("작업 ID: FRONTEND-003", "", "feat/fe"), "FRONTEND-003")
        self.assertEqual(discord.extract_task_id("", "PRODUCT-002 상품", "spec/po"), "PRODUCT-002")
        self.assertEqual(discord.extract_task_id("작업 ID: HARNESS-LEAN-001", "", "ops/tl"), "HARNESS-LEAN-001")
        self.assertEqual(discord.extract_task_id("", "HARNESS-LEAN-001 후속 수정", "ops/tl"), "HARNESS-LEAN-001")
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
        sections = discord.extract_sections("## QA 검증\n첫 QA 통과\n## QA 검증\n두 번째 QA\n## 검증\n단위 테스트 통과")
        self.assertEqual(sections["qa"], "첫 QA 통과")
        self.assertEqual(sections["validation"], "단위 테스트 통과")

    def test_duplicate_qa_heading_without_validation_stays_missing(self):
        sections = discord.extract_sections("## QA 검증\n첫 QA 통과\n## QA 검증\n두 번째 QA")
        self.assertEqual(sections["qa"], "첫 QA 통과")
        self.assertEqual(sections["validation"], discord.MISSING)

    def test_fenced_code_blocks_are_removed_before_section_extraction(self):
        body = """## 작업 목적
안전한 알림
```env
AWS_SECRET_ACCESS_KEY=do-not-send
CLIENT_SECRET=also-do-not-send
```
## 주요 변경
변경 A
~~~~python
PRIVATE_KEY=hidden
~~~~
"""
        sections = discord.extract_sections(body)
        serialized = json.dumps(sections, ensure_ascii=False)
        self.assertIn("안전한 알림", serialized)
        self.assertNotIn("do-not-send", serialized)
        self.assertNotIn("also-do-not-send", serialized)
        self.assertNotIn("PRIVATE_KEY", serialized)

    def test_workflow_api_failure_uses_explicit_fallback(self):
        payload = {"workflow_run": {"id": 7, "name": "Repository Validation", "conclusion": "failure", "head_branch": "ops/sre", "head_sha": "abc", "html_url": "https://example.invalid/run", "pull_requests": [{"number": 40}]}}
        with mock.patch.dict("os.environ", {"GITHUB_ACTOR": "runner"}):
            context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", FakeApi())
        self.assertEqual(context["number"], 40)
        self.assertEqual(context["ci_jobs"], discord.UNKNOWN)
        self.assertEqual(context["unresolved_threads"], discord.UNKNOWN)
        self.assertEqual(context["next_action"], "실패 Job과 Step 확인 후 최소 수정")

    def test_stale_workflow_run_preserves_run_sha_and_marks_context(self):
        payload = {"workflow_run": {"id": 8, "name": "Repository Validation", "conclusion": "success", "head_branch": "ops/sre", "head_sha": "old-sha", "html_url": "https://example.invalid/run", "pull_requests": [{"number": 40}]}}
        context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", StalePrApi())
        self.assertEqual(context["sha"], "old-sha")
        self.assertEqual(context["changed_files"], discord.UNKNOWN)
        self.assertIn("이전 SHA", context["risks"])

    def test_secret_patterns_are_redacted(self):
        source = '''https://discord.com/api/webhooks/123/opaque ghp_abcdefghijklmnopqrstuvwxyz
password=hunter2
"token": "json-secret"
client_secret=client-value
API_KEY: api-value
Authorization: Bearer auth-value
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
-----BEGIN PRIVATE KEY-----
private-material
-----END PRIVATE KEY-----'''
        cleaned = discord.clean_text(source)
        for secret in ("opaque", "ghp_", "hunter2", "json-secret", "client-value", "api-value", "auth-value", "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "private-material"):
            self.assertNotIn(secret, cleaned)
        self.assertIn("[REDACTED", cleaned)

    def test_issue_body_is_not_forwarded(self):
        payload = {"action": "opened", "issue": {"number": 9, "title": "OPS-007 문의", "body": "password=do-not-send", "user": {"login": "author"}}}
        context = discord.collect("issues", payload, "guseoh/pawcycle-commerce", FakeApi())
        self.assertNotIn("do-not-send", repr(context))
        self.assertEqual(context["purpose"], discord.MISSING)

    def test_review_threads_are_counted_across_all_pages(self):
        api = discord.GitHubApi("guseoh/pawcycle-commerce", "")
        first = {"repository": {"pullRequest": {"reviewThreads": {
            "nodes": [{"isResolved": True} for _ in range(100)],
            "pageInfo": {"hasNextPage": True, "endCursor": "page-2"},
        }}}}
        second = {"repository": {"pullRequest": {"reviewThreads": {
            "nodes": [{"isResolved": False}, {"isResolved": True}],
            "pageInfo": {"hasNextPage": False, "endCursor": "page-2"},
        }}}}
        with mock.patch.object(api, "graphql", side_effect=[first, second]) as graphql:
            self.assertEqual(api.unresolved_threads(40), 1)
        self.assertEqual(graphql.call_count, 2)
        self.assertEqual(graphql.call_args_list[1].args[1]["after"], "page-2")

    def test_review_thread_pagination_failure_and_repeated_cursor_are_unknown(self):
        api = discord.GitHubApi("guseoh/pawcycle-commerce", "")
        page = {"repository": {"pullRequest": {"reviewThreads": {
            "nodes": [{"isResolved": False}],
            "pageInfo": {"hasNextPage": True, "endCursor": "same"},
        }}}}
        with mock.patch.object(api, "graphql", side_effect=[page, page]):
            self.assertEqual(api.unresolved_threads(40), discord.UNKNOWN)
        with mock.patch.object(api, "graphql", side_effect=[page, None]):
            self.assertEqual(api.unresolved_threads(40), discord.UNKNOWN)

    def test_graphql_post_uses_json_content_type_but_get_does_not(self):
        api = discord.GitHubApi("guseoh/pawcycle-commerce", "")
        with mock.patch("urllib.request.urlopen", side_effect=[io.BytesIO(b'{}'), io.BytesIO(b'{}')]) as urlopen:
            api.get("/repos/guseoh/pawcycle-commerce")
            api.graphql("query { viewer { login } }", {})
        get_request = urlopen.call_args_list[0].args[0]
        post_request = urlopen.call_args_list[1].args[0]
        self.assertIsNone(get_request.get_header("Content-type"))
        self.assertEqual(post_request.get_header("Content-type"), "application/json")

    def test_ci_conclusions_map_to_distinct_events_and_actions(self):
        cases = {
            "failure": ("ci_failure", "실패 Job과 Step"),
            "timed_out": ("ci_timed_out", "timeout 원인"),
            "cancelled": ("ci_cancelled", "취소 원인"),
            "neutral": ("ci_neutral", "중립 결론"),
            "skipped": ("ci_skipped", "skip 조건"),
            "unexpected": ("ci_unknown", "Actions 실행 상태"),
        }
        for conclusion, (event, action) in cases.items():
            with self.subTest(conclusion=conclusion):
                payload = {"workflow_run": {"id": 7, "name": "Repository Validation", "conclusion": conclusion, "head_branch": "ops/sre", "head_sha": "abc", "pull_requests": [{"number": 40}]}}
                context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", FakeApi())
                self.assertEqual(context["event"], event)
                self.assertIn(action, context["next_action"])

    def test_ci_success_next_action_reflects_review_thread_state(self):
        payload = {"workflow_run": {"id": 8, "name": "Repository Validation", "conclusion": "success", "head_branch": "ops/sre", "head_sha": "new-sha", "pull_requests": [{"number": 40}]}}
        cases = {
            0: "사용자 최종 검토",
            2: "미해결 리뷰 반영 필요",
            discord.UNKNOWN: discord.UNKNOWN,
        }
        for unresolved, expected in cases.items():
            with self.subTest(unresolved=unresolved):
                context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", CurrentPrApi(unresolved))
                self.assertEqual(context["next_action"], expected)


if __name__ == "__main__":
    unittest.main()

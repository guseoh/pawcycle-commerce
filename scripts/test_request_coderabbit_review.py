#!/usr/bin/env python3
"""Regressions for automated CodeRabbit review requests."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from pathlib import Path
import unittest

from scripts.request_coderabbit_review import (
    BOT_LOGIN,
    HANDOFF_MARKER,
    evaluate_review_request,
    request_comment,
)

ROOT = Path(__file__).resolve().parents[1]
NOW = datetime(2026, 9, 6, 6, 30, tzinfo=timezone.utc)
HEAD = "a" * 40


def review_ready_pr(*, head_sha: str = HEAD, body: str = HANDOFF_MARKER, head_repo: str = "guseoh/pawcycle-commerce") -> dict:
    return {
        "number": 300,
        "state": "open",
        "body": body,
        "base": {"ref": "main"},
        "head": {"sha": head_sha, "repo": {"full_name": head_repo}},
    }


def request_comment_payload(created_at: datetime, sha: str = HEAD) -> dict:
    return {
        "user": {"login": BOT_LOGIN},
        "body": request_comment(sha),
        "created_at": created_at.isoformat().replace("+00:00", "Z"),
    }


class CodeRabbitReviewRequestTest(unittest.TestCase):
    def decide(self, pr: dict | None = None, *, stars: int = 0, reviews: list[dict] | None = None, comments: list[dict] | None = None):
        return evaluate_review_request(
            pr or review_ready_pr(),
            repository="guseoh/pawcycle-commerce",
            stars=stars,
            reviews=reviews or [],
            recent_repository_comments=comments or [],
            now=NOW,
        )

    def test_review_ready_current_repo_pr_requests_review(self) -> None:
        decision = self.decide()
        self.assertTrue(decision.request)

    def test_missing_handoff_marker_does_not_consume_review_budget(self) -> None:
        decision = self.decide(review_ready_pr(body="## 작업\nHARNESS-LEAN-003"))
        self.assertFalse(decision.request)
        self.assertIn("handoff marker", decision.reason)

    def test_fork_pr_does_not_consume_review_budget(self) -> None:
        decision = self.decide(review_ready_pr(head_repo="someone/fork"))
        self.assertFalse(decision.request)
        self.assertIn("not from this repository", decision.reason)

    def test_native_auto_review_is_preferred_once_repository_reaches_threshold(self) -> None:
        decision = self.decide(stars=10)
        self.assertFalse(decision.request)
        self.assertIn("native CodeRabbit auto-review", decision.reason)

    def test_current_head_review_prevents_duplicate_request(self) -> None:
        reviews = [{"user": {"login": "coderabbitai[bot]"}, "commit_id": HEAD, "state": "COMMENTED"}]
        decision = self.decide(reviews=reviews)
        self.assertFalse(decision.request)
        self.assertIn("already has", decision.reason)

    def test_old_review_does_not_cover_new_head(self) -> None:
        reviews = [{"user": {"login": "coderabbitai[bot]"}, "commit_id": "b" * 40, "state": "COMMENTED"}]
        decision = self.decide(reviews=reviews)
        self.assertTrue(decision.request)

    def test_repository_wide_cooldown_prevents_rate_limit_spam(self) -> None:
        comments = [request_comment_payload(NOW - timedelta(minutes=30), "b" * 40)]
        decision = self.decide(comments=comments)
        self.assertFalse(decision.request)
        self.assertIn("cooldown", decision.reason)

    def test_stale_request_is_retried_when_latest_head_is_still_unreviewed(self) -> None:
        comments = [request_comment_payload(NOW - timedelta(minutes=66))]
        decision = self.decide(comments=comments)
        self.assertTrue(decision.request)

    def test_only_github_actions_request_markers_control_cooldown(self) -> None:
        comments = [{
            "user": {"login": "random-user"},
            "body": request_comment(HEAD),
            "created_at": (NOW - timedelta(minutes=10)).isoformat().replace("+00:00", "Z"),
        }]
        decision = self.decide(comments=comments)
        self.assertTrue(decision.request)

    def test_workflow_uses_trusted_base_and_scheduled_retry(self) -> None:
        workflow = (ROOT / ".github" / "workflows" / "request-coderabbit-review.yml").read_text(encoding="utf-8")
        self.assertIn("pull_request_target:", workflow)
        self.assertIn("schedule:", workflow)
        self.assertIn("issues: write", workflow)
        self.assertIn("pull-requests: write", workflow)
        self.assertNotIn("pull-requests: read", workflow)
        self.assertIn("ref: main", workflow)
        self.assertIn("python scripts/request_coderabbit_review.py", workflow)
        self.assertNotIn("github.event.pull_request.head.sha", workflow)


if __name__ == "__main__":
    unittest.main()

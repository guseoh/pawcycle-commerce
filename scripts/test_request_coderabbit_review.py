#!/usr/bin/env python3
"""Regressions for CodeRabbit manual-review handoff."""

from __future__ import annotations

from pathlib import Path
import unittest

from scripts.request_coderabbit_review import (
    BOT_LOGIN,
    HANDOFF_MARKER,
    evaluate_review_handoff,
    manual_review_reminder,
)

ROOT = Path(__file__).resolve().parents[1]
HEAD = "a" * 40


def review_ready_pr(
    *,
    head_sha: str = HEAD,
    body: str = HANDOFF_MARKER,
    head_repo: str = "guseoh/pawcycle-commerce",
    draft: bool = False,
) -> dict:
    return {
        "number": 300,
        "state": "open",
        "draft": draft,
        "body": body,
        "base": {"ref": "main"},
        "head": {"sha": head_sha, "repo": {"full_name": head_repo}},
    }


def reminder_comment_payload(sha: str = HEAD, login: str = BOT_LOGIN) -> dict:
    return {
        "user": {"login": login},
        "body": manual_review_reminder(sha),
    }


def pull_request_target_types(workflow: str) -> list[str]:
    lines = workflow.splitlines()
    for index, line in enumerate(lines):
        if line == "  pull_request_target:":
            for child in lines[index + 1 :]:
                if child and not child.startswith("    "):
                    break
                stripped = child.strip()
                if stripped.startswith("types: [") and stripped.endswith("]"):
                    values = stripped.removeprefix("types: [").removesuffix("]")
                    return [value.strip() for value in values.split(",") if value.strip()]
    return []


class CodeRabbitReviewRequestTest(unittest.TestCase):
    def decide(self, pr: dict | None = None, *, stars: int = 0, reviews: list[dict] | None = None, comments: list[dict] | None = None):
        return evaluate_review_handoff(
            pr or review_ready_pr(),
            repository="guseoh/pawcycle-commerce",
            stars=stars,
            reviews=reviews or [],
            recent_repository_comments=comments or [],
        )

    def test_review_ready_current_repo_pr_needs_manual_review_reminder(self) -> None:
        decision = self.decide()
        self.assertTrue(decision.remind)
        self.assertIn("user-authenticated", decision.reason)

    def test_draft_pr_does_not_receive_manual_review_reminder(self) -> None:
        decision = self.decide(review_ready_pr(draft=True))
        self.assertFalse(decision.remind)
        self.assertIn("draft", decision.reason)

    def test_missing_handoff_marker_does_not_receive_manual_review_reminder(self) -> None:
        decision = self.decide(review_ready_pr(body="## 작업\nHARNESS-LEAN-003"))
        self.assertFalse(decision.remind)
        self.assertIn("handoff marker", decision.reason)

    def test_fork_pr_does_not_receive_manual_review_reminder(self) -> None:
        decision = self.decide(review_ready_pr(head_repo="someone/fork"))
        self.assertFalse(decision.remind)
        self.assertIn("not from this repository", decision.reason)

    def test_native_auto_review_is_preferred_once_repository_reaches_threshold(self) -> None:
        decision = self.decide(stars=10)
        self.assertFalse(decision.remind)
        self.assertIn("native CodeRabbit auto-review", decision.reason)

    def test_current_head_review_prevents_manual_review_reminder(self) -> None:
        reviews = [{"user": {"login": "coderabbitai[bot]"}, "commit_id": HEAD, "state": "COMMENTED"}]
        decision = self.decide(reviews=reviews)
        self.assertFalse(decision.remind)
        self.assertIn("already has", decision.reason)

    def test_old_review_does_not_cover_new_head(self) -> None:
        reviews = [{"user": {"login": "coderabbitai[bot]"}, "commit_id": "b" * 40, "state": "COMMENTED"}]
        decision = self.decide(reviews=reviews)
        self.assertTrue(decision.remind)

    def test_current_head_reminder_prevents_duplicate_comment(self) -> None:
        decision = self.decide(comments=[reminder_comment_payload()])
        self.assertFalse(decision.remind)
        self.assertIn("already has a manual-review reminder", decision.reason)

    def test_old_head_reminder_does_not_cover_new_head(self) -> None:
        decision = self.decide(comments=[reminder_comment_payload("b" * 40)])
        self.assertTrue(decision.remind)

    def test_only_github_actions_reminder_marker_suppresses_duplicate(self) -> None:
        decision = self.decide(comments=[reminder_comment_payload(login="random-user")])
        self.assertTrue(decision.remind)

    def test_reminder_does_not_issue_coderabbit_command_as_bot(self) -> None:
        body = manual_review_reminder(HEAD)
        self.assertIn("`@coderabbitai review`", body)
        self.assertNotIn("\n@coderabbitai review\n", body)
        self.assertIn(f"pawcycle-coderabbit-manual-review-needed:{HEAD}", body)

    def test_workflow_uses_trusted_base_without_scheduled_retry(self) -> None:
        workflow = (ROOT / ".github" / "workflows" / "request-coderabbit-review.yml").read_text(encoding="utf-8")
        self.assertIn("pull_request_target:", workflow)
        self.assertIn("ready_for_review", pull_request_target_types(workflow))
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotIn("schedule:", workflow)
        self.assertIn("issues: write", workflow)
        self.assertIn("pull-requests: write", workflow)
        self.assertIn("ref: main", workflow)
        self.assertIn("python scripts/request_coderabbit_review.py", workflow)
        self.assertNotIn("github.event.pull_request.head.sha", workflow)


if __name__ == "__main__":
    unittest.main()

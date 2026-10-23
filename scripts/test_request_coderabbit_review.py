#!/usr/bin/env python3
"""Regressions for CodeRabbit manual-review handoff."""

from __future__ import annotations

from pathlib import Path
import json
import unittest

from scripts.request_coderabbit_review import (
    BOT_LOGIN,
    HANDOFF_MARKER,
    completed_summary_evidence,
    current_review_evidence,
    evaluate_review_handoff,
    pull_request_comments,
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




def completed_summary(
    head_sha: str = HEAD,
    *,
    base_sha: str = "b" * 40,
    covered_sha: str | None = None,
    source_sha: str | None = None,
    user: str = "coderabbitai[bot]",
    message: str = "No actionable comments were generated in the recent review.",
    kind: str = "reviewed",
) -> dict:
    coverage = {
        "sourceCommitId": source_sha or head_sha,
        "coveredCommitId": covered_sha or head_sha,
        "kind": kind,
    }
    body = (
        "<!-- This is an auto-generated comment: summarize by coderabbit.ai -->\n"
        "<!-- recent_review_start -->\n"
        + message + "\n"
        + "Reviewing files that changed from the base of the PR and between "
        + base_sha + " and " + head_sha + ".\n"
        + "<!-- recent_review_end -->\n"
        + "<!-- final_review_risk_coverage:"
        + json.dumps(coverage, separators=(",", ":"))
        + " -->"
    )
    return {"user": {"login": user, "type": "Bot"}, "body": body}


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


    def test_current_head_completed_summary_suppresses_reminder(self) -> None:
        summary = completed_summary()
        evidence = current_review_evidence([], [summary], HEAD)
        self.assertTrue(evidence.performed)
        self.assertEqual(evidence.source, "summary")
        self.assertEqual(evidence.findings_status, "none_reported_in_recent_review")
        self.assertFalse(self.decide(comments=[summary]).remind)

    def test_summary_findings_and_review_execution_are_separate(self) -> None:
        summary = completed_summary(message="2 actionable comments were generated in the recent review.")
        evidence = current_review_evidence([], [summary], HEAD)
        self.assertTrue(evidence.performed)
        self.assertEqual(evidence.findings_status, "triage_required")
        self.assertFalse(self.decide(comments=[summary]).remind)

    def test_formal_review_precedes_summary_but_still_requires_triage(self) -> None:
        summary = completed_summary()
        reviews = [{"user": {"login": "coderabbitai[bot]"}, "commit_id": HEAD, "state": "COMMENTED"}]
        evidence = current_review_evidence(reviews, [summary], HEAD)
        self.assertEqual(evidence.source, "submission")
        self.assertEqual(evidence.findings_status, "triage_required")

    def test_pending_and_dismissed_submissions_are_not_valid_evidence(self) -> None:
        for state in ("PENDING", "DISMISSED"):
            with self.subTest(state=state):
                reviews = [{"user": {"login": "coderabbitai[bot]"}, "commit_id": HEAD, "state": state}]
                self.assertTrue(self.decide(reviews=reviews).remind)

    def test_stale_and_mismatched_summary_coverage_rejected(self) -> None:
        old_head = "c" * 40
        for summary in (
            completed_summary(head_sha=old_head),
            completed_summary(covered_sha=old_head),
            completed_summary(source_sha=old_head),
            completed_summary(kind="skipped"),
        ):
            with self.subTest(body=summary["body"][-125:]):
                self.assertIsNone(completed_summary_evidence(summary, HEAD))
                self.assertTrue(self.decide(comments=[summary]).remind)

    def test_spoofed_or_non_bot_author_rejected(self) -> None:
        for summary in (
            completed_summary(user="guseoh"),
            completed_summary(user="coderabbitai-copy[bot]"),
            {**completed_summary(), "user": {"login": "coderabbitai[bot]", "type": "User"}},
        ):
            self.assertTrue(self.decide(comments=[summary]).remind)

    def test_incomplete_skip_command_and_walkthrough_do_not_count(self) -> None:
        for body in (
            "Review finished.",
            "<!-- This is an auto-generated comment: skip review by coderabbit.ai -->\n"
            + "<!-- recent_review_start -->\nSkipped\n<!-- recent_review_end -->",
            "<!-- walkthrough_start -->\nWalkthrough only\n<!-- walkthrough_end -->",
            "<!-- This is an auto-generated comment: summarize by coderabbit.ai -->\n"
            + "<!-- recent_review_start -->\nNo actionable comments\n<!-- recent_review_end -->",
        ):
            self.assertTrue(self.decide(comments=[{
                "user": {"login": "coderabbitai[bot]", "type": "Bot"}, "body": body,
            }]).remind)

    def test_edited_summary_is_checked_at_current_body_not_historical_head(self) -> None:
        old_sha = "c" * 40
        edited = completed_summary(head_sha=old_sha)
        self.assertFalse(self.decide(
            review_ready_pr(head_sha=old_sha), comments=[edited]
        ).remind)
        edited["body"] = completed_summary()["body"]
        self.assertTrue(self.decide(
            review_ready_pr(head_sha=old_sha), comments=[edited]
        ).remind)
        self.assertFalse(self.decide(comments=[edited]).remind)

    def test_incremental_summary_must_end_at_current_head(self) -> None:
        self.assertFalse(self.decide(comments=[
            completed_summary(base_sha="d" * 40)
        ]).remind)
        self.assertTrue(self.decide(comments=[
            completed_summary(head_sha="d" * 40, base_sha=HEAD)
        ]).remind)

    def test_draft_duplicate_and_summary_do_not_generate_reminders(self) -> None:
        self.assertFalse(self.decide(review_ready_pr(draft=True), comments=[
            completed_summary()
        ]).remind)
        self.assertFalse(self.decide(comments=[
            completed_summary(), reminder_comment_payload()
        ]).remind)

    def test_comments_are_scoped_to_pr_and_pages_are_not_silently_truncated(self) -> None:
        class FakeApi:
            def __init__(self) -> None:
                self.paths: list[str] = []

            def get(self, path: str) -> list[dict]:
                self.paths.append(path)
                if path.endswith("&page=1"):
                    return [{} for _ in range(100)]
                return [completed_summary()]

        api = FakeApi()
        comments = pull_request_comments(api, "guseoh/pawcycle-commerce", 395)
        self.assertEqual(len(comments), 101)
        self.assertTrue(all("/issues/395/comments?" in path for path in api.paths))
        self.assertEqual(len(api.paths), 2)

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

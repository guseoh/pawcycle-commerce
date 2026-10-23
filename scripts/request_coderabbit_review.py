#!/usr/bin/env python3
"""Manage CodeRabbit manual-review handoff for review-ready PawCycle pull requests.

The workflow uses this script from the trusted base branch. It never executes
pull-request code. For OSS repositories that require manual CodeRabbit reviews,
GitHub Actions posts a one-time per-HEAD reminder instead of issuing the review
command itself.
"""

from __future__ import annotations

from dataclasses import dataclass
import json
import os
import re
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen

HANDOFF_MARKER = "<!-- pawcycle-ai-handoff: review-ready -->"
REMINDER_MARKER_RE = re.compile(r"<!-- pawcycle-coderabbit-manual-review-needed:([0-9a-f]{40}) -->")
NATIVE_AUTO_REVIEW_STAR_THRESHOLD = 10
BOT_LOGIN = "github-actions[bot]"
CODERABBIT_LOGIN = "coderabbitai[bot]"
SUMMARY_MARKER = "<!-- This is an auto-generated comment: summarize by coderabbit.ai -->"
RECENT_START = "<!-- recent_review_start -->"
RECENT_END = "<!-- recent_review_end -->"
SUMMARY_RANGE_RE = re.compile(
    r"Reviewing files that changed from the base of the PR and between "
    r"([0-9a-f]{40}) and ([0-9a-f]{40})\."
)
SUMMARY_COVERAGE_RE = re.compile(r"<!-- final_review_risk_coverage:(\{[^\n]+\}) -->")
NO_ACTIONABLE = "No actionable comments were generated in the recent review."


@dataclass(frozen=True)
class ReviewHandoffDecision:
    remind: bool
    reason: str


@dataclass(frozen=True)
class ReviewEvidence:
    """Review execution evidence, never an automatic merge-readiness decision."""

    performed: bool
    source: str = "none"
    findings_status: str = "unverified"


def is_coderabbit_login(login: str) -> bool:
    return (login or "").lower() == CODERABBIT_LOGIN


def has_current_coderabbit_review(reviews: list[dict[str, Any]], head_sha: str) -> bool:
    return any(
        is_coderabbit_login(str(((review.get("user") or {}).get("login") or "")))
        and review.get("commit_id") == head_sha
        and str(review.get("state") or "").upper() in {"APPROVED", "COMMENTED", "CHANGES_REQUESTED"}
        for review in reviews
    )


def completed_summary_evidence(
    comment: dict[str, Any], head_sha: str
) -> ReviewEvidence | None:
    """Accept only a CodeRabbit-bot summary with verified current-HEAD coverage."""
    author = comment.get("user") or {}
    if not is_coderabbit_login(str(author.get("login") or "")):
        return None
    if author.get("type") not in (None, "Bot"):
        return None
    body = str(comment.get("body") or "")
    if (
        body.count(SUMMARY_MARKER) != 1
        or body.count(RECENT_START) != 1
        or body.count(RECENT_END) != 1
        or "<!-- This is an auto-generated comment: skip review by coderabbit.ai -->" in body
    ):
        return None

    start = body.find(RECENT_START) + len(RECENT_START)
    end = body.find(RECENT_END)
    if end <= start:
        return None
    recent = body[start:end]
    ranges = SUMMARY_RANGE_RE.findall(recent)
    if len(ranges) != 1 or ranges[0][1] != head_sha:
        return None

    markers = SUMMARY_COVERAGE_RE.findall(body)
    if len(markers) != 1:
        return None
    try:
        coverage = json.loads(markers[0])
    except (TypeError, ValueError):
        return None
    if (
        not isinstance(coverage, dict)
        or coverage.get("kind") != "reviewed"
        or coverage.get("sourceCommitId") != head_sha
        or coverage.get("coveredCommitId") != head_sha
    ):
        return None

    findings_status = (
        "none_reported_in_recent_review"
        if NO_ACTIONABLE in recent
        else "triage_required"
    )
    return ReviewEvidence(True, "summary", findings_status)


def current_review_evidence(
    reviews: list[dict[str, Any]], comments: list[dict[str, Any]], head_sha: str
) -> ReviewEvidence:
    """Submission takes precedence; both sources require separate findings triage."""
    if has_current_coderabbit_review(reviews, head_sha):
        return ReviewEvidence(True, "submission", "triage_required")
    for comment in comments:
        evidence = completed_summary_evidence(comment, head_sha)
        if evidence is not None:
            return evidence
    return ReviewEvidence(False)


def has_current_manual_review_reminder(comments: list[dict[str, Any]], head_sha: str) -> bool:
    for comment in comments:
        login = str(((comment.get("user") or {}).get("login") or ""))
        body = str(comment.get("body") or "")
        match = REMINDER_MARKER_RE.search(body)
        if login == BOT_LOGIN and match and match.group(1) == head_sha:
            return True
    return False


def evaluate_review_handoff(
    pr: dict[str, Any],
    *,
    repository: str,
    stars: int,
    reviews: list[dict[str, Any]],
    recent_repository_comments: list[dict[str, Any]],
) -> ReviewHandoffDecision:
    if pr.get("state") != "open":
        return ReviewHandoffDecision(remind=False, reason="pull request is not open")

    if bool(pr.get("draft")):
        return ReviewHandoffDecision(remind=False, reason="pull request is draft")

    base_ref = str(((pr.get("base") or {}).get("ref") or ""))
    if base_ref != "main":
        return ReviewHandoffDecision(remind=False, reason="base branch is not main")

    head_repo = str((((pr.get("head") or {}).get("repo") or {}).get("full_name") or ""))
    if head_repo != repository:
        return ReviewHandoffDecision(remind=False, reason="pull request head is not from this repository")

    body = str(pr.get("body") or "")
    if HANDOFF_MARKER not in body:
        return ReviewHandoffDecision(remind=False, reason="review-ready handoff marker is absent")

    if stars >= NATIVE_AUTO_REVIEW_STAR_THRESHOLD:
        return ReviewHandoffDecision(remind=False, reason="native CodeRabbit auto-review is expected")

    head_sha = str(((pr.get("head") or {}).get("sha") or ""))
    if not head_sha:
        return ReviewHandoffDecision(remind=False, reason="pull request head SHA is missing")

    evidence = current_review_evidence(reviews, recent_repository_comments, head_sha)
    if evidence.performed:
        return ReviewHandoffDecision(
            remind=False,
            reason=f"latest HEAD already has verified CodeRabbit {evidence.source} evidence; findings require separate triage",
        )

    if has_current_manual_review_reminder(recent_repository_comments, head_sha):
        return ReviewHandoffDecision(remind=False, reason="latest HEAD already has a manual-review reminder")

    return ReviewHandoffDecision(remind=True, reason="latest HEAD needs a user-authenticated CodeRabbit review")


class GitHubApi:
    def __init__(self, token: str, api_url: str) -> None:
        self.token = token
        self.api_url = api_url.rstrip("/")

    def request(self, method: str, path: str, payload: dict[str, Any] | None = None) -> Any:
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        request = Request(
            f"{self.api_url}{path}",
            data=data,
            method=method,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "Content-Type": "application/json",
            },
        )
        try:
            with urlopen(request, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"GitHub API {method} {path} failed: {exc.code} {body}") from exc

    def get(self, path: str) -> Any:
        return self.request("GET", path)

    def post(self, path: str, payload: dict[str, Any]) -> Any:
        return self.request("POST", path, payload)


def load_event() -> dict[str, Any]:
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if not event_path:
        return {}
    with open(event_path, encoding="utf-8") as handle:
        return json.load(handle)


def manual_review_reminder(head_sha: str) -> str:
    return (
        "CodeRabbit manual review is required for this HEAD.\n\n"
        "This OSS repository currently requires a user-authenticated manual review trigger. "
        "Use CodeRabbit's **Trigger review** control or add the following PR comment from your GitHub user account:\n\n"
        "`@coderabbitai review`\n\n"
        "Repository automation intentionally does not issue the command itself because GitHub Actions-authored "
        "review commands are not a reliable manual-review trigger in this repository setup.\n\n"
        f"<!-- pawcycle-coderabbit-manual-review-needed:{head_sha} -->"
    )


def candidate_pull_requests(api: GitHubApi, repository: str, event: dict[str, Any]) -> list[dict[str, Any]]:
    event_pr = event.get("pull_request")
    if isinstance(event_pr, dict):
        return [event_pr]
    pulls = api.get(f"/repos/{repository}/pulls?state=open&base=main&sort=updated&direction=desc&per_page=100")
    if not isinstance(pulls, list):
        raise RuntimeError("GitHub open pull request response is not a list")
    return pulls


def pull_request_comments(
    api: GitHubApi, repository: str, number: int
) -> list[dict[str, Any]]:
    """Scope evidence/reminders to this PR, including edited comments and pagination."""
    all_comments: list[dict[str, Any]] = []
    for page in range(1, 21):
        comments = api.get(
            f"/repos/{repository}/issues/{number}/comments?per_page=100&page={page}"
        )
        if not isinstance(comments, list):
            raise RuntimeError(f"PR #{number} comments response is not a list")
        all_comments.extend(comments)
        if len(comments) < 100:
            return all_comments
    # Never silently assume review completion or post a duplicate reminder.
    raise RuntimeError(f"PR #{number} comments exceed safe pagination limit")


def main() -> int:
    token = os.environ.get("GITHUB_TOKEN")
    repository = os.environ.get("GITHUB_REPOSITORY")
    api_url = os.environ.get("GITHUB_API_URL", "https://api.github.com")
    if not token or not repository:
        raise RuntimeError("GITHUB_TOKEN and GITHUB_REPOSITORY are required")

    api = GitHubApi(token, api_url)
    event = load_event()
    repo_metadata = api.get(f"/repos/{repository}")
    stars = int(repo_metadata.get("stargazers_count") or 0)
    reminded = False
    for pr in candidate_pull_requests(api, repository, event):
        number = int(pr.get("number") or 0)
        if number <= 0:
            continue
        reviews = api.get(f"/repos/{repository}/pulls/{number}/reviews?per_page=100")
        if not isinstance(reviews, list):
            raise RuntimeError(f"GitHub review response for PR #{number} is not a list")

        comments = pull_request_comments(api, repository, number)
        decision = evaluate_review_handoff(
            pr,
            repository=repository,
            stars=stars,
            reviews=reviews,
            recent_repository_comments=comments,
        )
        print(f"PR #{number}: {decision.reason}")
        if not decision.remind:
            continue

        head_sha = str(((pr.get("head") or {}).get("sha") or ""))
        api.post(
            f"/repos/{repository}/issues/{number}/comments",
            {"body": manual_review_reminder(head_sha)},
        )
        print(f"PR #{number}: manual CodeRabbit review reminder posted for {head_sha}")
        reminded = True
        break

    if not reminded:
        print("No CodeRabbit manual-review reminder was needed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

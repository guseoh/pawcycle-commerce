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
CODERABBIT_LOGIN_PREFIX = "coderabbitai"


@dataclass(frozen=True)
class ReviewHandoffDecision:
    remind: bool
    reason: str


def is_coderabbit_login(login: str) -> bool:
    return (login or "").lower().startswith(CODERABBIT_LOGIN_PREFIX)


def has_current_coderabbit_review(reviews: list[dict[str, Any]], head_sha: str) -> bool:
    for review in reviews:
        login = str(((review.get("user") or {}).get("login") or ""))
        state = str(review.get("state") or "").upper()
        if is_coderabbit_login(login) and review.get("commit_id") == head_sha and state != "PENDING":
            return True
    return False


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
        return ReviewHandoffDecision(False, "pull request is not open")

    if bool(pr.get("draft")):
        return ReviewHandoffDecision(False, "pull request is draft")

    base_ref = str(((pr.get("base") or {}).get("ref") or ""))
    if base_ref != "main":
        return ReviewHandoffDecision(False, "base branch is not main")

    head_repo = str((((pr.get("head") or {}).get("repo") or {}).get("full_name") or ""))
    if head_repo != repository:
        return ReviewHandoffDecision(False, "pull request head is not from this repository")

    body = str(pr.get("body") or "")
    if HANDOFF_MARKER not in body:
        return ReviewHandoffDecision(False, "review-ready handoff marker is absent")

    if stars >= NATIVE_AUTO_REVIEW_STAR_THRESHOLD:
        return ReviewHandoffDecision(False, "native CodeRabbit auto-review is expected")

    head_sha = str(((pr.get("head") or {}).get("sha") or ""))
    if not head_sha:
        return ReviewHandoffDecision(False, "pull request head SHA is missing")

    if has_current_coderabbit_review(reviews, head_sha):
        return ReviewHandoffDecision(False, "latest HEAD already has a CodeRabbit review")

    if has_current_manual_review_reminder(recent_repository_comments, head_sha):
        return ReviewHandoffDecision(False, "latest HEAD already has a manual-review reminder")

    return ReviewHandoffDecision(True, "latest HEAD needs a user-authenticated CodeRabbit review")


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
    comments = api.get(f"/repos/{repository}/issues/comments?sort=created&direction=desc&per_page=100")
    if not isinstance(comments, list):
        raise RuntimeError("GitHub repository comment response is not a list")

    reminded = False
    for pr in candidate_pull_requests(api, repository, event):
        number = int(pr.get("number") or 0)
        if number <= 0:
            continue
        reviews = api.get(f"/repos/{repository}/pulls/{number}/reviews?per_page=100")
        if not isinstance(reviews, list):
            raise RuntimeError(f"GitHub review response for PR #{number} is not a list")

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

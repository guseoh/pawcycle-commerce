#!/usr/bin/env python3
"""Delete only a merged, unchanged task branch targeting dev (never main/dev)."""

from __future__ import annotations

import json
import os
import re
import sys
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

REPOSITORY = "guseoh/pawcycle-commerce"
KEEP = frozenset({"main", "dev"})
MAX_PR_PAGES = 20


def eligible_head(event: dict, repository: str) -> tuple[str, str] | None:
    """Accept only a same-repo task PR merged into dev."""
    pr = event.get("pull_request") or {}
    base = pr.get("base") or {}
    head = pr.get("head") or {}
    head_repo = head.get("repo") or {}
    name, sha = head.get("ref"), head.get("sha")
    if (
        repository != REPOSITORY
        or event.get("action") != "closed"
        or pr.get("merged") is not True
        or base.get("ref") != "dev"
        or head_repo.get("full_name") != repository
        or not isinstance(name, str)
        or name in KEEP
        or not isinstance(sha, str)
        or not re.fullmatch(r"[0-9a-f]{40}", sha)
    ):
        return None
    return name, sha


class GitHubAPI:
    def __init__(self, repository: str, token: str) -> None:
        if repository != REPOSITORY or not token:
            raise ValueError("Invalid repository or missing token")
        self.base = f"https://api.github.com/repos/{repository}/"
        self.headers = {
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
        }

    def _request(self, method: str, path: str):
        request = Request(self.base + path, headers=self.headers, method=method)
        try:
            with urlopen(request, timeout=20) as response:
                raw = response.read()
                return json.loads(raw) if raw else {}
        except HTTPError as exc:
            if exc.code == 404:
                return None
            raise

    def get(self, path: str):
        return self._request("GET", path)

    def delete(self, path: str) -> bool:
        return self._request("DELETE", path) is not None


def has_open_pr_reference(api, branch: str) -> bool:
    """Avoid deleting branches still used as heads or bases of open PRs."""
    for page in range(1, MAX_PR_PAGES + 1):
        prs = api.get(f"pulls?state=open&per_page=100&page={page}")
        if not isinstance(prs, list):
            raise RuntimeError("Unable to verify open pull requests")
        for pr in prs:
            head = pr.get("head") or {}
            base = pr.get("base") or {}
            head_repo = head.get("repo") or {}
            if (
                (head.get("ref") == branch and head_repo.get("full_name") == REPOSITORY)
                or base.get("ref") == branch
            ):
                return True
        if len(prs) < 100:
            return False
    raise RuntimeError("Open pull request pagination exceeded safety limit")


def cleanup(api, event: dict, repository: str) -> str:
    target = eligible_head(event, repository)
    if target is None:
        return "skip: ineligible event"

    name, expected_sha = target
    encoded = quote(name, safe="/")
    ref_path = f"git/ref/heads/{encoded}"
    delete_path = f"git/refs/heads/{encoded}"

    ref = api.get(ref_path)
    if ref is None:
        return "skip: branch already missing"
    if ref.get("ref") != f"refs/heads/{name}" or (ref.get("object") or {}).get("sha") != expected_sha:
        return "skip: branch HEAD changed"

    branch = api.get(f"branches/{encoded}")
    if branch is None:
        return "skip: branch already missing"
    if branch.get("protected") is not False:
        return "skip: branch protected or protection unknown"
    if has_open_pr_reference(api, name):
        return "skip: referenced by open PR"

    # The merged PR's head must still be current immediately before deletion.
    ref = api.get(ref_path)
    if ref is None or ref.get("ref") != f"refs/heads/{name}" or (ref.get("object") or {}).get("sha") != expected_sha:
        return "skip: branch HEAD changed"

    if not api.delete(delete_path):
        return "skip: branch already deleted"
    return f"deleted: {name}"


def main() -> int:
    event_path = os.environ["GITHUB_EVENT_PATH"]
    repository = os.environ["GITHUB_REPOSITORY"]
    with open(event_path, encoding="utf-8") as source:
        event = json.load(source)

    if eligible_head(event, repository) is None:
        print("skip: ineligible event")
        return 0

    token = os.environ["GITHUB_TOKEN"]
    print(cleanup(GitHubAPI(repository, token), event, repository))
    return 0


if __name__ == "__main__":
    sys.exit(main())

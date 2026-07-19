#!/usr/bin/env python3
"""Collect trusted, bounded Discord notification context from a GitHub event."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


UNKNOWN = "확인 불가"
MISSING = "기록 없음"
TASK_ID_PREFIXES = (
    "BOOTSTRAP",
    "PS",
    "ARCH",
    "FOUNDATION",
    "FRONTEND",
    "PRODUCT",
    "BUG",
    "PERF",
    "OPS",
    "SEC",
    "AUTH",
    "DOMAIN",
    "API",
    "UX",
    "DATA",
)
TASK_ID_PATTERN = rf"(?:HARNESS(?:-[A-Z][A-Z0-9]*)+-[0-9]{{3}}|(?:{'|'.join(TASK_ID_PREFIXES)})-[0-9]{{3}})"
TASK_LINE = re.compile(rf"(?im)^\s*(?:[-*]\s*)?작업\s*ID\s*:\s*`?({TASK_ID_PATTERN})`?\s*$")
FALLBACK_TASK = re.compile(rf"(?<![A-Z0-9]){TASK_ID_PATTERN}(?![A-Z0-9])", re.IGNORECASE)
SECRET_PATTERNS = (
    (re.compile(r"-----BEGIN [^-\r\n]*PRIVATE KEY-----.*?-----END [^-\r\n]*PRIVATE KEY-----", re.IGNORECASE | re.DOTALL), "[REDACTED_PRIVATE_KEY]"),
    (re.compile(r"https://(?:canary\.)?(?:discord(?:app)?\.com)/api/webhooks/[^\s`]+", re.IGNORECASE), "[REDACTED_WEBHOOK]"),
    (re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b"), "[REDACTED_TOKEN]"),
    (re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"), "[REDACTED_AWS_ACCESS_KEY]"),
    (re.compile(r'''(?i)(["']?authorization["']?\s*:\s*["']?bearer\s+)[^\s"']+'''), r"\1[REDACTED]"),
    (
        re.compile(
            r'''(?i)(["']?(?:authorization|password|token|secret|client[_-]?secret|api[_-]?key|apikey|access[_-]?key|aws_access_key_id|aws_secret_access_key|private_key)["']?\s*[:=]\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\s,;}\]]+)'''
        ),
        r"\1[REDACTED]",
    ),
)
ROLE_BY_BRANCH = {
    "spec/po": "Product Planner",
    "design/ux": "UX/UI Designer",
    "feat/be": "Backend Engineer",
    "feat/fe": "Frontend Engineer",
    "test/qa": "QA Engineer",
    "ops/sre": "Platform/SRE",
    "ops/tl": "Tech Lead",
}
SECTION_ALIASES = {
    "purpose": ("목적", "작업 목적"),
    "changes": ("변경 사항", "주요 변경", "변경 범위"),
    "process": ("처리 과정", "작업 과정"),
    "qa": ("QA와 구현 경계", "QA 검증", "QA 필요 여부"),
    "validation": ("검증", "실행한 검증"),
    "next": ("승인 후 다음 작업", "다음 작업"),
    "risks": ("남은 위험", "위험과 제한"),
}


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def clean_text(value: Any, limit: int = 700, multiline: bool = True) -> str:
    if value is None:
        return MISSING
    text = str(value)
    text = re.sub(r"<!--.*?-->", " ", text, flags=re.DOTALL)
    for pattern, replacement in SECRET_PATTERNS:
        text = pattern.sub(replacement, text)
    text = "".join(char for char in text if char in "\n\t" or ord(char) >= 32)
    text = text.replace("@everyone", "@\u200beveryone").replace("@here", "@\u200bhere")
    if multiline:
        lines = [re.sub(r"[ \t]+", " ", line).strip() for line in text.replace("\r", "").split("\n")]
        text = "\n".join(line for line in lines if line)
    else:
        text = re.sub(r"\s+", " ", text).strip()
    if not text:
        return MISSING
    return text if len(text) <= limit else text[: max(0, limit - 1)] + "…"


def extract_task_id(body: str = "", title: str = "", branch: str = "", issue_text: str = "") -> str:
    match = TASK_LINE.search(body or "")
    if match:
        return match.group(1).upper()
    for candidate in (title, branch, issue_text):
        match = FALLBACK_TASK.search(candidate or "")
        if match:
            return match.group(0).upper()
    return MISSING


def role_for_branch(branch: str) -> str:
    return ROLE_BY_BRANCH.get(branch or "", MISSING)


def strip_automatic_summary(body: str) -> str:
    body = re.sub(r"<!--.*?-->", "", body or "", flags=re.DOTALL)
    return re.split(r"(?im)^##\s+(?:Summary by CodeRabbit|CodeRabbit Summary)\s*$", body, maxsplit=1)[0]


def strip_fenced_code_blocks(body: str) -> str:
    kept: list[str] = []
    fence_char = ""
    fence_length = 0
    for line in (body or "").splitlines(keepends=True):
        marker = re.match(r"^\s*(`{3,}|~{3,})", line)
        if not fence_char:
            if marker:
                fence_char = marker.group(1)[0]
                fence_length = len(marker.group(1))
            else:
                kept.append(line)
            continue
        if re.match(rf"^\s*{re.escape(fence_char)}{{{fence_length},}}\s*$", line.rstrip("\r\n")):
            fence_char = ""
            fence_length = 0
    return "".join(kept)


def extract_sections(body: str) -> dict[str, str]:
    body = strip_fenced_code_blocks(strip_automatic_summary(body))
    headings = list(re.finditer(r"(?m)^##\s+(.+?)\s*$", body))
    sections: dict[str, str] = {key: MISSING for key in SECTION_ALIASES}
    for index, heading in enumerate(headings):
        name = clean_text(heading.group(1), 80, multiline=False)
        start = heading.end()
        end = headings[index + 1].start() if index + 1 < len(headings) else len(body)
        content = body[start:end]
        lines = []
        for line in content.splitlines():
            line = re.sub(r"^\s*[-*]\s+", "- ", line.strip())
            if line and not line.startswith("```"):
                lines.append(line)
            if len(lines) >= 6:
                break
        value = clean_text("\n".join(lines), 650)
        for key, aliases in SECTION_ALIASES.items():
            if any(alias.lower() in name.lower() for alias in aliases):
                if sections[key] == MISSING:
                    sections[key] = value
                break
    return sections


class GitHubApi:
    def __init__(self, repository: str, token: str, api_url: str = "https://api.github.com") -> None:
        self.repository = repository
        self.token = token
        self.api_url = api_url.rstrip("/")

    def _request(self, url: str, data: dict[str, Any] | None = None) -> Any:
        headers = {"Accept": "application/vnd.github+json", "User-Agent": "PawCycle-Discord-Context/1.0"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        encoded = None if data is None else json.dumps(data).encode("utf-8")
        if encoded is not None:
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(url, data=encoded, headers=headers, method="POST" if data else "GET")
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                return json.load(response)
        except urllib.error.HTTPError as exc:
            print(f"GitHub API 조회 실패: HTTP {exc.code}", file=sys.stderr)
        except urllib.error.URLError:
            print("GitHub API 조회 실패: 네트워크 오류", file=sys.stderr)
        return None

    def get(self, path: str) -> Any:
        return self._request(f"{self.api_url}{path}")

    def graphql(self, query: str, variables: dict[str, Any]) -> Any:
        result = self._request(f"{self.api_url}/graphql", {"query": query, "variables": variables})
        return result.get("data") if isinstance(result, dict) and not result.get("errors") else None

    def pull_request(self, number: int) -> dict[str, Any] | None:
        result = self.get(f"/repos/{self.repository}/pulls/{number}")
        return result if isinstance(result, dict) else None

    def workflow_jobs(self, run_id: int) -> list[dict[str, Any]] | None:
        result = self.get(f"/repos/{self.repository}/actions/runs/{run_id}/jobs?per_page=100")
        return result.get("jobs") if isinstance(result, dict) and isinstance(result.get("jobs"), list) else None

    def latest_validation(self, branch: str) -> str:
        encoded = urllib.parse.quote(branch, safe="")
        result = self.get(f"/repos/{self.repository}/actions/workflows/validate-conventions.yml/runs?branch={encoded}&per_page=20")
        if not isinstance(result, dict) or not isinstance(result.get("workflow_runs"), list):
            return UNKNOWN
        runs = [run for run in result["workflow_runs"] if run.get("name") == "Repository Validation"]
        if not runs:
            return UNKNOWN
        run = runs[0]
        return clean_text(run.get("conclusion") or run.get("status"), 40, multiline=False)

    def review_states(self, number: int) -> list[dict[str, str]] | None:
        result = self.get(f"/repos/{self.repository}/pulls/{number}/reviews?per_page=100")
        if not isinstance(result, list):
            return None
        latest: dict[str, dict[str, str]] = {}
        for review in result:
            login = ((review.get("user") or {}).get("login")) or MISSING
            latest[login] = {"reviewer": login, "state": str(review.get("state") or UNKNOWN).lower()}
        return list(latest.values())

    def unresolved_threads(self, number: int) -> int | str:
        owner, name = self.repository.split("/", 1)
        query = """
        query($owner:String!,$name:String!,$number:Int!,$after:String){
          repository(owner:$owner,name:$name){
            pullRequest(number:$number){
              reviewThreads(first:100,after:$after){
                nodes{isResolved}
                pageInfo{hasNextPage endCursor}
              }
            }
          }
        }
        """
        after: str | None = None
        seen_cursors: set[str] = set()
        unresolved = 0
        while True:
            data = self.graphql(query, {"owner": owner, "name": name, "number": number, "after": after})
            try:
                threads = data["repository"]["pullRequest"]["reviewThreads"]
                nodes = threads["nodes"]
                page_info = threads["pageInfo"]
                if not isinstance(nodes, list) or not isinstance(page_info, dict):
                    return UNKNOWN
                unresolved += sum(1 for node in nodes if isinstance(node, dict) and not node.get("isResolved"))
                if not page_info.get("hasNextPage"):
                    return unresolved
                cursor = page_info.get("endCursor")
                if not isinstance(cursor, str) or not cursor or cursor in seen_cursors:
                    return UNKNOWN
                seen_cursors.add(cursor)
                after = cursor
            except (KeyError, TypeError):
                return UNKNOWN


def default_context(repository: str, event: str, timestamp: str | None = None) -> dict[str, Any]:
    return {
        "notify": True,
        "event": event,
        "repository": repository,
        "number": MISSING,
        "task_id": MISSING,
        "role": MISSING,
        "actor": MISSING,
        "head": MISSING,
        "base": MISSING,
        "sha": MISSING,
        "title": MISSING,
        "purpose": MISSING,
        "changed_files": UNKNOWN,
        "additions": UNKNOWN,
        "deletions": UNKNOWN,
        "changes": MISSING,
        "process": MISSING,
        "validation": MISSING,
        "ci_status": UNKNOWN,
        "qa": MISSING,
        "ci_jobs": UNKNOWN,
        "failed_jobs": UNKNOWN,
        "reviews": UNKNOWN,
        "unresolved_threads": UNKNOWN,
        "mergeable": UNKNOWN,
        "draft": UNKNOWN,
        "merged": False,
        "status": MISSING,
        "risks": MISSING,
        "next_action": MISSING,
        "url": MISSING,
        "actions_url": MISSING,
        "timestamp": timestamp or utc_now(),
        "preview": False,
    }


def pr_context(context: dict[str, Any], pr: dict[str, Any], api: GitHubApi | None = None) -> dict[str, Any]:
    body = str(pr.get("body") or "")
    head = (pr.get("head") or {}).get("ref") or MISSING
    base = (pr.get("base") or {}).get("ref") or MISSING
    sections = extract_sections(body)
    context.update(
        {
            "number": pr.get("number", context["number"]),
            "task_id": extract_task_id(body, str(pr.get("title") or ""), head),
            "role": role_for_branch(head),
            "actor": ((pr.get("user") or {}).get("login")) or context["actor"],
            "head": head,
            "base": base,
            "sha": (pr.get("head") or {}).get("sha") or context["sha"],
            "title": clean_text(pr.get("title"), 180, multiline=False),
            "purpose": sections["purpose"],
            "changed_files": pr.get("changed_files", UNKNOWN),
            "additions": pr.get("additions", UNKNOWN),
            "deletions": pr.get("deletions", UNKNOWN),
            "changes": sections["changes"],
            "process": sections["process"],
            "validation": sections["validation"],
            "qa": sections["qa"],
            "risks": sections["risks"],
            "next_action": sections["next"],
            "mergeable": pr.get("mergeable", UNKNOWN),
            "draft": pr.get("draft", UNKNOWN),
            "merged": bool(pr.get("merged")),
            "url": pr.get("html_url") or context["url"],
        }
    )
    if api and isinstance(context["number"], int):
        context["unresolved_threads"] = api.unresolved_threads(context["number"])
        reviews = api.review_states(context["number"])
        context["reviews"] = reviews if reviews is not None else UNKNOWN
        context["ci_status"] = api.latest_validation(head)
    return context


def format_jobs(jobs: list[dict[str, Any]] | None) -> tuple[Any, Any]:
    if jobs is None:
        return UNKNOWN, UNKNOWN
    formatted = []
    failed = []
    for job in jobs:
        name = clean_text(job.get("name"), 100, multiline=False)
        conclusion = clean_text(job.get("conclusion") or job.get("status"), 40, multiline=False)
        formatted.append({"name": name, "conclusion": conclusion})
        if conclusion not in ("success", "skipped", "neutral"):
            step = next(
                (
                    clean_text(item.get("name"), 120, multiline=False)
                    for item in job.get("steps") or []
                    if item.get("conclusion") not in (None, "success", "skipped", "neutral")
                ),
                UNKNOWN,
            )
            failed.append({"name": name, "conclusion": conclusion, "step": step})
    return formatted, failed


def collect(event_name: str, payload: dict[str, Any], repository: str, api: GitHubApi, scenario: str = "", pr_number: str = "") -> dict[str, Any]:
    action = str(payload.get("action") or "")
    timestamp = payload.get("repository", {}).get("updated_at") or utc_now()
    context = default_context(repository, "suppressed", timestamp)
    context["actor"] = ((payload.get("sender") or {}).get("login")) or os.environ.get("GITHUB_ACTOR", MISSING)

    if event_name == "workflow_dispatch":
        if scenario == "pr_preview":
            if not re.fullmatch(r"[1-9][0-9]*", pr_number or ""):
                raise ValueError("pr_preview의 pr_number는 양의 정수여야 함")
            context["preview"] = True
            pr = api.pull_request(int(pr_number))
            if pr:
                pr_context(context, pr, api)
                if pr.get("merged"):
                    context["event"] = "pr_merged"
                    context["status"] = "병합 완료 Preview"
                    context["sha"] = pr.get("merge_commit_sha") or context["sha"]
                elif pr.get("state") != "open":
                    context["event"] = "connection_test"
                    context["status"] = "종료됨(미병합) Preview"
                    context["next_action"] = "PR 종료 사유 확인"
                elif pr.get("draft"):
                    context["event"] = "pr_draft"
                    context["status"] = "Draft Preview"
                else:
                    context["event"] = "pr_ready"
                    context["status"] = "리뷰 가능 Preview"
            else:
                context["event"] = "connection_test"
                context["status"] = "PR 상태 조회 실패 Preview"
                context["number"] = int(pr_number)
                context["next_action"] = "GitHub API 조회 상태 확인"
            return context
        context.update(
            {
                "event": "connection_test",
                "preview": True,
                "status": "연결 테스트",
                "title": "Discord 협업 알림 연결 테스트",
                "purpose": "실제 제품 이벤트가 아닌 수동 Preview입니다.",
                "next_action": "Discord 채널의 Preview 카드 수신 확인",
                "url": os.environ.get("GITHUB_SERVER_URL", "https://github.com") + "/" + repository + "/actions",
            }
        )
        return context

    if event_name == "pull_request_target":
        pr = payload.get("pull_request") or {}
        if action == "closed" and pr.get("merged"):
            event = "pr_merged"
        elif action == "synchronize":
            event = "pr_synchronize"
        elif action == "review_requested":
            event = "pr_review_requested"
        elif action in ("opened", "ready_for_review", "reopened"):
            event = "pr_draft" if pr.get("draft") else "pr_ready"
        else:
            context["notify"] = False
            return context
        context["event"] = event
        pr_context(context, pr, api)
        context["status"] = {
            "pr_merged": "병합 완료",
            "pr_synchronize": "새 커밋 반영",
            "pr_review_requested": "리뷰어 지정",
            "pr_draft": "Draft",
            "pr_ready": "리뷰 가능",
        }[event]
        context["timestamp"] = pr.get("merged_at") or pr.get("updated_at") or pr.get("created_at") or context["timestamp"]
        if event == "pr_synchronize":
            context["next_action"] = "Repository Validation 재실행 확인"
        elif event == "pr_draft":
            context["next_action"] = "작업 완료 후 Ready for review 전환"
        elif event == "pr_review_requested":
            context["next_action"] = "지정 리뷰어 검토"
        elif event == "pr_merged" and context["next_action"] == MISSING:
            context["next_action"] = "후속 작업 확인"
        if event == "pr_merged":
            context["sha"] = pr.get("merge_commit_sha") or context["sha"]
        return context

    if event_name == "pull_request_review":
        review = payload.get("review") or {}
        state = str(review.get("state") or "").lower()
        if state not in ("approved", "changes_requested"):
            context["notify"] = False
            return context
        context["event"] = "review_approved" if state == "approved" else "changes_requested"
        pr_context(context, payload.get("pull_request") or {}, api)
        reviewer = ((review.get("user") or {}).get("login")) or context["actor"]
        context["actor"] = reviewer
        context["status"] = "승인" if state == "approved" else "변경 요청"
        context["review_body"] = clean_text(review.get("body"), 280)
        context["timestamp"] = review.get("submitted_at") or context["timestamp"]
        context["next_action"] = (
            "CI와 사용자 최종 병합 검토" if state == "approved" else "유효한 지적 확인과 최소 수정"
        )
        return context

    if event_name == "workflow_run":
        run = payload.get("workflow_run") or {}
        if run.get("name") != "Repository Validation":
            context["notify"] = False
            return context
        conclusion = str(run.get("conclusion") or "unknown").lower()
        event_by_conclusion = {
            "success": "ci_success",
            "failure": "ci_failure",
            "timed_out": "ci_timed_out",
            "cancelled": "ci_cancelled",
            "neutral": "ci_neutral",
            "skipped": "ci_skipped",
        }
        context["event"] = event_by_conclusion.get(conclusion, "ci_unknown")
        context.update(
            {
                "actor": ((run.get("actor") or {}).get("login")) or context["actor"],
                "head": run.get("head_branch") or MISSING,
                "role": role_for_branch(str(run.get("head_branch") or "")),
                "sha": run.get("head_sha") or MISSING,
                "status": conclusion,
                "actions_url": run.get("html_url") or MISSING,
                "url": run.get("html_url") or MISSING,
                "title": run.get("name") or "Repository Validation",
            }
        )
        pulls = run.get("pull_requests") or []
        if pulls and pulls[0].get("number"):
            number = int(pulls[0]["number"])
            context["number"] = number
            pr = api.pull_request(number)
            if pr:
                run_sha = str(run.get("head_sha") or MISSING)
                pr_sha = str((pr.get("head") or {}).get("sha") or MISSING)
                pr_context(context, pr, api)
                context["actions_url"] = run.get("html_url") or MISSING
                context["sha"] = run_sha
                if pr_sha not in (MISSING, run_sha):
                    context.update(
                        {
                            "changed_files": UNKNOWN,
                            "additions": UNKNOWN,
                            "deletions": UNKNOWN,
                            "changes": "현재 PR 최신 SHA와 다른 이전 CI 실행",
                            "process": UNKNOWN,
                            "validation": f"Repository Validation {conclusion} @ {run_sha[:7]}",
                            "qa": UNKNOWN,
                            "risks": "이 결과는 현재 PR 최신 커밋보다 이전 SHA에 대한 검증임",
                        }
                    )
        jobs, failed = format_jobs(api.workflow_jobs(int(run.get("id") or 0)))
        context["ci_jobs"] = jobs
        context["failed_jobs"] = failed
        context["ci_status"] = conclusion
        context["timestamp"] = run.get("updated_at") or run.get("run_started_at") or context["timestamp"]
        if conclusion == "success":
            if context["unresolved_threads"] == 0:
                context["next_action"] = "사용자 최종 검토"
            elif isinstance(context["unresolved_threads"], int):
                context["next_action"] = "미해결 리뷰 반영 필요"
            else:
                context["next_action"] = UNKNOWN
        else:
            context["next_action"] = {
                "failure": "실패 Job과 Step 확인 후 최소 수정",
                "timed_out": "timeout 원인과 장시간 Step 확인 후 재실행 판단",
                "cancelled": "취소 원인 확인 후 필요 시 재실행",
                "neutral": "중립 결론 조건과 check 설정 확인",
                "skipped": "skip 조건이 의도된 것인지 확인",
            }.get(conclusion, "Actions 실행 상태 확인")
        return context

    if event_name == "issues":
        issue = payload.get("issue") or {}
        context["event"] = "issue_closed" if action == "closed" else "issue_opened"
        context.update(
            {
                "number": issue.get("number", MISSING),
                "title": clean_text(issue.get("title"), 180, multiline=False),
                "purpose": MISSING,
                "actor": ((issue.get("user") or {}).get("login")) or context["actor"],
                "task_id": extract_task_id("", "", "", f"{issue.get('title', '')}\n{issue.get('body', '')}"),
                "status": "Closed" if action == "closed" else "Open",
                "url": issue.get("html_url") or MISSING,
                "next_action": "후속 작업 확인" if action == "closed" else "담당자와 우선순위 확인",
                "timestamp": issue.get("updated_at") or issue.get("created_at") or context["timestamp"],
            }
        )
        return context

    context["notify"] = False
    return context


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--event-name", required=True)
    result.add_argument("--event-path", required=True)
    result.add_argument("--repository", required=True)
    result.add_argument("--output", required=True)
    result.add_argument("--scenario", default="")
    result.add_argument("--pr-number", default="")
    result.add_argument("--api-url", default=os.environ.get("GITHUB_API_URL", "https://api.github.com"))
    return result


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    args = parser().parse_args()
    payload = json.loads(Path(args.event_path).read_text(encoding="utf-8"))
    api = GitHubApi(args.repository, os.environ.get("GITHUB_TOKEN", ""), args.api_url)
    try:
        context = collect(args.event_name, payload, args.repository, api, args.scenario, args.pr_number)
    except ValueError as exc:
        print(f"Discord context 생성 실패: {exc}", file=sys.stderr)
        return 1
    Path(args.output).write_text(json.dumps(context, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Discord context 생성 완료: event={context['event']}, notify={str(context['notify']).lower()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

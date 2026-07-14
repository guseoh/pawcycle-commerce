#!/usr/bin/env python3
"""Validate task report and handoff artifacts referenced by a pull request."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import re
import sys
from pathlib import Path

TASK_ID_PREFIXES = (
    "BOOTSTRAP",
    "PS",
    "ARCH",
    "FOUNDATION",
    "FRONTEND",
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
TASK_ID_RE = re.compile(rf"\b({'|'.join(TASK_ID_PREFIXES)})-\d{{3}}\b")
HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")


@dataclass(frozen=True)
class SectionRequirement:
    label: str
    aliases: tuple[str, ...]


@dataclass
class MarkdownSection:
    heading: str
    line: int
    content: list[str]


REPORT_REQUIREMENTS = (
    SectionRequirement("작업 정보 또는 작업 목적", ("작업 정보", "작업 목적", "task info", "purpose")),
    SectionRequirement("승인 입력 또는 입력 문서", ("승인 입력", "입력 문서", "input docs", "approved input")),
    SectionRequirement("변경 범위", ("변경 범위", "change scope", "changed scope")),
    SectionRequirement("변경하지 않은 범위 또는 제외 범위", ("변경하지 않은 범위", "제외 범위", "unchanged scope", "excluded scope")),
    SectionRequirement("실행한 검증 또는 검증 결과", ("실행한 검증", "검증 결과", "validation result", "executed validation")),
    SectionRequirement("실행하지 못한 검증과 이유", ("실행하지 못한 검증", "미실행 검증", "테스트 미실행", "not-run validation", "not run validation")),
    SectionRequirement(
        "남은 위험 또는 위험",
        ("남은 위험", "위험", "제한", "차단 사유", "remaining risk", "known limitation", "risk summary", "blocker reason"),
    ),
    SectionRequirement("Git 결과", ("git 결과", "git result")),
    SectionRequirement("PR 결과", ("pr 결과", "pr 상태", "pull request result", "pr result")),
)

HANDOFF_REQUIREMENTS = (
    SectionRequirement("전달 목적", ("전달 목적", "delivery purpose", "handoff purpose")),
    SectionRequirement("다음 역할 또는 대상 역할", ("다음 역할", "대상 역할", "target role", "next role")),
    SectionRequirement("입력 문서", ("입력 문서", "input docs")),
    SectionRequirement("사용 가능한 결과", ("사용 가능한 결과", "완료된 작업", "관련 파일", "usable result", "usable results")),
    SectionRequirement("미결정 사항 또는 승인 필요 항목", ("미결정", "승인 필요", "미확정", "approval needed", "undecided")),
    SectionRequirement("검증 포인트", ("검증 포인트", "검증 결과", "validation point", "validation points")),
    SectionRequirement("중단 조건", ("중단 조건", "차단 조건", "stop condition", "stop conditions")),
    SectionRequirement(
        "남은 위험 또는 주의 사항",
        ("남은 위험", "주의 사항", "위험", "제한", "remaining risk", "known limitation", "risk summary", "blocker reason"),
    ),
)

VALIDATION_ALIASES = ("실행한 검증", "검증 결과", "검증 포인트", "validation result", "executed validation", "validation point")
NOT_RUN_ALIASES = ("실행하지 못한 검증", "미실행 검증", "테스트 미실행", "not-run validation", "not run validation")
RISK_ALIASES = (
    "남은 위험",
    "위험",
    "제한",
    "차단 사유",
    "주의 사항",
    "remaining risk",
    "known limitation",
    "risk summary",
    "blocker reason",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--task-id", help="Explicit task ID to validate.")
    parser.add_argument(
        "--from-stdin",
        action="store_true",
        help="Read PR title and body text from standard input and detect the task ID.",
    )
    parser.add_argument(
        "--root",
        default=".",
        help="Repository root. Defaults to the current directory.",
    )
    return parser.parse_args()


def find_task_id(args: argparse.Namespace) -> str:
    if args.task_id:
        return args.task_id

    if not args.from_stdin:
        raise SystemExit("작업 ID를 찾으려면 --task-id 또는 --from-stdin이 필요함")

    text = sys.stdin.read()
    match = TASK_ID_RE.search(text)
    if not match:
        raise SystemExit("PR 제목 또는 본문에서 작업 ID를 찾을 수 없음")
    return match.group(0)


def markdown_files(path: Path) -> list[Path]:
    if not path.is_dir():
        return []
    return sorted(child for child in path.iterdir() if child.is_file() and child.suffix == ".md")


def normalize(text: str) -> str:
    text = text.strip().casefold()
    text = text.replace("`", "")
    return re.sub(r"\s+", " ", text)


def heading_matches(heading: str, aliases: tuple[str, ...]) -> bool:
    normalized_heading = normalize(heading)
    return any(normalize(alias) in normalized_heading for alias in aliases)


def parse_sections(path: Path) -> list[MarkdownSection]:
    sections: list[MarkdownSection] = []
    current: MarkdownSection | None = None
    text = path.read_text(encoding="utf-8")

    for line_no, line in enumerate(text.splitlines(), start=1):
        match = HEADING_RE.match(line)
        if match:
            current = MarkdownSection(heading=match.group(2).strip(), line=line_no, content=[])
            sections.append(current)
            continue
        if current is not None:
            current.content.append(line)

    return sections


def has_meaningful_content(section: MarkdownSection) -> bool:
    rows = [line.strip() for line in section.content]

    for index, stripped in enumerate(rows):
        if not stripped:
            continue

        # Markdown table separators and bare list markers are not evidence.
        without_table_marks = stripped.replace("|", "").replace("-", "").replace(":", "").replace(" ", "")
        if not without_table_marks:
            continue
        if stripped in {"-", "*", "- [ ]", "- [x]", "- [X]"}:
            continue
        next_stripped = rows[index + 1] if index + 1 < len(rows) else ""
        next_without_marks = next_stripped.replace("|", "").replace("-", "").replace(":", "").replace(" ", "")
        if stripped.startswith("|") and stripped.endswith("|") and next_stripped and not next_without_marks:
            continue
        return True
    return False


def matching_sections(sections: list[MarkdownSection], aliases: tuple[str, ...]) -> list[MarkdownSection]:
    return [section for section in sections if heading_matches(section.heading, aliases)]


def validate_required_sections(
    *,
    kind: str,
    files: list[Path],
    requirements: tuple[SectionRequirement, ...],
) -> list[str]:
    failures: list[str] = []

    for path in files:
        sections = parse_sections(path)
        for requirement in requirements:
            if not matching_sections(sections, requirement.aliases):
                failures.append(f"{kind} 필수 섹션 없음: {path}: {requirement.label}")

        validation_sections = matching_sections(sections, VALIDATION_ALIASES)
        if validation_sections and not any(has_meaningful_content(section) for section in validation_sections):
            failures.append(f"{kind} 검증 결과 섹션이 비어 있음: {path}")

        not_run_sections = matching_sections(sections, NOT_RUN_ALIASES)
        for section in not_run_sections:
            if not has_meaningful_content(section):
                failures.append(f"{kind} 실행하지 못한 검증 사유 없음: {path}:{section.line}")

        risk_sections = matching_sections(sections, RISK_ALIASES)
        if not risk_sections:
            failures.append(f"{kind} 위험/제한/차단/남은 위험 섹션 없음: {path}")
        elif not any(has_meaningful_content(section) for section in risk_sections):
            failures.append(f"{kind} 위험/제한/차단/남은 위험 섹션이 비어 있음: {path}")

    return failures


def main() -> int:
    args = parse_args()
    task_id = find_task_id(args)
    root = Path(args.root)

    report_dir = root / "docs" / "reports" / task_id
    handoff_dir = root / "docs" / "handoffs" / task_id

    report_files = markdown_files(report_dir)
    handoff_files = markdown_files(handoff_dir)

    failures: list[str] = []
    if not report_files:
        failures.append(f"작업 보고서 Markdown 파일 없음: {report_dir}")
    if not handoff_files:
        failures.append(f"역할 인수인계 Markdown 파일 없음: {handoff_dir}")
    if report_files:
        failures.extend(
            validate_required_sections(
                kind="작업 보고서",
                files=report_files,
                requirements=REPORT_REQUIREMENTS,
            )
        )
    if handoff_files:
        failures.extend(
            validate_required_sections(
                kind="역할 인수인계",
                files=handoff_files,
                requirements=HANDOFF_REQUIREMENTS,
            )
        )

    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        return 1

    print(f"task artifacts validated for {task_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

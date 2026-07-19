#!/usr/bin/env python3
"""Validate risk-graded task artifacts without retroactively changing legacy records."""

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
TASK_ID_PATTERN = rf"(?:HARNESS(?:-[A-Z][A-Z0-9]*)+-\d{{3}}|(?:{'|'.join(TASK_ID_PREFIXES)})-\d{{3}})"
TASK_ID_RE = re.compile(rf"(?<![A-Z0-9]){TASK_ID_PATTERN}(?![A-Z0-9])")
HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
TASK_GRADE_FIELD_RE = re.compile(
    r"^\s*(?:[-*]\s*)?(?:작업 등급|task grade)\s*:\s*(.*?)\s*$",
    re.IGNORECASE | re.MULTILINE,
)

LIGHTWEIGHT = "경량"
STANDARD = "일반"
HIGH_RISK = "고위험"
TASK_GRADE_ALIASES = {
    "경량": LIGHTWEIGHT,
    "lightweight": LIGHTWEIGHT,
    "light": LIGHTWEIGHT,
    "일반": STANDARD,
    "standard": STANDARD,
    "normal": STANDARD,
    "고위험": HIGH_RISK,
    "high-risk": HIGH_RISK,
    "high risk": HIGH_RISK,
}


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

GRADED_REPORT_REQUIREMENTS = (
    SectionRequirement("QA 필요 여부", ("qa 필요 여부", "qa 검증", "qa decision")),
    SectionRequirement(
        "QA 문서 경로 또는 생략 사유",
        ("qa 문서 경로", "qa 생략 사유", "qa document path", "qa omission reason"),
    ),
)

HIGH_RISK_EVIDENCE_REQUIREMENTS = (
    SectionRequirement("명시적 승인 근거", ("명시적 승인 근거", "승인 근거", "explicit approval")),
    SectionRequirement("적용 전 검증", ("적용 전 검증", "변경 전 검증", "pre-change validation")),
    SectionRequirement("적용 후 검증", ("적용 후 검증", "변경 후 검증", "post-change validation")),
    SectionRequirement("독립 검증", ("독립 검증", "independent validation", "independent verification")),
    SectionRequirement(
        "복구·롤백 증거",
        ("복구·롤백", "복구 및 롤백", "롤백 증거", "복구 증거", "recovery and rollback", "rollback evidence"),
    ),
)

HANDOFF_REQUIREMENTS = (
    SectionRequirement("전달 목적", ("전달 목적", "delivery purpose", "handoff purpose")),
    SectionRequirement(
        "대상 역할 또는 운영자",
        ("대상 역할 또는 운영자", "대상 역할", "다음 역할", "target role", "next role", "operator"),
    ),
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
HANDOFF_OMISSION_ALIASES = (
    "인수인계 생략",
    "인수인계 생략 사유",
    "handoff omitted",
    "handoff omission",
)
HANDOFF_OMISSION_DENIAL_RE = re.compile(
    r"(?:인수인계(?:를|은|는)?\s*)?생략(?:을|은|는)?\s*(?:하지\s*않|안\s*(?:함|한|하))"
    r"|해당\s*없음|not\s+omitted|not\s+applicable|^n/?a$|^none$",
    re.IGNORECASE | re.MULTILINE,
)
HTML_COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)
PLACEHOLDER_LINE_RE = re.compile(
    r"^(?:[-*]\s*)?(?:<[^>]+>|\[[^\]]+\]|todo|tbd|미정|작성\s*(?:필요|예정))\.?$",
    re.IGNORECASE,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--task-id", help="Explicit task ID to validate.")
    parser.add_argument(
        "--task-grade",
        help="Explicit task grade: 경량, 일반, or 고위험.",
    )
    parser.add_argument(
        "--allow-legacy-without-grade",
        action="store_true",
        help="Allow an existing ungraded artifact to use the legacy 일반 rules.",
    )
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


def find_task_id(args: argparse.Namespace, input_text: str) -> str:
    if args.task_id:
        return args.task_id

    if not args.from_stdin:
        raise SystemExit("작업 ID를 찾으려면 --task-id 또는 --from-stdin이 필요함")

    match = TASK_ID_RE.search(input_text)
    if not match:
        raise SystemExit("PR 제목 또는 본문에서 작업 ID를 찾을 수 없음")
    return match.group(0)


def normalize_task_grade(value: str) -> str | None:
    normalized = normalize(value).strip("`*_ ")
    return TASK_GRADE_ALIASES.get(normalized)


def find_task_grade(args: argparse.Namespace, input_text: str) -> tuple[str, bool]:
    if args.task_grade:
        grade = normalize_task_grade(args.task_grade)
        if grade is None:
            raise SystemExit("작업 등급은 경량, 일반 또는 고위험이어야 함")
        return grade, True

    matches = TASK_GRADE_FIELD_RE.findall(input_text)
    if not matches:
        if args.allow_legacy_without_grade:
            return STANDARD, False
        raise SystemExit("작업 등급 필드가 없음; 기존 산출물만 --allow-legacy-without-grade로 검증할 수 있음")

    grades = [normalize_task_grade(value) for value in matches]
    if any(grade is None for grade in grades):
        raise SystemExit("작업 등급 필드 값은 경량, 일반 또는 고위험이어야 함")
    distinct_grades = set(grades)
    if len(distinct_grades) != 1:
        raise SystemExit("서로 충돌하는 작업 등급 필드가 있음")
    grade = grades[0]
    assert grade is not None
    return grade, True


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
    content = HTML_COMMENT_RE.sub("", "\n".join(section.content))
    rows = [line.strip() for line in content.splitlines()]

    for index, stripped in enumerate(rows):
        if not stripped:
            continue

        # Markdown table separators and bare list markers are not evidence.
        without_table_marks = stripped.replace("|", "").replace("-", "").replace(":", "").replace(" ", "")
        if not without_table_marks:
            continue
        if stripped in {"-", "*", "- [ ]", "- [x]", "- [X]"}:
            continue
        if PLACEHOLDER_LINE_RE.fullmatch(stripped):
            continue
        next_stripped = rows[index + 1] if index + 1 < len(rows) else ""
        next_without_marks = next_stripped.replace("|", "").replace("-", "").replace(":", "").replace(" ", "")
        if stripped.startswith("|") and stripped.endswith("|") and next_stripped and not next_without_marks:
            continue
        return True
    return False


def matching_sections(sections: list[MarkdownSection], aliases: tuple[str, ...]) -> list[MarkdownSection]:
    return [section for section in sections if heading_matches(section.heading, aliases)]


def has_explicit_handoff_omission(section: MarkdownSection) -> bool:
    if not has_meaningful_content(section):
        return False

    content = "\n".join(
        line.strip().lstrip("-* ").rstrip(".")
        for line in section.content
        if line.strip()
    )
    return not HANDOFF_OMISSION_DENIAL_RE.search(content)


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


def validate_meaningful_requirements(
    *,
    kind: str,
    files: list[Path],
    requirements: tuple[SectionRequirement, ...],
) -> list[str]:
    failures: list[str] = []
    for path in files:
        sections = parse_sections(path)
        for requirement in requirements:
            matched = matching_sections(sections, requirement.aliases)
            if not matched:
                failures.append(f"{kind} 필수 증거 섹션 없음: {path}: {requirement.label}")
            elif not any(has_meaningful_content(section) for section in matched):
                failures.append(f"{kind} 필수 증거 섹션이 비어 있음: {path}: {requirement.label}")
    return failures


def validate_report_grades(files: list[Path], expected_grade: str) -> list[str]:
    failures: list[str] = []
    for path in files:
        values = TASK_GRADE_FIELD_RE.findall(path.read_text(encoding="utf-8"))
        if not values:
            failures.append(f"작업 보고서 작업 등급 필드 없음: {path}")
            continue
        grades = [normalize_task_grade(value) for value in values]
        if any(grade is None for grade in grades):
            failures.append(f"작업 보고서 작업 등급 값이 유효하지 않음: {path}")
        elif set(grades) != {expected_grade}:
            failures.append(f"작업 보고서 작업 등급 불일치: {path}: 기대값 {expected_grade}")
    return failures


def validate_handoff_omission(files: list[Path]) -> tuple[bool, list[str]]:
    sections_by_path = {
        path: matching_sections(parse_sections(path), HANDOFF_OMISSION_ALIASES)
        for path in files
    }
    if not any(sections_by_path.values()):
        return False, []

    failures: list[str] = []
    for path, omission_sections in sections_by_path.items():
        if not omission_sections:
            failures.append(f"작업 보고서 인수인계 생략 사유 없음: {path}")
            continue
        failures.extend(
            f"작업 보고서 인수인계 생략 사유 없음: {path}:{section.line}"
            for section in omission_sections
            if not has_explicit_handoff_omission(section)
        )

    return not failures, failures


def main() -> int:
    args = parse_args()
    input_text = sys.stdin.read() if args.from_stdin else ""
    task_id = find_task_id(args, input_text)
    task_grade, grade_explicit = find_task_grade(args, input_text)
    root = Path(args.root)

    report_dir = root / "docs" / "reports" / task_id
    handoff_dir = root / "docs" / "handoffs" / task_id

    report_files = markdown_files(report_dir)
    handoff_files = markdown_files(handoff_dir)
    handoff_omitted, omission_failures = validate_handoff_omission(report_files)

    failures: list[str] = []
    if task_grade != LIGHTWEIGHT:
        failures.extend(omission_failures)
    if not report_files and task_grade != LIGHTWEIGHT:
        failures.append(f"작업 보고서 Markdown 파일 없음: {report_dir}")
    if not handoff_files and not handoff_omitted and task_grade != LIGHTWEIGHT:
        failures.append(f"역할 인수인계 Markdown 파일 없음: {handoff_dir}")
    if report_files:
        requirements = REPORT_REQUIREMENTS
        if grade_explicit:
            failures.extend(validate_report_grades(report_files, task_grade))
        failures.extend(
            validate_required_sections(
                kind="작업 보고서",
                files=report_files,
                requirements=requirements,
            )
        )
        if grade_explicit and task_grade in (STANDARD, HIGH_RISK):
            failures.extend(
                validate_meaningful_requirements(
                    kind=f"{task_grade} 작업 보고서",
                    files=report_files,
                    requirements=GRADED_REPORT_REQUIREMENTS,
                )
            )
        if task_grade == HIGH_RISK:
            failures.extend(
                validate_meaningful_requirements(
                    kind="고위험 작업 보고서",
                    files=report_files,
                    requirements=HIGH_RISK_EVIDENCE_REQUIREMENTS,
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

    if not grade_explicit:
        print(
            "경고: 명시적 legacy 옵션으로 등급 없는 기존 산출물에 일반 규칙을 적용함",
            file=sys.stderr,
        )
    print(f"task artifacts validated for {task_id} ({task_grade})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

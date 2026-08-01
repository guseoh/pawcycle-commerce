#!/usr/bin/env python3
"""Validate the minimum risk-graded PR and execution artifact contract."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import re
import sys
from pathlib import Path

TASK_ID_PREFIXES = (
    "BOOTSTRAP", "PS", "ARCH", "FOUNDATION", "FRONTEND", "PRODUCT",
    "BUG", "PERF", "OPS", "SEC", "AUTH", "DOMAIN", "API", "UX", "DATA",
)
TASK_ID_PATTERN = rf"(?:HARNESS(?:-[A-Z][A-Z0-9]*)+-\d{{3}}|(?:{'|'.join(TASK_ID_PREFIXES)})-\d{{3}})"
TASK_ID_RE = re.compile(rf"(?<![A-Z0-9]){TASK_ID_PATTERN}(?![A-Z0-9])")
HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
HTML_COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)

LIGHTWEIGHT = "경량"
STANDARD = "일반"
HIGH_RISK = "고위험"
REPOSITORY_CHANGE = "저장소 변경"
PRODUCTION_EXECUTION = "실제 운영 실행"

GRADE_ALIASES = {
    "경량": LIGHTWEIGHT, "lightweight": LIGHTWEIGHT, "light": LIGHTWEIGHT,
    "일반": STANDARD, "standard": STANDARD, "normal": STANDARD,
    "고위험": HIGH_RISK, "high-risk": HIGH_RISK, "high risk": HIGH_RISK,
}
EXECUTION_ALIASES = {
    "저장소 변경": REPOSITORY_CHANGE,
    "repository change": REPOSITORY_CHANGE,
    "repository preparation": REPOSITORY_CHANGE,
    "실제 운영 실행": PRODUCTION_EXECUTION,
    "production execution": PRODUCTION_EXECUTION,
}

FIELD_PATTERNS = {
    "grade": re.compile(r"^\s*(?:[-*]\s*)?(?:작업 등급|task grade)\s*:\s*(.*?)\s*$", re.IGNORECASE | re.MULTILINE),
    "execution": re.compile(r"^\s*(?:[-*]\s*)?(?:실행 구분|execution type)\s*:\s*(.*?)\s*$", re.IGNORECASE | re.MULTILINE),
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
    SectionRequirement("목적", ("작업 목적", "목적", "purpose")),
    SectionRequirement("결과 또는 증거", ("주요 결과", "결과", "증거", "evidence", "result")),
    SectionRequirement("위험·제한", ("남은 위험", "위험과 제한", "위험 또는 제한", "위험", "제한", "risk", "limitation")),
)

PRODUCTION_EXECUTION_REQUIREMENTS = (
    SectionRequirement("명시적 승인 근거", ("명시적 승인 근거", "승인 근거", "explicit approval")),
    SectionRequirement("적용 전 확인", ("적용 전 확인", "적용 전 검증", "pre-change", "pre-execution")),
    SectionRequirement("적용 후 확인", ("적용 후 확인", "적용 후 검증", "post-change", "post-execution")),
    SectionRequirement("독립 확인", ("독립 확인", "독립 검증", "independent verification", "independent validation")),
    SectionRequirement("복구·rollback", ("복구·rollback", "복구·롤백", "복구 및 롤백", "rollback", "recovery")),
)
UNEXECUTED_ALIASES = ("미실행 항목", "실행하지 못한", "미실행", "not run")
REMAINING_RISK_ALIASES = ("남은 위험", "remaining risk")

PLACEHOLDER_RE = re.compile(
    r"^(?:[-*]\s*)?(?:<[^>]+>|\[[^\]]+\]|todo|tbd|미정|작성\s*(?:필요|예정))\.?$",
    re.IGNORECASE,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--task-id")
    parser.add_argument("--task-grade")
    parser.add_argument("--execution-type")
    parser.add_argument("--allow-legacy-without-grade", action="store_true")
    parser.add_argument("--from-stdin", action="store_true")
    parser.add_argument("--root", default=".")
    return parser.parse_args()


def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text.strip().casefold().replace("`", ""))


def visible_text(text: str) -> str:
    return HTML_COMMENT_RE.sub("", text)


def normalize_grade(value: str) -> str | None:
    return GRADE_ALIASES.get(normalize(value).strip("*_ "))


def normalize_execution(value: str) -> str | None:
    return EXECUTION_ALIASES.get(normalize(value).strip("*_ "))


def resolve_field(
    *,
    cli_value: str | None,
    text: str,
    pattern: re.Pattern[str],
    normalizer,
    label: str,
    allowed: str,
    required: bool,
) -> str | None:
    raw_values = ([cli_value] if cli_value else []) + pattern.findall(text)
    if not raw_values:
        if required:
            raise SystemExit(f"{label} 필드가 없음")
        return None
    values = [normalizer(value) for value in raw_values]
    if any(value is None for value in values):
        raise SystemExit(f"{label} 필드 값은 {allowed}이어야 함")
    if len(set(values)) != 1:
        raise SystemExit(f"서로 충돌하는 {label} 필드가 있음")
    return values[0]


def find_task_id(args: argparse.Namespace, text: str) -> str:
    if args.task_id:
        if not re.fullmatch(TASK_ID_PATTERN, args.task_id):
            raise SystemExit("작업 ID 형식이 유효하지 않음")
        return args.task_id
    if not args.from_stdin:
        raise SystemExit("작업 ID를 찾으려면 --task-id 또는 --from-stdin이 필요함")
    match = TASK_ID_RE.search(text)
    if not match:
        raise SystemExit("PR 제목 또는 본문에서 작업 ID를 찾을 수 없음")
    return match.group(0)


def markdown_files(path: Path) -> list[Path]:
    if not path.is_dir():
        return []
    return sorted(item for item in path.iterdir() if item.is_file() and item.suffix == ".md")


def parse_sections_text(text: str) -> list[MarkdownSection]:
    sections: list[MarkdownSection] = []
    current: MarkdownSection | None = None
    for line_no, line in enumerate(text.splitlines(), start=1):
        match = HEADING_RE.match(line)
        if match:
            current = MarkdownSection(match.group(2).strip(), line_no, [])
            sections.append(current)
        elif current is not None:
            current.content.append(line)
    return sections


def parse_sections(path: Path) -> list[MarkdownSection]:
    return parse_sections_text(path.read_text(encoding="utf-8"))


def meaningful_text(text: str) -> bool:
    lines = visible_text(text).splitlines()
    for index, line in enumerate(lines):
        value = line.strip()
        if not value or value in {"-", "*", "- [ ]", "- [x]", "- [X]"}:
            continue
        if not value.replace("|", "").replace("-", "").replace(":", "").strip():
            continue
        if "|" in value:
            cells = [cell.strip() for cell in value.strip("|").split("|")]
            if cells and all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells):
                continue
            next_value = next((item.strip() for item in lines[index + 1 :] if item.strip()), "")
            next_cells = [cell.strip() for cell in next_value.strip("|").split("|")]
            if next_cells and all(re.fullmatch(r":?-{3,}:?", cell) for cell in next_cells):
                continue
            if not any(cell and not PLACEHOLDER_RE.fullmatch(cell) for cell in cells):
                continue
            return True
        if PLACEHOLDER_RE.fullmatch(value):
            continue
        return True
    return False


def section_matches(section: MarkdownSection, aliases: tuple[str, ...]) -> bool:
    heading = normalize(section.heading)
    return any(normalize(alias) in heading for alias in aliases)


def matching_sections(sections: list[MarkdownSection], aliases: tuple[str, ...]) -> list[MarkdownSection]:
    return [section for section in sections if section_matches(section, aliases)]


def section_text(section: MarkdownSection) -> str:
    return "\n".join(section.content)


def labeled_values(text: str, labels: tuple[str, ...]) -> list[str]:
    label_pattern = "|".join(re.escape(label) for label in labels)
    pattern = re.compile(rf"^\s*(?:[-*]\s*)?(?:{label_pattern})\s*:\s*(.*?)\s*$", re.IGNORECASE | re.MULTILINE)
    return pattern.findall(text)


def has_meaningful_label(text: str, labels: tuple[str, ...]) -> bool:
    return any(meaningful_text(value) for value in labeled_values(text, labels))


def validate_pr_contract(text: str) -> list[str]:
    failures: list[str] = []
    sections = parse_sections_text(text)

    purpose_sections = matching_sections(sections, ("목적과 범위", "purpose and scope"))
    if not purpose_sections:
        failures.append("PR 본문 필수 구획 없음: 목적과 범위")
    else:
        content = "\n".join(section_text(section) for section in purpose_sections)
        if not has_meaningful_label(content, ("목적", "purpose")):
            failures.append("PR 본문 목적이 비어 있음")
        if not has_meaningful_label(content, ("변경 범위", "change scope")):
            failures.append("PR 본문 변경 범위가 비어 있음")

    validation_sections = matching_sections(sections, ("검증", "validation"))
    if not validation_sections:
        failures.append("PR 본문 필수 구획 없음: 검증")
    else:
        content = "\n".join(section_text(section) for section in validation_sections)
        executed = has_meaningful_label(content, ("실행 결과", "실행한 검증", "result"))
        not_run = has_meaningful_label(content, ("실패·미실행과 이유", "미실행 이유", "not run reason"))
        if not executed and not not_run:
            failures.append("PR 본문 실행한 검증 또는 미실행 이유가 비어 있음")

    risk_sections = matching_sections(sections, ("위험과 복구", "risk and recovery"))
    if not risk_sections:
        failures.append("PR 본문 필수 구획 없음: 위험과 복구")
    else:
        content = "\n".join(section_text(section) for section in risk_sections)
        risk = has_meaningful_label(content, ("남은 위험", "remaining risk"))
        recovery = has_meaningful_label(content, ("실패·rollback·revert 경계", "복구 경계", "rollback", "revert"))
        if not risk and not recovery:
            failures.append("PR 본문 남은 위험 또는 복구 경계가 비어 있음")
    return failures


def validate_requirements(kind: str, files: list[Path], requirements: tuple[SectionRequirement, ...]) -> list[str]:
    failures: list[str] = []
    for path in files:
        sections = parse_sections(path)
        for requirement in requirements:
            matched = matching_sections(sections, requirement.aliases)
            if not matched:
                failures.append(f"{kind} 필수 섹션 없음: {path}: {requirement.label}")
            elif not any(meaningful_text(section_text(section)) for section in matched):
                failures.append(f"{kind} 필수 섹션이 비어 있음: {path}: {requirement.label}")
    return failures


def validate_execution_risk_details(path: Path) -> list[str]:
    sections = parse_sections(path)
    unexecuted_sections = matching_sections(sections, UNEXECUTED_ALIASES)
    risk_sections = matching_sections(sections, REMAINING_RISK_ALIASES)
    shared_lines = {section.line for section in unexecuted_sections} & {section.line for section in risk_sections}

    unexecuted_ok = any(
        section.line not in shared_lines and meaningful_text(section_text(section))
        for section in unexecuted_sections
    )
    risk_ok = any(
        section.line not in shared_lines and meaningful_text(section_text(section))
        for section in risk_sections
    )
    for section in sections:
        if section.line not in shared_lines:
            continue
        content = section_text(section)
        unexecuted_ok = unexecuted_ok or has_meaningful_label(content, UNEXECUTED_ALIASES)
        risk_ok = risk_ok or has_meaningful_label(content, REMAINING_RISK_ALIASES)

    failures: list[str] = []
    if not unexecuted_ok:
        failures.append(f"실제 운영 실행 보고서 필수 섹션 없음 또는 비어 있음: {path}: 미실행 항목")
    if not risk_ok:
        failures.append(f"실제 운영 실행 보고서 필수 섹션 없음 또는 비어 있음: {path}: 남은 위험")
    return failures


def report_field_values(path: Path, field: str, normalizer) -> list[str | None]:
    text = visible_text(path.read_text(encoding="utf-8"))
    return [normalizer(value) for value in FIELD_PATTERNS[field].findall(text)]


def production_report_files(files: list[Path]) -> list[Path]:
    reports: list[Path] = []
    for path in files:
        values = report_field_values(path, "execution", normalize_execution)
        if values and set(values) == {PRODUCTION_EXECUTION}:
            reports.append(path)
    return reports


def validate_any_production_report(files: list[Path]) -> list[str]:
    attempted: list[str] = []
    for path in files:
        failures = validate_requirements("실제 운영 실행 보고서", [path], PRODUCTION_EXECUTION_REQUIREMENTS)
        failures.extend(validate_execution_risk_details(path))
        if not failures:
            return []
        attempted.extend(failures)
    return ["완전한 실제 운영 실행 보고서가 없음", *attempted]


def validate_optional_report_fields(files: list[Path], grade: str, execution: str) -> list[str]:
    failures: list[str] = []
    for path in files:
        text = visible_text(path.read_text(encoding="utf-8"))
        grade_values = FIELD_PATTERNS["grade"].findall(text)
        if grade_values:
            normalized = [normalize_grade(value) for value in grade_values]
            if any(value is None for value in normalized) or set(normalized) != {grade}:
                failures.append(f"작업 보고서 작업 등급 불일치: {path}: 기대값 {grade}")
        execution_values = FIELD_PATTERNS["execution"].findall(text)
        if execution_values:
            normalized = [normalize_execution(value) for value in execution_values]
            if any(value is None for value in normalized) or set(normalized) != {execution}:
                failures.append(f"작업 보고서 실행 구분 불일치: {path}: 기대값 {execution}")
    return failures


def main() -> int:
    args = parse_args()
    input_text = visible_text(sys.stdin.read() if args.from_stdin else "")
    task_id = find_task_id(args, input_text)

    legacy = args.allow_legacy_without_grade
    grade = resolve_field(
        cli_value=args.task_grade,
        text=input_text,
        pattern=FIELD_PATTERNS["grade"],
        normalizer=normalize_grade,
        label="작업 등급",
        allowed="경량, 일반 또는 고위험",
        required=not legacy,
    )

    execution = resolve_field(
        cli_value=args.execution_type,
        text=input_text,
        pattern=FIELD_PATTERNS["execution"],
        normalizer=normalize_execution,
        label="실행 구분",
        allowed="저장소 변경 또는 실제 운영 실행",
        required=not legacy,
    )

    root = Path(args.root)
    report_files = markdown_files(root / "docs" / "reports" / task_id)
    handoff_files = markdown_files(root / "docs" / "handoffs" / task_id)
    failures: list[str] = []

    if legacy:
        if execution is not None:
            failures.append("legacy 옵션은 실행 구분이 없는 기존 산출물에만 허용됨")
        if not report_files:
            failures.append(f"legacy 작업 보고서 Markdown 파일 없음: {root / 'docs' / 'reports' / task_id}")
        for path in report_files:
            report_text = visible_text(path.read_text(encoding="utf-8"))
            grade_values = [normalize_grade(value) for value in FIELD_PATTERNS["grade"].findall(report_text)]
            if any(value is None for value in grade_values) or len(set(grade_values)) > 1:
                failures.append(f"legacy 작업 보고서 작업 등급이 유효하지 않음: {path}")
            if FIELD_PATTERNS["execution"].search(report_text):
                failures.append(f"legacy 옵션은 실행 구분이 없는 기존 보고서에만 허용됨: {path}")
    else:
        assert grade is not None
        assert execution is not None
        if args.from_stdin:
            failures.extend(validate_pr_contract(input_text))
        if execution == PRODUCTION_EXECUTION and grade != HIGH_RISK:
            failures.append("실제 운영 실행의 작업 등급은 고위험이어야 함")
        if execution == PRODUCTION_EXECUTION and not report_files:
            failures.append(f"실제 운영 실행 보고서 Markdown 파일 없음: {root / 'docs' / 'reports' / task_id}")
        failures.extend(validate_optional_report_fields(report_files, grade, execution))

    if report_files:
        failures.extend(validate_requirements("작업 보고서", report_files, REPORT_REQUIREMENTS))
        if execution == PRODUCTION_EXECUTION:
            designated_reports = production_report_files(report_files)
            if not designated_reports:
                failures.append("실행 구분이 표시된 실제 운영 실행 보고서가 없음")
            else:
                for path in designated_reports:
                    grade_values = report_field_values(path, "grade", normalize_grade)
                    if not grade_values or set(grade_values) != {HIGH_RISK}:
                        failures.append(f"실제 운영 실행 보고서 작업 등급은 고위험이어야 함: {path}")
                failures.extend(validate_any_production_report(designated_reports))

    # Handoffs are conditional. Existing files remain readable inputs, but their
    # necessity and semantic completeness are reviewed by the owner and Tech Lead.
    _ = handoff_files

    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        return 1

    if legacy:
        print("경고: 명시적 legacy 옵션으로 실행 구분 없는 기존 산출물을 검증함", file=sys.stderr)
        print(f"task artifacts validated for {task_id} (legacy)")
    else:
        print(f"task artifacts validated for {task_id} ({grade}, {execution})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

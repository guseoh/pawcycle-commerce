#!/usr/bin/env python3
"""Validate that a pull request body was transferred as intact UTF-8 text."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


DOUBLE_QUESTION_RE = re.compile(r"\?{2,}")
FENCED_CODE_RE = re.compile(r"(?ms)^```.*?^```")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument(
        "--from-stdin",
        action="store_true",
        help="Read the PR body from standard input as UTF-8 bytes.",
    )
    source.add_argument(
        "--body-file",
        help="Read the PR body from a UTF-8 Markdown file.",
    )
    return parser.parse_args()


def read_body(args: argparse.Namespace) -> str:
    if args.from_stdin:
        raw = sys.stdin.buffer.read()
    else:
        raw = Path(args.body_file).read_bytes()

    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError(f"UTF-8 strict decoding failed at byte {exc.start}") from exc


def strip_fenced_code_blocks(text: str) -> str:
    return FENCED_CODE_RE.sub("", text)


def count_hangul(text: str) -> int:
    return sum(1 for char in text if "가" <= char <= "힣")


def has_repeated_question_damage(text: str) -> bool:
    text_without_code = strip_fenced_code_blocks(text)
    lines = text_without_code.splitlines()
    damaged_lines = [line for line in lines if DOUBLE_QUESTION_RE.search(line)]
    question_runs = DOUBLE_QUESTION_RE.findall(text_without_code)

    if len(question_runs) < 5 or len(damaged_lines) < 3:
        return False

    affected_text = "\n".join(damaged_lines)
    question_chars = sum(len(run) for run in question_runs)
    affected_non_space = sum(1 for char in affected_text if not char.isspace())
    affected_hangul = count_hangul(affected_text)
    damaged_headings = sum(1 for line in damaged_lines if line.lstrip().startswith("#"))

    if affected_non_space == 0:
        return False

    question_ratio = question_chars / affected_non_space
    hangul_ratio = affected_hangul / affected_non_space

    return (
        question_chars >= 10
        and (
            damaged_headings >= 2
            or (question_ratio >= 0.08 and hangul_ratio <= 0.20)
            or len(question_runs) >= 12
        )
    )


def validate_body(text: str) -> list[str]:
    failures: list[str] = []
    if "\ufffd" in text:
        failures.append("U+FFFD replacement character detected")
    if "\x00" in text:
        failures.append("NUL character detected")
    if has_repeated_question_damage(text):
        failures.append("repeated question-mark mojibake pattern detected")
    return failures


def print_failure(failures: list[str]) -> None:
    print("PR 본문 문자 손상 가능성이 감지됐습니다.", file=sys.stderr)
    for failure in failures:
        print(f"- {failure}", file=sys.stderr)
    print("UTF-8 Markdown 파일로 PR 본문을 작성하세요.", file=sys.stderr)
    print("gh pr create/edit --body-file 사용 후 원격 PR 본문을 다시 확인하세요.", file=sys.stderr)
    print("본문 전체는 로그에 출력하지 않습니다.", file=sys.stderr)


def main() -> int:
    args = parse_args()
    try:
        body = read_body(args)
    except ValueError as exc:
        print_failure([str(exc)])
        return 1

    failures = validate_body(body)
    if failures:
        print_failure(failures)
        return 1

    print("PR body encoding validated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

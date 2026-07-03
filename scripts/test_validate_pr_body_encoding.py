#!/usr/bin/env python3
"""Unit tests for PR body encoding validation."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "validate-pr-body-encoding.py"


NORMAL_KOREAN_MARKDOWN = """## 작업 정보

- 작업 ID: `OPS-002`
- 역할: Platform/SRE

## 목적

Discord Webhook 요청의 HTTP 403 문제를 확인하기 위해 명시적인 User-Agent를 추가한다.
"""

NORMAL_ENGLISH_MARKDOWN = """## Work Summary

- Task ID: `OPS-002`
- Role: Platform/SRE

This pull request improves webhook request metadata and validation.
"""

KOREAN_QUESTION = "이 변경은 왜 필요한가?\nAPI 응답이 null일 수 있는가?\n"
SINGLE_QUESTION = "이 값은 선택 사항인가?\n"
CODE_BLOCK_WITH_QUESTIONS = """## 검증

```python
value = data.get("question") ?? "fallback"
pattern = "??"
```

코드 블록 밖의 본문은 정상이다.
"""

CORRUPTED_MARKDOWN = """## ?? ??

- ?? ID: `OPS-002`
- ??: Platform/SRE
- ?? ???: `ops/sre`

## ??

Discord Webhook ??? `HTTP 403`?? ??? ??? ???? ??.
"""

OBSERVED_PR_10_CORRUPTION = """## ?? ??

- ?? ID: `OPS-002`
- ??: Platform/SRE
- ?? ???: `ops/sre`
- ?? ???: `main`

## ??

Discord Webhook ??? `HTTP 403`?? ??? `Collaboration Notification` ??? ???? ?? Discord HTTP API? ??? ? ?? ??? `User-Agent` ?? ??? ?????.

## ?? ??

- `.github/scripts/send-discord-notification.py`? `build_request()`?? `User-Agent` ?? ??
- `scripts/test_send_discord_notification.py`? ?? ?? ?? ??? ??
- `docs/runbook/collaboration-automation.md`? ??? 403 ??? ?? ??
"""


def run_validator_bytes(data: bytes) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(SCRIPT), "--from-stdin"],
        input=data,
        capture_output=True,
        check=False,
    )


def run_validator_text(text: str) -> subprocess.CompletedProcess[str]:
    return run_validator_bytes(text.encode("utf-8"))


class ValidatePrBodyEncodingTest(unittest.TestCase):
    def assert_valid(self, text: str) -> None:
        result = run_validator_text(text)
        self.assertEqual(result.returncode, 0, result.stderr.decode("utf-8", errors="replace"))
        self.assertIn("PR body encoding validated", result.stdout.decode("utf-8"))

    def assert_invalid(self, text: str) -> subprocess.CompletedProcess[str]:
        result = run_validator_text(text)
        self.assertNotEqual(result.returncode, 0)
        return result

    def test_normal_korean_markdown_passes(self) -> None:
        self.assert_valid(NORMAL_KOREAN_MARKDOWN)

    def test_normal_english_markdown_passes(self) -> None:
        self.assert_valid(NORMAL_ENGLISH_MARKDOWN)

    def test_korean_question_sentences_pass(self) -> None:
        self.assert_valid(KOREAN_QUESTION)

    def test_single_question_mark_passes(self) -> None:
        self.assert_valid(SINGLE_QUESTION)

    def test_question_marks_inside_code_block_pass(self) -> None:
        self.assert_valid(CODE_BLOCK_WITH_QUESTIONS)

    def test_replacement_character_fails(self) -> None:
        result = self.assert_invalid("정상 본문 뒤에 손상 문자 \ufffd 포함")
        self.assertIn("U+FFFD", result.stderr.decode("utf-8"))

    def test_nul_character_fails(self) -> None:
        result = self.assert_invalid("정상 본문\x00")
        self.assertIn("NUL", result.stderr.decode("utf-8"))

    def test_repeated_question_mojibake_fails(self) -> None:
        result = self.assert_invalid(CORRUPTED_MARKDOWN)
        self.assertIn("문자 손상 가능성", result.stderr.decode("utf-8"))

    def test_observed_pr_10_corruption_fails(self) -> None:
        result = self.assert_invalid(OBSERVED_PR_10_CORRUPTION)
        self.assertIn("repeated question-mark", result.stderr.decode("utf-8"))

    def test_utf8_file_input_passes(self) -> None:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as handle:
            handle.write(NORMAL_KOREAN_MARKDOWN)
            path = Path(handle.name)

        try:
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--body-file", str(path)],
                text=True,
                encoding="utf-8",
                capture_output=True,
                check=False,
            )
        finally:
            path.unlink()

        self.assertEqual(result.returncode, 0, result.stderr)

    def test_invalid_byte_file_fails(self) -> None:
        with tempfile.NamedTemporaryFile("wb", delete=False) as handle:
            handle.write(b"\xff\xfe\x00")
            path = Path(handle.name)

        try:
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--body-file", str(path)],
                text=True,
                encoding="utf-8",
                capture_output=True,
                check=False,
            )
        finally:
            path.unlink()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("UTF-8 strict decoding failed", result.stderr)

    def test_error_message_does_not_print_full_body(self) -> None:
        sensitive_body = CORRUPTED_MARKDOWN + "\nSECRET-LIKE-CONTENT-DO-NOT-PRINT\n"
        result = self.assert_invalid(sensitive_body)
        stderr = result.stderr.decode("utf-8")

        self.assertNotIn("SECRET-LIKE-CONTENT-DO-NOT-PRINT", stderr)
        self.assertNotIn(CORRUPTED_MARKDOWN, stderr)
        self.assertIn("--body-file", stderr)


if __name__ == "__main__":
    unittest.main()

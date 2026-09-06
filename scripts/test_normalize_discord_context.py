#!/usr/bin/env python3
"""Regressions for Discord context post-normalization."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".github" / "scripts" / "normalize-discord-context.py"
SPEC = importlib.util.spec_from_file_location("normalize_discord_context", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Discord context normalizer를 불러올 수 없음")
NORMALIZER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NORMALIZER)


class DiscordContextNormalizerTest(unittest.TestCase):
    def test_generic_task_id_and_four_section_template_are_normalized(self) -> None:
        body = """## 작업

- 작업 ID: `HTTP-CLIENT-REFACTOR-001`
- 작업 등급: 고위험
- 실행 구분: 저장소 변경

## 변경

- 목적: HTTP 계약과 client 구조 수렴
- 포함 범위: Backend validation, Frontend client
- 제외 범위: Production 실행

## 검증

- 실행 결과: Repository Validation PASS
- 실패·미실행: Production 실행 없음

## 위험

- 남은 위험: 외부 reviewer 제한
- 복구 경계: 일반 revert PR
"""
        context = {
            "task_id": "기록 없음",
            "title": "refactor: HTTP 계약 수렴",
            "head": "refactor/http-client-contract-convergence",
            "number": 280,
            "purpose": "기록 없음",
            "changes": "기록 없음",
            "validation": "기록 없음",
            "risks": "기록 없음",
        }
        payload = {
            "pull_request": {
                "number": 280,
                "title": "refactor: HTTP 계약 수렴",
                "body": body,
                "head": {"ref": "refactor/http-client-contract-convergence"},
            }
        }

        result = NORMALIZER.normalize_context(
            context,
            payload,
            "guseoh/pawcycle-commerce",
        )

        self.assertEqual(result["task_id"], "HTTP-CLIENT-REFACTOR-001")
        self.assertEqual(result["purpose"], "HTTP 계약과 client 구조 수렴")
        self.assertIn("Backend validation", result["changes"])
        self.assertIn("Production 실행", result["changes"])
        self.assertIn("Repository Validation PASS", result["validation"])
        self.assertIn("Production 실행 없음", result["validation"])
        self.assertIn("외부 reviewer 제한", result["risks"])
        self.assertIn("일반 revert PR", result["risks"])

    def test_branch_fallback_recognizes_new_family_without_pr_body(self) -> None:
        context = {
            "task_id": "기록 없음",
            "title": "후속 수정",
            "head": "ops/tl/HARNESS-LEAN-003",
            "number": "기록 없음",
        }
        result = NORMALIZER.normalize_context(
            context,
            {},
            "guseoh/pawcycle-commerce",
        )
        self.assertEqual(result["task_id"], "HARNESS-LEAN-003")

    def test_fenced_secrets_are_not_copied_from_pr_sections(self) -> None:
        body = """## 작업
- 작업 ID: `SEC-HARNESS-001`

## 변경
- 목적: 안전 검증
- 포함 범위: 정상 변경
- 제외 범위: 없음

```env
PASSWORD=do-not-copy
```

## 위험
- 남은 위험: 없음
- 복구 경계: revert
"""
        context = {"task_id": "기록 없음", "title": "", "head": ""}
        result = NORMALIZER.normalize_context(
            context,
            {"pull_request": {"body": body, "title": "", "head": {"ref": ""}}},
            "guseoh/pawcycle-commerce",
        )
        self.assertNotIn("do-not-copy", repr(result))


if __name__ == "__main__":
    unittest.main()

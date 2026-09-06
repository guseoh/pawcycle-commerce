#!/usr/bin/env python3
"""Regressions for the generic task-id parser."""

from __future__ import annotations

import unittest

from scripts.task_id import extract_task_id, normalize_task_id


class TaskIdTest(unittest.TestCase):
    def test_new_families_do_not_need_an_allowlist_change(self) -> None:
        for task_id in (
            "HTTP-CLIENT-REFACTOR-001",
            "BACKEND-REFACTOR-004",
            "OPS-OCI-002",
            "PERF-PH10-008",
            "HARNESS-LEAN-003",
            "X-HTTP-CLIENT-REFACTOR-001",
        ):
            with self.subTest(task_id=task_id):
                self.assertEqual(normalize_task_id(task_id), task_id)
                self.assertEqual(extract_task_id(f"작업 ID: `{task_id}`"), task_id)

    def test_fallback_scan_respects_identifier_boundaries(self) -> None:
        self.assertEqual(
            extract_task_id("", "작업 HTTP-CLIENT-REFACTOR-001 후속 수정"),
            "HTTP-CLIENT-REFACTOR-001",
        )
        for malformed in (
            "HTTP-CLIENT-REFACTOR-001-extra",
            "HTTP-CLIENT-REFACTOR-001_extra",
            "HTTP-CLIENT-REFACTOR-001é",
        ):
            with self.subTest(malformed=malformed):
                self.assertIsNone(extract_task_id("", malformed))

    def test_case_is_normalized_without_changing_the_family_grammar(self) -> None:
        self.assertEqual(
            extract_task_id("", "ops/tl/http-client-refactor-001"),
            "HTTP-CLIENT-REFACTOR-001",
        )
        self.assertIsNone(normalize_task_id("feature/no-id"))


if __name__ == "__main__":
    unittest.main()

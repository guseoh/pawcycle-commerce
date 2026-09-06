#!/usr/bin/env python3
"""Regression tests for the Codex-to-review PR handoff contract."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MARKER = "<!-- pawcycle-ai-handoff: review-ready -->"


class PrHandoffContractTest(unittest.TestCase):
    def test_codex_prompt_skill_emits_review_ready_marker(self) -> None:
        skill = (ROOT / ".agents" / "skills" / "codex-delta-prompt" / "SKILL.md").read_text(encoding="utf-8")
        self.assertIn(MARKER, skill)
        self.assertIn("native auto-review", skill)
        self.assertIn("사용자가 이 정보를 다시 ChatGPT에 복사해야 하는 계약으로 만들지는 않는다", skill)

    def test_pr_readiness_skill_discovers_handoff_without_pr_number(self) -> None:
        skill = (ROOT / ".agents" / "skills" / "pr-readiness-review" / "SKILL.md").read_text(encoding="utf-8")
        self.assertIn(MARKER, skill)
        self.assertIn("PR 번호를 다시 요구하는 것을 기본 동작으로 삼지 않는다", skill)
        self.assertIn("CodeRabbit native auto-review", skill)
        self.assertIn("임의 선택하지 않고 후보를 명시한다", skill)

    def test_root_agent_routes_generic_review_request_to_handoff_discovery(self) -> None:
        agent = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        self.assertIn(MARKER, agent)
        self.assertIn("PR 번호를 다시 요구하는 것을 기본 동작으로 삼지 않는다", agent)
        self.assertIn("pr-readiness-review", agent)
        self.assertIn("@coderabbitai review", agent)
        self.assertIn("매 commit마다 반복 호출하지 않는다", agent)


if __name__ == "__main__":
    unittest.main()

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
        self.assertIn("request-coderabbit-review.yml", skill)
        self.assertIn("사용자가 이 정보를 다시 ChatGPT에 복사해야 하는 계약으로 만들지는 않는다", skill)
        self.assertIn("manual-review reminder", skill)
        self.assertIn("사용자 인증 GitHub identity", skill)

    def test_pr_readiness_skill_discovers_handoff_without_pr_number(self) -> None:
        skill = (ROOT / ".agents" / "skills" / "pr-readiness-review" / "SKILL.md").read_text(encoding="utf-8")
        self.assertIn(MARKER, skill)
        self.assertIn("PR 번호를 다시 요구하는 것을 기본 동작으로 삼지 않는다", skill)
        self.assertIn("request-coderabbit-review.yml", skill)
        self.assertIn("HEAD당 한 번의 manual-review reminder", skill)
        self.assertIn("user-authenticated GitHub identity", skill)
        self.assertIn("임의 선택하지 않고 후보를 명시한다", skill)

    def test_root_agent_routes_generic_review_request_to_handoff_discovery(self) -> None:
        agent = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        self.assertIn(MARKER, agent)
        self.assertIn("PR 번호를 다시 요구하는 것을 기본 동작으로 삼지 않는다", agent)
        self.assertIn("pr-readiness-review", agent)
        self.assertIn("request-coderabbit-review.yml", agent)
        self.assertIn("HEAD당 한 번의 manual-review handoff reminder", agent)
        self.assertIn("user-authenticated GitHub", agent)
        self.assertIn("Codex와 repository automation은 이 명령을 발행하지 않는다", agent)

    def test_codex_review_guidelines_exist_once_per_agent_file(self) -> None:
        # Codex Review reads the `Review guidelines` heading from the AGENTS.md closest to each changed file.
        for relative in ("AGENTS.md", "backend/AGENTS.md", "frontend/AGENTS.md", "infra/AGENTS.md", "qa/AGENTS.md"):
            with self.subTest(path=relative):
                agent = (ROOT / relative).read_text(encoding="utf-8")
                self.assertEqual(agent.count("\n## Review guidelines\n"), 1)
        root = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        self.assertIn("`@codex review`도 `@coderabbitai review`와 같은 경계를 적용한다", root)
        self.assertIn("병합 blocker로 다룬다", root)

    def test_readiness_skill_gates_codex_findings_on_latest_head(self) -> None:
        skill = (ROOT / ".agents" / "skills" / "pr-readiness-review" / "SKILL.md").read_text(encoding="utf-8")
        self.assertIn("`Codex Review 미실행`", skill)
        self.assertIn("최신 HEAD에 대한 Codex `P0`·`P1` finding", skill)
        prompt_skill = (ROOT / ".agents" / "skills" / "codex-delta-prompt" / "SKILL.md").read_text(encoding="utf-8")
        self.assertIn("`@codex review` 호출을 넣지 않는다", prompt_skill)


if __name__ == "__main__":
    unittest.main()

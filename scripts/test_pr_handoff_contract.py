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
        self.assertIn("루트 `AGENTS.md`의 `리뷰` 절", skill)
        self.assertIn("수동 복사해야 하는 계약으로 만들지는 않는다", skill)
        self.assertIn("manual-review reminder", skill)

    def test_pr_readiness_skill_discovers_handoff_without_pr_number(self) -> None:
        skill = (ROOT / ".agents" / "skills" / "pr-readiness-review" / "SKILL.md").read_text(encoding="utf-8")
        self.assertIn(MARKER, skill)
        self.assertIn("PR 번호를 다시 요구하는 것을 기본 동작으로 삼지 않는다", skill)
        self.assertIn("루트 `AGENTS.md`의 `리뷰` 절", skill)
        self.assertIn("user-authenticated GitHub", skill)
        self.assertIn("임의 선택하지 않고 후보를 명시한다", skill)

    def test_root_agent_routes_generic_review_request_to_handoff_discovery(self) -> None:
        agent = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        self.assertIn(MARKER, agent)
        self.assertIn("PR 번호를 다시 요구하는 것을 기본 동작으로 삼지 않는다", agent)
        self.assertIn("pr-readiness-review", agent)
        self.assertIn("request-coderabbit-review.yml", agent)
        self.assertIn("HEAD당 한 번의 manual-review handoff reminder", agent)
        self.assertIn("user-authenticated GitHub", agent)
        self.assertIn("구현 에이전트(예: Codex)를 포함한 에이전트가 연결된 user-authenticated GitHub 경로로", agent)
        self.assertIn("repository automation(GitHub Actions bot)은 이 명령을 대신 발행하지 않는다", agent)

    def test_codex_review_guidelines_exist_once_per_agent_file(self) -> None:
        # Codex Review reads the `Review guidelines` heading from the AGENTS.md closest to each changed file.
        for relative in (
            "AGENTS.md",
            "backend/AGENTS.md",
            "frontend/AGENTS.md",
            "infra/AGENTS.md",
            "qa/AGENTS.md",
            "scripts/AGENTS.md",
        ):
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
        self.assertIn("`@coderabbitai review`·`@codex review`를 HEAD당 한 번 요청할 수 있다", prompt_skill)
        self.assertIn("finding을 근거 없이 임의 resolve하거나", prompt_skill)


if __name__ == "__main__":
    unittest.main()

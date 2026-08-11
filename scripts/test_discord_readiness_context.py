#!/usr/bin/env python3
"""Regression tests for Production Release Readiness Discord routing."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".github" / "scripts" / "collect-discord-context.py"
READINESS_PATH = ".github/workflows/production-release-readiness.yml"


def load_module():
    spec = importlib.util.spec_from_file_location("collect_discord_context", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError("collector를 불러올 수 없음")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


discord = load_module()


class FakeApi:
    pass


class DiscordReadinessContextTests(unittest.TestCase):
    def test_actual_run_name_shape_is_routed_by_workflow_path(self):
        target_sha = "3acd70b5fa3401195c63f497b41b9780451f8281"
        run_name = f"Production Release Readiness · {target_sha}"
        payload = {
            "workflow_run": {
                "name": run_name,
                "display_title": run_name,
                "path": READINESS_PATH,
                "conclusion": "success",
                "head_branch": "main",
                "html_url": "https://example.invalid/actions/runs/31495715791",
            }
        }

        context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", FakeApi())

        self.assertTrue(context["notify"])
        self.assertEqual(context["event"], "release_readiness_success")
        self.assertEqual(context["sha"], target_sha)
        self.assertEqual(context["status"], "Readiness 확인 완료")

    def test_readiness_failure_stays_notifiable(self):
        target_sha = "a" * 40
        run_name = f"Production Release Readiness · {target_sha}"
        payload = {
            "workflow_run": {
                "name": run_name,
                "display_title": run_name,
                "path": READINESS_PATH,
                "conclusion": "failure",
                "head_branch": "main",
            }
        }

        context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", FakeApi())

        self.assertTrue(context["notify"])
        self.assertEqual(context["event"], "release_readiness_failure")
        self.assertEqual(context["sha"], target_sha)

    def test_similar_run_name_from_other_workflow_is_suppressed(self):
        target_sha = "b" * 40
        run_name = f"Production Release Readiness · {target_sha}"
        payload = {
            "workflow_run": {
                "name": run_name,
                "display_title": run_name,
                "path": ".github/workflows/unrelated.yml",
                "conclusion": "success",
            }
        }

        context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", FakeApi())

        self.assertFalse(context["notify"])
        self.assertEqual(context["event"], "suppressed")

    def test_invalid_display_title_does_not_expose_untrusted_sha_text(self):
        invalid_title = "Production Release Readiness · INVALID-SHA-DO-NOT-EXPOSE"
        payload = {
            "workflow_run": {
                "name": invalid_title,
                "display_title": invalid_title,
                "path": READINESS_PATH,
                "conclusion": "success",
                "head_branch": "main",
            }
        }

        context = discord.collect("workflow_run", payload, "guseoh/pawcycle-commerce", FakeApi())

        self.assertTrue(context["notify"])
        self.assertEqual(context["event"], "release_readiness_failure")
        self.assertEqual(context["sha"], discord.MISSING)
        self.assertNotIn("INVALID-SHA-DO-NOT-EXPOSE", repr(context))


if __name__ == "__main__":
    unittest.main()

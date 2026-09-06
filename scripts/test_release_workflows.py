#!/usr/bin/env python3
"""Static regressions for Production image publishing and explicit deploy approval gates."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS_DIR = ROOT / ".github" / "workflows"
PUBLISH_WORKFLOW = WORKFLOWS_DIR / "publish-production-images.yml"
PRODUCTION_DEPLOY_WORKFLOW = WORKFLOWS_DIR / "production-deploy.yml"


class ReleaseWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.publish = PUBLISH_WORKFLOW.read_text(encoding="utf-8")
        cls.production_deploy = PRODUCTION_DEPLOY_WORKFLOW.read_text(encoding="utf-8")
        cls.other_workflows = {
            path.name: path.read_text(encoding="utf-8")
            for path in WORKFLOWS_DIR.glob("*.yml")
            if path != PRODUCTION_DEPLOY_WORKFLOW
        }

    def test_publish_workflow_uses_job_level_runtime_diff_gate(self) -> None:
        header = self.publish.split("permissions:", 1)[0]
        self.assertNotIn("paths:", header)
        self.assertIn("name: Classify production image changes", self.publish)
        self.assertIn('git diff --no-renames --name-only "$BEFORE_SHA" "$HEAD_SHA"', self.publish)
        self.assertIn("grep -Eq '^(backend/|frontend/|infra/production/)'", self.publish)
        publish_block = self.publish[self.publish.index("\n  publish:\n") :]
        self.assertIn("needs: classify", publish_block)
        self.assertIn("if: needs.classify.outputs.publish == 'true'", publish_block)

    def test_production_deploy_requires_explicit_workflow_dispatch(self) -> None:
        header = self.production_deploy.split("permissions:", 1)[0]
        self.assertIn("workflow_dispatch:", header)
        for forbidden_trigger in (
            "workflow_run:",
            "push:",
            "schedule:",
            "repository_dispatch:",
        ):
            self.assertNotIn(forbidden_trigger, header)
        self.assertIn("environment: production", self.production_deploy)

    def test_no_other_workflow_dispatches_production_deploy(self) -> None:
        for workflow_name, workflow in self.other_workflows.items():
            with self.subTest(workflow=workflow_name):
                self.assertNotIn(
                    "production-deploy.yml",
                    workflow,
                    f"{workflow_name} must not dispatch Production Deploy automatically",
                )


if __name__ == "__main__":
    unittest.main()

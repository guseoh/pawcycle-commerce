#!/usr/bin/env python3
"""Static regressions for Production image publishing and temporary auto deploy gates."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PUBLISH_WORKFLOW = ROOT / ".github" / "workflows" / "publish-production-images.yml"
AUTO_DEPLOY_WORKFLOW = ROOT / ".github" / "workflows" / "mvp4-temporary-auto-production-deploy.yml"


class ReleaseWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.publish = PUBLISH_WORKFLOW.read_text(encoding="utf-8")
        cls.auto_deploy = AUTO_DEPLOY_WORKFLOW.read_text(encoding="utf-8")

    def test_publish_workflow_uses_job_level_runtime_diff_gate(self) -> None:
        header = self.publish.split("permissions:", 1)[0]
        self.assertNotIn("paths:", header)
        self.assertIn("name: Classify production image changes", self.publish)
        self.assertIn('git diff --no-renames --name-only "$BEFORE_SHA" "$HEAD_SHA"', self.publish)
        self.assertIn("grep -Eq '^(backend/|frontend/|infra/production/)'", self.publish)
        publish_block = self.publish[self.publish.index("\n  publish:\n") :]
        self.assertIn("needs: classify", publish_block)
        self.assertIn("if: needs.classify.outputs.publish == 'true'", publish_block)

    def test_auto_deploy_requires_successful_publish_job(self) -> None:
        self.assertIn("SOURCE_RUN_ID: ${{ github.event.workflow_run.id }}", self.auto_deploy)
        self.assertIn('/actions/runs/$SOURCE_RUN_ID/jobs?per_page=100', self.auto_deploy)
        self.assertIn('.name == "Publish production images" and .conclusion == "success"', self.auto_deploy)
        self.assertIn("echo \"deploy=false\" >> \"$GITHUB_OUTPUT\"", self.auto_deploy)
        self.assertIn("if: steps.release.outputs.deploy == 'true'", self.auto_deploy)
        self.assertIn("Production Deploy workflow: not dispatched", self.auto_deploy)


if __name__ == "__main__":
    unittest.main()

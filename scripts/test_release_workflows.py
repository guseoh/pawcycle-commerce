#!/usr/bin/env python3
"""Static regressions for Production image publishing and deployment decommissioning."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS_DIR = ROOT / ".github" / "workflows"
PUBLISH_WORKFLOW = WORKFLOWS_DIR / "publish-production-images.yml"
PRODUCTION_DEPLOY_WORKFLOW = WORKFLOWS_DIR / "production-deploy.yml"
AUTO_DISPATCH_PATTERNS = (
    re.compile(r"\bgh\s+workflow\s+run\s+[\"']?production-deploy\.yml\b"),
    re.compile(r"/actions/workflows/production-deploy\.yml/dispatches\b"),
    re.compile(r"createWorkflowDispatch[\s\S]{0,800}workflow_id\s*:\s*[\"']production-deploy\.yml[\"']"),
    re.compile(r"workflow_id\s*:\s*[\"']production-deploy\.yml[\"'][\s\S]{0,800}createWorkflowDispatch"),
)
AWS_WORKFLOW_PATTERNS = (
    "aws-actions/configure-aws-credentials",
    "aws ssm ",
    "aws ec2 ",
    "aws rds ",
    "aws s3 ",
    "aws cloudwatch ",
    "aws sns ",
)


class ReleaseWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.publish = PUBLISH_WORKFLOW.read_text(encoding="utf-8")
        cls.workflows = {
            path.name: path.read_text(encoding="utf-8")
            for path in WORKFLOWS_DIR.glob("*.yml")
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

    def test_production_deploy_workflow_is_retired(self) -> None:
        self.assertFalse(PRODUCTION_DEPLOY_WORKFLOW.exists())

    def test_no_workflow_dispatches_retired_production_deploy(self) -> None:
        for workflow_name, workflow in self.workflows.items():
            for pattern in AUTO_DISPATCH_PATTERNS:
                with self.subTest(workflow=workflow_name, pattern=pattern.pattern):
                    self.assertIsNone(pattern.search(workflow))

    def test_no_workflow_contains_active_aws_execution(self) -> None:
        for workflow_name, workflow in self.workflows.items():
            lowered = workflow.lower()
            for pattern in AWS_WORKFLOW_PATTERNS:
                with self.subTest(workflow=workflow_name, pattern=pattern):
                    self.assertNotIn(pattern, lowered)


if __name__ == "__main__":
    unittest.main()

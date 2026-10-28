#!/usr/bin/env python3
"""Static regressions for Production image publishing and deployment decommissioning."""

from pathlib import Path
import importlib.util
import re
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS_DIR = ROOT / ".github" / "workflows"
PUBLISH_WORKFLOW = WORKFLOWS_DIR / "publish-production-images.yml"
PATH_CLASSIFIER = ROOT / "scripts" / "classify-production-image-changes.py"
SPEC = importlib.util.spec_from_file_location("production_image_classifier", PATH_CLASSIFIER)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Production image classifier is unavailable")
CLASSIFIER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CLASSIFIER)
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
        self.assertIn('python scripts/classify-production-image-changes.py >> "$GITHUB_OUTPUT"', self.publish)
        self.assertIn('if ! changed_paths="$(git diff', self.publish)
        self.assertIn('if [ "$EVENT_NAME" = "workflow_dispatch" ]', self.publish)
        self.assertIn('org.opencontainers.image.revision=', self.publish)
        self.assertIn('Backend digest:', self.publish)
        self.assertIn('Frontend digest:', self.publish)
        publish_block = self.publish[self.publish.index("\n  publish:\n") :]
        self.assertIn("needs: classify", publish_block)
        self.assertIn("if: needs.classify.outputs.publish == 'true'", publish_block)

    def test_diff_guard_and_manual_publish_fail_safe(self) -> None:
        self.assertIn('''if [ "$EVENT_NAME" = "workflow_dispatch" ]; then''', self.publish)
        self.assertIn('''''|0000000000000000000000000000000000000000)''', self.publish)
        self.assertIn('''if [[ ! "$BEFORE_SHA" =~ ^[0-9a-f]{40}$ ]]''', self.publish)
        self.assertIn('''if ! changed_paths="$(git diff''', self.publish)
        self.assertIn('''echo "publish=true" >> "$GITHUB_OUTPUT"''', self.publish)

    def test_release_readiness_keeps_exact_sha_image_pair(self) -> None:
        readiness = (WORKFLOWS_DIR / "production-release-readiness.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn('''-backend:${TARGET_SHA}''', readiness)
        self.assertIn('''-frontend:${TARGET_SHA}''', readiness)
        self.assertIn('''git merge-base --is-ancestor "$TARGET_SHA" HEAD''', readiness)
        self.assertIn('"refs/heads/main"', readiness)
        self.assertIn('^[0-9a-f]{40}$', readiness)
        self.assertIn('git cat-file -e "${TARGET_SHA}^{commit}"', readiness)
        self.assertEqual(readiness.count("docker buildx imagetools inspect"), 2)

    def test_current_target_does_not_imply_delivery_or_verification(self) -> None:
        spec = importlib.util.spec_from_file_location(
            "production_contracts", ROOT / "infra/production/validate-production-contracts.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        readiness = self.workflows["production-release-readiness.yml"]
        architecture = module.ARCHITECTURE.read_text(encoding="utf-8")
        module.validate_current_status_contract(readiness, architecture)
        mutations = (
            (readiness, architecture.replace("**OCI app01**", "**없음**")),
            (readiness, architecture.replace("CD: **DEFER**", "CD: **ACTIVE**")),
            (readiness, architecture.replace("무중단 배포: **DEFER**", "무중단 배포: **ACTIVE**")),
            (readiness, architecture.replace("Production Verified가 아니다", "Production Verified")),
            (readiness.replace("No OCI Application Deploy workflow", "OCI Application Deploy workflow"), architecture),
            (readiness.replace("full CI success, deployment approval, or deployment success", "deployment success"), architecture),
            (readiness.replace("separate approval and recovery gates", "no approval"), architecture),
            (readiness + "There is no active Production deployment target.", architecture),
        )
        for index, (changed_readiness, changed_architecture) in enumerate(mutations):
            with self.subTest(mutation=index):
                with self.assertRaises(SystemExit):
                    module.validate_current_status_contract(changed_readiness, changed_architecture)


    def test_production_deploy_workflow_is_retired(self) -> None:
        self.assertFalse(PRODUCTION_DEPLOY_WORKFLOW.exists())

    def test_oci_release_tests_are_ci_only_and_activation_is_not_wired(self) -> None:
        validation = self.workflows["validate-conventions.yml"]
        self.assertIn("infra/production/test-oci-release.py", validation)
        for name, workflow in self.workflows.items():
            with self.subTest(workflow=name):
                self.assertNotRegex(workflow, r"python(?:3)?\s+infra/production/oci-release\.py\b")

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


class ImagePublishClassifierTest(unittest.TestCase):
    def test_non_image_changes_skip_both_images(self) -> None:
        paths = [
            "backend/src/test/java/ExampleTest.java",
            "backend/AGENTS.md",
            "frontend/AGENTS.md",
            "frontend/README.md",
            "frontend/scripts/run-tests.mjs",
            "docs/runbook/release.md",
            "scripts/test_release_workflows.py",
            ".agents/skills/platform-sre/SKILL.md",
            ".github/workflows/validate-conventions.yml",
            "infra/performance/k6/test.js",
        ]
        for path in paths:
            with self.subTest(path=path):
                self.assertFalse(CLASSIFIER.should_publish([path]))

    def test_backend_and_frontend_image_inputs_require_pair(self) -> None:
        paths = [
            "backend/src/main/java/com/pawcycle/backend/Application.java",
            "backend/src/main/resources/application.yml",
            "backend/build.gradle",
            "backend/gradle/wrapper/gradle-wrapper.properties",
            "infra/production/backend.Dockerfile",
            "infra/production/frontend.Dockerfile",
            "frontend/src/app/page.tsx",
            "frontend/package.json",
            "frontend/next.config.ts",
            ".github/workflows/publish-production-images.yml",
            ".dockerignore",
            "unknown-build-input.config",
        ]
        for path in paths:
            with self.subTest(path=path):
                self.assertTrue(CLASSIFIER.should_publish([path]))

    def test_mixed_diff_unknown_and_empty_diff(self) -> None:
        self.assertTrue(CLASSIFIER.should_publish([
            "backend/src/test/java/ExampleTest.java", "backend/build.gradle"
        ]))
        self.assertTrue(CLASSIFIER.should_publish([
            "README.md", "unclassified/new-file.toml"
        ]))
        self.assertFalse(CLASSIFIER.should_publish([]))

    def test_cli_publishes_exact_boolean_output(self) -> None:
        for paths, expected in (
            (["backend/src/test/java/A.java", "README.md"], "publish=false"),
            (["backend/src/test/java/A.java", "frontend/src/app/page.tsx"], "publish=true"),
            (["unknown.config"], "publish=true"),
        ):
            with self.subTest(paths=paths):
                run = subprocess.run(
                    [sys.executable, str(PATH_CLASSIFIER)],
                    input="\n".join(paths) + "\n",
                    capture_output=True, text=True, check=True,
                )
                self.assertEqual(run.stdout.strip(), expected)


if __name__ == "__main__":
    unittest.main()

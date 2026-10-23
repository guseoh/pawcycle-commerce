#!/usr/bin/env python3
"""Offline regressions for safe deletion of merged dev task branches."""

from __future__ import annotations

from pathlib import Path
import unittest

from scripts.delete_merged_task_branch import (
    REPOSITORY,
    cleanup,
    eligible_head,
)

ROOT = Path(__file__).resolve().parents[1]
SHA = "a" * 40


def event(
    *, action="closed", merged=True, base="dev",
    head="feat/be/task-123", sha=SHA, origin=REPOSITORY,
):
    return {
        "action": action,
        "pull_request": {
            "merged": merged,
            "base": {"ref": base},
            "head": {
                "ref": head,
                "sha": sha,
                "repo": {"full_name": origin},
            },
        },
    }


class FakeAPI:
    def __init__(self, branch="feat/be/task-123", sha=SHA, protected=False):
        self.branch = branch
        self.sha = sha
        self.protected = protected
        self.gets = []
        self.deletes = []
        self.open_prs = []

    def get(self, path):
        self.gets.append(path)
        if path.startswith("git/ref/heads/"):
            return {"ref": f"refs/heads/{self.branch}", "object": {"sha": self.sha}}
        if path.startswith("branches/"):
            return {"protected": self.protected}
        if path.startswith("pulls?"):
            return self.open_prs
        raise AssertionError(f"Unexpected GET {path}")

    def delete(self, path):
        self.deletes.append(path)
        return True


class BranchCleanupTest(unittest.TestCase):
    def test_only_merged_dev_task_pr_is_eligible(self):
        self.assertEqual(eligible_head(event(), REPOSITORY), ("feat/be/task-123", SHA))
        for bad in (
            event(merged=False),
            event(action="reopened"),
            event(base="main"),
            event(head="dev"),
            event(head="main"),
            event(origin="someone/fork"),
            event(sha="unknown"),
        ):
            with self.subTest(bad=bad):
                self.assertIsNone(eligible_head(bad, REPOSITORY))
        self.assertIsNone(eligible_head(event(), "unrelated/repo"))

    def test_delete_only_after_full_checks(self):
        api = FakeAPI()
        self.assertEqual(cleanup(api, event(), REPOSITORY), "deleted: feat/be/task-123")
        self.assertEqual(api.deletes, ["git/refs/heads/feat/be/task-123"])
        self.assertEqual(api.gets.count("git/ref/heads/feat/be/task-123"), 2)

    def test_changed_head_never_deleted(self):
        api = FakeAPI(sha="b" * 40)
        self.assertIn("HEAD changed", cleanup(api, event(), REPOSITORY))
        self.assertEqual(api.deletes, [])

    def test_ref_deleted_before_cleanup_is_idempotent(self):
        class MissingRefAPI(FakeAPI):
            def get(self, path):
                if path.startswith("git/ref/heads/"):
                    self.gets.append(path)
                    return None
                return super().get(path)

        api = MissingRefAPI()
        self.assertIn("already missing", cleanup(api, event(), REPOSITORY))
        self.assertEqual(api.deletes, [])

    def test_head_changed_during_checks_never_deleted(self):
        class RacingRefAPI(FakeAPI):
            def get(self, path):
                result = super().get(path)
                if path.startswith("git/ref/heads/") and self.gets.count(path) == 2:
                    result["object"]["sha"] = "b" * 40
                return result

        api = RacingRefAPI()
        self.assertIn("HEAD changed", cleanup(api, event(), REPOSITORY))
        self.assertEqual(api.deletes, [])

    def test_protected_branch_never_deleted(self):
        api = FakeAPI(protected=True)
        self.assertIn("protected", cleanup(api, event(), REPOSITORY))
        self.assertEqual(api.deletes, [])

    def test_open_pr_head_or_base_never_deleted(self):
        for pr in (
            {"head": {"ref": "feat/be/task-123", "repo": {"full_name": REPOSITORY}}, "base": {"ref": "dev"}},
            {"head": {"ref": "other", "repo": {"full_name": REPOSITORY}}, "base": {"ref": "feat/be/task-123"}},
        ):
            with self.subTest(pr=pr):
                api = FakeAPI()
                api.open_prs = [pr]
                self.assertIn("open PR", cleanup(api, event(), REPOSITORY))
                self.assertEqual(api.deletes, [])

    def test_other_branch_or_unmerged_event_does_nothing(self):
        for e in (event(head="dev"), event(base="main"), event(merged=False)):
            api = FakeAPI()
            self.assertIn("ineligible", cleanup(api, e, REPOSITORY))
            self.assertEqual(api.gets, [])
            self.assertEqual(api.deletes, [])

    def test_workflow_has_scoped_trigger_and_permissions(self):
        text = (ROOT / ".github/workflows/delete-merged-task-branch.yml").read_text(encoding="utf-8")
        self.assertIn("types: [closed]", text)
        self.assertIn("branches: [dev]", text)
        self.assertIn("pull_request:", text)
        self.assertNotIn("pull_request_target:", text)
        self.assertIn("github.event.pull_request.merged == true", text)
        self.assertIn("github.event.pull_request.head.ref != 'dev'", text)
        self.assertIn("github.event.pull_request.head.ref != 'main'", text)
        self.assertIn("contents: write", text)
        self.assertIn("pull-requests: read", text)
        self.assertNotIn("workflow_dispatch:", text)
        self.assertIn("python scripts/delete_merged_task_branch.py", text)


if __name__ == "__main__":
    unittest.main()

"""Offline tests for OCI A1 monitor watchdog: no network, OCI or Discord calls."""

import importlib.util
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest import TestCase
from unittest.mock import patch

path = Path(__file__).with_name("oci_a1_monitor_health.py")
spec = importlib.util.spec_from_file_location("oci_a1_monitor_health", path)
health = importlib.util.module_from_spec(spec)
spec.loader.exec_module(health)

NOW = datetime(2026, 10, 16, 5, 0, tzinfo=timezone.utc)


def run(hours_ago, conclusion="success", status="completed"):
    stamp = (NOW - timedelta(hours=hours_ago)).isoformat()
    return {
        "id": int(hours_ago * 10 + 1),
        "created_at": stamp,
        "conclusion": conclusion,
        "status": status,
        "html_url": "https://github.com/guseoh/pawcycle-commerce/actions/runs/123",
    }


class HealthTests(TestCase):
    def test_recent_success_is_healthy(self):
        report = health.evaluate([run(0.5), run(1.5)], NOW)
        self.assertFalse(report["unhealthy"])
        self.assertEqual(report["success_24h"], 2)

    def test_stale_last_success_alerts(self):
        report = health.evaluate([run(3.1)], NOW)
        self.assertTrue(report["unhealthy"])
        self.assertIn("older than 3 hours", report["reasons"][0])

    def test_exact_three_hours_not_stale(self):
        self.assertFalse(health.evaluate([run(3)], NOW)["unhealthy"])

    def test_no_schedule_runs_alerts(self):
        report = health.evaluate([], NOW)
        self.assertTrue(report["unhealthy"])
        self.assertIsNone(report["last_good"])

    def test_latest_failure_alerts_even_if_recent_success(self):
        report = health.evaluate([run(0.1, "failure"), run(1)], NOW)
        self.assertTrue(report["unhealthy"])
        self.assertEqual(report["failed_24h"], 1)
        self.assertEqual(report["success_24h"], 1)

    def test_pending_latest_run_does_not_mask_old_success(self):
        report = health.evaluate([run(0.01, None, "in_progress"), run(4)], NOW)
        self.assertTrue(report["unhealthy"])

    def test_recovered_run_clears_previous_failure(self):
        report = health.evaluate([run(0.1), run(1, "failure")], NOW)
        self.assertFalse(report["unhealthy"])
        self.assertEqual(report["failed_24h"], 1)

    def test_report_window_excludes_older_runs(self):
        report = health.evaluate([run(1), run(25)], NOW)
        self.assertEqual(report["success_24h"], 1)
        self.assertEqual(report["total_24h"], 1)

    def test_discord_payload_has_mentions_disabled(self):
        payload = health.discord_payload("Status", "Nothing reserved",
                                         [("Result", "OUT_OF_HOST_CAPACITY")],
                                         health.run_url(None), NOW)
        self.assertEqual(payload["allowed_mentions"], {"parse": []})
        self.assertEqual(len(payload["embeds"]), 1)
        self.assertEqual(payload["embeds"][0]["fields"][0]["value"], "OUT_OF_HOST_CAPACITY")

    @patch.object(health, "github_request")
    @patch.object(health, "post_discord")
    @patch.object(health, "find_health_issue", return_value=None)
    def test_first_incident_sends_then_creates_issue(self, _find, send, api):
        report = health.evaluate([run(5)], NOW)
        self.assertEqual(health.sync_health(report, NOW), "new_health_alert")
        send.assert_called_once()
        self.assertEqual(api.call_args.args[:2], ("POST", "/issues"))
        self.assertIn(health.MARKER, api.call_args.args[2]["body"])

    @patch.object(health, "github_request")
    @patch.object(health, "post_discord")
    @patch.object(health, "find_health_issue", return_value={"state": "open", "number": 55})
    def test_repeated_incident_does_not_spam(self, _find, send, api):
        self.assertEqual(health.sync_health(health.evaluate([], NOW), NOW), "alert_already_open")
        send.assert_not_called()
        api.assert_not_called()

    @patch.object(health, "github_request")
    @patch.object(health, "post_discord")
    @patch.object(health, "find_health_issue", return_value={"state": "open", "number": 55})
    def test_recovery_closes_health_issue_without_alert(self, _find, send, api):
        self.assertEqual(health.sync_health(health.evaluate([run(1)], NOW), NOW), "recovered")
        self.assertEqual(api.call_args.args[:2], ("PATCH", "/issues/55"))
        self.assertEqual(api.call_args.args[2]["state"], "closed")
        send.assert_not_called()

    @patch.object(health, "github_request")
    @patch.object(health, "post_discord", side_effect=RuntimeError("Discord unavailable"))
    @patch.object(health, "find_health_issue", return_value=None)
    def test_discord_failure_does_not_record_alert(self, _find, _send, api):
        with self.assertRaises(RuntimeError):
            health.sync_health(health.evaluate([], NOW), NOW)
        api.assert_not_called()

    @patch.object(health, "post_discord")
    @patch.object(health, "find_health_issue")
    def test_preview_does_not_access_issue_or_discord(self, find, send):
        self.assertEqual(health.sync_health(health.evaluate([], NOW), NOW, dry_run=True), "would_alert")
        find.assert_not_called()
        send.assert_not_called()

    @patch.object(health, "github_request")
    def test_sched_runs_sorted_by_latest_created_at(self, api):
        api.return_value = {"workflow_runs": [run(5), run(1), run(3)]}
        self.assertEqual(health.scheduled_runs()[0]["created_at"], run(1)["created_at"])

    @patch.object(health, "post_discord")
    def test_daily_unavailable_sends_daily_summary_only(self, send):
        import oci_a1_capacity_monitor as monitor
        report = health.evaluate([run(1)], NOW)
        with patch.object(monitor, "fetch_capacity", return_value=("OUT_OF_HOST_CAPACITY", None)), \
             patch.object(monitor, "sync_issue") as sync:
            self.assertEqual(health.daily_report(report, NOW), ("OUT_OF_HOST_CAPACITY", None))
            sync.assert_called_once()
        self.assertIn("daily capacity", send.call_args.args[0]["embeds"][0]["title"])

    @patch.object(health, "post_discord")
    def test_daily_probe_failure_never_claims_available(self, send):
        import oci_a1_capacity_monitor as monitor
        report = health.evaluate([run(1)], NOW)
        with patch.object(monitor, "fetch_capacity", side_effect=RuntimeError("hidden details")):
            self.assertEqual(health.daily_report(report, NOW), ("CHECK_FAILED", "RuntimeError"))
        payload = send.call_args.args[0]
        values = [f["value"] for f in payload["embeds"][0]["fields"]]
        self.assertIn("CHECK_FAILED", values)
        self.assertNotIn("hidden details", str(payload))

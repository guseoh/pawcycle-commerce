"""Offline tests: do not reach OCI, GitHub, or create VM resources."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

path = Path(__file__).with_name("oci_a1_capacity_monitor.py")
spec = importlib.util.spec_from_file_location("monitor", path)
monitor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(monitor)


class MonitorTests(unittest.TestCase):
    def report(self, status="OUT_OF_HOST_CAPACITY", count=None, ocpus=2, mem=12):
        entry = SimpleNamespace(
            availability_status=status,
            available_count=count,
            instance_shape=monitor.SHAPE,
            instance_shape_config=SimpleNamespace(ocpus=ocpus, memory_in_gbs=mem),
        )
        return SimpleNamespace(shape_availabilities=[entry])

    def api(self, issue=None):
        api = Mock()
        api.find_issue.return_value = issue
        api.request.return_value = {"html_url": "https://github.com/guseoh/pawcycle-commerce/issues/1"}
        return api

    def issue(self, status, state="open"):
        return {"number": 12, "state": state, "title": monitor.TITLE,
                "body": "<!-- oci-a1-capacity-monitor status=" + status + " -->"}

    def test_unavailable_count_unknown(self):
        self.assertEqual(monitor.parse_report(self.report()), ("OUT_OF_HOST_CAPACITY", None))

    def test_available_positive_count(self):
        self.assertEqual(monitor.parse_report(self.report("AVAILABLE", 1)), ("AVAILABLE", 1))

    def test_available_unknown_count(self):
        self.assertEqual(monitor.parse_report(self.report("AVAILABLE")), ("AVAILABLE", None))

    def test_available_zero_count_rejected(self):
        with self.assertRaises(ValueError):
            monitor.parse_report(self.report("AVAILABLE", 0))

    def test_wrong_cpu_rejected(self):
        with self.assertRaises(ValueError):
            monitor.parse_report(self.report("AVAILABLE", 1, ocpus=1))

    def test_wrong_mem_rejected(self):
        with self.assertRaises(ValueError):
            monitor.parse_report(self.report("AVAILABLE", 1, mem=6))

    def test_unexpected_status_rejected(self):
        with self.assertRaises(ValueError):
            monitor.parse_report(self.report("HARDWARE_NOT_SUPPORTED"))

    @patch.object(monitor, "send_discord_alert")
    def test_unavailable_no_issue_no_notification(self, send_alert):
        api = self.api()
        monitor.sync_issue(api, "OUT_OF_HOST_CAPACITY", None, "now", "run")
        api.request.assert_not_called()
        send_alert.assert_not_called()

    @patch.object(monitor, "send_discord_alert")
    def test_initial_available_creates_unassigned_issue_and_discord_alert(self, send_alert):
        api = self.api()
        monitor.sync_issue(api, "AVAILABLE", 1, "now", "run")
        method, path, payload = api.request.call_args.args
        self.assertEqual((method, path), ("POST", "/issues"))
        self.assertNotIn("assignees", payload)
        self.assertNotIn("@", payload["body"])
        self.assertIn("status=AVAILABLE", payload["body"])
        send_alert.assert_called_once_with(1, "now", "run")


    @patch.object(monitor, "send_discord_alert")
    def test_repeat_available_no_spam(self, send_alert):
        api = self.api(self.issue("AVAILABLE"))
        monitor.sync_issue(api, "AVAILABLE", 1, "now", "run")
        api.request.assert_not_called()
        send_alert.assert_not_called()

    @patch.object(monitor, "send_discord_alert")
    def test_user_closed_available_no_reopen(self, send_alert):
        api = self.api(self.issue("AVAILABLE", "closed"))
        monitor.sync_issue(api, "AVAILABLE", 1, "now", "run")
        api.request.assert_not_called()
        send_alert.assert_not_called()

    @patch.object(monitor, "send_discord_alert")
    def test_out_of_capacity_closes_tracking_issue_without_discord(self, send_alert):
        api = self.api(self.issue("AVAILABLE"))
        monitor.sync_issue(api, "OUT_OF_HOST_CAPACITY", None, "now", "run")
        payload = api.request.call_args.args[2]
        self.assertEqual(payload["state"], "closed")
        self.assertEqual(payload["assignees"], [])
        send_alert.assert_not_called()

    @patch.object(monitor, "send_discord_alert")
    def test_return_of_capacity_reopens_unassigned_and_sends_discord(self, send_alert):
        api = self.api(self.issue("OUT_OF_HOST_CAPACITY", "closed"))
        monitor.sync_issue(api, "AVAILABLE", None, "now", "run")
        self.assertEqual(api.request.call_count, 1)
        payload = api.request.call_args.args[2]
        self.assertEqual(payload["state"], "open")
        self.assertEqual(payload["assignees"], [])
        self.assertNotIn("@", payload["body"])
        send_alert.assert_called_once_with(None, "now", "run")

    @patch.object(monitor, "send_discord_alert")
    def test_repeat_available_clears_legacy_assignments_without_spam(self, send_alert):
        issue = self.issue("AVAILABLE")
        issue["assignees"] = [{"login": "guseoh"}]
        api = self.api(issue)
        monitor.sync_issue(api, "AVAILABLE", 1, "now", "run")
        self.assertEqual(api.request.call_args.args[2], {"assignees": []})
        send_alert.assert_not_called()

    @patch.object(monitor, "send_discord_alert")
    def test_repeat_unavailable_clears_legacy_assignments_without_discord(self, send_alert):
        issue = self.issue("OUT_OF_HOST_CAPACITY", "closed")
        issue["assignees"] = [{"login": "guseoh"}]
        api = self.api(issue)
        monitor.sync_issue(api, "OUT_OF_HOST_CAPACITY", None, "now", "run")
        self.assertEqual(api.request.call_args.args[2], {"assignees": []})
        send_alert.assert_not_called()

    def test_discord_payload_has_mention_protection_and_capacity_details(self):
        payload = monitor.discord_payload(1, "now", "https://github.com/run")
        self.assertEqual(payload["allowed_mentions"], {"parse": []})
        embed = payload["embeds"][0]
        self.assertEqual(len(payload["embeds"]), 1)
        self.assertEqual(len(embed["fields"]), 5)
        self.assertEqual(embed["url"], "https://github.com/run")
        self.assertIn("no VM created", embed["footer"]["text"])


if __name__ == "__main__":
    unittest.main()

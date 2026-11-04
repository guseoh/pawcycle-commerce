import os
import unittest
from unittest.mock import patch

import oci_a1_heartbeat as heartbeat


VALID = "https://hc-ping.com/12345678-1234-4234-a234-1234567890ab"


class FakeResponse:
    def __init__(self, status=200, body=b"OK"):
        self.status = status
        self.body = body

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def read(self, _):
        return self.body


class FakeOpener:
    def __init__(self, response=None, error=None):
        self.response = response or FakeResponse()
        self.error = error
        self.calls = []

    def open(self, request, timeout):
        self.calls.append((request, timeout))
        if self.error is not None:
            raise self.error
        return self.response


class HeartbeatTests(unittest.TestCase):
    def test_valid_url_posts_without_token_or_url_in_log(self):
        opener = FakeOpener()
        heartbeat.report_success(VALID, opener)
        request, timeout = opener.calls[0]
        self.assertEqual(request.full_url, VALID)
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(timeout, 8)
        self.assertNotIn("Authorization", request.headers)

    def test_noncanonical_or_insecure_urls_rejected_before_network(self):
        for url in ("http://" + VALID[8:], VALID + "/fail", VALID + "?token=1",
                    VALID + "#fragment", "https://evil.example/uuid", ""):
            opener = FakeOpener()
            with self.subTest(url=url), self.assertRaisesRegex(RuntimeError, "Invalid OCI heartbeat"):
                heartbeat.report_success(url, opener)
            self.assertEqual(opener.calls, [])

    def test_200_not_found_and_non200_are_failures(self):
        for response in (FakeResponse(200, b"OK (not found)"),
                         FakeResponse(200, b"OK (rate limited)"), FakeResponse(503)):
            with self.subTest(status=response.status, body=response.body):
                with self.assertRaisesRegex(RuntimeError, "rejected success"):
                    heartbeat.report_success(VALID, FakeOpener(response))

    def test_transport_error_redacts_url_and_exception(self):
        from urllib.error import URLError
        opener = FakeOpener(error=URLError("my-private-credential"))
        with self.assertRaisesRegex(RuntimeError, "^OCI heartbeat transport failed$") as ctx:
            heartbeat.report_success(VALID, opener)
        self.assertNotIn(VALID, str(ctx.exception))
        self.assertNotIn("my-private-credential", str(ctx.exception))

    def test_redirect_handler_rejects_redirects(self):
        req = heartbeat.Request(VALID)
        handler = heartbeat.BlockRedirect()
        self.assertIsNone(handler.redirect_request(req, None, 302, "found", {}, "https://example.com"))

    def test_no_configured_secret_does_not_make_network_call(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(heartbeat, "report_success") as report:
            heartbeat.main()
            report.assert_not_called()

    def test_configured_secret_calls_success_once(self):
        with patch.dict(os.environ, {"HEALTHCHECKS_OCI_PING_URL": VALID}, clear=True), \
             patch.object(heartbeat, "report_success") as report:
            heartbeat.main()
            report.assert_called_once_with(VALID)


if __name__ == "__main__":
    unittest.main()

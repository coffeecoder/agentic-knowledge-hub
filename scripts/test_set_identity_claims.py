"""Offline tests for administrative updates; no real accounts or credentials."""
import io
import json
import subprocess
import unittest
import urllib.error
from unittest.mock import patch

import set_identity_claims as setup


class ClaimsSetupTest(unittest.TestCase):
    def test_reader_has_no_publishing_capability(self):
        result = setup.application_claims("local", "support", "manual-text", "reader")
        self.assertEqual(["knowledge:read"], result["permissions"])
        self.assertEqual([], result["write_sources"])
        self.assertEqual([], result["assignable_groups"])

    def test_apply_preserves_unrelated_claims_and_verifies_result(self):
        desired = setup.application_claims("local", "support", "manual-text", "publisher")
        responses = [
            {"users": [{"localId": "synthetic", "customAttributes": '{"other":{"enabled":true}}'}]},
            {},
            {"users": [{"localId": "synthetic", "customAttributes": json.dumps({"other": {"enabled": True}, "akh": desired})}]},
        ]
        requests = []

        def open_request(request, timeout):
            requests.append(request)
            return io.BytesIO(json.dumps(responses.pop(0)).encode())

        args = ["setup", "--project", "synthetic-project", "--uid", "synthetic", "--access", "publisher", "--apply"]
        output = io.StringIO()
        with patch("sys.argv", args), patch("sys.stdout", output), patch(
            "subprocess.run", return_value=subprocess.CompletedProcess([], 0, stdout="synthetic-token")
        ), patch("urllib.request.urlopen", side_effect=open_request):
            setup.main()
        update = json.loads(requests[1].data)
        self.assertEqual("synthetic", update["localId"])
        self.assertEqual({"other": {"enabled": True}, "akh": desired}, json.loads(update["customAttributes"]))
        self.assertIn("permissions verified", output.getvalue())
        self.assertNotIn("synthetic-token", output.getvalue())
        self.assertEqual(3, len(requests))
        for request in requests:
            self.assertEqual("synthetic-project", request.get_header("X-goog-user-project"))

    def test_http_failure_reports_stage_without_response_or_token(self):
        args = ["setup", "--project", "synthetic-project", "--uid", "synthetic", "--access", "publisher", "--apply"]
        failure = urllib.error.HTTPError("https://example.invalid", 403, "sensitive-detail", {}, io.BytesIO(b"sensitive-body"))
        with patch("sys.argv", args), patch(
            "subprocess.run", return_value=subprocess.CompletedProcess([], 0, stdout="sensitive-token")
        ), patch("urllib.request.urlopen", side_effect=failure):
            with self.assertRaises(setup.SetupError) as caught:
                setup.main()
        self.assertIn("lookup failed (HTTP 403)", str(caught.exception))
        self.assertNotIn("sensitive", str(caught.exception))

    def test_gcloud_failure_does_not_expose_captured_output(self):
        args = ["setup", "--project", "synthetic-project", "--uid", "synthetic", "--access", "publisher", "--apply"]
        failure = subprocess.CalledProcessError(1, "gcloud", output="sensitive-token", stderr="sensitive-detail")
        with patch("sys.argv", args), patch("subprocess.run", side_effect=failure):
            with self.assertRaises(setup.SetupError) as caught:
                setup.main()
        self.assertIn("credential acquisition failed", str(caught.exception))
        self.assertNotIn("sensitive", str(caught.exception))

    def test_unknown_user_never_triggers_update(self):
        args = ["setup", "--project", "synthetic-project", "--uid", "missing", "--access", "publisher", "--apply"]
        with patch("sys.argv", args), patch(
            "subprocess.run", return_value=subprocess.CompletedProcess([], 0, stdout="synthetic-token")
        ), patch("urllib.request.urlopen", return_value=io.BytesIO(b'{"users":[]}')) as request:
            with self.assertRaises(ValueError):
                setup.main()
        self.assertEqual(1, request.call_count)


if __name__ == "__main__":
    unittest.main()

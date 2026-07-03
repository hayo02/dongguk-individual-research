from __future__ import annotations

import io
import json
import shutil
import unittest
import uuid
from pathlib import Path

from dongguk_notice.api import ApiApplication
from dongguk_notice.database import prepare_database


class AuthApiTests(unittest.TestCase):
    def setUp(self):
        self.tmp_path = Path("data/test-auth-api") / uuid.uuid4().hex
        self.db_path = self.tmp_path / "app.db"
        prepare_database(self.db_path).close()
        self.app = ApiApplication(self.db_path, secret="test-secret")

    def tearDown(self):
        shutil.rmtree(self.tmp_path, ignore_errors=True)

    def request(self, method: str, path: str, payload: dict | None = None):
        raw = json.dumps(payload or {}).encode("utf-8")
        environ = {
            "REQUEST_METHOD": method,
            "PATH_INFO": path,
            "CONTENT_LENGTH": str(len(raw)),
            "wsgi.input": io.BytesIO(raw),
        }
        captured = {}

        def start_response(status, headers):
            captured["status"] = status
            captured["headers"] = headers

        body = b"".join(self.app(environ, start_response)).decode("utf-8")
        return captured["status"], json.loads(body)

    def test_login_student_account(self):
        status, body = self.request(
            "POST",
            "/api/auth/login",
            {"loginId": "2026123456", "password": "1234"},
        )

        self.assertEqual(status, "200 OK")
        self.assertTrue(body["success"])
        self.assertEqual(body["data"]["user"]["role"], "STUDENT")
        self.assertEqual(body["data"]["user"]["name"], "테스트 학생")
        self.assertIn("accessToken", body["data"])
        self.assertNotIn("password", json.dumps(body, ensure_ascii=False))

    def test_login_staff_account(self):
        status, body = self.request(
            "POST",
            "/api/auth/login",
            {"loginId": "2025123456", "password": "5678"},
        )

        self.assertEqual(status, "200 OK")
        self.assertEqual(body["data"]["user"]["role"], "STAFF")

    def test_login_rejects_wrong_password(self):
        status, body = self.request(
            "POST",
            "/api/auth/login",
            {"loginId": "2026123456", "password": "wrong"},
        )

        self.assertEqual(status, "401 Unauthorized")
        self.assertFalse(body["success"])

    def test_login_requires_credentials(self):
        status, body = self.request("POST", "/api/auth/login", {"loginId": ""})

        self.assertEqual(status, "400 Bad Request")
        self.assertFalse(body["success"])

    def test_unknown_api_returns_404(self):
        status, body = self.request("GET", "/api/missing")

        self.assertEqual(status, "404 Not Found")
        self.assertFalse(body["success"])

    def test_options_request_allows_frontend_preflight(self):
        status, body = self.request("OPTIONS", "/api/auth/login")

        self.assertEqual(status, "204 No Content")
        self.assertEqual(body, {})


if __name__ == "__main__":
    unittest.main()

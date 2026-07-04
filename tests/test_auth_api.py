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
        return self.authorized_request(method, path, payload)

    def authorized_request(
        self,
        method: str,
        path: str,
        payload: dict | None = None,
        token: str | None = None,
    ):
        raw = json.dumps(payload or {}).encode("utf-8")
        headers = []
        if token:
            headers.append(("HTTP_AUTHORIZATION", f"Bearer {token}"))
        environ = {
            "REQUEST_METHOD": method,
            "PATH_INFO": path,
            "CONTENT_LENGTH": str(len(raw)),
            "wsgi.input": io.BytesIO(raw),
            **dict(headers),
        }
        captured = {}

        def start_response(status, headers):
            captured["status"] = status
            captured["headers"] = headers

        body = b"".join(self.app(environ, start_response)).decode("utf-8")
        return captured["status"], json.loads(body)

    def student_token(self):
        _, body = self.request(
            "POST",
            "/api/auth/login",
            {"loginId": "2026123456", "password": "1234"},
        )
        return body["data"]["accessToken"]

    def staff_token(self):
        _, body = self.request(
            "POST",
            "/api/auth/login",
            {"loginId": "2025123456", "password": "5678"},
        )
        return body["data"]["accessToken"]

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

    def test_me_returns_current_user(self):
        status, body = self.authorized_request(
            "GET", "/api/auth/me", token=self.student_token()
        )

        self.assertEqual(status, "200 OK")
        self.assertTrue(body["success"])
        self.assertEqual(body["data"]["user"]["loginId"], "2026123456")
        self.assertEqual(body["data"]["user"]["role"], "STUDENT")

    def test_me_requires_valid_token(self):
        status, body = self.request("GET", "/api/auth/me")

        self.assertEqual(status, "401 Unauthorized")
        self.assertFalse(body["success"])

    def test_logout_revokes_access_token(self):
        token = self.student_token()

        logout_status, logout_body = self.authorized_request(
            "POST", "/api/auth/logout", token=token
        )
        self.assertEqual(logout_status, "200 OK")
        self.assertTrue(logout_body["success"])

        me_status, me_body = self.authorized_request("GET", "/api/auth/me", token=token)
        self.assertEqual(me_status, "401 Unauthorized")
        self.assertFalse(me_body["success"])

        dashboard_status, dashboard_body = self.authorized_request(
            "GET", "/api/student/dashboard", token=token
        )
        self.assertEqual(dashboard_status, "401 Unauthorized")
        self.assertFalse(dashboard_body["success"])

    def test_unknown_api_returns_404(self):
        status, body = self.request("GET", "/api/missing")

        self.assertEqual(status, "404 Not Found")
        self.assertFalse(body["success"])

    def test_options_request_allows_frontend_preflight(self):
        status, body = self.request("OPTIONS", "/api/auth/login")

        self.assertEqual(status, "204 No Content")
        self.assertEqual(body, {})

    def test_student_dashboard_uses_seed_notice_and_student_profile(self):
        status, body = self.authorized_request(
            "GET", "/api/student/dashboard", token=self.student_token()
        )

        self.assertEqual(status, "200 OK")
        self.assertTrue(body["success"])
        self.assertEqual(body["data"]["student"]["role"], "STUDENT")
        self.assertEqual(body["data"]["currentNotice"]["semester"], "2026 여름학기")
        self.assertEqual(body["data"]["applicationStatus"], "NO_APPLICATION")
        self.assertEqual(body["data"]["primaryAction"], "개별연구 신청하기")

    def test_student_dashboard_requires_student_role(self):
        status, body = self.authorized_request(
            "GET", "/api/student/dashboard", token=self.staff_token()
        )

        self.assertEqual(status, "401 Unauthorized")
        self.assertFalse(body["success"])

    def test_current_notice_requires_login_and_returns_notice(self):
        missing_status, missing_body = self.request("GET", "/api/notices/current")
        self.assertEqual(missing_status, "401 Unauthorized")
        self.assertFalse(missing_body["success"])

        status, body = self.authorized_request(
            "GET", "/api/notices/current", token=self.student_token()
        )

        self.assertEqual(status, "200 OK")
        self.assertEqual(body["data"]["title"], "2026학년도 여름학기 개별연구 신청 안내")
        self.assertEqual(body["data"]["requiredDocuments"], ["교수 서명 신청서", "증빙자료"])


if __name__ == "__main__":
    unittest.main()

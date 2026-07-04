from __future__ import annotations

import json
from pathlib import Path
from wsgiref.simple_server import make_server

from .auth import authenticate_user, public_user, token_hash, verify_access_token
from .database import prepare_database


DEFAULT_SECRET = "dongguk-individual-research-dev-secret"


class ApiApplication:
    def __init__(self, db_path: str | Path = "data/app.db", secret: str = DEFAULT_SECRET):
        self.db_path = Path(db_path)
        self.secret = secret

    def __call__(self, environ, start_response):
        method = environ.get("REQUEST_METHOD", "")
        path = environ.get("PATH_INFO", "")
        if method == "OPTIONS":
            return self._json(start_response, 204, {})
        if method == "POST" and path == "/api/auth/login":
            return self._login(environ, start_response)
        if method == "GET" and path == "/api/auth/me":
            return self._me(environ, start_response)
        if method == "POST" and path == "/api/auth/logout":
            return self._logout(environ, start_response)
        if method == "GET" and path == "/api/student/dashboard":
            return self._student_dashboard(environ, start_response)
        if method == "GET" and path == "/api/notices/current":
            return self._current_notice(environ, start_response)
        if path == "/api/auth/login":
            return self._json(start_response, 405, {"success": False, "message": "허용되지 않는 메서드입니다."})
        if path in ("/api/auth/me", "/api/auth/logout"):
            return self._json(start_response, 405, {"success": False, "message": "허용되지 않는 메서드입니다."})
        return self._json(start_response, 404, {"success": False, "message": "API 경로를 찾을 수 없습니다."})

    def _login(self, environ, start_response):
        try:
            body = _read_json(environ)
        except ValueError as exc:
            return self._json(start_response, 400, {"success": False, "message": str(exc)})

        login_id = str(body.get("loginId", "")).strip()
        password = str(body.get("password", ""))
        if not login_id or not password:
            return self._json(
                start_response,
                400,
                {"success": False, "message": "아이디와 비밀번호를 입력해 주세요."},
            )

        with prepare_database(self.db_path) as connection:
            try:
                result = authenticate_user(connection, login_id, password, self.secret)
            except PermissionError as exc:
                return self._json(start_response, 401, {"success": False, "message": str(exc)})

        return self._json(
            start_response,
            200,
            {
                "success": True,
                "data": {
                    "user": result.user,
                    "accessToken": result.access_token,
                },
            },
        )

    def _me(self, environ, start_response):
        with prepare_database(self.db_path) as connection:
            try:
                user = self._authorized_user(environ, connection)
            except PermissionError as exc:
                return self._json(start_response, 401, {"success": False, "message": str(exc)})

        return self._json(start_response, 200, {"success": True, "data": {"user": user}})

    def _logout(self, environ, start_response):
        with prepare_database(self.db_path) as connection:
            try:
                token, payload = self._authorized_payload(environ, connection)
            except PermissionError as exc:
                return self._json(start_response, 401, {"success": False, "message": str(exc)})

            connection.execute(
                "DELETE FROM revoked_tokens WHERE expires_at < strftime('%s', 'now')"
            )
            connection.execute(
                """
                INSERT OR IGNORE INTO revoked_tokens (token_hash, user_id, expires_at)
                VALUES (?, ?, ?)
                """,
                (token_hash(token), int(payload["sub"]), int(payload["exp"])),
            )
            connection.commit()

        return self._json(start_response, 200, {"success": True, "message": "로그아웃되었습니다."})

    def _student_dashboard(self, environ, start_response):
        with prepare_database(self.db_path) as connection:
            try:
                user = self._authorized_user(environ, connection, required_role="STUDENT")
            except PermissionError as exc:
                return self._json(start_response, 401, {"success": False, "message": str(exc)})

            notice = _current_notice(connection)
            application = connection.execute(
                """
                SELECT id, status, submitted_at, updated_at
                FROM applications
                WHERE student_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """,
                (user["id"],),
            ).fetchone()

        application_status = application["status"] if application else "NO_APPLICATION"
        primary_action = {
            "NO_APPLICATION": "개별연구 신청하기",
            "DRAFT": "작성 중인 신청서 계속하기",
            "SUBMITTED": "제출 내역 확인",
            "REVISION_REQUESTED": "보완 내용 확인 및 수정",
            "APPROVED": "승인 결과 확인",
            "REJECTED": "반려 결과 확인",
        }[application_status]

        return self._json(
            start_response,
            200,
            {
                "success": True,
                "data": {
                    "student": user,
                    "currentNotice": _notice_response(notice),
                    "applicationStatus": application_status,
                    "primaryAction": primary_action,
                    "recentNotices": [_notice_summary(notice)],
                    "processSummary": [
                        "신청 안내 확인",
                        "개설 과목 선택",
                        "신청서 작성",
                        "신청서 다운로드 및 교수 서명",
                        "서명본·증빙자료 업로드",
                        "최종 제출",
                    ],
                },
            },
        )

    def _current_notice(self, environ, start_response):
        with prepare_database(self.db_path) as connection:
            try:
                self._authorized_payload(environ, connection)
            except PermissionError as exc:
                return self._json(start_response, 401, {"success": False, "message": str(exc)})
            notice = _current_notice(connection)
        if notice is None:
            return self._json(start_response, 404, {"success": False, "message": "신청 안내 공지가 없습니다."})
        return self._json(start_response, 200, {"success": True, "data": _notice_response(notice)})

    def _authorized_payload(self, environ, connection) -> tuple[str, dict]:
        header = environ.get("HTTP_AUTHORIZATION", "")
        if not header.startswith("Bearer "):
            raise PermissionError("로그인이 필요합니다.")
        token = header.removeprefix("Bearer ").strip()
        payload = verify_access_token(token, self.secret)
        revoked = connection.execute(
            "SELECT id FROM revoked_tokens WHERE token_hash = ?",
            (token_hash(token),),
        ).fetchone()
        if revoked is not None:
            raise PermissionError("로그인이 필요합니다.")
        return token, payload

    def _authorized_user(self, environ, connection, required_role: str | None = None) -> dict:
        _, payload = self._authorized_payload(environ, connection)
        row = connection.execute(
            """
            SELECT id, login_id, name, role, department, email, phone
            FROM users
            WHERE id = ?
            """,
            (payload["sub"],),
        ).fetchone()
        if row is None:
            raise PermissionError("사용자 정보를 찾을 수 없습니다.")
        if required_role and row["role"] != required_role:
            raise PermissionError("접근 권한이 없습니다.")
        return public_user(row)

    @staticmethod
    def _json(start_response, status_code: int, payload: dict):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        status_text = {
            204: "204 No Content",
            200: "200 OK",
            400: "400 Bad Request",
            401: "401 Unauthorized",
            404: "404 Not Found",
            405: "405 Method Not Allowed",
        }.get(status_code, f"{status_code} Error")
        start_response(
            status_text,
            [
                ("Content-Type", "application/json; charset=utf-8"),
                ("Content-Length", str(len(body))),
                ("Access-Control-Allow-Origin", "http://127.0.0.1:5173"),
                ("Access-Control-Allow-Methods", "GET, POST, PATCH, PUT, DELETE, OPTIONS"),
                ("Access-Control-Allow-Headers", "Content-Type, Authorization"),
            ],
        )
        return [body]


def _read_json(environ) -> dict:
    try:
        length = int(environ.get("CONTENT_LENGTH") or "0")
    except ValueError:
        length = 0
    raw = environ["wsgi.input"].read(length) if length else b""
    if not raw:
        return {}
    try:
        value = json.loads(raw.decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError("JSON 요청 본문을 확인해 주세요.") from exc
    if not isinstance(value, dict):
        raise ValueError("JSON 객체 형식의 요청 본문이 필요합니다.")
    return value


def _current_notice(connection):
    return connection.execute(
        """
        SELECT *
        FROM notices
        ORDER BY start_date DESC, id DESC
        LIMIT 1
        """
    ).fetchone()


def _notice_response(row) -> dict:
    if row is None:
        return {}
    return {
        "id": row["id"],
        "title": row["title"],
        "semester": row["semester"],
        "startDate": row["start_date"],
        "endDate": row["end_date"],
        "originalUrl": row["original_url"],
        "needsReview": bool(row["needs_review"]),
        "requiredDocuments": _json_value(row["required_documents"], []),
        "scheduleInfo": _json_value(row["schedule_info"], {}),
        "submissionInfo": _json_value(row["submission_info"], {}),
        "noticeNotes": row["notice_notes"],
        "publishedAt": row["published_at"],
    }


def _notice_summary(row) -> dict:
    return {
        "id": row["id"],
        "title": row["title"],
        "publishedAt": row["published_at"],
        "needsReview": bool(row["needs_review"]),
    }


def _json_value(value: str | None, fallback):
    if not value:
        return fallback
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return fallback


def serve(host: str = "127.0.0.1", port: int = 8000, db_path: str = "data/app.db") -> None:
    app = ApiApplication(db_path=db_path)
    with make_server(host, port, app) as server:
        print(f"Serving API on http://{host}:{port}")
        server.serve_forever()

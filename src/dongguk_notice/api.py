from __future__ import annotations

import json
from pathlib import Path
from wsgiref.simple_server import make_server

from .auth import authenticate_user
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
        if path == "/api/auth/login":
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


def serve(host: str = "127.0.0.1", port: int = 8000, db_path: str = "data/app.db") -> None:
    app = ApiApplication(db_path=db_path)
    with make_server(host, port, app) as server:
        print(f"Serving API on http://{host}:{port}")
        server.serve_forever()

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import time
from dataclasses import dataclass


PBKDF2_ITERATIONS = 120_000
TOKEN_TTL_SECONDS = 60 * 60 * 12


@dataclass(frozen=True)
class LoginResult:
    access_token: str
    user: dict


def hash_password(password: str, salt: bytes | None = None) -> str:
    salt = salt or os.urandom(16)
    digest = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS
    )
    return (
        f"pbkdf2_sha256${PBKDF2_ITERATIONS}$"
        f"{base64.urlsafe_b64encode(salt).decode('ascii')}$"
        f"{base64.urlsafe_b64encode(digest).decode('ascii')}"
    )


def verify_password(password: str, password_hash: str) -> bool:
    try:
        algorithm, iterations, salt_value, expected_value = password_hash.split("$", 3)
        if algorithm != "pbkdf2_sha256":
            return False
        salt = base64.urlsafe_b64decode(salt_value.encode("ascii"))
        expected = base64.urlsafe_b64decode(expected_value.encode("ascii"))
        digest = hashlib.pbkdf2_hmac(
            "sha256", password.encode("utf-8"), salt, int(iterations)
        )
        return hmac.compare_digest(digest, expected)
    except (ValueError, TypeError):
        return False


def issue_access_token(user: dict, secret: str, now: int | None = None) -> str:
    issued_at = now or int(time.time())
    payload = {
        "sub": str(user["id"]),
        "role": user["role"],
        "iat": issued_at,
        "exp": issued_at + TOKEN_TTL_SECONDS,
    }
    payload_bytes = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode(
        "utf-8"
    )
    signature = hmac.new(secret.encode("utf-8"), payload_bytes, hashlib.sha256).digest()
    return ".".join(
        [
            base64.urlsafe_b64encode(payload_bytes).decode("ascii").rstrip("="),
            base64.urlsafe_b64encode(signature).decode("ascii").rstrip("="),
        ]
    )


def public_user(row) -> dict:
    return {
        "id": row["id"],
        "loginId": row["login_id"],
        "name": row["name"],
        "role": row["role"],
        "department": row["department"],
        "email": row["email"],
        "phone": row["phone"],
    }


def authenticate_user(connection, login_id: str, password: str, secret: str) -> LoginResult:
    row = connection.execute(
        """
        SELECT id, login_id, password_hash, name, role, department, email, phone
        FROM users
        WHERE login_id = ?
        """,
        (login_id,),
    ).fetchone()
    if row is None or not verify_password(password, row["password_hash"]):
        raise PermissionError("아이디 또는 비밀번호가 올바르지 않습니다.")

    user = public_user(row)
    return LoginResult(access_token=issue_access_token(user, secret), user=user)

from __future__ import annotations

import sqlite3
from pathlib import Path

from .auth import hash_password


DEFAULT_STUDENT = {
    "login_id": "2026123456",
    "password": "1234",
    "name": "테스트 학생",
    "role": "STUDENT",
    "department": "컴퓨터·AI학부",
    "email": "student@dongguk.edu",
    "phone": "010-1234-5678",
}

DEFAULT_STAFF = {
    "login_id": "2025123456",
    "password": "5678",
    "name": "테스트 교직원",
    "role": "STAFF",
    "department": "컴퓨터·AI학부 행정실",
    "email": "staff@dongguk.edu",
    "phone": "02-0000-0000",
}


def connect_database(path: str | Path) -> sqlite3.Connection:
    db_path = Path(path)
    db_path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(db_path)
    connection.row_factory = sqlite3.Row
    return connection


def initialize_database(connection: sqlite3.Connection) -> None:
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            login_id TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            name TEXT NOT NULL,
            role TEXT NOT NULL CHECK (role IN ('STUDENT', 'STAFF')),
            department TEXT,
            email TEXT,
            phone TEXT,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """
    )
    connection.commit()


def seed_initial_users(connection: sqlite3.Connection) -> None:
    for user in (DEFAULT_STUDENT, DEFAULT_STAFF):
        existing = connection.execute(
            "SELECT id FROM users WHERE login_id = ?", (user["login_id"],)
        ).fetchone()
        if existing:
            continue
        connection.execute(
            """
            INSERT INTO users (
                login_id, password_hash, name, role, department, email, phone
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                user["login_id"],
                hash_password(user["password"]),
                user["name"],
                user["role"],
                user["department"],
                user["email"],
                user["phone"],
            ),
        )
    connection.commit()


def prepare_database(path: str | Path) -> sqlite3.Connection:
    connection = connect_database(path)
    initialize_database(connection)
    seed_initial_users(connection)
    return connection

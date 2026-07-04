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

DEFAULT_NOTICE = {
    "title": "2026학년도 여름학기 개별연구 신청 안내",
    "semester": "2026 여름학기",
    "start_date": "2026-07-20",
    "end_date": "2026-07-30",
    "original_url": "https://cs.dongguk.edu/",
    "needs_review": 0,
    "required_documents": '["교수 서명 신청서", "증빙자료"]',
    "schedule_info": '{"신청기간":"2026-07-20 ~ 2026-07-30"}',
    "submission_info": '{"제출방식":"온라인 신청 후 서명본 업로드"}',
    "notice_notes": "담당 교수 면담 후 서명을 받아 업로드해 주세요.",
    "attachment_info": "[]",
    "excel_data": "{}",
    "analysis_result": "{}",
    "published_at": "2026-07-03",
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
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS notices (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            semester TEXT NOT NULL,
            start_date TEXT NOT NULL,
            end_date TEXT NOT NULL,
            original_url TEXT,
            needs_review INTEGER NOT NULL DEFAULT 0,
            body_text TEXT,
            published_at TEXT,
            required_documents TEXT,
            schedule_info TEXT,
            submission_info TEXT,
            notice_notes TEXT,
            attachment_info TEXT,
            excel_data TEXT,
            analysis_result TEXT,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """
    )
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS applications (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            student_id INTEGER NOT NULL,
            course_id INTEGER,
            status TEXT NOT NULL CHECK (
                status IN (
                    'DRAFT',
                    'SUBMITTED',
                    'REVISION_REQUESTED',
                    'APPROVED',
                    'REJECTED'
                )
            ),
            application_reason TEXT,
            research_purpose TEXT,
            submitted_at TEXT,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY(student_id) REFERENCES users(id)
        )
        """
    )
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS revoked_tokens (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            token_hash TEXT NOT NULL UNIQUE,
            user_id INTEGER NOT NULL,
            expires_at INTEGER NOT NULL,
            revoked_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY(user_id) REFERENCES users(id)
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


def seed_initial_notice(connection: sqlite3.Connection) -> None:
    existing = connection.execute(
        "SELECT id FROM notices WHERE semester = ?", (DEFAULT_NOTICE["semester"],)
    ).fetchone()
    if existing:
        return
    connection.execute(
        """
        INSERT INTO notices (
            title,
            semester,
            start_date,
            end_date,
            original_url,
            needs_review,
            required_documents,
            schedule_info,
            submission_info,
            notice_notes,
            attachment_info,
            excel_data,
            analysis_result,
            published_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            DEFAULT_NOTICE["title"],
            DEFAULT_NOTICE["semester"],
            DEFAULT_NOTICE["start_date"],
            DEFAULT_NOTICE["end_date"],
            DEFAULT_NOTICE["original_url"],
            DEFAULT_NOTICE["needs_review"],
            DEFAULT_NOTICE["required_documents"],
            DEFAULT_NOTICE["schedule_info"],
            DEFAULT_NOTICE["submission_info"],
            DEFAULT_NOTICE["notice_notes"],
            DEFAULT_NOTICE["attachment_info"],
            DEFAULT_NOTICE["excel_data"],
            DEFAULT_NOTICE["analysis_result"],
            DEFAULT_NOTICE["published_at"],
        ),
    )
    connection.commit()


def prepare_database(path: str | Path) -> sqlite3.Connection:
    connection = connect_database(path)
    initialize_database(connection)
    seed_initial_users(connection)
    seed_initial_notice(connection)
    return connection

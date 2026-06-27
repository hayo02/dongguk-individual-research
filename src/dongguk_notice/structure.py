from __future__ import annotations

import re


def evidence(value=None, source_text: str = "", source_type: str = "NOTICE_BODY") -> dict:
    return {
        "value": value,
        "source_text": source_text if value not in (None, "", []) else "",
        "source_type": source_type,
        "status": "CONFIRMED" if value not in (None, "", []) else "NOT_FOUND",
    }


def structure_notice(text: str, attachments: list[dict]) -> dict:
    normalized = text.replace("\xa0", " ")
    deadline = _deadline(normalized)
    facts = _submission_facts(normalized)
    schedule = {
        "application_start": evidence(None),
        "application_deadline": deadline,
        "interview_period": _schedule_row(normalized, ["교수 인터뷰", "인터뷰"]),
        "document_submission_period": _schedule_row(normalized, ["수강신청서", "제출"]),
        "course_registration_period": _schedule_row(normalized, ["수강신청"], ["수강신청서", "수강신청원"]),
        "research_period": _schedule_row(normalized, ["개별연구참여", "과제수행"]),
    }
    submission = {
        "location": _first_group_evidence(
            normalized,
            r"(원흥관\s*4?층?\s*423호)",
            r"(원흥관\s*423호)",
        ),
        "method": _method(normalized),
        "required_documents": _required_documents(normalized, attachments),
        **facts,
        "contact": _first_group_evidence(normalized, r"(0\d{1,2}-\d{3,4}-\d{4})"),
        "cautions": _sentences(normalized, ["반드시", "유의", "이메일 제출", "별도"]),
    }
    return {
        "schedule": schedule,
        "submission": submission,
        "process_steps": process_steps(submission),
        "checklist_template": checklist_template(submission),
    }


def process_steps(submission: dict) -> list[dict]:
    return [
        {"id": "notice_checked", "label": "최신 개별연구 공지 확인", "required": True},
        {"id": "research_list_checked", "label": "교수 및 연구 주제 확인", "required": True},
        {"id": "research_selected", "label": "연구 주제 선택", "required": True},
        {
            "id": "professor_contact",
            "label": "담당교수 연락",
            "required": bool(submission["professor_contact_required"]["value"]),
        },
        {
            "id": "professor_approval_requested",
            "label": "담당교수 면담 또는 승인 요청",
            "required": bool(submission["professor_approval_required"]["value"]),
        },
        {
            "id": "professor_approval_evidence_ready",
            "label": "담당교수 승인 증빙 준비",
            "required": bool(submission["professor_approval_required"]["value"]),
            "completion_rule": {
                "type": "ONE_OF",
                "accepted_evidence": [
                    "SIGNED_APPLICATION_FORM",
                    "EMAIL_PERMISSION_EVIDENCE",
                ],
            },
        },
        {"id": "application_form_written", "label": "개별연구 수강신청원 작성", "required": True},
        {"id": "documents_checked", "label": "필요 서류 확인", "required": True},
        {"id": "missing_fields_checked", "label": "제출 전 누락 항목 확인", "required": True},
        {"id": "visit_submission", "label": "방문 제출", "required": True},
        {
            "id": "course_registration",
            "label": "별도 수강신청",
            "required": bool(submission["separate_course_registration_required"]["value"]),
        },
        {"id": "completion_checked", "label": "신청 완료 확인", "required": True},
    ]


def checklist_template(submission: dict) -> list[dict]:
    checklist = []
    for step in process_steps(submission):
        source_type = "SYSTEM_WORKFLOW"
        source_text = ""
        if step["id"] == "research_selected":
            source_type = "EXCEL"
            source_text = "개별연구 개설 신청 시트에서 연구 주제를 선택해야 함"
        elif step["id"] == "research_list_checked":
            source_type = "EXCEL"
            source_text = "개별연구 개설 신청 시트의 교원명·과목명·연구내용 목록"
        elif step["id"] == "application_form_written":
            source_type = "NOTICE_BODY"
            source_text = _first_document_evidence(submission, "개별연구 수강신청원")
        elif step["id"] == "professor_contact":
            source_text = submission["professor_contact_required"]["source_text"]
            source_type = "NOTICE_BODY" if source_text else "SYSTEM_WORKFLOW"
        elif step["id"].startswith("professor_"):
            source_type = "NOTICE_BODY"
            source_text = submission["professor_approval_required"]["source_text"]
        elif step["id"] == "visit_submission":
            source_text = submission["method"]["source_text"]
            source_type = "NOTICE_BODY" if source_text else "SYSTEM_WORKFLOW"
        elif step["id"] == "course_registration":
            source_text = submission["separate_course_registration_required"]["source_text"]
            source_type = "NOTICE_BODY" if source_text else "SYSTEM_WORKFLOW"
        checklist.append({
            "id": step["id"],
            "label": step["label"],
            "required": step["required"],
            "source_type": source_type,
            "source_text": source_text,
            **({"completion_rule": step["completion_rule"]} if "completion_rule" in step else {}),
        })
    return checklist


def _deadline(text: str) -> dict:
    match = re.search(
        r"(신청(?:기간|기한)[^.\n]*?"
        r"(?P<year>20\d{2})[.\-/년]\s*(?P<month>\d{1,2})[.\-/월]\s*"
        r"(?P<day>\d{1,2}).{0,30}?"
        r"(?P<hour>\d{1,2})(?::|시\s*)?(?P<minute>\d{2})?\s*(?:까지)?)",
        text,
    )
    if not match:
        return evidence(None)
    minute = match.group("minute") or "00"
    return evidence(
        (
            f"{int(match.group('year')):04d}-{int(match.group('month')):02d}-"
            f"{int(match.group('day')):02d} {int(match.group('hour')):02d}:{minute}"
        ),
        match.group(1),
    )


def _line_evidence(text: str, keywords: list[str], exclude: list[str] | None = None) -> dict:
    for line in [line.strip() for line in text.splitlines() if line.strip()]:
        if all(keyword in line for keyword in keywords) and not (
            exclude and any(keyword in line for keyword in exclude)
        ):
            return evidence(line, line)
    return evidence(None)


def _schedule_row(text: str, keywords: list[str], exclude: list[str] | None = None) -> dict:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    for index, line in enumerate(lines):
        if not all(keyword in line for keyword in keywords):
            continue
        if exclude and any(keyword in line for keyword in exclude):
            continue
        start = max(0, index - 1)
        end = min(len(lines), index + 2)
        source_lines = []
        for candidate in lines[start:end]:
            if candidate != line and _is_section_heading(candidate):
                continue
            source_lines.append(candidate)
        source = "\n".join(source_lines)
        value = _nearby_date(lines, index) or line
        return evidence(value, source)
    return evidence(None)


def _nearby_date(lines: list[str], index: int) -> str | None:
    date_pattern = re.compile(
        r"\d{1,2}[./]\d{1,2}\.\([^)]\)\s*[~～-]\s*\d{1,2}[./]\d{1,2}\.\([^)]\)|"
        r"(?:~\s*)?\d{1,2}[./]\d{1,2}\.\([^)]\)"
    )
    for offset in (-1, 0, 1):
        candidate_index = index + offset
        if 0 <= candidate_index < len(lines):
            match = date_pattern.search(lines[candidate_index])
            if match:
                return match.group(0)
    return None


def _first_group_evidence(text: str, *patterns: str) -> dict:
    for pattern in patterns:
        match = re.search(pattern, text, re.I)
        if match:
            return evidence(match.group(1).strip(), match.group(0))
    return evidence(None)


def _method(text: str) -> dict:
    for pattern, value in [
        (r"(방문\s*제출|방문제출)", "방문 제출"),
        (r"(이메일\s*제출|메일\s*제출)", "이메일 제출"),
        (r"(학과사무실[^.\n]*제출)", "학과사무실 제출"),
    ]:
        match = re.search(pattern, text)
        if match:
            return evidence(value, match.group(0))
    return evidence(None)


def _submission_facts(text: str) -> dict:
    contact = _line_match(text, [r"담당교수.{0,20}연락"])
    signature = _line_match(text, [r"담당교수.{0,20}서명"])
    approval = _line_match(text, [
        r"담당교수.{0,20}승인",
        r"수강\s*(?:허가|수락|승인).{0,30}(?:증빙|확인)",
        r"담당교수.{0,20}서명",
        r"승인\s*증빙",
    ])
    evidence_line = _line_match(text, [r"서명.{0,20}어려운.{0,50}(?:허가|증빙|이메일)"])
    course_registration = _line_match(text, [
        r"반드시.{0,20}수강신청기간.{0,40}수강신청사이트.{0,20}수강신청",
        r"반드시.{0,40}수강신청사이트.{0,20}수강신청",
    ])
    email_forbidden = _line_match(text, [
        r"이메일\s*제출은\s*접수하지\s*않음",
        r"이메일\s*제출\s*건은\s*접수하지\s*않",
    ])
    proxy = _line_match(text, [r"대리\s*제출도?\s*가능"])
    return {
        "professor_contact_required": evidence(True, contact) if contact else evidence(None),
        "professor_approval_required": evidence(True, evidence_line or approval or signature) if (evidence_line or approval or signature) else evidence(None),
        "professor_signature_required": _conditional_signature_evidence(signature, evidence_line),
        "approval_evidence_allowed": evidence(True, evidence_line) if evidence_line else evidence(None),
        "separate_course_registration_required": evidence(True, course_registration) if course_registration else evidence(None),
        "email_submission_allowed": evidence(False, email_forbidden) if email_forbidden else evidence(None),
        "proxy_submission_allowed": evidence(True, proxy) if proxy else evidence(None),
    }


def _conditional_signature_evidence(signature: str, email_evidence: str) -> dict:
    if not signature and not email_evidence:
        return evidence(None)
    source = ". ".join(line for line in [signature, email_evidence] if line)
    return {
        "value": False,
        "conditional": True,
        "required_unless": "EMAIL_PERMISSION_EVIDENCE",
        "source_text": source,
        "source_type": "NOTICE_BODY",
        "status": "CONFIRMED",
    }


def _required_documents(text: str, attachments: list[dict]) -> list[dict]:
    form_line = _line_match(text, [r"수강신청원\s*양식\s*작성", r"개별연구\s*수강신청원"])
    signature_line = _line_match(text, [r"담당교수\s*서명"])
    email_evidence_line = _line_match(text, [r"서명.{0,20}어려운.{0,60}(?:허가|이메일|증빙)"])
    attachment_names = "\n".join(item.get("name", "") for item in attachments)
    result = []
    if form_line or "수강신청원" in attachment_names:
        result.append({
            "name": "개별연구 수강신청원",
            "required": True,
            "requirement_type": "REQUIRED",
            "evidence": evidence("개별연구 수강신청원", form_line or "첨부파일: 개별연구 수강신청원"),
        })
    if signature_line or email_evidence_line:
        result.append({
            "name": "담당교수 승인 증빙",
            "required": True,
            "requirement_type": "ONE_OF",
            "evidence": evidence("담당교수 승인 증빙", "\n".join(line for line in [signature_line, email_evidence_line] if line)),
            "alternatives": [
                {
                    "type": "SIGNED_APPLICATION_FORM",
                    "name": "담당교수 서명이 포함된 수강신청원",
                    "required": False,
                    "source_text": signature_line,
                },
                {
                    "type": "EMAIL_PERMISSION_EVIDENCE",
                    "name": "수강 허가 이메일 등 증빙자료",
                    "required": False,
                    "condition": "담당교수 서명이 어려운 경우",
                    "source_text": email_evidence_line,
                },
            ],
        })
    return result


def _sentences(text: str, keywords: list[str]) -> list[dict]:
    results = []
    for line in [line.strip() for line in text.splitlines() if line.strip()]:
        if _is_section_heading(line):
            continue
        if any(keyword in line for keyword in keywords):
            results.append(evidence(line, line))
    return results[:30]


def _is_section_heading(line: str) -> bool:
    if re.fullmatch(r"\s*[가-힣]\)\s*유의사항\s*", line):
        return True
    if re.fullmatch(r"\s*유의사항\s*", line):
        return True
    if re.fullmatch(r"\s*\d+\.\s*[가-힣A-Za-z].*", line):
        return True
    if re.fullmatch(r"\s*[가-힣]\)\s*.+", line):
        return True
    return False


def _line_match(text: str, patterns: list[str]) -> str:
    for line in [line.strip() for line in text.splitlines() if line.strip()]:
        for pattern in patterns:
            if re.search(pattern, line):
                return line
    return ""


def _first_document_evidence(submission: dict, name: str) -> str:
    for item in submission["required_documents"]:
        if item["name"] == name:
            return item["evidence"]["source_text"]
    return ""

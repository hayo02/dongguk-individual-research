from __future__ import annotations

import json
import re
import hashlib
from datetime import datetime, timezone
from pathlib import Path

from .config import INDIVIDUAL_RESEARCH_KEYWORDS, PARSER_VERSION, SCHEMA_VERSION
from .downloads import download_attachment
from .errors import StructureError
from .snapshot import compare_snapshots, previous_snapshot
from .structure import structure_notice
from .website import NoticeClient


def crawl(data_dir: str | Path = "data", client=None) -> dict:
    data_dir = Path(data_dir)
    client = client or NoticeClient()
    started = datetime.now(timezone.utc)
    selected, discovery = discover_latest_notice(client)
    result = crawl_notice(client, selected, data_dir, discovery, started)

    snapshot_dir = data_dir / "snapshots" / "individual-research"
    snapshot_dir.mkdir(parents=True, exist_ok=True)
    _, previous = previous_snapshot(snapshot_dir)
    comparison = compare_snapshots(previous, result)
    result["comparison_status"] = comparison["status"]
    result["changes"] = comparison["changes"]

    timestamp = started.strftime("%Y%m%dT%H%M%S.%fZ")
    snapshot_path = snapshot_dir / f"{timestamp}.json"
    latest_path = snapshot_dir / "latest.json"
    result["snapshot_path"] = str(snapshot_path.resolve())
    snapshot_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    latest_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return result


def discover_latest_notice(
    client,
    first_page: list[dict] | None = None,
    max_pages: int = 10,
) -> tuple[dict, dict]:
    list_notices, list_pages = _collect_notice_pages(
        client, keyword=None, first_page=first_page, max_pages=max_pages
    )
    local_matches = [
        notice for notice in list_notices if _matches(notice["title"], INDIVIDUAL_RESEARCH_KEYWORDS)
    ]
    searched = []
    candidates = {notice["id"]: notice for notice in local_matches}
    if not candidates:
        for keyword in INDIVIDUAL_RESEARCH_KEYWORDS:
            notices, pages = _collect_notice_pages(client, keyword=keyword, max_pages=max_pages)
            searched.append({"keyword": keyword, "count": len(notices), "pages": pages})
            for notice in notices:
                if _matches(notice["title"], INDIVIDUAL_RESEARCH_KEYWORDS):
                    candidates[notice["id"]] = notice
    if not candidates:
        raise LookupError("첫 페이지 및 제목 검색 첫 페이지에서 개별연구 공지를 찾지 못했습니다.")
    selected = max(candidates.values(), key=lambda item: (item.get("date") or "", int(item["id"])))
    return selected, {
        "strategy": "paginated-list-filter" if local_matches else "paginated-title-search",
        "keywords": INDIVIDUAL_RESEARCH_KEYWORDS,
        "list_pages": list_pages,
        "searches": searched,
        "candidate_count": len(candidates),
        "selected_by": "written_at_desc_then_notice_id_desc",
    }


def crawl_notice(client, selected: dict, data_dir: Path, discovery: dict, started: datetime) -> dict:
    detail = client.get_notice(selected["id"])
    attachments = [
        download_attachment(client, detail["id"], item, data_dir)
        for item in detail["attachments"]
    ]
    structured = structure_notice(
        "\n".join(part for part in [detail["title"], detail["body_text"]] if part),
        attachments,
    )
    research_items = _research_items(attachments)
    application_form = _application_form(attachments)
    errors = [
        {
            "attachment": item["name"],
            "error": item["parse_error"],
        }
        for item in attachments
        if (not item["download_success"]) or (item["download_success"] and not item["parse_success"])
    ]
    warnings = []
    if not research_items:
        warnings.append({"code": "NO_RESEARCH_ITEMS", "message": "개별연구 Excel 연구 목록을 추출하지 못했습니다.", "fields": []})
    if not application_form["fields"]:
        warnings.append({"code": "NO_HWP_FIELDS", "message": "개별연구 HWP 신청원 필드 구조를 추출하지 못했습니다.", "fields": []})
    warnings.extend(_research_warnings_from_attachments(attachments))

    snapshot = {
        "schema_version": SCHEMA_VERSION,
        "parser_version": PARSER_VERSION,
        "category": "individual-research",
        "crawled_at": started.isoformat(),
        "notice": {
            "notice_id": str(detail["id"]),
            "title": detail["title"],
            "author": detail["author"] or selected.get("author"),
            "written_at": detail["created_at"] or selected.get("date"),
            "modified_at": detail["modified_at"],
            "views": detail["views"] or selected.get("views"),
            "url": detail["url"],
            "body_text": detail["body_text"],
            "body_html": detail["body_html"],
            "selection": discovery,
        },
        "schedule": structured["schedule"],
        "submission": structured["submission"],
        "process_steps": structured["process_steps"],
        "checklist_template": structured["checklist_template"],
        "attachments": attachments,
        "research_items": research_items,
        "application_form": application_form,
        "autofill_candidates": autofill_candidates(structured, detail, application_form),
        "changes": [],
        "errors": errors,
        "warnings": warnings,
    }
    snapshot["source_fingerprint"] = source_fingerprint(snapshot)
    return snapshot


def autofill_candidates(structured: dict, detail: dict, application_form: dict | None = None) -> dict:
    year, semester = _academic_year_semester(detail, application_form or {})
    deadline = structured["schedule"]["application_deadline"]["value"] or ""
    year = year or (deadline[:4] if deadline else None)
    return {
        "academic_year": {"source": "NOTICE_BODY", "value": year},
        "semester": {"source": "NOTICE_BODY", "value": semester},
        "student_name": {"source": "USER_PROFILE", "required": True},
        "student_id": {"source": "USER_PROFILE", "required": True},
        "affiliation": {"source": "USER_PROFILE", "required": True},
        "contact": {"source": "USER_PROFILE", "required": True},
        "email": {"source": "USER_PROFILE", "required": False},
        "professor": {"source": "SELECTED_RESEARCH_ITEM", "value": None},
        "course_name": {"source": "SELECTED_RESEARCH_ITEM", "value": None},
        "course_code": {"source": "SELECTED_RESEARCH_ITEM", "value": None},
        "research_topic": {"source": "SELECTED_RESEARCH_ITEM", "value": None},
    }


def _research_items(attachments: list[dict]) -> list[dict]:
    for attachment in attachments:
        analysis = attachment.get("analysis") or {}
        if analysis.get("type") == "individual-research-excel":
            return analysis.get("result", {}).get("items", [])
    return []


def _application_form(attachments: list[dict]) -> dict:
    for attachment in attachments:
        analysis = attachment.get("analysis") or {}
        if analysis.get("type") == "hwp-form":
            result = analysis.get("result", {})
            return {
                "document_title": result.get("document_title", ""),
                "fields": result.get("fields", []),
                "additional_autofill_fields": result.get("additional_autofill_fields", []),
                "required_signatures": result.get("signature_boxes", []),
                "tables": result.get("tables", []),
                "text": result.get("text", ""),
            }
    return {
        "document_title": "",
        "fields": [],
        "additional_autofill_fields": [],
        "required_signatures": [],
        "tables": [],
    }


def _matches(title: str, keywords: list[str]) -> bool:
    compact = title.replace(" ", "")
    return any(keyword.replace(" ", "") in compact for keyword in keywords)


def _collect_notice_pages(
    client,
    keyword: str | None,
    max_pages: int,
    first_page: list[dict] | None = None,
) -> tuple[list[dict], int]:
    notices = []
    previous_ids: set[int] | None = None
    page = 1
    while page <= max_pages:
        try:
            page_notices = first_page if page == 1 and first_page is not None else client.list_notices(page=page, keyword=keyword)
        except StructureError:
            raise
        if not page_notices:
            break
        ids = {item["id"] for item in page_notices}
        if previous_ids is not None and ids and ids <= previous_ids:
            break
        notices.extend(page_notices)
        previous_ids = ids
        page += 1
    unique = {notice["id"]: notice for notice in notices}
    return list(unique.values()), page - 1


def _research_warnings_from_attachments(attachments: list[dict]) -> list[dict]:
    for attachment in attachments:
        analysis = attachment.get("analysis") or {}
        if analysis.get("type") == "individual-research-excel":
            return analysis.get("result", {}).get("warnings", [])
    return []


def source_fingerprint(snapshot: dict) -> str:
    notice = snapshot.get("notice", {})
    body = re.sub(r"\s+", " ", notice.get("body_text") or "").strip()
    attachments = [
        {
            "role": item.get("role"),
            "name": item.get("name"),
            "sha256": item.get("sha256"),
        }
        for item in snapshot.get("attachments", [])
    ]
    payload = {
        "notice_id": notice.get("notice_id"),
        "body": body,
        "attachments": sorted(attachments, key=lambda item: (item.get("role") or "", item.get("name") or "")),
    }
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()


def _academic_year_semester(detail: dict, application_form: dict) -> tuple[str | None, str | None]:
    text = "\n".join([
        detail.get("title", ""),
        detail.get("body_text", ""),
        application_form.get("text", ""),
    ])
    match = re.search(r"(20\d{2})\s*[- ]\s*(봄학기|여름학기|가을학기|겨울학기|[12]학기)", text)
    if not match:
        match = re.search(r"(20\d{2})\s*학년도\s*(봄학기|여름학기|가을학기|겨울학기|[12]학기)", text)
    if not match:
        return None, None
    return match.group(1), match.group(2)


def print_crawl_summary(snapshot: dict) -> None:
    notice = snapshot["notice"]
    print(f"스냅샷: {snapshot['snapshot_path']}")
    print(f"공지: {notice['notice_id']} - {notice['title']}")
    print(f"선택 기준: {notice['selection']['selected_by']}")
    print(f"본문 길이: {len(notice['body_text'])}자")
    print("첨부파일:")
    for attachment in snapshot["attachments"]:
        state = "성공" if attachment["download_success"] else "실패"
        parse = "파싱 성공" if attachment["parse_success"] else "파싱 실패"
        print(f"  - {attachment['name']}: {state}, {parse}")
    print(f"Excel 연구 항목: {len(snapshot['research_items'])}개")
    print(f"HWP 입력 필드: {len(snapshot['application_form']['fields'])}개")
    print(f"HWP 서명란: {len(snapshot['application_form']['required_signatures'])}개")
    print("일정:")
    for key, item in snapshot["schedule"].items():
        print(f"  - {key}: {item.get('value')}")
    print(f"제출 장소: {snapshot['submission']['location']['value']}")
    print(f"제출 방법: {snapshot['submission']['method']['value']}")
    print("체크리스트:")
    for item in snapshot["checklist_template"]:
        required = "필수" if item["required"] else "선택"
        print(f"  - {item['label']} ({required})")
    print(f"변경사항: {len(snapshot['changes'])}건")

from __future__ import annotations

import json
from pathlib import Path


def previous_snapshot(directory: str | Path, current: Path | None = None) -> tuple[Path | None, dict | None]:
    directory = Path(directory)
    files = sorted(path for path in directory.glob("*.json") if path.name != "latest.json")
    if current is not None:
        files = [path for path in files if path.resolve() != current.resolve()]
    if not files:
        return None, None
    path = files[-1]
    return path, json.loads(path.read_text(encoding="utf-8"))


def compare_snapshots(previous: dict | None, current: dict) -> dict:
    if previous is None:
        return {"status": "NO_PREVIOUS_SNAPSHOT", "changes": []}
    if previous.get("source_fingerprint") == current.get("source_fingerprint"):
        if previous.get("parser_version") != current.get("parser_version"):
            return {"status": "REPROCESSED_BY_NEW_PARSER", "changes": []}
    elif _same_legacy_source(previous, current):
        return {"status": "REPROCESSED_BY_NEW_PARSER", "changes": []}
    changes = []
    _notice_changes(changes, previous, current)
    _attachment_changes(changes, previous.get("attachments", []), current.get("attachments", []))
    _research_changes(changes, previous.get("research_items", []), current.get("research_items", []))
    return {"status": "CHANGED" if changes else "NO_CHANGES", "changes": changes}


def _notice_changes(changes: list[dict], previous: dict, current: dict) -> None:
    if _normalize_body(previous.get("notice", {}).get("body_text")) != _normalize_body(current.get("notice", {}).get("body_text")):
        changes.append({
            "type": "공지 본문 변경",
            "previous": previous.get("notice", {}).get("body_text"),
            "current": current.get("notice", {}).get("body_text"),
        })
    pairs = [
        ("신청 시작일 변경", ["schedule", "application_start", "value"]),
        ("신청 마감 변경", ["schedule", "application_deadline", "value"]),
        ("제출 장소 변경", ["submission", "location", "value"]),
        ("제출 방법 변경", ["submission", "method", "value"]),
        ("필요 서류 변경", ["submission", "required_documents"]),
        ("교수 승인 조건 변경", ["submission", "professor_approval_required", "value"]),
        ("별도 수강신청 여부 변경", ["submission", "separate_course_registration_required", "value"]),
    ]
    for label, path in pairs:
        old = _get(previous, path)
        new = _get(current, path)
        if old != new:
            changes.append({"type": label, "previous": old, "current": new})


def _attachment_changes(changes: list[dict], old_items: list[dict], new_items: list[dict]) -> None:
    matched, removed, added = _match_attachments(old_items, new_items)
    for item in added:
        changes.append({"type": "첨부파일 추가", "previous": None, "current": _attachment_value(item)})
    for item in removed:
        changes.append({"type": "첨부파일 삭제", "previous": _attachment_value(item), "current": None})
    for old_item, new_item in matched:
        if new_item.get("sha256") is None:
            changes.append({
                "type": "첨부파일 비교 불가",
                "role": new_item.get("role") or old_item.get("role"),
                "reason": "CURRENT_DOWNLOAD_FAILED",
                "previous": _attachment_value(old_item),
                "current": _attachment_value(new_item),
            })
        elif old_item.get("sha256") != new_item.get("sha256"):
            changes.append({
                "type": "첨부파일 교체",
                "role": new_item.get("role") or old_item.get("role"),
                "previous": _attachment_value(old_item),
                "current": _attachment_value(new_item),
            })


def _research_changes(changes: list[dict], old_items: list[dict], new_items: list[dict]) -> None:
    old = _research_map(old_items)
    new = _research_map(new_items)
    for key in sorted(new.keys() - old.keys(), key=str):
        changes.append({"type": "새 연구 추가", "research_key": key, "previous": None, "current": new[key]})
    for key in sorted(old.keys() - new.keys(), key=str):
        changes.append({"type": "연구 삭제", "research_key": key, "previous": old[key], "current": None})
    fields = [
        "교원명",
        "과목명",
        "학수강좌번호",
        "연구내용",
        "수강정원",
        "인터뷰 일정",
        "주당 연구시간",
        "수강 자격사항",
    ]
    for key in sorted(old.keys() & new.keys(), key=str):
        for field in fields:
            if old[key].get(field) != new[key].get(field):
                changes.append({
                    "type": f"{field} 변경",
                    "research_key": key,
                    "previous": old[key].get(field),
                    "current": new[key].get(field),
                })


def _attachment_key(item: dict) -> tuple[str | None, str | None]:
    if item.get("role") and item.get("role") != "UNKNOWN":
        return ("role", item.get("role"))
    return ("file_seq", item.get("file_seq")) if item.get("file_seq") else ("name", _normalize_filename(item.get("name")))


def _match_attachments(old_items: list[dict], new_items: list[dict]) -> tuple[list[tuple[dict, dict]], list[dict], list[dict]]:
    unmatched_old = list(old_items)
    unmatched_new = list(new_items)
    matched: list[tuple[dict, dict]] = []
    for matcher in (_same_file_seq, _same_sha, _same_role, _same_role_and_name, _same_name_and_extension):
        for old in list(unmatched_old):
            partner = next((new for new in unmatched_new if matcher(old, new)), None)
            if partner is not None:
                matched.append((old, partner))
                unmatched_old.remove(old)
                unmatched_new.remove(partner)
    return matched, unmatched_old, unmatched_new


def _same_file_seq(old: dict, new: dict) -> bool:
    return bool(old.get("file_seq") and old.get("file_seq") == new.get("file_seq"))


def _same_sha(old: dict, new: dict) -> bool:
    return bool(old.get("sha256") and old.get("sha256") == new.get("sha256"))


def _same_role_and_name(old: dict, new: dict) -> bool:
    return bool(
        old.get("role") and new.get("role")
        and old.get("role") == new.get("role")
        and _normalize_filename(old.get("name")) == _normalize_filename(new.get("name"))
    )


def _same_role(old: dict, new: dict) -> bool:
    return bool(old.get("role") and new.get("role") and old.get("role") == new.get("role") and old.get("role") != "UNKNOWN")


def _same_name_and_extension(old: dict, new: dict) -> bool:
    return (
        _normalize_filename(old.get("name")) == _normalize_filename(new.get("name"))
        and (old.get("extension") or _extension(old.get("name"))) == (new.get("extension") or _extension(new.get("name")))
    )


def _attachment_value(item: dict) -> dict:
    return {"name": item.get("name"), "role": item.get("role"), "file_seq": item.get("file_seq"), "sha256": item.get("sha256")}


def _research_map(items: list[dict]) -> dict:
    group_counts: dict[tuple[str, str], int] = {}
    result = {}
    for item in items:
        course_code = str(item.get("학수강좌번호") or "").strip()
        professor = str(item.get("교원명") or "").strip()
        course_name = str(item.get("과목명") or "").strip()
        if course_code:
            key = ("course_code", course_code)
        else:
            base = (professor, _normalize_course(course_name))
            group_index = group_counts.get(base, 0)
            group_counts[base] = group_index + 1
            key = ("professor_course", professor, _normalize_course(course_name), str(group_index))
        result[key] = item
    return result


def _normalize_filename(value: str | None) -> str:
    return "".join((value or "").lower().split())


def _extension(value: str | None) -> str:
    if not value or "." not in value:
        return ""
    return "." + value.rsplit(".", 1)[-1].lower()


def _normalize_course(value: str) -> str:
    return " ".join(value.split()).lower()


def _get(data: dict, path: list[str]):
    current = data
    for key in path:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def _same_legacy_source(previous: dict, current: dict) -> bool:
    if previous.get("source_fingerprint") or not current.get("source_fingerprint"):
        return False
    old_notice = previous.get("notice", {})
    new_notice = current.get("notice", {})
    if str(old_notice.get("notice_id")) != str(new_notice.get("notice_id")):
        return False
    if _normalize_body(old_notice.get("body_text")) != _normalize_body(new_notice.get("body_text")):
        return False
    matched, removed, added = _match_attachments(previous.get("attachments", []), current.get("attachments", []))
    if removed or added:
        return False
    return all(old.get("sha256") and old.get("sha256") == new.get("sha256") for old, new in matched)


def _normalize_body(value: str | None) -> str:
    return " ".join((value or "").split())

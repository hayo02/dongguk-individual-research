from __future__ import annotations

import hashlib
import mimetypes
import re
from email.message import Message
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

from .excel import analyze_individual_research_excel
from .hwp import classify_and_validate_hwp


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def safe_filename(value: str) -> str:
    value = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", value).strip(" .")
    return value or "download"


def content_disposition_filename(header: str | None) -> str | None:
    if not header:
        return None
    extended = re.search(r"filename\*\s*=\s*([^']*)''([^;]+)", header, re.I)
    if extended:
        return unquote(extended.group(2))
    regular = re.search(r'filename\s*=\s*"([^"]+)"|filename\s*=\s*([^;]+)', header, re.I)
    if regular:
        return unquote((regular.group(1) or regular.group(2)).strip().strip('"'))
    return None


def file_sequence(url: str) -> str | None:
    values = parse_qs(urlparse(url).query).get("fileSeq")
    return values[0] if values else None


def choose_extension(filename: str, content_type: str | None) -> str:
    extension = Path(filename).suffix.lower()
    if extension:
        return extension
    guessed = mimetypes.guess_extension((content_type or "").split(";")[0].strip())
    return guessed or ""


def download_attachment(client, notice_id: int, attachment: dict, data_dir: str | Path) -> dict:
    output_dir = Path(data_dir) / "attachments" / str(notice_id)
    output_dir.mkdir(parents=True, exist_ok=True)
    result = {
        "name": attachment["name"],
        "download_url": attachment["url"],
        "file_seq": file_sequence(attachment["url"]),
        "role": attachment_role(attachment["name"]),
        "content_type": None,
        "content_disposition": None,
        "size": 0,
        "extension": Path(attachment["name"]).suffix.lower(),
        "local_path": None,
        "sha256": None,
        "download_success": False,
        "parse_success": False,
        "parse_error": None,
        "analysis": None,
    }
    try:
        data, headers, final_url = client.download(attachment["url"])
        disposition = headers.get("Content-Disposition")
        content_type = headers.get("Content-Type")
        filename = (
            content_disposition_filename(disposition)
            or attachment["name"]
            or f"file_{result['file_seq'] or 'unknown'}"
        )
        filename = safe_filename(filename)
        extension = choose_extension(filename, content_type)
        if extension and not filename.lower().endswith(extension):
            filename += extension
        path = output_dir / filename
        path.write_bytes(data)
        result.update({
            "final_url": final_url,
            "content_type": content_type,
            "content_disposition": disposition,
            "size": len(data),
            "extension": path.suffix.lower(),
            "local_path": str(path.resolve()),
            "sha256": sha256_bytes(data),
            "download_success": True,
        })
        try:
            result["analysis"] = analyze_downloaded_file(path)
            result["parse_success"] = True
        except Exception as exc:
            result["parse_error"] = f"{type(exc).__name__}: {exc}"
    except Exception as exc:
        result["parse_error"] = f"{type(exc).__name__}: {exc}"
    return result


def attachment_role(filename: str) -> str:
    compact = filename.replace(" ", "")
    suffix = Path(filename).suffix.lower()
    if suffix in {".xlsx", ".xlsm"} and "개별연구" in compact:
        return "INDIVIDUAL_RESEARCH_LIST"
    if suffix == ".hwp" and "수강신청원" in compact:
        return "APPLICATION_FORM"
    return "UNKNOWN"


def analyze_downloaded_file(path: str | Path) -> dict:
    path = Path(path)
    suffix = path.suffix.lower()
    if suffix in {".xlsx", ".xlsm"}:
        return {
            "type": "individual-research-excel",
            "result": analyze_individual_research_excel(path),
        }
    if suffix == ".hwp":
        return {
            "type": "hwp-form",
            "result": classify_and_validate_hwp(path),
        }
    raise ValueError(f"지원하지 않는 첨부파일 형식입니다: {suffix or '(확장자 없음)'}")

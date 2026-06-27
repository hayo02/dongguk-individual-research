from __future__ import annotations

import re
import struct
import zlib
from pathlib import Path

from .config import HWP_FORM_PROFILES
from .errors import StructureError

FREESECT = 0xFFFFFFFF
ENDOFCHAIN = 0xFFFFFFFE


class CompoundFile:
    def __init__(self, data: bytes):
        if data[:8] != bytes.fromhex("D0CF11E0A1B11AE1"):
            raise StructureError("HWP가 OLE Compound File 형식이 아닙니다.")
        self.data = data
        self.sector_size = 1 << struct.unpack_from("<H", data, 30)[0]
        self.mini_sector_size = 1 << struct.unpack_from("<H", data, 32)[0]
        self.first_dir_sector = struct.unpack_from("<I", data, 48)[0]
        self.mini_cutoff = struct.unpack_from("<I", data, 56)[0]
        self.first_minifat_sector = struct.unpack_from("<I", data, 60)[0]
        self.num_minifat_sectors = struct.unpack_from("<I", data, 64)[0]
        first_difat_sector = struct.unpack_from("<I", data, 68)[0]
        num_difat_sectors = struct.unpack_from("<I", data, 72)[0]
        difat = [x for x in struct.unpack_from("<109I", data, 76) if x != FREESECT]
        next_sector = first_difat_sector
        for _ in range(num_difat_sectors):
            sector = self._sector(next_sector)
            values = struct.unpack(f"<{self.sector_size // 4}I", sector)
            difat.extend(x for x in values[:-1] if x != FREESECT)
            next_sector = values[-1]
        self.fat = []
        for sector_id in difat:
            self.fat.extend(struct.unpack(
                f"<{self.sector_size // 4}I", self._sector(sector_id)
            ))
        directory = self._read_chain(self.first_dir_sector, self.fat)
        self.entries = []
        for offset in range(0, len(directory), 128):
            entry = directory[offset:offset + 128]
            if len(entry) < 128:
                break
            name_length = struct.unpack_from("<H", entry, 64)[0]
            name = entry[:name_length - 2].decode("utf-16le", errors="replace") if name_length >= 2 else ""
            self.entries.append({
                "name": name,
                "type": entry[66],
                "start": struct.unpack_from("<I", entry, 116)[0],
                "size": struct.unpack_from("<Q", entry, 120)[0],
            })
        root = next((entry for entry in self.entries if entry["type"] == 5), None)
        if root is None:
            raise StructureError("HWP 루트 OLE 스트림이 없습니다.")
        self.ministream = self._read_chain(root["start"], self.fat)[:root["size"]]
        minifat_bytes = self._read_chain(
            self.first_minifat_sector, self.fat, self.num_minifat_sectors
        )
        self.minifat = list(struct.unpack(
            f"<{len(minifat_bytes) // 4}I",
            minifat_bytes[:len(minifat_bytes) // 4 * 4],
        ))

    def _sector(self, sector_id: int) -> bytes:
        start = (sector_id + 1) * self.sector_size
        return self.data[start:start + self.sector_size]

    def _read_chain(self, start: int, fat: list[int], limit: int | None = None) -> bytes:
        chunks, seen, sector_id = [], set(), start
        while sector_id not in (FREESECT, ENDOFCHAIN) and sector_id not in seen:
            if sector_id >= len(fat):
                break
            seen.add(sector_id)
            chunks.append(self._sector(sector_id))
            if limit is not None and len(chunks) >= limit:
                break
            sector_id = fat[sector_id]
        return b"".join(chunks)

    def _read_mini_chain(self, start: int, size: int) -> bytes:
        chunks, seen, sector_id = [], set(), start
        while sector_id not in (FREESECT, ENDOFCHAIN) and sector_id not in seen:
            if sector_id >= len(self.minifat):
                break
            seen.add(sector_id)
            offset = sector_id * self.mini_sector_size
            chunks.append(self.ministream[offset:offset + self.mini_sector_size])
            sector_id = self.minifat[sector_id]
        return b"".join(chunks)[:size]

    def streams(self) -> dict[str, bytes]:
        streams = {}
        for entry in self.entries:
            if entry["type"] != 2 or not entry["name"]:
                continue
            if entry["size"] < self.mini_cutoff:
                value = self._read_mini_chain(entry["start"], entry["size"])
            else:
                value = self._read_chain(entry["start"], self.fat)[:entry["size"]]
            streams[entry["name"]] = value
        return streams


def _records(data: bytes):
    offset = 0
    while offset + 4 <= len(data):
        header = struct.unpack_from("<I", data, offset)[0]
        offset += 4
        tag = header & 0x3FF
        level = (header >> 10) & 0x3FF
        size = (header >> 20) & 0xFFF
        if size == 0xFFF:
            size = struct.unpack_from("<I", data, offset)[0]
            offset += 4
        payload = data[offset:offset + size]
        if len(payload) != size:
            raise StructureError("HWP 레코드 길이가 잘렸습니다.")
        offset += size
        yield tag, level, payload


def _paragraph_text(payload: bytes) -> str:
    units = list(struct.unpack(
        f"<{len(payload) // 2}H", payload[:len(payload) // 2 * 2]
    ))
    output = []
    index = 0
    extended_controls = {1, 2, 3, 11, 12, 14, 15, 16, 17, 18, 21, 22, 23}
    while index < len(units):
        code = units[index]
        if code in extended_controls:
            index += 8
            continue
        if code == 13:
            output.append("\n")
        elif code == 9:
            output.append("\t")
        elif code >= 32:
            output.append(chr(code))
        index += 1
    return re.sub(r"\n{3,}", "\n\n", "".join(output)).strip()


def _extract_tables(records: list[tuple[int, int, bytes]]) -> list[dict]:
    tables = []
    index = 0
    while index < len(records):
        tag, level, payload = records[index]
        if tag != 71 or payload[:4] != b" lbt":
            index += 1
            continue
        table_level = level
        index += 1
        cells = []
        current = None
        while index < len(records):
            next_tag, next_level, next_payload = records[index]
            if next_level <= table_level:
                break
            if next_tag == 72 and next_level == table_level + 1 and len(next_payload) >= 16:
                if current is not None:
                    cells.append(current)
                current = {
                    "column": struct.unpack_from("<H", next_payload, 8)[0],
                    "row": struct.unpack_from("<H", next_payload, 10)[0],
                    "column_span": struct.unpack_from("<H", next_payload, 12)[0],
                    "row_span": struct.unpack_from("<H", next_payload, 14)[0],
                    "texts": [],
                }
            elif next_tag == 67 and current is not None:
                text = _paragraph_text(next_payload)
                if text:
                    current["texts"].append(text)
            index += 1
        if current is not None:
            cells.append(current)
        for cell in cells:
            cell["text"] = "\n".join(cell.pop("texts"))
        if cells:
            tables.append({"cells": cells, "cell_count": len(cells)})
    return tables


def analyze_hwp(path: str | Path) -> dict:
    path = Path(path)
    streams = CompoundFile(path.read_bytes()).streams()
    header = streams.get("FileHeader")
    if not header or len(header) < 40:
        raise StructureError(f"{path.name}: FileHeader 스트림이 없습니다.")
    compressed = bool(struct.unpack_from("<I", header, 36)[0] & 1)
    all_text, all_tables, sections = [], [], []
    section_names = sorted(
        (name for name in streams if re.fullmatch(r"Section\d+", name)),
        key=lambda value: int(value[7:]),
    )
    if not section_names:
        raise StructureError(f"{path.name}: BodyText Section 스트림이 없습니다.")
    for name in section_names:
        body = zlib.decompress(streams[name], -15) if compressed else streams[name]
        records = list(_records(body))
        texts = [
            text for tag, _, payload in records
            if tag == 67 and (text := _paragraph_text(payload))
        ]
        tables = _extract_tables(records)
        all_text.extend(texts)
        all_tables.extend(tables)
        sections.append({
            "name": name,
            "record_count": len(records),
            "text_count": len(texts),
            "table_count": len(tables),
        })
    return {
        "file": path.name,
        "compressed": compressed,
        "sections": sections,
        "text": "\n".join(all_text),
        "tables": all_tables,
    }


def classify_and_validate_hwp(path: str | Path) -> dict:
    path = Path(path)
    analysis = analyze_hwp(path)
    normalized_filename = re.sub(r"\s+", "", path.name)
    matches = []
    for form_type, profile in HWP_FORM_PROFILES.items():
        filename_match = all(
            keyword.replace(" ", "") in normalized_filename
            for keyword in profile["filename_keywords"]
        )
        text_match = all(
            required.replace(" ", "") in analysis["text"].replace(" ", "")
            for required in profile["required_text"]
        )
        if filename_match or text_match:
            matches.append((form_type, profile))
    if len(matches) != 1:
        raise StructureError(
            f"HWP 양식을 하나로 판별하지 못했습니다: {path.name} ({len(matches)}개 후보)"
        )
    form_type, profile = matches[0]
    missing = [
        required for required in profile["required_text"]
        if required.replace(" ", "") not in analysis["text"].replace(" ", "")
    ]
    if missing:
        raise StructureError(
            f"{profile['display_name']} 필수 문구가 없습니다: {', '.join(missing)}"
        )
    signatures = profile["required_signatures"]
    fields = _extract_form_fields(analysis)
    signature_boxes = _extract_signature_boxes(analysis, signatures)
    missing_fields = [field["label"] for field in fields if not field["required"]]
    if missing_fields:
        raise StructureError(f"{profile['display_name']} 필수 입력 라벨이 없습니다: {', '.join(missing_fields)}")
    if not any(box["source_text"] for box in signature_boxes):
        raise StructureError(f"{profile['display_name']} 담당교수 서명란을 찾지 못했습니다.")
    analysis.update({
        "form_type": form_type,
        "form_name": profile["display_name"],
        "structure_valid": True,
        "required_signatures": signatures,
        "fields": fields,
        "additional_autofill_fields": _additional_autofill_fields(),
        "signature_boxes": signature_boxes,
        "document_title": profile["display_name"],
    })
    return analysis


def _extract_form_fields(analysis: dict) -> list[dict]:
    labels = [
        "학년도/학기",
        "성명",
        "학번",
        "소속",
        "연락처",
        "교과목명",
        "연구주제",
        "담당교수",
    ]
    compact_text = analysis["text"].replace(" ", "")
    fields = []
    for label in labels:
        compact = label.replace(" ", "")
        fields.append({
            "field": _field_id(label),
            "label": label,
            "required": compact in compact_text,
            "source_type": "HWP",
            "source_text": _line_with(analysis["text"], label),
        })
    return fields


def _extract_signature_boxes(analysis: dict, required_signatures: list[dict]) -> list[dict]:
    signatures = []
    for signature in required_signatures:
        name = signature["name"]
        signatures.append({
            "name": name,
            "required": signature.get("required", True),
            "source_type": "HWP",
            "source_text": _signature_source(analysis["text"], name),
        })
    return signatures


def _additional_autofill_fields() -> list[dict]:
    return [
        {"field": "email", "source": "USER_PROFILE", "required": False},
        {"field": "course_code", "source": "SELECTED_RESEARCH_ITEM", "required": False},
    ]


def _field_id(label: str) -> str:
    return {
        "학년도/학기": "academic_year_semester",
        "성명": "student_name",
        "학번": "student_id",
        "소속": "affiliation",
        "연락처": "contact",
        "교과목명": "course_name",
        "담당교수": "professor",
        "연구주제": "research_topic",
    }[label]


def _line_with(text: str, keyword: str) -> str:
    compact_keyword = keyword.replace(" ", "")
    for line in [line.strip() for line in text.splitlines() if line.strip()]:
        if compact_keyword in line.replace(" ", ""):
            return line
    return ""


def _signature_source(text: str, name: str) -> str:
    compact_name = name.replace(" 서명", "").replace(" ", "")
    for line in [line.strip() for line in text.splitlines() if line.strip()]:
        compact_line = line.replace(" ", "")
        if compact_name in compact_line and "(인)" in line:
            return line
    return _line_with(text, name.replace(" 서명", ""))

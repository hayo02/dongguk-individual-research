from __future__ import annotations

import datetime as dt
import re
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

from .config import EXCEL_LAYOUTS
from .errors import StructureError

MAIN = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
REL = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"


def _column_index(reference: str) -> int:
    letters = re.match(r"[A-Z]+", reference).group()
    value = 0
    for char in letters:
        value = value * 26 + ord(char) - 64
    return value - 1


def _shared_strings(zf: zipfile.ZipFile) -> list[str]:
    if "xl/sharedStrings.xml" not in zf.namelist():
        return []
    root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
    return [
        "".join(node.text or "" for node in item.iter(f"{MAIN}t"))
        for item in root.findall(f"{MAIN}si")
    ]


def _date_style_ids(zf: zipfile.ZipFile) -> set[int]:
    root = ET.fromstring(zf.read("xl/styles.xml"))
    custom = {
        int(node.attrib["numFmtId"]): node.attrib.get("formatCode", "")
        for node in root.findall(f".//{MAIN}numFmt")
    }
    built_in_dates = set(range(14, 23)) | {45, 46, 47}
    result = set()
    cell_xfs = root.find(f"{MAIN}cellXfs")
    if cell_xfs is None:
        return result
    for index, xf in enumerate(cell_xfs):
        fmt_id = int(xf.attrib.get("numFmtId", 0))
        code = custom.get(fmt_id, "")
        if fmt_id in built_in_dates or re.search(r"[ymdhis]", code, re.I):
            result.add(index)
    return result


def _cell_value(cell, shared: list[str], date_styles: set[int]):
    cell_type = cell.attrib.get("t")
    value_node = cell.find(f"{MAIN}v")
    if cell_type == "inlineStr":
        return "".join(x.text or "" for x in cell.iter(f"{MAIN}t"))
    if value_node is None:
        return None
    raw = value_node.text or ""
    if cell_type == "s":
        return shared[int(raw)]
    if cell_type == "b":
        return raw == "1"
    if cell_type in {"str", "e"}:
        return raw
    try:
        number = float(raw)
    except ValueError:
        return raw
    style = int(cell.attrib.get("s", 0))
    if style in date_styles:
        base = dt.datetime(1899, 12, 30)
        return (base + dt.timedelta(days=number)).isoformat()
    return int(number) if number.is_integer() else number


def analyze_excel(path: str | Path) -> dict:
    path = Path(path)
    with zipfile.ZipFile(path) as zf:
        shared = _shared_strings(zf)
        date_styles = _date_style_ids(zf)
        workbook = ET.fromstring(zf.read("xl/workbook.xml"))
        relationships = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
        rel_map = {node.attrib["Id"]: node.attrib["Target"] for node in relationships}
        sheets = []
        for sheet_node in workbook.find(f"{MAIN}sheets"):
            name = sheet_node.attrib["name"]
            target = rel_map[sheet_node.attrib[f"{REL}id"]].lstrip("/")
            xml_path = target if target.startswith("xl/") else f"xl/{target}"
            root = ET.fromstring(zf.read(xml_path))
            dimension_node = root.find(f"{MAIN}dimension")
            dimension = dimension_node.attrib.get("ref") if dimension_node is not None else None
            merge_node = root.find(f"{MAIN}mergeCells")
            merged = [x.attrib["ref"] for x in merge_node] if merge_node is not None else []
            rows: dict[int, list] = {}
            max_col = 0
            for row_node in root.findall(f".//{MAIN}sheetData/{MAIN}row"):
                row_number = int(row_node.attrib["r"])
                cells = {}
                for cell in row_node.findall(f"{MAIN}c"):
                    col = _column_index(cell.attrib["r"])
                    max_col = max(max_col, col)
                    cells[col] = _cell_value(cell, shared, date_styles)
                rows[row_number] = cells
            matrix = []
            for row_number in range(1, max(rows, default=0) + 1):
                cells = rows.get(row_number, {})
                matrix.append([cells.get(col) for col in range(max_col + 1)])
            layout = EXCEL_LAYOUTS.get(name)
            if layout is None:
                raise StructureError(f"알 수 없는 Excel 시트 구조: {name}")
            detected = _detect_table_layout(matrix, layout["columns"])
            data = []
            for row_number in range(detected["data_start_row"], len(matrix) + 1):
                source_row = matrix[row_number - 1]
                values = [
                    source_row[index] if index < len(source_row) else None
                    for index in detected["column_indexes"]
                ]
                if not any(value not in (None, "") for value in values):
                    continue
                data.append(dict(zip(detected["columns"], values)))
            sheets.append({
                "name": name,
                "dimension": dimension,
                "merged_cells": merged,
                "layout": detected,
                "row_count": len(data),
                "data": data,
            })
    return {"file": path.name, "sheets": sheets}


def analyze_individual_research_excel(path: str | Path) -> dict:
    workbook = analyze_excel(path)
    target = next(
        (sheet for sheet in workbook["sheets"] if sheet["name"] == "개별연구 개설 신청"),
        None,
    )
    if target is None:
        raise StructureError("개별연구 개설 신청 시트를 찾지 못했습니다.")

    items = []
    for row in target["data"]:
        research_description_value = row.get("연구내용")
        research_description = str(research_description_value or "")
        qualification = row.get("수강 자격사항")
        qualification_text = str(qualification or "")
        combined = "\n".join(
            str(row.get(key) or "")
            for key in ("연구내용", "인터뷰 일정", "수강 자격사항")
        )
        emails = sorted(set(re.findall(
            r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}", combined
        )))
        phones = sorted(set(re.findall(
            r"(?:0\d{1,2}[-)\s]?\d{3,4}[-\s]?\d{4})", combined
        )))
        item = dict(row)
        item["research_description"] = research_description_value
        item["qualification"] = qualification
        item["mentioned_technologies"] = _technology_mentions(
            research_description, "research_description"
        )
        item["required_skills"] = [
            {
                "value": mention["value"],
                "source_field": "qualification",
                "source_text": mention["source_text"],
                "status": "CONFIRMED",
            }
            for mention in _technology_mentions(qualification_text, "qualification")
        ]
        qualification_topics = _qualification_topics(qualification_text)
        grade_match = re.search(r"([1-4](?:\s*[~-]\s*[1-4])?)\s*학년", combined)
        major_match = re.search(
            r"((?:컴퓨터|AI|인공지능|소프트웨어|멀티미디어)[^,\n]{0,25}(?:학부|학과|전공))",
            combined,
        )
        interview_text = str(row.get("인터뷰 일정") or "")
        contact_required = _contact_before_application_required(interview_text, combined)
        consultation_required = _interview_or_consultation_required(interview_text, combined)
        item["derived"] = {
            "교수 이메일": emails,
            "교수 전화번호": phones,
            "사전 상담 필요 여부": contact_required or consultation_required,
            "지원 전 교수 연락 필요 여부": contact_required,
            "인터뷰 또는 상담 필요 여부": consultation_required,
            "인터뷰 방식": _interview_method(interview_text),
            "필수 기술": [entry["value"] for entry in item["required_skills"]],
            "프로그래밍 언어": _programming_languages(
                item["mentioned_technologies"],
                item["required_skills"],
            ),
            "학년 제한": grade_match.group(0) if grade_match else None,
            "전공 제한": major_match.group(1) if major_match else None,
            "추가 신청 불가 여부": bool(
                re.search(r"추가\s*(?:신청|접수).{0,10}(?:불가|받지)", combined)
            ),
        }
        item["qualification_topics"] = qualification_topics
        items.append(item)
    return {
        "file": workbook["file"],
        "sheet": target["name"],
        "dimension": target["dimension"],
        "merged_cells": target["merged_cells"],
        "item_count": len(items),
        "items": items,
        "warnings": research_warnings(items),
    }


def research_warnings(items: list[dict]) -> list[dict]:
    warnings = []
    groups: dict[tuple[str, str], list[dict]] = {}
    for item in items:
        row = item.get("순번")
        professor = item.get("교원명")
        course = item.get("과목명")
        if not professor:
            warnings.append({"code": "MISSING_PROFESSOR", "row": row, "message": "교수명이 비어 있습니다.", "fields": ["교원명"]})
        if not course:
            warnings.append({"code": "MISSING_COURSE_NAME", "row": row, "message": "과목명이 비어 있습니다.", "fields": ["과목명"]})
        if item.get("연구내용") in (None, ""):
            warnings.append({"code": "MISSING_RESEARCH_DESCRIPTION", "row": row, "message": "연구내용이 비어 있습니다.", "fields": ["연구내용"]})
        if item.get("수강정원") in (None, ""):
            warnings.append({"code": "MISSING_CAPACITY", "row": row, "message": "수강정원이 비어 있습니다.", "fields": ["수강정원"]})
        if professor and course:
            groups.setdefault((str(professor), str(course)), []).append(item)
    for duplicates in groups.values():
        if len(duplicates) > 1:
            for item in duplicates:
                warnings.append({
                    "code": "DUPLICATE_RESEARCH_ITEM",
                    "row": item.get("순번"),
                    "message": "동일 교수·동일 과목명의 연구 항목이 중복되어 있습니다.",
                    "fields": ["교원명", "과목명"],
                })
    return warnings


def _detect_table_layout(matrix: list[list], required_columns: list[str]) -> dict:
    for row_index in range(len(matrix)):
        next_row = matrix[row_index + 1] if row_index + 1 < len(matrix) else []
        column_indexes = []
        used_next_row = False
        for required in required_columns:
            found = None
            found_in_next = False
            for col_index in range(max(len(matrix[row_index]), len(next_row))):
                current_text = (
                    str(matrix[row_index][col_index])
                    if col_index < len(matrix[row_index]) and matrix[row_index][col_index] not in (None, "")
                    else ""
                )
                next_text = (
                    str(next_row[col_index])
                    if col_index < len(next_row) and next_row[col_index] not in (None, "")
                    else ""
                )
                if _header_contains(current_text, required):
                    found = col_index
                    break
                if _header_contains(next_text, required):
                    found = col_index
                    found_in_next = True
                    break
                if _header_contains("\n".join(part for part in [current_text, next_text] if part), required):
                    found = col_index
                    found_in_next = bool(next_text)
                    break
            if found is None:
                break
            used_next_row = used_next_row or found_in_next
            column_indexes.append(found)
        if len(column_indexes) == len(required_columns):
            header_rows = [row_index + 1, row_index + 2] if used_next_row else [row_index + 1]
            return {
                "title_rows": list(range(1, row_index + 1)),
                "header_rows": header_rows,
                "data_start_row": row_index + 3 if used_next_row else row_index + 2,
                "columns": required_columns,
                "column_indexes": column_indexes,
            }
    missing = ", ".join(required_columns)
    raise StructureError(f"Excel 필수 열을 찾지 못했습니다: {missing}")


def _header_contains(header_text: str, required: str) -> bool:
    return _compact(required) in _compact(header_text)


def _compact(value: str) -> str:
    return re.sub(r"\s+", "", value).replace("/", "").lower()


def _technology_mentions(text: str, source_field: str) -> list[dict]:
    patterns = [
        ("Graph RAG", r"(?<![A-Za-z0-9_])Graph\s+RAG(?![A-Za-z0-9_])"),
        ("ProteinBERT", r"(?<![A-Za-z0-9_])ProteinBERT(?![A-Za-z0-9_])"),
        ("C++", r"(?<![A-Za-z0-9_+#])C\+\+(?![A-Za-z0-9_+#])"),
        ("C#", r"(?<![A-Za-z0-9_+#])C#(?![A-Za-z0-9_+#])"),
        ("Computer Vision", r"컴퓨터\s*비전|(?<![A-Za-z0-9_])Computer\s+Vision(?![A-Za-z0-9_])"),
        ("Linux", r"리눅스|(?<![A-Za-z0-9_])Linux(?![A-Za-z0-9_])"),
        ("PyG", r"(?<![A-Za-z0-9_])pyg(?![A-Za-z0-9_])"),
        ("Python", r"파이썬|(?<![A-Za-z0-9_])Python(?![A-Za-z0-9_])"),
        ("PyTorch", r"(?<![A-Za-z0-9_])PyTorch(?![A-Za-z0-9_])"),
        ("pandas", r"(?<![A-Za-z0-9_])pandas(?![A-Za-z0-9_])"),
        ("Kubernetes", r"(?<![A-Za-z0-9_])Kubernetes(?![A-Za-z0-9_])"),
        ("JavaScript", r"자바스크립트|(?<![A-Za-z0-9_])JavaScript(?![A-Za-z0-9_])"),
        ("Java", r"자바|(?<![A-Za-z0-9_])Java(?![A-Za-z0-9_])"),
        ("MATLAB", r"매트랩|(?<![A-Za-z0-9_])MATLAB(?![A-Za-z0-9_])"),
        ("Diffusion", r"(?<![A-Za-z0-9_])Diffusion(?![A-Za-z0-9_])"),
        ("LiDAR", r"(?<![A-Za-z0-9_])LiDAR(?![A-Za-z0-9_])"),
        ("Unity", r"유니티|(?<![A-Za-z0-9_])Unity(?![A-Za-z0-9_])"),
        ("Unreal", r"언리얼|(?<![A-Za-z0-9_])Unreal(?![A-Za-z0-9_])"),
        ("AIoT", r"(?<![A-Za-z0-9_])AIoT(?![A-Za-z0-9_])"),
        ("IoT", r"(?<![A-Za-z0-9_])IoT(?![A-Za-z0-9_])"),
        ("ECG", r"(?<![A-Za-z0-9_])ECG(?![A-Za-z0-9_])"),
        ("PPG", r"(?<![A-Za-z0-9_])PPG(?![A-Za-z0-9_])"),
        ("GNN", r"(?<![A-Za-z0-9_])GNN(?![A-Za-z0-9_])"),
        ("VAE", r"(?<![A-Za-z0-9_])VAE(?![A-Za-z0-9_])"),
        ("SDK", r"(?<![A-Za-z0-9_])SDK(?![A-Za-z0-9_])"),
        ("API", r"(?<![A-Za-z0-9_])API(?![A-Za-z0-9_])"),
        ("AWS", r"(?<![A-Za-z0-9_])AWS(?![A-Za-z0-9_])"),
        ("IAM", r"(?<![A-Za-z0-9_])IAM(?![A-Za-z0-9_])"),
        ("IDP", r"(?<![A-Za-z0-9_])IDP(?![A-Za-z0-9_])"),
        ("MoE", r"(?<![A-Za-z0-9_])MoE(?![A-Za-z0-9_])"),
        ("LLM", r"(?<![A-Za-z0-9_])LLM(?![A-Za-z0-9_])"),
        ("LoRA", r"(?<![A-Za-z0-9_])LoRA(?![A-Za-z0-9_])"),
        ("RAG", r"(?<![A-Za-z0-9_])RAG(?![A-Za-z0-9_])"),
        ("ROS", r"(?<![A-Za-z0-9_])ROS(?![A-Za-z0-9_])"),
        ("TypeScript", r"타입스크립트|(?<![A-Za-z0-9_])TypeScript(?![A-Za-z0-9_])"),
        ("Kotlin", r"(?<![A-Za-z0-9_])Kotlin(?![A-Za-z0-9_])"),
        ("Swift", r"(?<![A-Za-z0-9_])Swift(?![A-Za-z0-9_])"),
        ("Go", r"(?<![A-Za-z0-9_])Go(?![A-Za-z0-9_])"),
        ("Rust", r"(?<![A-Za-z0-9_])Rust(?![A-Za-z0-9_])"),
        ("SQL", r"(?<![A-Za-z0-9_])SQL(?![A-Za-z0-9_])"),
        ("R", r"(?<![A-Za-z0-9_])R(?![A-Za-z0-9_])"),
        ("C", r"(?<![A-Za-z0-9_+#])C(?![A-Za-z0-9_+#])"),
    ]
    mentions = []
    seen = set()
    for value, pattern in patterns:
        for match in re.finditer(pattern, text, re.I):
            key = value.lower()
            if key in seen:
                continue
            seen.add(key)
            mentions.append({
                "value": value,
                "source_field": source_field,
                "source_text": _source_sentence(text, match.start()),
                "matched_text": match.group(0),
                "source_index": match.start(),
                "status": "CONFIRMED",
            })
    return mentions


def _programming_languages(
    mentioned_technologies: list[dict],
    required_skills: list[dict] | None = None,
) -> list[str]:
    allowed = {
        "C", "C++", "C#", "Java", "Python", "JavaScript", "TypeScript",
        "R", "MATLAB", "Kotlin", "Swift", "Go", "Rust", "SQL",
    }
    values = []
    seen = set()
    entries = sorted(
        [*mentioned_technologies, *(required_skills or [])],
        key=lambda item: (item.get("source_field") != "research_description", item.get("source_index", 0)),
    )
    for entry in entries:
        value = entry.get("value")
        if value in allowed and value not in seen:
            values.append(value)
            seen.add(value)
    return values


def _qualification_topics(text: str) -> list[dict]:
    topics = []
    patterns = [
        ("자료구조", r"자료\s*구조"),
        ("알고리즘", r"알고리즘"),
        ("Linux", r"(?<![A-Za-z0-9_])Linux(?![A-Za-z0-9_])"),
        ("머신러닝", r"머신러닝"),
        ("딥러닝", r"딥러닝"),
        ("인공지능", r"인공지능"),
        ("컴퓨터비전", r"컴퓨터\s*비전"),
        ("자연어처리", r"자연어\s*처리"),
    ]
    for value, pattern in patterns:
        match = re.search(pattern, text, re.I)
        if match:
            topics.append({
                "value": value,
                "source_field": "qualification",
                "source_text": _source_sentence(text, match.start()),
                "status": "CONFIRMED",
            })
    return topics


def _source_sentence(text: str, index: int) -> str:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    cursor = 0
    for line in lines:
        start = text.find(line, cursor)
        end = start + len(line)
        cursor = end
        if start <= index <= end:
            return line
    return text.strip()


def _interview_method(text: str) -> list[str]:
    methods = []
    if re.search(r"이메일|메일", text):
        methods.append("이메일")
    if re.search(r"전화|유선", text):
        methods.append("전화")
    if re.search(r"대면|방문|연구실", text):
        methods.append("대면")
    if re.search(r"온라인|Zoom|Webex|Teams", text, re.I):
        methods.append("온라인")
    if re.search(r"면담|인터뷰", text):
        methods.append("면담")
    return methods


def _contact_before_application_required(interview_text: str, combined: str) -> bool:
    text = "\n".join([interview_text, combined])
    patterns = [
        r"사전\s*연락", r"메일\s*컨택", r"이메일로\s*(?:사전\s*)?연락", r"연락\s*후\s*(?:결정|일정\s*협의)",
        r"접수\s*전\s*상담\s*필수", r"신청\s*전\s*상담\s*필수",
        r"전화\s*상담\s*(?:후|필수)?", r"상담\s*후\s*수락받은\s*학생만",
        r"면담\s*후\s*수락", r"수락받은\s*학생만\s*신청",
        r"인터뷰\s*신청", r"면담\s*신청", r"이메일로.{0,20}(?:연구\s*방향|인터뷰).{0,20}(?:제출|신청)",
    ]
    return any(re.search(pattern, text) for pattern in patterns)


def _interview_or_consultation_required(interview_text: str, combined: str) -> bool:
    text = "\n".join([interview_text, combined])
    patterns = [
        r"사전\s*상담", r"(?:접수|신청)\s*전\s*상담\s*필수",
        r"상담\s*필수", r"면담\s*필수", r"인터뷰\s*신청",
        r"면담\s*신청", r"전화\s*상담", r"수락받은\s*학생만", r"개별\s*면담",
    ]
    return any(re.search(pattern, text) for pattern in patterns)

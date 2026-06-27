import unittest
from pathlib import Path

from dongguk_notice.excel import (
    _contact_before_application_required,
    _interview_or_consultation_required,
    _programming_languages,
    _technology_mentions,
    analyze_excel,
    analyze_individual_research_excel,
)
from dongguk_notice.hwp import analyze_hwp, classify_and_validate_hwp

SAMPLES = Path(__file__).resolve().parents[1] / "samples"


class SampleTests(unittest.TestCase):
    def test_excel_structure(self):
        workbook = analyze_excel(next(SAMPLES.glob("*.xlsx")))
        self.assertEqual([sheet["name"] for sheet in workbook["sheets"]], [
            "개별연구 개설 신청",
            "※ 개별연구 개설현황(2016~2026)",
        ])
        first = workbook["sheets"][0]
        self.assertEqual(first["dimension"], "A1:K47")
        self.assertEqual(first["layout"]["data_start_row"], 5)
        self.assertIn("H3:K3", first["merged_cells"])
        self.assertEqual(first["row_count"], 43)

    def test_hwp_text_and_tables(self):
        individual = analyze_hwp(next(SAMPLES.glob("*수강신청원*.hwp")))
        self.assertIn("개별연구 수강신청원", individual["text"])
        self.assertEqual(len(individual["tables"]), 2)

    def test_individual_research_excel_extracts_items_and_skills(self):
        workbook = analyze_individual_research_excel(next(SAMPLES.glob("*.xlsx")))
        self.assertEqual(workbook["item_count"], 43)
        first = workbook["items"][0]
        self.assertIn("research_description", first)
        self.assertIn("qualification", first)

    def test_repeated_research_row_does_not_inherit_unmerged_empty_cells(self):
        workbook = analyze_individual_research_excel(next(SAMPLES.glob("*.xlsx")))
        row_41 = next(item for item in workbook["items"] if item["순번"] == 41)
        self.assertIsNone(row_41["연구내용"])
        self.assertIsNone(row_41["수강 자격사항"])
        self.assertNotIn("_inherited_fields", row_41)
        warning_codes = [item["code"] for item in workbook["warnings"] if item.get("row") == 41]
        self.assertIn("DUPLICATE_RESEARCH_ITEM", warning_codes)
        self.assertIn("MISSING_RESEARCH_DESCRIPTION", warning_codes)

    def test_technology_mentions_keep_cpp_csharp_c_and_ros_separate(self):
        values = [
            item["value"]
            for item in _technology_mentions("Retrosynthetic 분석과 ROS, C++, C#, C 실습", "qualification")
        ]
        self.assertIn("ROS", values)
        self.assertIn("C++", values)
        self.assertIn("C#", values)
        self.assertIn("C", values)
        retro_values = [
            item["value"]
            for item in _technology_mentions("Retrosynthetic analysis", "research_description")
        ]
        self.assertNotIn("ROS", retro_values)

    def test_cpp_and_csharp_before_korean_are_required_skills(self):
        values = [
            item["value"]
            for item in _technology_mentions("C++/C#언어 사용가능자", "qualification")
        ]
        self.assertIn("C++", values)
        self.assertIn("C#", values)

    def test_research_technology_extraction_examples(self):
        text = "ECG PPG Graph RAG GNN VAE Diffusion AWS IAM Kubernetes MoE LLM LoRA"
        values = [item["value"] for item in _technology_mentions(text, "research_description")]
        for expected in ["ECG", "PPG", "Graph RAG", "GNN", "VAE", "Diffusion", "AWS", "IAM", "Kubernetes", "MoE", "LLM", "LoRA"]:
            self.assertIn(expected, values)

    def test_general_technologies_are_not_programming_languages(self):
        text = "ECG PPG AWS IAM Kubernetes IDP GNN VAE Diffusion C C++ C# Java Python C"
        mentions = _technology_mentions(text, "research_description")
        values = [item["value"] for item in mentions]
        self.assertIn("ECG", values)
        self.assertIn("PPG", values)
        languages = _programming_languages(mentions)
        for not_language in ["ECG", "PPG", "AWS", "IAM", "Kubernetes", "IDP", "GNN", "VAE", "Diffusion"]:
            self.assertNotIn(not_language, languages)
        self.assertEqual(languages, ["C", "C++", "C#", "Java", "Python"])

    def test_qualification_technology_normalization_examples(self):
        text = "컴퓨터능력: 리눅스, Python, PyTorch, pyg, pandas, 유니티, 언리얼, 컴퓨터 비전"
        values = [item["value"] for item in _technology_mentions(text, "qualification")]
        for expected in ["Linux", "Python", "PyTorch", "PyG", "pandas", "Unity", "Unreal", "Computer Vision"]:
            self.assertIn(expected, values)

    def test_c_cpp_csharp_are_distinct(self):
        values = [item["value"] for item in _technology_mentions("C C++ C# Java", "qualification")]
        self.assertIn("C", values)
        self.assertIn("C++", values)
        self.assertIn("C#", values)

    def test_professor_contact_phrase_detection(self):
        self.assertTrue(_contact_before_application_required("메일 컨택 후 일정 협의", ""))
        self.assertTrue(_contact_before_application_required("이메일로 연락 후 결정", ""))
        self.assertTrue(_contact_before_application_required("접수 전 상담 필수\n전화 상담 후 수락받은 학생만 신청", ""))
        self.assertTrue(_contact_before_application_required("전화 상담 필수", ""))
        self.assertTrue(_contact_before_application_required("이메일로 연구 방향을 제출하면서 인터뷰 신청", ""))
        self.assertTrue(_interview_or_consultation_required("인터뷰 신청", ""))
        self.assertTrue(_interview_or_consultation_required("전화 상담 후 수락받은 학생만 신청", ""))
        self.assertFalse(_contact_before_application_required("메일주소: example@dongguk.edu", ""))

    def test_actual_rows_14_to_22_have_phone_consultation_only_per_item(self):
        workbook = analyze_individual_research_excel(next(SAMPLES.glob("*.xlsx")))
        rows = [item for item in workbook["items"] if 14 <= item["순번"] <= 22]
        self.assertEqual(len(rows), 9)
        for item in rows:
            self.assertTrue(item["derived"]["지원 전 교수 연락 필요 여부"])
            self.assertTrue(item["derived"]["인터뷰 또는 상담 필요 여부"])
            self.assertIn("전화", item["derived"]["인터뷰 방식"])
        row_13 = next(item for item in workbook["items"] if item["순번"] == 13)
        self.assertNotIn("전화", row_13["derived"]["인터뷰 방식"])

    def test_retrosynthetic_is_not_ros_in_any_technology_field(self):
        workbook = analyze_individual_research_excel(next(SAMPLES.glob("*.xlsx")))
        artificial = {
            "mentioned_technologies": _technology_mentions("Retrosynthetic Analysis", "research_description"),
            "required_skills": _technology_mentions("Retrosynthetic Analysis", "qualification"),
            "qualification_topics": [],
            "derived": {"필수 기술": []},
        }
        values = []
        for field in ("mentioned_technologies", "required_skills", "qualification_topics"):
            values.extend(item["value"] for item in artificial[field])
        values.extend(artificial["derived"]["필수 기술"])
        for item in workbook["items"]:
            if "Retrosynthetic" in (item.get("research_description") or ""):
                values.extend(entry["value"] for entry in item["mentioned_technologies"])
                values.extend(entry["value"] for entry in item["required_skills"])
                values.extend(entry["value"] for entry in item["qualification_topics"])
                values.extend(item["derived"]["필수 기술"])
        self.assertNotIn("ROS", values)

    def test_hwp_application_form_fields_and_signatures(self):
        form = classify_and_validate_hwp(next(SAMPLES.glob("*수강신청원*.hwp")))
        self.assertEqual(form["document_title"], "개별연구 수강신청원")
        self.assertEqual(len(form["fields"]), 8)
        self.assertTrue(any(field["field"] == "student_name" for field in form["fields"]))
        self.assertFalse(any(field["field"] == "email" for field in form["fields"]))
        self.assertFalse(any(field["field"] == "course_code" for field in form["fields"]))
        self.assertTrue(any(field["field"] == "email" for field in form["additional_autofill_fields"]))
        self.assertFalse(next(field for field in form["additional_autofill_fields"] if field["field"] == "email")["required"])
        self.assertTrue(any(field["field"] == "course_code" for field in form["additional_autofill_fields"]))
        self.assertTrue(any(item["name"] == "담당교수 서명" for item in form["signature_boxes"]))
        self.assertIn("(인)", form["signature_boxes"][0]["source_text"])

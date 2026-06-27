import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from dongguk_notice.crawler import autofill_candidates, crawl_notice, discover_latest_notice
from dongguk_notice.errors import StructureError
from dongguk_notice.snapshot import compare_snapshots
from dongguk_notice.structure import structure_notice


class FakeClient:
    def __init__(self, search_results):
        self.search_results = search_results

    def list_notices(self, page=1, keyword=None):
        if (keyword, page) in self.search_results:
            return self.search_results[(keyword, page)]
        return self.search_results.get(keyword, [])


class CrawlerTests(unittest.TestCase):
    def test_selects_latest_date_then_id_without_hardcoding(self):
        client = FakeClient({
            "개별연구": [
                {"id": 10, "title": "개별연구 A", "date": "2026-01-01"},
                {"id": 12, "title": "개별연구 B", "date": "2026-01-01"},
            ],
            "개별 연구": [],
        })
        selected, discovery = discover_latest_notice(client, [])
        self.assertEqual(selected["id"], 12)
        self.assertEqual(discovery["strategy"], "paginated-title-search")

    def test_ignores_non_individual_research_notice(self):
        client = FakeClient({"개별연구": [], "개별 연구": []})
        with self.assertRaises(LookupError):
            discover_latest_notice(client, [{"id": 1, "title": "일반 학사 안내", "date": "2026-01-01"}])

    def test_notice_structure_keeps_evidence(self):
        text = (
            "신청기간 : 2026.4.30. 17:00까지\n"
            "담당교수 승인 후 개별연구 수강신청원 방문 제출\n"
            "제출처 : 원흥관 4층 423호\n"
            "수강신청은 별도 진행"
        )
        result = structure_notice(text, [])
        self.assertEqual(result["schedule"]["application_deadline"]["value"], "2026-04-30 17:00")
        self.assertEqual(result["submission"]["location"]["value"], "원흥관 4층 423호")
        self.assertEqual(result["submission"]["method"]["value"], "방문 제출")
        self.assertTrue(result["submission"]["professor_approval_required"]["value"])
        self.assertTrue(any(item["id"] == "course_registration" for item in result["checklist_template"]))

    def test_actual_notice_phrases_confirm_submission_requirements(self):
        text = (
            "- [붙임1] 교과목 개설 목록 및 신청방법 확인하여 담당교수에게 연락\n"
            "- [붙임2] 수강신청원 양식 작성 및 담당교수 서명 후 학과사무실 제출(원흥관 423호)\n"
            "※ 담당교수 서명 어려운 경우 수강 허가를 확인할 수 있는 이메일 등 증빙자료 함께 지참하여 제출\n"
            "1) 수강신청원 제출 및 개별연구 수강 예정자는 반드시 수강신청기간(05/13~05/15)에 수강신청사이트에서 수강신청을 해야 함\n"
            "2) 수강신청원은 방문제출을 원칙으로 하며, 부득이하게 학과사무실 방문이 어려울 경우 대리제출도 가능함 (이메일 제출은 접수하지 않음)"
        )
        result = structure_notice(text, [])
        submission = result["submission"]
        self.assertTrue(submission["professor_contact_required"]["value"])
        self.assertTrue(submission["professor_approval_required"]["value"])
        self.assertEqual(submission["professor_approval_required"]["status"], "CONFIRMED")
        self.assertFalse(submission["professor_signature_required"]["value"])
        self.assertTrue(submission["professor_signature_required"]["conditional"])
        self.assertTrue(submission["approval_evidence_allowed"]["value"])
        self.assertTrue(submission["separate_course_registration_required"]["value"])
        self.assertFalse(submission["email_submission_allowed"]["value"])
        self.assertTrue(submission["proxy_submission_allowed"]["value"])
        course = next(item for item in result["checklist_template"] if item["id"] == "course_registration")
        self.assertIs(course["required"], True)

    def test_required_documents_model_approval_evidence_as_one_of(self):
        text = (
            "수강신청원 양식 작성 및 담당교수 서명 후 학과사무실 제출\n"
            "담당교수 서명 어려운 경우 수강 허가를 확인할 수 있는 이메일 등 증빙자료 함께 지참"
        )
        documents = structure_notice(text, [])["submission"]["required_documents"]
        form = next(item for item in documents if item["name"] == "개별연구 수강신청원")
        approval = next(item for item in documents if item["name"] == "담당교수 승인 증빙")
        self.assertTrue(form["required"])
        self.assertEqual(form["requirement_type"], "REQUIRED")
        self.assertEqual(approval["requirement_type"], "ONE_OF")
        self.assertFalse(all(item["required"] for item in approval["alternatives"]))

    def test_approval_checklist_has_one_of_completion_rule(self):
        text = (
            "수강신청원 양식 작성 및 담당교수 서명 후 제출\n"
            "담당교수 서명 어려운 경우 수강 허가 이메일 등 증빙자료 제출"
        )
        step = next(
            item for item in structure_notice(text, [])["checklist_template"]
            if item["id"] == "professor_approval_evidence_ready"
        )
        self.assertEqual(step["completion_rule"]["type"], "ONE_OF")
        self.assertIn("SIGNED_APPLICATION_FORM", step["completion_rule"]["accepted_evidence"])
        ids = [item["id"] for item in structure_notice(text, [])["checklist_template"]]
        self.assertNotIn("professor_signature_ready", ids)
        self.assertEqual(ids.count("professor_approval_evidence_ready"), 1)

    def test_notice_based_checklist_items_keep_source_text(self):
        text = (
            "교과목 개설 목록 및 신청방법 확인하여 담당교수에게 연락\n"
            "수강신청원 양식 작성 및 담당교수 서명 후 학과사무실 제출(원흥관 423호)\n"
            "담당교수 서명 어려운 경우 수강 허가를 확인할 수 있는 이메일 등 증빙자료 함께 지참하여 제출\n"
            "반드시 수강신청기간(05/13~05/15)에 수강신청사이트에서 수강신청을 해야 함\n"
            "수강신청원은 방문제출을 원칙으로 함"
        )
        checklist = structure_notice(text, [])["checklist_template"]
        required_ids = {
            "professor_contact",
            "professor_approval_evidence_ready",
            "application_form_written",
            "visit_submission",
            "course_registration",
        }
        by_id = {item["id"]: item for item in checklist}
        for item_id in required_ids:
            self.assertEqual(by_id[item_id]["source_type"], "NOTICE_BODY")
            self.assertTrue(by_id[item_id]["source_text"])
        self.assertNotIn("professor_signature_ready", by_id)
        self.assertEqual(by_id["missing_fields_checked"]["source_type"], "SYSTEM_WORKFLOW")
        self.assertEqual(by_id["completion_checked"]["source_type"], "SYSTEM_WORKFLOW")

    def test_autofill_waits_for_selected_research_and_extracts_year_semester(self):
        detail = {"title": "2026학년도 여름학기 개별연구 신청 안내", "body_text": ""}
        result = autofill_candidates({"schedule": {"application_deadline": {"value": None}}}, detail, {"text": ""})
        self.assertEqual(result["academic_year"]["value"], "2026")
        self.assertEqual(result["semester"]["value"], "여름학기")
        for key in ("professor", "course_name", "course_code", "research_topic"):
            self.assertIsNone(result[key]["value"])
        self.assertFalse(result["email"]["required"])

    def test_snapshot_detects_research_changes(self):
        old = {
            "research_items": [
                {"교원명": "김교수", "학수강좌번호": "ABC1", "과목명": "개별연구", "연구내용": "old"}
            ]
        }
        new = {
            "research_items": [
                {"교원명": "김교수", "학수강좌번호": "ABC1", "과목명": "개별연구", "연구내용": "new"}
            ]
        }
        result = compare_snapshots(old, new)
        self.assertEqual(result["status"], "CHANGED")
        self.assertTrue(any(item["type"] == "연구내용 변경" for item in result["changes"]))

    def test_attachment_hash_change_is_single_replacement(self):
        old = {"attachments": [{"name": "old.xlsx", "file_seq": "1", "role": "INDIVIDUAL_RESEARCH_LIST", "sha256": "old"}]}
        new = {"attachments": [{"name": "new.xlsx", "file_seq": "2", "role": "INDIVIDUAL_RESEARCH_LIST", "sha256": "new"}]}
        changes = compare_snapshots(old, new)["changes"]
        attachment_changes = [item for item in changes if item["type"].startswith("첨부파일")]
        self.assertEqual([item["type"] for item in attachment_changes], ["첨부파일 교체"])
        self.assertEqual(attachment_changes[0]["role"], "INDIVIDUAL_RESEARCH_LIST")

    def test_role_added_to_legacy_attachment_is_not_add_delete(self):
        old = {"attachments": [{"name": "개별연구 목록.xlsx", "file_seq": "2303", "sha256": "abc", "role": None}]}
        new = {"attachments": [{"name": "개별연구 목록.xlsx", "file_seq": "2303", "sha256": "abc", "role": "INDIVIDUAL_RESEARCH_LIST"}]}
        self.assertEqual(compare_snapshots(old, new)["changes"], [])

    def test_download_failed_attachment_is_not_treated_as_replacement(self):
        old = {"attachments": [{"name": "old.xlsx", "role": "INDIVIDUAL_RESEARCH_LIST", "sha256": "old"}]}
        new = {"attachments": [{"name": "new.xlsx", "role": "INDIVIDUAL_RESEARCH_LIST", "sha256": None}]}
        changes = compare_snapshots(old, new)["changes"]
        self.assertEqual(changes[0]["type"], "첨부파일 비교 불가")
        self.assertEqual(changes[0]["reason"], "CURRENT_DOWNLOAD_FAILED")

    def test_research_without_course_code_uses_professor_course_order(self):
        old = {"research_items": [{"교원명": "김교수", "과목명": "개별연구", "순번": 1, "연구내용": "old"}]}
        new = {"research_items": [{"교원명": "김교수", "과목명": "개별연구", "순번": 1, "연구내용": "new"}]}
        changes = compare_snapshots(old, new)["changes"]
        self.assertEqual([item["type"] for item in changes], ["연구내용 변경"])

    def test_research_without_course_code_reordered_is_not_delete_add(self):
        old = {"research_items": [
            {"교원명": "김교수", "과목명": "A", "학수강좌번호": "", "연구내용": "a"},
            {"교원명": "박교수", "과목명": "B", "학수강좌번호": "", "연구내용": "b"},
        ]}
        new = {"research_items": list(reversed(old["research_items"]))}
        self.assertEqual(compare_snapshots(old, new)["changes"], [])

    def test_same_source_different_parser_version_is_reprocessed(self):
        old = {"source_fingerprint": "same", "parser_version": "0.1.0"}
        new = {"source_fingerprint": "same", "parser_version": "0.2.0"}
        result = compare_snapshots(old, new)
        self.assertEqual(result["status"], "REPROCESSED_BY_NEW_PARSER")
        self.assertEqual(result["changes"], [])

    def test_legacy_same_source_is_reprocessed(self):
        previous = {
            "notice": {"notice_id": "1390", "body_text": "same body"},
            "attachments": [{"name": "list.xlsx", "file_seq": "1", "extension": ".xlsx", "sha256": "abc"}],
            "research_items": [{"순번": 41, "수강 자격사항": "wrong"}],
        }
        current = {
            "source_fingerprint": "new",
            "parser_version": "0.2.1",
            "notice": {"notice_id": "1390", "body_text": "same body"},
            "attachments": [{"name": "list.xlsx", "file_seq": "1", "role": "INDIVIDUAL_RESEARCH_LIST", "extension": ".xlsx", "sha256": "abc"}],
            "research_items": [{"순번": 41, "수강 자격사항": None}],
        }
        result = compare_snapshots(previous, current)
        self.assertEqual(result["status"], "REPROCESSED_BY_NEW_PARSER")
        self.assertEqual(result["changes"], [])

    def test_real_source_changes_are_still_changed(self):
        previous = {
            "notice": {"notice_id": "1390", "body_text": "old body"},
            "attachments": [{"name": "list.xlsx", "file_seq": "1", "extension": ".xlsx", "sha256": "abc"}],
        }
        current = {
            "source_fingerprint": "new",
            "notice": {"notice_id": "1390", "body_text": "new body"},
            "attachments": [{"name": "list.xlsx", "file_seq": "1", "extension": ".xlsx", "sha256": "abc"}],
        }
        self.assertEqual(compare_snapshots(previous, current)["status"], "CHANGED")

    def test_title_search_finds_second_page_result(self):
        client = FakeClient({
            (None, 1): [{"id": 1, "title": "일반 안내", "date": "2026-01-01"}],
            (None, 2): [],
            ("개별연구", 1): [{"id": 2, "title": "일반 안내", "date": "2026-01-01"}],
            ("개별연구", 2): [{"id": 3, "title": "개별연구 안내", "date": "2026-02-01"}],
            ("개별연구", 3): [],
            ("개별 연구", 1): [],
        })
        selected, discovery = discover_latest_notice(client, max_pages=3)
        self.assertEqual(selected["id"], 3)
        self.assertEqual(discovery["strategy"], "paginated-title-search")
        self.assertEqual(discovery["searches"][0]["pages"], 2)

    def test_mid_page_structure_error_is_not_hidden(self):
        class ErrorClient:
            def list_notices(self, page=1, keyword=None):
                if page == 1:
                    return [{"id": 1, "title": "일반 안내", "date": "2026-01-01"}]
                raise StructureError("broken")

        with self.assertRaises(StructureError):
            discover_latest_notice(ErrorClient(), max_pages=3)

    def test_repeated_page_stops_pagination(self):
        client = FakeClient({
            (None, 1): [{"id": 1, "title": "개별연구 안내", "date": "2026-01-01"}],
            (None, 2): [{"id": 1, "title": "개별연구 안내", "date": "2026-01-01"}],
        })
        _, discovery = discover_latest_notice(client, max_pages=3)
        self.assertEqual(discovery["list_pages"], 1)

    def test_excel_and_notice_checklist_sources_are_not_empty(self):
        text = "수강신청원 양식 작성 및 담당교수 서명 후 제출"
        checklist = structure_notice(text, [])["checklist_template"]
        for item in checklist:
            if item["source_type"] in {"EXCEL", "NOTICE_BODY", "HWP"}:
                self.assertTrue(item["source_text"])
            if item["source_type"] == "SYSTEM_WORKFLOW":
                self.assertEqual(item["source_text"], "")

    def test_caution_headings_are_removed_but_real_cautions_remain(self):
        text = (
            "다) 유의사항\n"
            "1) 반드시 수강신청기간(05/13~05/15)에 수강신청사이트에서 수강신청을 해야 함\n"
            "2) 이메일 제출은 접수하지 않음\n"
            "4. 개별연구 관련 문의"
        )
        cautions = [item["value"] for item in structure_notice(text, [])["submission"]["cautions"]]
        self.assertNotIn("다) 유의사항", cautions)
        self.assertTrue(any("반드시" in item for item in cautions))
        self.assertTrue(any("이메일 제출" in item for item in cautions))

    def test_schedule_source_excludes_next_section_heading(self):
        text = "6.23.(화)~7.13.(월)\n개별연구참여, 과제수행, 결과보고 등\n3. 신청방법 및 기한"
        source = structure_notice(text, [])["schedule"]["research_period"]["source_text"]
        self.assertIn("6.23.(화)~7.13.(월)", source)
        self.assertNotIn("3. 신청방법 및 기한", source)

    def test_download_failure_is_recorded_in_errors(self):
        class BrokenDownloadClient:
            def get_notice(self, notice_id):
                return {
                    "id": notice_id,
                    "title": "2026학년도 여름학기 개별연구 신청 안내",
                    "author": "관리자",
                    "created_at": "2026-04-23",
                    "modified_at": None,
                    "views": 1,
                    "url": "https://example.test/1",
                    "body_text": "수강신청원 양식 작성 및 담당교수 서명 후 학과사무실 제출",
                    "body_html": "<p></p>",
                    "attachments": [{"name": "broken.xlsx", "url": "https://example.test/file"}],
                }

        failed = {
            "name": "broken.xlsx",
            "download_success": False,
            "parse_success": False,
            "parse_error": "OSError: boom",
            "analysis": None,
        }
        with patch("dongguk_notice.crawler.download_attachment", return_value=failed):
            result = crawl_notice(
                BrokenDownloadClient(),
                {"id": 1, "title": "개별연구", "date": "2026-04-23"},
                Path("data"),
                {},
                datetime.now(timezone.utc),
            )
        self.assertEqual(result["errors"][0]["attachment"], "broken.xlsx")

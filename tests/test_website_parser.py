import unittest

from dongguk_notice.website import NoticeClient


LIST_HTML = """
<ul class="bd_list">
  <li>
    <div class="tit"><a href="#none" onclick="goDetail(1390);">개별연구 안내</a></div>
    <ul class="etc">
      <li class="date">2026-04-23</li>
      <li class="name">AI융합 관리자</li>
      <li class="count">1495</li>
    </ul>
    <img alt="첨부파일">
  </li>
</ul>
"""

DETAIL_HTML = """
<div class="detail_wrap">
  <div class="tit"><h3>개별연구 안내</h3></div>
  <div class="files1"><a href="/cmmn/fileDown.do?fileSeq=2303">목록.xlsx</a></div>
  <div class="contents"><p>신청기간은 4월 30일까지입니다.</p></div>
</div>
"""


class StubClient(NoticeClient):
    def _get(self, path: str, **params) -> str:
        return DETAIL_HTML if "/detail/" in path else LIST_HTML


class WebsiteParserTests(unittest.TestCase):
    def test_list_parser_uses_go_detail_id(self):
        notice = StubClient().list_notices()[0]
        self.assertEqual(notice["id"], 1390)
        self.assertEqual(notice["views"], 1495)
        self.assertTrue(notice["has_attachment"])

    def test_detail_parser(self):
        notice = StubClient().get_notice(1390)
        self.assertEqual(notice["title"], "개별연구 안내")
        self.assertEqual(notice["attachments"][0]["name"], "목록.xlsx")
        self.assertIn("4월 30일", notice["body_text"])

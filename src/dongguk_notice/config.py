BASE_URL = "https://cs.dongguk.edu"
NOTICE_LIST_PATH = "/article/notice/list"
NOTICE_DETAIL_PATH = "/article/notice/detail/{notice_id}"

SELECTORS = {
    "list_items": "ul.bd_list > li",
    "list_title_link": ".tit a[onclick*='goDetail']",
    "list_date": ".etc .date",
    "list_author": ".etc .name",
    "list_views": ".etc .count",
    "detail_root": ".detail_wrap",
    "detail_title": ".tit h3",
    "detail_attachments": "a[href*='fileDown.do']",
    "detail_body": ".contents",
}

INDIVIDUAL_RESEARCH_KEYWORDS = ["개별연구", "개별 연구"]
SCHEMA_VERSION = "1.0.0"
PARSER_VERSION = "0.2.1"

EXCEL_LAYOUTS = {
    "개별연구 개설 신청": {
        "title_rows": [1, 2],
        "header_rows": [3, 4],
        "data_start_row": 5,
        "columns": [
            "순번",
            "개설학부",
            "교원명",
            "과목명",
            "기존/신설 여부",
            "학수강좌번호",
            "연구내용",
            "수강정원",
            "인터뷰 일정",
            "주당 연구시간",
            "수강 자격사항",
        ],
    },
    "※ 개별연구 개설현황(2016~2026)": {
        "title_rows": [1],
        "header_rows": [3],
        "data_start_row": 4,
        "columns": [
            "학년",
            "교과과정",
            "학수강좌번호",
            "교과목명",
            "담당교원",
            "학과/전공",
            "연도학기",
        ],
    },
}

HWP_FORM_PROFILES = {
    "individual-research-application": {
        "display_name": "개별연구 수강신청원",
        "filename_keywords": ["개별연구", "수강신청원"],
        "required_text": [
            "개별연구 수강신청원",
            "학년도/학기",
            "인적사항",
            "담당교수",
        ],
        "expected_table_count": 2,
        "required_signatures": [
            {"name": "담당교수 서명", "required": True},
        ],
    },
}

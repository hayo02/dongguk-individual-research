from __future__ import annotations

import re
import secrets
from email.message import Message
from urllib.parse import urlencode
from urllib.request import HTTPCookieProcessor, Request, build_opener
from urllib.parse import urljoin
from http.cookiejar import CookieJar

from lxml import html

from .config import BASE_URL, NOTICE_DETAIL_PATH, NOTICE_LIST_PATH, SELECTORS
from .errors import StructureError


class NoticeClient:
    def __init__(self, base_url: str = BASE_URL, timeout: float = 20.0):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.user_agent = (
            "Mozilla/5.0 (compatible; DonggukNoticeAnalyzer/0.1; "
            "+https://cs.dongguk.edu/)"
        )
        self.opener = build_opener(HTTPCookieProcessor(CookieJar()))

    def _request(
        self,
        path: str,
        params: dict | None = None,
        method: str = "GET",
        multipart: bool = False,
    ):
        url = urljoin(self.base_url + "/", path.lstrip("/"))
        data = None
        if params and method == "GET":
            url = f"{url}?{urlencode(params)}"
        content_type = "application/x-www-form-urlencoded"
        if params and multipart:
            boundary = f"----DonggukNotice{secrets.token_hex(12)}"
            chunks = []
            for name, value in params.items():
                chunks.extend([
                    f"--{boundary}\r\n".encode(),
                    f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode(),
                    str(value).encode("utf-8"),
                    b"\r\n",
                ])
            chunks.append(f"--{boundary}--\r\n".encode())
            data = b"".join(chunks)
            content_type = f"multipart/form-data; boundary={boundary}"
        elif params:
            data = urlencode(params).encode("utf-8")
        request = Request(
            url,
            data=data,
            method=method,
            headers={
                "User-Agent": self.user_agent,
                "Content-Type": content_type,
            },
        )
        return self.opener.open(request, timeout=self.timeout)

    def _get(self, path: str, **params) -> str:
        with self._request(path, params or None, "GET") as response:
            charset = response.headers.get_content_charset() or "utf-8"
            return response.read().decode(charset, errors="replace")

    def _post(self, path: str, **params) -> str:
        with self._request(path, params or None, "POST", multipart=True) as response:
            charset = response.headers.get_content_charset() or "utf-8"
            return response.read().decode(charset, errors="replace")

    def list_notices(self, page: int = 1, keyword: str | None = None) -> list[dict]:
        if keyword:
            source = self._post(
                NOTICE_LIST_PATH,
                pageIndex=str(page),
                searchCondition="TA.SUBJECT",
                searchKeyword=keyword,
                article_seq="",
                prt_seq="",
                flag="",
                searchOrderBy="",
            )
        else:
            source = self._get(NOTICE_LIST_PATH, pageIndex=page)
        document = html.fromstring(source)
        items = document.xpath("//ul[contains(concat(' ',normalize-space(@class),' '),' bd_list ')]/li")
        if not items:
            raise StructureError(f"공지 목록 선택자가 일치하지 않습니다: {SELECTORS['list_items']}")
        notices = {}
        for item in items:
            links = item.xpath(
                ".//div[contains(concat(' ',normalize-space(@class),' '),' tit ')]"
                "//a[contains(@onclick,'goDetail')]"
            )
            if not links:
                continue
            link = links[0]
            match = re.search(r"goDetail\((\d+)\)", link.get("onclick", ""))
            if not match:
                raise StructureError("공지 제목 링크에서 goDetail 번호를 찾지 못했습니다.")
            notice_id = int(match.group(1))
            notices[notice_id] = {
                "id": notice_id,
                "title": _node_text(link),
                "date": _xpath_text(item, ".//ul[contains(@class,'etc')]//li[contains(@class,'date')]"),
                "author": _xpath_text(item, ".//ul[contains(@class,'etc')]//li[contains(@class,'name')]"),
                "views": _integer(_xpath_text(
                    item, ".//ul[contains(@class,'etc')]//li[contains(@class,'count')]"
                )),
                "url": urljoin(self.base_url + "/", NOTICE_DETAIL_PATH.format(
                    notice_id=notice_id
                ).lstrip("/")),
                "has_attachment": bool(item.xpath(".//img[@alt='첨부파일']")),
                "search_keyword": keyword,
            }
        if not notices and keyword:
            return []
        if not notices:
            raise StructureError("공지 목록에서 상세 ID가 있는 항목을 찾지 못했습니다.")
        return list(notices.values())

    def get_notice(self, notice_id: int) -> dict:
        path = NOTICE_DETAIL_PATH.format(notice_id=notice_id)
        document = html.fromstring(self._get(path))
        roots = document.xpath(
            "//*[contains(concat(' ',normalize-space(@class),' '),' detail_wrap ')]"
        )
        if not roots:
            raise StructureError(f"상세 루트 선택자가 일치하지 않습니다: {SELECTORS['detail_root']}")
        root = roots[0]
        titles = root.xpath(".//div[contains(@class,'tit')]//h3")
        bodies = root.xpath(".//*[contains(concat(' ',normalize-space(@class),' '),' contents ')]")
        if not titles or not bodies:
            raise StructureError("공지 상세 제목 또는 본문 구조가 변경되었습니다.")
        title, body = titles[0], bodies[0]
        metadata = {}
        for item in root.xpath(".//div[contains(@class,'desc')]//ul[contains(@class,'info')]/li"):
            labels = item.xpath("./span")
            if not labels:
                continue
            label = _node_text(labels[0])
            value = " ".join(
                text.strip() for text in item.xpath("./text()") if text.strip()
            )
            metadata[label] = value
        attachments = [
            {
                "name": _node_text(link),
                "url": urljoin(self.base_url + "/", link.get("href", "").lstrip("/")),
            }
            for link in root.xpath(".//a[contains(@href,'fileDown.do')]")
        ]
        images = [
            {
                "url": urljoin(self.base_url + "/", image.get("src", "").lstrip("/")),
                "alt": image.get("alt"),
            }
            for image in body.xpath(".//img[@src]")
        ]
        return {
            "id": notice_id,
            "url": urljoin(self.base_url + "/", path.lstrip("/")),
            "title": _node_text(title),
            "author": metadata.get("작성자"),
            "created_at": metadata.get("작성일"),
            "modified_at": metadata.get("수정일"),
            "views": _integer(metadata.get("조회수")),
            "body_text": "\n".join(
                line.strip() for line in body.text_content().splitlines() if line.strip()
            ),
            "body_html": html.tostring(body, encoding="unicode"),
            "image_body": bool(images) and len(_node_text(body)) < 250,
            "images": images,
            "attachments": attachments,
            "metadata": metadata,
        }

    def download(self, url: str) -> tuple[bytes, Message, str]:
        request = Request(url, headers={"User-Agent": self.user_agent})
        with self.opener.open(request, timeout=self.timeout) as response:
            return response.read(), response.headers, response.geturl()


def _node_text(node) -> str:
    return " ".join(node.text_content().split())


def _xpath_text(root, xpath: str) -> str | None:
    nodes = root.xpath(xpath)
    return _node_text(nodes[0]) if nodes else None


def _integer(value: str | None) -> int | None:
    if not value:
        return None
    digits = re.sub(r"\D", "", value)
    return int(digits) if digits else None

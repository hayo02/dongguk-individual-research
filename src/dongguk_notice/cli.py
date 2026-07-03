from __future__ import annotations

import argparse

from .api import serve
from .crawler import crawl, print_crawl_summary


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(
        description="동국대학교 컴퓨터·AI학부 개별연구 신청 데이터 수집 크롤러"
    )
    sub = root.add_subparsers(dest="command", required=True)

    crawl_parser = sub.add_parser("crawl", help="최신 개별연구 공지와 첨부파일 수집")
    crawl_parser.add_argument("--data-dir", default="data")

    serve_parser = sub.add_parser("serve", help="개별연구 신청 API 개발 서버 실행")
    serve_parser.add_argument("--host", default="127.0.0.1")
    serve_parser.add_argument("--port", type=int, default=8000)
    serve_parser.add_argument("--db-path", default="data/app.db")
    return root


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.command == "crawl":
        result = crawl(args.data_dir)
        print_crawl_summary(result)
    elif args.command == "serve":
        serve(host=args.host, port=args.port, db_path=args.db_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

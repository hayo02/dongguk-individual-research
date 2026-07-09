from __future__ import annotations

import argparse

from .crawler import crawl, print_crawl_summary


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(
        description="동국대학교 컴퓨터·AI학부 개별연구 신청 데이터 수집 크롤러"
    )
    sub = root.add_subparsers(dest="command", required=True)

    crawl_parser = sub.add_parser("crawl", help="최신 개별연구 공지와 첨부파일 수집")
    crawl_parser.add_argument("--data-dir", default="data")
    crawl_parser.add_argument(
        "--category",
        default="individual-research",
        choices=["individual-research", "all", "affiliation-change"],
        help="호환성을 위한 카테고리 옵션입니다. 현재 크롤러는 개별연구 공지를 수집합니다.",
    )

    return root


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.command == "crawl":
        if args.category != "individual-research":
            print(f"카테고리 {args.category!r} 요청을 받았지만 현재 구현은 개별연구 공지 수집으로 처리합니다.")
        result = crawl(args.data_dir)
        print_crawl_summary(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

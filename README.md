# Dongguk Individual Research

동국대학교 컴퓨터·AI학부 개별연구 신청 프로세스 개선 프로젝트

공지, Excel, HWP에 분산된 신청 정보를 자동 수집·분석하고
연구 탐색부터 신청 절차 확인까지 한곳에서 관리하는 시스템

---

## Tech Stack

### Backend

* Python 3.12
* HTML Parsing
* XML Parsing
* Regular Expression
* JSON

### Document Parser

* Excel `.xlsx`
* HWP `.hwp`

### Test

* unittest
* unittest.mock

---

## 주요 기능

* 최신 개별연구 공지 자동 탐색
* 공지 목록 페이지네이션
* 공지 본문 및 첨부파일 추출
* Excel 연구 목록 분석
* HWP 신청원 구조 분석
* 신청 일정 및 제출 방식 추출
* 교수별 연구 조건 분석
* 연락·면담·전화 상담 방식 구분
* 필수 기술 및 프로그래밍 언어 추출
* 교수 서명 또는 승인 이메일 조건 처리
* 공지 및 첨부파일 변경 감지
* JSON 결과 저장

---

## 지원 대상

### Website

```text
https://cs.dongguk.edu/
```

### Keywords

```text
개별연구
개별 연구
```

### Files

```text
.xlsx
.hwp
```

---

## System Flow

```text
공지 목록 조회
    ↓
개별연구 공지 필터링
    ↓
최신 공지 선택
    ↓
공지 본문 분석
    ↓
첨부파일 다운로드
    ↓
Excel / HWP 분석
    ↓
신청 조건 구조화
    ↓
이전 결과와 변경 비교
    ↓
JSON 저장
```

---

## Project Structure

```text
dongguk-individual-research/
├── src/
│   └── dongguk_notice/
│       ├── cli.py
│       ├── config.py
│       ├── crawler.py
│       ├── downloads.py
│       ├── errors.py
│       ├── excel.py
│       ├── hwp.py
│       ├── snapshot.py
│       ├── structure.py
│       └── website.py
│
├── tests/
│   ├── test_crawler.py
│   ├── test_samples.py
│   └── test_website_parser.py
│
├── samples/
├── pyproject.toml
└── README.md
```

---

## Module

| File           | Role         |
| -------------- | ------------ |
| `crawler.py`   | 전체 크롤링 흐름    |
| `website.py`   | 공지 목록·상세 분석  |
| `downloads.py` | 첨부파일 다운로드    |
| `excel.py`     | 연구 목록 분석     |
| `hwp.py`       | 신청원 구조 분석    |
| `structure.py` | 일정·제출 규칙 구조화 |
| `snapshot.py`  | 변경사항 비교      |
| `cli.py`       | 실행 명령        |

---

## Output

```text
data/
├── attachments/
│   └── {notice_id}/
│
└── snapshots/
    └── individual-research/
        ├── latest.json
        └── {timestamp}.json
```

### JSON Data

* 공지 정보
* 신청 일정
* 제출 장소 및 방법
* 필수 제출서류
* 교수 승인 조건
* 교수별 연구 목록
* 연구내용 및 자격조건
* 연락·상담 방식
* 필요 기술
* HWP 입력 필드
* 신청 체크리스트
* 변경사항
* 데이터 경고

---

## Install

```bash
git clone https://github.com/hayo02/dongguk-individual-research.git
cd dongguk-individual-research

python -m pip install -e .
```

---

## Run

```bash
python -m dongguk_notice crawl
```

---

## Test

```bash
python -m unittest discover -s tests -v
```

```text
Ran 40 tests
OK
```

---

## Version

| Item   | Version |
| ------ | ------- |
| Python | 3.12    |
| Schema | 1.0.0   |
| Parser | 0.2.1   |

---

## Roadmap

* 연구 주제 검색 및 필터링
* 학생별 연구 선택
* 교수별 신청 조건 표시
* 신청 체크리스트
* 신청서 자동채움
* 신청 진행 상태 관리
* 공지 변경 알림
* Frontend
* Backend API
* Database

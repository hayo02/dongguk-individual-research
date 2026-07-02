# Dongguk Individual Research

동국대학교 컴퓨터·AI학부 개별연구 신청 프로세스 개선 프로젝트입니다.

현재 저장소는 **개별연구 공지·첨부파일 수집기**, **Excel/HWP 문서 분석기**, **신청 시스템 웹 와이어프레임**까지 포함합니다. 실제 학생 신청 서비스의 백엔드 API, 데이터베이스, 로그인, 제출 기능은 아직 구현 전이며, 지금 단계의 핵심은 신청 정보 구조화와 화면 흐름 검증입니다.

---

## Tech Stack

### Crawler / Parser

![Python](https://img.shields.io/badge/PYTHON-3776AB?style=for-the-badge&logo=python&logoColor=white)
![lxml](https://img.shields.io/badge/LXML-222222?style=for-the-badge)
![Regex](https://img.shields.io/badge/REGEX-4B5563?style=for-the-badge)
![JSON](https://img.shields.io/badge/JSON-111111?style=for-the-badge&logo=json&logoColor=white)

### Documents

![XLSX](https://img.shields.io/badge/XLSX-217346?style=for-the-badge&logo=microsoftexcel&logoColor=white)
![HWP](https://img.shields.io/badge/HWP-1F4E79?style=for-the-badge)
![ZIP/XML](https://img.shields.io/badge/ZIP%20%2F%20XML-6B7280?style=for-the-badge)

### Wireframe

![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white)
![CSS3](https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white)
![JavaScript](https://img.shields.io/badge/JAVASCRIPT-F7DF1E?style=for-the-badge&logo=javascript&logoColor=111111)

### Test / Dev

![unittest](https://img.shields.io/badge/UNITTEST-334155?style=for-the-badge)
![Git](https://img.shields.io/badge/GIT-F05032?style=for-the-badge&logo=git&logoColor=white)
![GitHub](https://img.shields.io/badge/GITHUB-181717?style=for-the-badge&logo=github&logoColor=white)

---

## 현재 개발 상태

### 구현 완료

- 동국대학교 컴퓨터·AI학부 공지 목록 조회 및 페이지네이션
- 개별연구 관련 최신 공지 탐색
- 공지 상세 본문, 작성일, 첨부파일 메타데이터 추출
- 첨부파일 다운로드 및 SHA-256 기반 변경 감지
- Excel `.xlsx` 개별연구 개설 과목 목록 분석
- HWP `.hwp` 신청원 텍스트, 표, 입력 필드, 서명란 분석
- 신청 일정, 제출 방법, 필수 서류, 교수 승인 조건 구조화
- 교수별 연구 주제, 수강 자격, 필요 기술, 면담/상담 방식 추출
- 이전 스냅샷과 최신 결과 비교
- JSON 스냅샷 저장
- 개별연구 신청 시스템 웹 와이어프레임 제작
- 학생 정상 신청, 보완 요청 재제출, 교직원 검토 흐름 보드 정리

### 검증 상태

```text
Ran 40 tests
OK
```

### 아직 구현 전

- 실제 웹 프론트엔드 앱
- 백엔드 API 서버
- 데이터베이스
- 로그인/권한 관리
- 학생 신청서 제출 기능
- 교직원 승인·보완 요청·반려 처리 기능
- 알림 및 배포 자동화

---

## 주요 기능

- 최신 개별연구 공지 자동 탐색
- 공지 본문 및 첨부파일 추출
- Excel 연구 목록 분석
- HWP 신청원 구조 분석
- 신청 일정 및 제출 방식 추출
- 교수별 연구 조건 분석
- 연락·면담·전화 상담 방식 구분
- 필수 기술 및 프로그래밍 언어 추출
- 교수 서명 또는 승인 이메일 조건 처리
- 공지 및 첨부파일 변경 감지
- 신청 체크리스트 생성
- JSON 결과 저장
- 화면 흐름 검증용 웹 와이어프레임 제공

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
    ↓
웹 신청 시스템 화면 흐름 검토
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
├── design-preview/
│   ├── wireframe-system.html
│   ├── styles/
│   │   └── wireframes.css
│   └── scripts/
│       └── wireframes.js
│
├── samples/
├── reports/
├── data/
├── pyproject.toml
└── README.md
```

---

## Module

| File | Role |
| --- | --- |
| `crawler.py` | 전체 크롤링 흐름, 최신 공지 선택, 결과 조립 |
| `website.py` | 공지 목록·상세 페이지 분석 |
| `downloads.py` | 첨부파일 다운로드, 파일명 정리, 해시 계산 |
| `excel.py` | 개별연구 개설 과목 Excel 분석 |
| `hwp.py` | 신청원 HWP 구조, 입력 필드, 서명란 분석 |
| `structure.py` | 일정·제출 규칙·체크리스트 구조화 |
| `snapshot.py` | 이전 결과와 최신 결과 변경사항 비교 |
| `cli.py` | CLI 실행 명령 |

---

## Design Preview

웹 와이어프레임은 다음 파일에서 확인할 수 있습니다.

```text
design-preview/wireframe-system.html
```

포함된 보드:

- User Flow
- Student Wireframes
- Staff Wireframes
- States and Modals
- Components

User Flow 보드는 학생 정상 신청, 보완 요청 재제출, 교직원 검토 흐름을 구역별로 나누어 표시합니다.

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

- 공지 정보
- 신청 일정
- 제출 장소 및 방법
- 필수 제출서류
- 교수 승인 조건
- 교수별 연구 목록
- 연구내용 및 자격조건
- 연락·상담 방식
- 필요 기술
- HWP 입력 필드
- 신청 체크리스트
- 변경사항
- 데이터 경고

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

또는 패키지 설치 후:

```bash
dongguk-notice crawl
```

---

## Test

```bash
python -m unittest discover -s tests -v
```

---

## Roadmap

- 실제 React 기반 프론트엔드 구현
- 신청서 자동채움 화면과 문서 생성 연결
- 학생 신청 진행 상태 관리
- 교직원 검토 API
- 신청 데이터 저장용 데이터베이스
- 공지 변경 알림
- 배포 및 자동 실행 환경 구성

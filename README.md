# 동국대학교 개별연구 신청 시스템

<p>
  동국대학교 컴퓨터·AI학부 개별연구 신청 공지와 개설 과목 정보를 기반으로<br/>
  학생 신청, 교직원 검토, 보완 요청 흐름을 웹 시스템으로 구현하는 프로젝트입니다.
</p>

> 현재는 로컬 개발 단계입니다. 로그인, 공지/신청 안내, 개설 과목 조회, 학생/교직원 대시보드 API와 React 화면을 연결해 구현하고 있습니다.

---

## Tech Stack

### Frontend

<p>
  <img src="https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=React&logoColor=61DAFB"/>
  <img src="https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=JavaScript&logoColor=111111"/>
  <img src="https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=HTML5&logoColor=white"/>
  <img src="https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=CSS3&logoColor=white"/>
  <img src="https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=Vite&logoColor=white"/>
  <img src="https://img.shields.io/badge/VS_Code-007ACC?style=for-the-badge&logo=VisualStudioCode&logoColor=white"/>
</p>

### Backend

<p>
  <img src="https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=OpenJDK&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=SpringBoot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Gradle_Groovy-02303A?style=for-the-badge&logo=Gradle&logoColor=white"/>
  <img src="https://img.shields.io/badge/IntelliJ_IDEA-000000?style=for-the-badge&logo=IntelliJIDEA&logoColor=white"/>
</p>

### Crawler

<p>
  <img src="https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=Python&logoColor=white"/>
  <img src="https://img.shields.io/badge/lxml-0B5F7A?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/XLSX-217346?style=for-the-badge&logo=MicrosoftExcel&logoColor=white"/>
  <img src="https://img.shields.io/badge/HWP-1F4E79?style=for-the-badge"/>
</p>

### Database

<p>
  <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=MySQL&logoColor=white"/>
</p>

### Dev

<p>
  <img src="https://img.shields.io/badge/Git-F05032?style=for-the-badge&logo=Git&logoColor=white"/>
  <img src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=GitHub&logoColor=white"/>
</p>

---

## 주요 기능

- **공통 인증**: 학생/교직원 로그인, 로그인 정보 조회, 로그아웃 및 토큰 무효화
- **공지 기반 신청 안내**: 크롤링 결과 JSON에서 현재 개별연구 공지, 일정, 제출 방식, 제출 자료를 조회
- **원문 공지 연결**: 공지 원문 URL을 백엔드 API로 조회한 뒤 프론트에서 새 탭으로 이동
- **학생 대시보드**: 로그인 사용자 정보와 신청 학기 정보를 표시
- **개설 과목 조회**: 개별연구 개설 과목 목록, 검색, 상세 모달 제공
- **교직원 대시보드**: 교직원 검토 화면으로 확장하기 위한 기본 API와 화면 구성
- **와이어프레임 보드**: 신청 흐름, 보완 요청/재제출 흐름, 교직원 검토 흐름을 구현 기준용 HTML로 정리

---

## 시스템 구조

### 전체 구조

```text
dongguk-individual-research/
├── backend/                 # Spring Boot API 서버
├── frontend/                # React + Vite 구현용 UI
├── src/dongguk_notice/      # 동국대 공지 크롤러/파서
├── data/                    # 크롤링 결과, 첨부파일, 스냅샷
├── design-preview/          # HTML 와이어프레임 및 User Flow
├── tests/                   # Python 크롤러/파서 테스트
└── README.md
```

### 서비스 구성

| 구분 | 기술 | 로컬 주소/포트 | 역할 |
| --- | --- | --- | --- |
| Frontend | React 18, JavaScript, Vite | `http://127.0.0.1:5173` | 사용자 화면, API 연결 |
| Backend | Spring Boot 3.3, Java 17, Gradle | `http://127.0.0.1:8000` | 인증, 공지, 대시보드, 과목 API |
| Database | MySQL | `127.0.0.1:3307` | 사용자, 공지, 과목, 신청, 무효화 토큰 저장 |
| Crawler | Python 3.10+, lxml | CLI | 공지/첨부파일 수집 및 `latest.json` 생성 |

### 데이터 흐름

```text
동국대학교 공지 페이지
        ↓
Python crawler/parser
        ↓
data/snapshots/individual-research/latest.json
        ↓
Spring Boot startup importer
        ↓
MySQL notices / courses
        ↓
REST API
        ↓
React frontend
```

---

## 코드 구조

### Backend

```text
backend/src/main/java/kr/ac/dongguk/individualresearch/
├── auth/        # 로그인, 사용자 조회, 로그아웃, 토큰 무효화
├── common/      # 공통 응답, 예외 처리, CORS, DB 초기 적재
├── course/      # 개설 과목 목록/검색/상세 조회
├── notice/      # 현재 공지, 원문 공지 URL 조회
├── staff/       # 교직원 대시보드
└── student/     # 학생 대시보드, 신청 상태
```

### Frontend

```text
frontend/
├── public/
│   └── course-overview.png
└── src/
    ├── main.jsx      # 화면 상태, API 호출, 페이지 렌더링
    └── styles.css    # 구현용 UI 스타일
```

### Crawler

```text
src/dongguk_notice/
├── cli.py        # CLI 진입점
├── crawler.py    # 공지 수집 흐름
├── website.py    # 공지 페이지 조회/파싱
├── downloads.py  # 첨부파일 다운로드
├── excel.py      # 개설 과목 Excel 분석
├── hwp.py        # 신청서 HWP 분석
└── snapshot.py   # latest.json 스냅샷 저장
```

---

## 구현된 API

### Auth

| Method | URL | 설명 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 로그인 후 access token 발급 |
| `GET` | `/api/auth/me` | 현재 로그인 사용자 정보 조회 |
| `POST` | `/api/auth/logout` | 현재 토큰 무효화 후 로그아웃 |

### Notice

| Method | URL | 설명 |
| --- | --- | --- |
| `GET` | `/api/notices/current` | 현재 개별연구 신청 안내 공지 조회 |
| `GET` | `/api/notices/{noticeId}/source` | 원문 공지 URL 조회 |

### Dashboard

| Method | URL | 설명 |
| --- | --- | --- |
| `GET` | `/api/student/dashboard` | 학생 대시보드 조회 |
| `GET` | `/api/staff/dashboard` | 교직원 대시보드 조회 |

### Course

| Method | URL | 설명 |
| --- | --- | --- |
| `GET` | `/api/courses` | 개설 과목 목록 조회 |
| `GET` | `/api/courses?keyword={keyword}` | 개설 과목 검색 |
| `GET` | `/api/courses/{courseId}` | 개설 과목 상세 조회 |

---

## 로컬 실행

### 1. MySQL 준비

로컬 MySQL 서버를 실행하고 프로젝트 DB를 생성합니다.

```sql
CREATE DATABASE individual_research
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

기본 개발 DB 포트는 `3307`입니다. 다른 포트를 사용한다면 `DB_URL`을 수정합니다.

```text
DB_URL=jdbc:mariadb://127.0.0.1:3307/individual_research?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&useUnicode=true&characterEncoding=utf8
DB_USERNAME=root
DB_PASSWORD=your_password
```

### 2. Backend 실행

```powershell
cd backend
.\gradlew.bat bootRun
```

Backend 기본 주소:

```text
http://127.0.0.1:8000
```

### 3. Frontend 실행

```powershell
cd frontend
npm install
npm run dev
```

Frontend 기본 주소:

```text
http://127.0.0.1:5173
```

### 4. Crawler 실행

```powershell
python -m pip install -e .
python -m dongguk_notice crawl --category individual-research
```

크롤링 결과는 아래 파일에 저장됩니다.

```text
data/snapshots/individual-research/latest.json
```

---

## 테스트 계정

| 역할 | ID | Password |
| --- | --- | --- |
| 학생 | `2026123456` | `1234` |
| 교직원 | `2025123456` | `5678` |

---

## Build / Test

### Backend

```powershell
cd backend
.\gradlew.bat compileJava
.\gradlew.bat test
```

### Frontend

```powershell
cd frontend
npm run build
```

### Crawler

```powershell
python -m unittest discover -s tests -v
```

---

## 현재 개발 상태

| 영역 | 상태 | 내용 |
| --- | --- | --- |
| 인증 | 진행 완료 | 로그인, 내 정보 조회, 로그아웃, 토큰 무효화 |
| 공지 | 진행 완료 | 현재 공지 조회, 원문 공지 URL 조회, 신청 안내 화면 연결 |
| 대시보드 | 진행 중 | 학생 대시보드 구현, 교직원 대시보드 기본 구조 |
| 개설 과목 | 진행 완료 | 목록, 검색, 상세 조회 API와 화면 연결 |
| 신청서 작성 | 예정 | 학생 신청서 입력, 파일 업로드, 최종 제출 |
| 교직원 검토 | 예정 | 신청 목록, 상세 검토, 승인/보완 요청/반려 |
| 파일 관리 | 예정 | 제출 서류 저장소, 다운로드, 검증 |

---

## 다음 구현 예정

- 학생 신청서 작성 화면
- 신청 과목 선택 후 신청서로 이어지는 흐름
- 제출 자료 업로드
- 신청서 확인 및 최종 제출
- 교직원 신청 목록/상세 검토
- 보완 요청 및 학생 재제출 흐름

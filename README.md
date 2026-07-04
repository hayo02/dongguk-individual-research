# Dongguk Individual Research

동국대학교 컴퓨터·AI학부 개별연구 신청 프로세스를 웹 시스템으로 개선하는 프로젝트입니다.

현재 저장소는 개별연구 공지·첨부파일 크롤러, Excel/HWP 분석기, 신청 시스템 와이어프레임, React 구현용 UI, Spring Boot 백엔드 API를 포함합니다.

---

## Tech Stack

### Frontend

![React](https://img.shields.io/badge/REACT-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![JavaScript](https://img.shields.io/badge/JAVASCRIPT-F7DF1E?style=for-the-badge&logo=javascript&logoColor=111111)
![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white)
![CSS3](https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white)
![Vite](https://img.shields.io/badge/VITE-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![VS Code](https://img.shields.io/badge/VS%20CODE-007ACC?style=for-the-badge&logo=visualstudiocode&logoColor=white)

### Backend

![Java](https://img.shields.io/badge/JAVA-007396?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/SPRING%20BOOT-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Gradle](https://img.shields.io/badge/GRADLE%20GROOVY-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![IntelliJ IDEA](https://img.shields.io/badge/INTELLIJ%20IDEA-000000?style=for-the-badge&logo=intellijidea&logoColor=white)

### Database

![MySQL](https://img.shields.io/badge/MYSQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)

### Crawler / Parser

![Python](https://img.shields.io/badge/PYTHON-3776AB?style=for-the-badge&logo=python&logoColor=white)
![JSON](https://img.shields.io/badge/JSON-111111?style=for-the-badge&logo=json&logoColor=white)
![XLSX](https://img.shields.io/badge/XLSX-217346?style=for-the-badge&logo=microsoftexcel&logoColor=white)
![HWP](https://img.shields.io/badge/HWP-1F4E79?style=for-the-badge)

### Dev

![Git](https://img.shields.io/badge/GIT-F05032?style=for-the-badge&logo=git&logoColor=white)
![GitHub](https://img.shields.io/badge/GITHUB-181717?style=for-the-badge&logo=github&logoColor=white)

---

## Current Status

### Crawler / Parser

- 동국대학교 컴퓨터·AI학부 공지 목록 조회 및 페이지네이션
- 개별연구 관련 최신 공지 탐색
- 공지 본문, 작성일, 원문 URL, 첨부파일 메타데이터 추출
- 첨부파일 다운로드 및 SHA-256 기반 변경 감지
- Excel 개별연구 개설 과목 목록 분석
- HWP 개별연구 수강신청원 구조, 입력 필드, 서명란 분석
- 신청 일정, 제출 방식, 제출자료, 교수 승인 조건 구조화
- 교수별 연구 주제, 수강 자격, 필요 기술, 면담/상담 방식 추출
- `data/snapshots/individual-research/latest.json` 저장

### Backend API

- Spring Boot / Gradle Groovy 프로젝트 구성
- MySQL 연동
- 서버 시작 시 `latest.json` 기반 공지와 개설 과목 데이터 적재
- 로그인, 로그인 정보 조회, 로그아웃 토큰 무효화
- 학생 대시보드 API
- 교직원 대시보드 API
- 현재 공지 조회 API
- 원문 공지 URL 조회 API
- 개설 과목 목록 조회 API
- 개설 과목 상세 조회 API

### Frontend UI

- React/Vite 기반 구현용 UI
- 로그인 화면
- 로그인 세션 복구
- 로그아웃 연결
- 학생 대시보드
- 신청 안내 화면
- 원문 공지 확인 버튼
- 교과목 개요 이미지 표시
- 개설 과목 목록 화면
- 개설 과목 검색
- 개설 과목 상세 모달
- 교직원 대시보드 기본 화면

### Design

- `design-preview/wireframe-system.html`
- 학생 정상 신청 흐름
- 학생 보완 요청 및 재제출 흐름
- 교직원 검토 흐름
- User Flow 보드
- 화면 상태 및 모달 정의

---

## Implemented APIs

### Auth

| Method | URL | Description |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 로그인 |
| `GET` | `/api/auth/me` | 로그인 사용자 정보 조회 |
| `POST` | `/api/auth/logout` | 로그아웃 및 토큰 무효화 |

### Notice

| Method | URL | Description |
| --- | --- | --- |
| `GET` | `/api/notices/current` | 현재 개별연구 신청 안내 공지 조회 |
| `GET` | `/api/notices/{noticeId}/source` | 원문 공지 URL 조회 |

### Dashboard

| Method | URL | Description |
| --- | --- | --- |
| `GET` | `/api/student/dashboard` | 학생 대시보드 조회 |
| `GET` | `/api/staff/dashboard` | 교직원 대시보드 조회 |

### Course

| Method | URL | Description |
| --- | --- | --- |
| `GET` | `/api/courses` | 개설 과목 목록 조회 |
| `GET` | `/api/courses?keyword={keyword}` | 개설 과목 검색 |
| `GET` | `/api/courses/{courseId}` | 개설 과목 상세 조회 |

---

## Data Flow

```text
동국대 공지 사이트
    ↓
Python crawler
    ↓
latest.json
    ↓
Spring Boot startup importer
    ↓
MySQL notices / courses
    ↓
Backend REST API
    ↓
React frontend
```

---

## Project Structure

```text
dongguk-individual-research/
├── backend/
│   ├── build.gradle
│   ├── settings.gradle
│   └── src/main/java/kr/ac/dongguk/individualresearch/
│       ├── auth/
│       ├── common/
│       ├── course/
│       ├── notice/
│       ├── staff/
│       └── student/
│
├── frontend/
│   ├── public/
│   │   └── course-overview.png
│   ├── src/
│   │   ├── main.jsx
│   │   └── styles.css
│   ├── package.json
│   └── vite.config.js
│
├── src/dongguk_notice/
│   ├── cli.py
│   ├── config.py
│   ├── crawler.py
│   ├── downloads.py
│   ├── excel.py
│   ├── hwp.py
│   ├── snapshot.py
│   ├── structure.py
│   └── website.py
│
├── data/
│   ├── attachments/
│   └── snapshots/individual-research/
│       ├── latest.json
│       └── {timestamp}.json
│
├── design-preview/
│   └── wireframe-system.html
│
├── tests/
├── pyproject.toml
└── README.md
```

---

## Run

### 1. MySQL

로컬 MySQL을 실행하고 프로젝트 DB를 준비합니다.

```sql
CREATE DATABASE individual_research CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

현재 로컬 기본 포트는 `3307` 기준입니다.

```text
DB_URL=jdbc:mariadb://127.0.0.1:3307/individual_research?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&useUnicode=true&characterEncoding=utf8
DB_USERNAME=root
DB_PASSWORD=your_password
```

### 2. Backend

```bash
cd backend
./gradlew bootRun
```

Windows PowerShell:

```powershell
cd backend
.\gradlew.bat bootRun
```

Backend URL:

```text
http://127.0.0.1:8000
```

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend URL:

```text
http://127.0.0.1:5173
```

### 4. Crawler

```bash
python -m pip install -e .
python -m dongguk_notice crawl
```

---

## Test / Build

### Python

```bash
python -m unittest discover -s tests -v
```

### Backend

```bash
cd backend
./gradlew test
./gradlew compileJava
```

### Frontend

```bash
cd frontend
npm run build
```

---

## Test Accounts

```text
학생: 2026123456 / 1234
교직원: 2025123456 / 5678
```

---

## Remaining Work

- 학생 신청서 작성 화면
- 신청서 확인 및 다운로드
- 서명본/증빙자료 업로드
- 최종 제출
- 내 신청 현황
- 보완 요청 수정 및 재제출
- 교직원 신청 목록
- 교직원 신청 상세 검토
- 승인, 보완 요청, 반려 처리
- 파일 저장소 연동
- 테스트 코드 보강

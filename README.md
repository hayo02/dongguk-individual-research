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
- **신청서 작성**: 학교 기본 정보 자동 입력, 연락처와 이메일 수정, 신청 내용 임시 저장
- **신청서 PDF 다운로드**: 신청자 정보, 신청 내용, 교수 서명란, 날짜란을 포함한 PDF 신청서 제공
- **메인 랜딩 화면**: Figma 디자인 레퍼런스를 반영한 첫 화면과 오른쪽 로그인 패널 제공
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
│   ├── course-overview.png
│   └── dongguk-logo.jpg
└── src/
    ├── main.jsx      # 화면 상태, API 호출, 페이지 렌더링
    └── styles.css    # 공통 UI 스타일, 랜딩/로그인 레이아웃
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

### 공통 규칙

- 인증이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더를 사용합니다.
- JSON API의 공통 응답 형식은 다음과 같습니다.

```json
{
  "success": true,
  "errorCode": null,
  "message": null,
  "data": {}
}
```

오류 응답에서는 `success`가 `false`이고 `errorCode`와 `message`가 제공됩니다.
파일 다운로드 API는 공통 JSON 형식 대신 파일 본문과 `Content-Disposition` 헤더를 반환합니다.

### Auth

| Method | URL | 권한 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | Public | 로그인 후 access token 발급 |
| `GET` | `/api/auth/me` | 로그인 | 현재 로그인 사용자 정보 조회 |
| `POST` | `/api/auth/logout` | 로그인 | 현재 토큰 무효화 후 로그아웃 |

### Notice

| Method | URL | 권한 | 설명 |
| --- | --- | --- | --- |
| `GET` | `/api/notices/current` | 로그인 | 현재 개별연구 신청 안내 공지 조회 |
| `GET` | `/api/notices/{noticeId}/source` | 로그인 | 원문 공지 URL 조회 |

### Dashboard

| Method | URL | 권한 | 설명 |
| --- | --- | --- | --- |
| `GET` | `/api/student/dashboard` | Student | 학생 대시보드 조회 |
| `GET` | `/api/staff/dashboard` | Staff | 교직원 대시보드 조회 |
| `GET` | `/api/staff/applications` | Staff | 작성 중을 제외한 전체 신청 목록, 검색·상태 필터·정렬·페이지 조회 |
| `GET` | `/api/staff/applications/{applicationId}` | Staff | 학생·신청 내용·제출 파일·처리 기록 상세 조회 |
| `GET` | `/api/staff/application-files/{fileId}/download` | Staff | 검토 대상 제출 파일 다운로드 |

### Course

| Method | URL | 권한 | 설명 |
| --- | --- | --- | --- |
| `GET` | `/api/courses` | 로그인 | 개설 과목 목록 조회 |
| `GET` | `/api/courses?keyword={keyword}` | 로그인 | 개설 과목 검색 |
| `GET` | `/api/courses/{courseId}` | 로그인 | 개설 과목 상세 조회 |

### Application

| Method | URL | 권한 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/applications` | Student | 신청 과목 선택 및 DRAFT 신청서 생성 |
| `GET` | `/api/applications/me/current` | Student | 내 최신 신청서 조회 |
| `PATCH` | `/api/applications/me/current` | Student | 내 최신 신청서의 기본 항목 임시 저장 |
| `DELETE` | `/api/applications/me/current` | Student | DRAFT 상태의 내 임시저장 신청서 삭제 |
| `GET` | `/api/applications/{applicationId}/autofill` | Student | 신청서 자동채움 데이터 조회 |
| `GET` | `/api/applications/{applicationId}/document.pdf` | Student/본인 | 제출 전 확인용 신청서 PDF 다운로드 |
| `POST` | `/api/applications/{applicationId}/validate` | Student/본인 | 제출 필수값 및 필수 파일 검증 |
| `POST` | `/api/applications/{applicationId}/submit` | Student/본인 | 서버 검증 후 신청서를 최종 제출 |
| `GET` | `/api/applications/{applicationId}/document.hwp` | Student | 기존 HWP 자동채움 파일 다운로드(레거시) |
| `GET` | `/api/applications/{applicationId}/interview.png` | Student | 기존 면담자료 PNG 다운로드(레거시) |

검증 응답:

```json
{
  "success": true,
  "errorCode": null,
  "message": null,
  "data": {
    "valid": false,
    "missingFields": ["applicationReason", "researchPurpose"],
    "missingFiles": []
  }
}
```

최종 제출 응답:

```json
{
  "success": true,
  "errorCode": null,
  "message": null,
  "data": {
    "id": 1,
    "status": "SUBMITTED",
    "submittedAt": "2026-07-09T13:25:31"
  }
}
```

최종 제출은 `DRAFT` 또는 `REVISION_REQUESTED` 상태에서만 가능하며, 서버가 검증을
다시 수행합니다. `SUBMITTED` 이후에는 기존 신청서 PATCH가 거부됩니다. 현재 필수 파일
`SIGNED_APPLICATION`이 없으면 `missingFiles`에 해당 값이 반환됩니다.

### 단계별 신청 화면

기존 단일 `내 신청 현황` 화면을 다음 단계로 분리했습니다. React의 현재 화면 상태와
History API를 사용하며 URL은 `/applications/{applicationId}/{step}` 형태입니다.

| 단계 | URL suffix | 역할 |
| --- | --- | --- |
| S2 | `/applicant` | 신청자 정보 확인, 연락처 및 이메일 수정 |
| S3 | `/content` | 신청사유·연구목적·상세 작성 |
| S4 | `/documents` | 교수 서명란과 날짜란이 포함된 신청서 PDF 다운로드 |
| S5 | `/signature-guide` | 교수 서명본 준비 안내 |
| S6 | `/files` | 제출 파일 업로드·교체·삭제·다운로드 |
| S7 | `/review` | 제출 전 입력값·서명본 검증 및 최종 제출 |
| S8 | `/complete` | 제출 상태와 제출일시 확인 |
| S9 | `/applications/{id}` | 상태·진행 단계·파일 수를 보여주는 요약 화면 |

### Application Files

| Method | URL | 권한 | 설명 |
| --- | --- | --- | --- |
| `GET` | `/api/applications/{applicationId}/files` | Student/본인 | 제출 파일 목록 |
| `POST` | `/api/applications/{applicationId}/files` | Student/본인 | 제출 파일 업로드 |
| `PUT` | `/api/application-files/{fileId}` | Student/본인 | 기존 제출 파일 교체 |
| `DELETE` | `/api/application-files/{fileId}` | Student/본인 | 제출 파일 삭제 |
| `GET` | `/api/application-files/{fileId}/download` | Student/본인 | 제출 파일 다운로드 |

업로드는 `multipart/form-data`로 `documentType`과 `file`을 전송합니다. 문서 종류는
`SIGNED_APPLICATION`, `ADDITIONAL_FILE`이며 PDF/JPG/JPEG/PNG, 최대 10MB만 허용합니다.
`SIGNED_APPLICATION`은 신청서당 1개만 등록할 수 있고 이후에는 교체 API를 사용합니다.
실제 파일은 `storage/uploads/applications/{applicationId}`에 UUID 파일명으로 저장하며,
DB에는 원본명, 저장명, 경로, MIME type, 크기, SHA-256 해시만 저장합니다.

`SUBMITTED`, `APPROVED`, `REJECTED` 상태에서는 업로드·교체·삭제가 거부되지만 다운로드는
가능합니다.

### Application Draft

상세 작성 내용과 문서 생성을 위한 초안 API입니다. 초안 상태는 `DRAFT`, `READY`,
`GENERATED` 중 하나입니다.

| Method | URL | 권한 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/drafts` | Student | 신청 초안 생성 |
| `GET` | `/api/drafts/{draftId}` | Student/본인 | 신청 초안 조회 |
| `PATCH` | `/api/drafts/{draftId}` | Student/본인 | 신청 초안 전체 항목 임시저장 |

`POST`, `PATCH` JSON 필드:

```json
{
  "noticeId": 1,
  "researchTopicId": 10,
  "semester": "2026학년도 여름학기",
  "studentName": "홍길동",
  "studentNumber": "2026123456",
  "department": "컴퓨터·AI학부",
  "grade": "3",
  "phone": "010-1234-5678",
  "email": "student@dongguk.edu",
  "professorName": "김교수",
  "researchTitle": "생성형 AI 연구",
  "researchContent": "연구 내용",
  "courseName": "개별연구",
  "applicationReason": "신청 사유",
  "researchPurpose": "연구 목적",
  "relatedExperience": "관련 경험",
  "researchPlan": "연구 수행 계획",
  "interviewQuestions": "면담 질문",
  "status": "DRAFT"
}
```

### HWPX Template

모든 API는 Staff 권한이 필요합니다.

| Method | URL | 설명 |
| --- | --- | --- |
| `POST` | `/api/staff/document-templates` | HWPX 검사 후 템플릿 등록 |
| `GET` | `/api/staff/document-templates` | 등록 템플릿 목록 조회 |
| `PATCH` | `/api/staff/document-templates/{templateId}/activate` | 해당 공지의 활성 템플릿 변경 |
| `DELETE` | `/api/staff/document-templates/{templateId}` | 템플릿 비활성화 |
| `GET` | `/api/staff/document-templates/{templateId}/download` | 원본 HWPX 템플릿 다운로드 |

등록 API는 `multipart/form-data`를 사용합니다.

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `file` | Y | `.hwpx` 파일 |
| `templateName` | Y | 템플릿 표시 이름 |
| `noticeId` | N | 연결할 공지 ID |
| `semester` | N | 적용 학기 |
| `templateVersion` | N | 생략 시 공지별 자동 증가 |
| `active` | N | 즉시 활성화 여부, 기본값 `false` |

검증 항목은 확장자, ZIP 구조, `Contents/section*.xml`, 필수 placeholder,
SHA-256 중복 여부입니다.

### Generated Document

| Method | URL | 권한 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/drafts/{draftId}/documents/application-hwpx` | Student/본인 | 활성 템플릿으로 수강신청원 HWPX 생성 |
| `POST` | `/api/drafts/{draftId}/documents/interview-pdf` | Student/본인 | 인터뷰 자료 PDF 생성 |
| `GET` | `/api/documents/{documentId}/download` | Student/본인 | 생성 파일 다운로드 |

문서 생성 응답 예시:

```json
{
  "success": true,
  "errorCode": null,
  "message": null,
  "data": {
    "documentId": 12,
    "documentType": "INTERVIEW_PDF",
    "filename": "개별연구_인터뷰자료_홍길동.pdf",
    "downloadUrl": "/api/documents/12/download"
  }
}
```

### 주요 오류 코드

| HTTP | errorCode | 상황 |
| --- | --- | --- |
| `400` | `UNSUPPORTED_FILE_TYPE` | HWPX 이외의 파일 업로드 |
| `400` | `INVALID_HWPX` | ZIP 또는 HWPX 내부 구조 오류 |
| `400` | `REQUIRED_PLACEHOLDER_MISSING` | 필수 placeholder 누락 |
| `400` | `HWPX_GENERATION_FAILED` | HWPX 치환·생성 실패 |
| `400` | `PDF_FONT_NOT_FOUND` | 설정한 한글 폰트 파일 없음 |
| `400` | `PDF_GENERATION_FAILED` | PDF 렌더링 실패 |
| `400` | `FILE_STORAGE_FAILED` | 파일 저장 또는 읽기 실패 |
| `400` | `APPLICATION_INVALID` | 제출 필수값 누락 또는 제출 불가능 상태 |
| `400` | `APPLICATION_ALREADY_SUBMITTED` | 이미 제출된 신청서 재제출 |
| `400` | `APPLICATION_PDF_GENERATION_FAILED` | 확인용 신청서 PDF 생성 실패 |
| `400` | `APPLICATION_SUBMIT_FAILED` | 상태 변경 중 제출 실패 |
| `400` | `APPLICATION_INVALID_STATUS` | 제출 완료 상태에서 파일 변경 |
| `400` | `APPLICATION_FILE_INVALID_TYPE` | 허용하지 않는 파일 형식 |
| `400` | `APPLICATION_FILE_TOO_LARGE` | 10MB 초과 파일 |
| `400` | `APPLICATION_FILE_DUPLICATE` | 교수 서명본 중복 업로드 |
| `400` | `APPLICATION_FILE_UPLOAD_FAILED` | 제출 파일 저장 실패 |
| `400` | `APPLICATION_FILE_DELETE_FAILED` | 제출 파일 삭제 실패 |
| `400` | `APPLICATION_FILE_DOWNLOAD_FAILED` | 제출 파일 읽기 실패 |
| `403` | `FORBIDDEN` | 다른 사용자의 초안·문서 접근 |
| `403` | `APPLICATION_FORBIDDEN` | 다른 학생의 신청서 접근 |
| `404` | `DRAFT_NOT_FOUND` | 초안 없음 |
| `404` | `APPLICATION_NOT_FOUND` | 신청서 없음 |
| `404` | `APPLICATION_FILE_NOT_FOUND` | 제출 파일 또는 저장 파일 없음 |
| `404` | `TEMPLATE_NOT_FOUND` | 템플릿 또는 활성 템플릿 없음 |
| `404` | `DOCUMENT_NOT_FOUND` | 생성 파일 또는 메타데이터 없음 |
| `409` | `DUPLICATE_TEMPLATE` | 동일 SHA-256 템플릿 존재 |

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
DB_URL=jdbc:mysql://127.0.0.1:3307/individual_research?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&useUnicode=true&characterEncoding=utf8
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
| 대시보드 | 진행 중 | 학생 대시보드와 교직원 대시보드 기본 구조 |
| 개설 과목 | 진행 완료 | 목록, 검색, 상세 조회 API와 화면 연결 |
| 신청서 작성 | 진행 완료 | 상세 항목 입력, 연락처/이메일 수정, debounce 자동저장, PDF 생성 |
| 교직원 검토 | 진행 중 | 신청 목록·검색·필터·상세 검토 구현, 승인/보완 요청/반려 예정 |
| HWPX 템플릿 | 진행 완료 | 업로드, 구조·placeholder 검증, 활성화, 다운로드 |
| 생성 문서 | 진행 완료 | 신청서 PDF 생성, 메타데이터 저장, 권한 기반 다운로드 |

---

## 다음 구현 예정

- 제출 자료 업로드
- 공식 신청서 PDF 양식 적용
- 교수 서명본 업로드 및 제출 검증 연결
- 보완 요청 및 학생 재제출 흐름
- 교직원 승인/보완 요청/반려 처리

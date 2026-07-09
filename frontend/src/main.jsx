import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const API_BASE_URL = "http://127.0.0.1:8000";
const ACCESS_TOKEN_KEY = "individualResearchAccessToken";

function App() {
  const [user, setUser] = useState(null);
  const [accessToken, setAccessToken] = useState("");
  const [isCheckingSession, setIsCheckingSession] = useState(true);

  useEffect(() => {
    const savedToken = localStorage.getItem(ACCESS_TOKEN_KEY);
    if (!savedToken) {
      setIsCheckingSession(false);
      return;
    }

    let isMounted = true;

    async function restoreSession() {
      try {
        const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
          headers: { Authorization: `Bearer ${savedToken}` },
        });
        const body = await response.json();

        if (!response.ok || !body.success || !body.data) {
          throw new Error(body.message ?? "로그인이 필요합니다.");
        }

        if (isMounted) {
          setUser(body.data.user ?? body.data);
          setAccessToken(savedToken);
        }
      } catch {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
      } finally {
        if (isMounted) {
          setIsCheckingSession(false);
        }
      }
    }

    void restoreSession();

    return () => {
      isMounted = false;
    };
  }, []);

  async function handleLogout() {
    if (accessToken) {
      try {
        await fetch(`${API_BASE_URL}/api/auth/logout`, {
          method: "POST",
          headers: { Authorization: `Bearer ${accessToken}` },
        });
      } catch {
        // The local session is cleared even if the network request fails.
      }
    }

    localStorage.removeItem(ACCESS_TOKEN_KEY);
    setUser(null);
    setAccessToken("");
  }

  if (isCheckingSession) {
    return (
      <main className="login-shell">
        <section className="login-card">
          <p className="muted">로그인 상태를 확인하는 중입니다.</p>
        </section>
      </main>
    );
  }

  if (user) {
    return (
      <Dashboard
        user={user}
        accessToken={accessToken}
        onLogout={handleLogout}
      />
    );
  }

  return (
    <LoginPage
      onLogin={(nextUser, token) => {
        localStorage.setItem(ACCESS_TOKEN_KEY, token);
        setUser(nextUser);
        setAccessToken(token);
      }}
    />
  );
}

function LoginPage({ onLogin }) {
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();
    setErrorMessage("");
    setIsSubmitting(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ loginId, password }),
      });
      const body = await response.json();

      if (!response.ok || !body.success || !body.data) {
        setErrorMessage(body.message ?? "로그인에 실패했습니다.");
        return;
      }

      onLogin(body.data.user, body.data.accessToken);
    } catch {
      setErrorMessage("백엔드 API 서버에 연결할 수 없습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="login-shell">
      <section className="login-card" aria-labelledby="login-title">
        <div className="brand-row">
          <div className="logo-box">LOGO</div>
          <div>
            <p className="eyebrow">개별연구 신청</p>
            <h1 id="login-title">개별연구 신청 시스템</h1>
          </div>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <label>
            아이디
            <input
              value={loginId}
              onChange={(event) => setLoginId(event.target.value)}
              placeholder="학번 또는 교직원 번호"
              autoComplete="username"
            />
          </label>
          <label>
            비밀번호
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              placeholder="비밀번호"
              autoComplete="current-password"
            />
          </label>

          {errorMessage ? <p className="error-message">{errorMessage}</p> : null}

          <button className="primary-button" disabled={isSubmitting}>
            {isSubmitting ? "로그인 중" : "로그인"}
          </button>
        </form>

        <div className="login-help">
          <strong>테스트 계정</strong>
          <span>학생 2026123456 / 1234</span>
          <span>교직원 2025123456 / 5678</span>
        </div>

        <p className="notice">
          본 시스템은 동국대학교 포털과 연동되지 않습니다. 포털 비밀번호가 아닌
          웹서비스 전용 비밀번호를 입력해 주세요.
        </p>
      </section>
    </main>
  );
}

function Dashboard({ user, accessToken, onLogout }) {
  const isStudent = user.role === "STUDENT";
  const [activePage, setActivePage] = useState(() =>
    window.location.pathname.startsWith("/applications") ? "application" : "dashboard",
  );
  const [dashboard, setDashboard] = useState(null);
  const [dashboardError, setDashboardError] = useState("");
  const [isLoadingDashboard, setIsLoadingDashboard] = useState(true);

  useEffect(() => {
    let isMounted = true;

    async function loadDashboard() {
      setDashboardError("");
      setIsLoadingDashboard(true);

      try {
        const path = isStudent ? "/api/student/dashboard" : "/api/staff/dashboard";
        const response = await fetch(`${API_BASE_URL}${path}`, {
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        const body = await response.json();

        if (!response.ok || !body.success || !body.data) {
          throw new Error(body.message ?? "대시보드 정보를 불러오지 못했습니다.");
        }

        if (isMounted) {
          setDashboard(body.data);
        }
      } catch (error) {
        if (isMounted) {
          setDashboardError(
            error instanceof Error
              ? error.message
              : "대시보드 정보를 불러오지 못했습니다.",
          );
        }
      } finally {
        if (isMounted) {
          setIsLoadingDashboard(false);
        }
      }
    }

    void loadDashboard();

    return () => {
      isMounted = false;
    };
  }, [accessToken, isStudent]);

  const currentNotice = dashboard?.currentNotice;
  const metrics = dashboard?.statusCards ?? dashboard?.metrics ?? [];
  const displayUser = isStudent ? `${user.name} 학생` : `${user.name} 교직원`;

  return (
    <main className="app-shell">
      <header className="app-header">
        <div className="brand-row compact">
          <div className="logo-box small">LOGO</div>
          <div>
            <strong>
              {isStudent ? "개별연구 신청 시스템" : "개별연구 관리 시스템"}
            </strong>
            <span>{user.department}</span>
          </div>
        </div>
        <nav>
          {isStudent ? (
            <>
              <button
                className={activePage === "dashboard" ? "active" : ""}
                onClick={() => setActivePage("dashboard")}
              >
                대시보드
              </button>
              <button
                className={activePage === "notice" ? "active" : ""}
                onClick={() => setActivePage("notice")}
              >
                신청 안내
              </button>
              <button
                className={activePage === "courses" ? "active" : ""}
                onClick={() => setActivePage("courses")}
              >
                개설 과목
              </button>
              <button
                className={activePage === "application" ? "active" : ""}
                onClick={() => setActivePage("application")}
              >
                내 신청 현황
              </button>
            </>
          ) : (
            <>
              <button
                className={activePage === "dashboard" ? "active" : ""}
                onClick={() => setActivePage("dashboard")}
              >
                대시보드
              </button>
              <button>신청 목록</button>
              <button>크롤링 결과</button>
              <button
                className={activePage === "templates" ? "active" : ""}
                onClick={() => setActivePage("templates")}
              >
                HWPX 템플릿
              </button>
            </>
          )}
        </nav>
        <div className="user-actions">
          <span>{displayUser}</span>
          <button onClick={onLogout}>로그아웃</button>
        </div>
      </header>

      {activePage === "templates" && !isStudent ? (
        <TemplateManager accessToken={accessToken} />
      ) : activePage === "notice" && isStudent ? (
        <NoticeGuide
          accessToken={accessToken}
          onBack={() => setActivePage("dashboard")}
          onOpenCourses={() => setActivePage("courses")}
        />
      ) : activePage === "courses" && isStudent ? (
        <CourseList
          accessToken={accessToken}
          onApplicationCreated={() => setActivePage("application")}
        />
      ) : activePage === "application" && isStudent ? (
        <CurrentApplication accessToken={accessToken} onOpenCourses={() => setActivePage("courses")} />
      ) : (
      <section className="dashboard-page">
        <div className="screen-title">
          <span>{dashboard?.pageCode ?? (isStudent ? "S1" : "A1")}</span>
          <div>
            <p className="eyebrow">{isStudent ? "학생 홈" : "교직원 홈"}</p>
            <h1>{dashboard?.screenName ?? (isStudent ? "학생 대시보드" : "교직원 대시보드")}</h1>
          </div>
        </div>

        {isLoadingDashboard ? (
          <section className="status-panel">
            <p className="muted">대시보드 정보를 불러오는 중입니다.</p>
          </section>
        ) : dashboardError ? (
          <section className="status-panel">
            <p className="error-message">{dashboardError}</p>
          </section>
        ) : (
          <>
            <section className="metric-row">
              {metrics.map((metric) => (
                <article className={`metric-card ${metric.tone ?? "neutral"}`} key={metric.label}>
                  <span>{metric.label}</span>
                  <strong>{metric.value}</strong>
                  {metric.description ? <p>{metric.description}</p> : null}
                </article>
              ))}
            </section>

            <section className="dashboard-grid">
              <article className="status-panel notice-overview">
                <div className="notice-heading">
                  <div>
                    <p className="eyebrow">
                      {isStudent ? "현재 신청 상태" : "처리가 필요한 신청"}
                    </p>
                    <h2>
                      {isStudent
                        ? dashboard?.applicationStatusLabel ?? "신청 상태 확인"
                        : "제출 완료 신청부터 검토합니다"}
                    </h2>
                  </div>
                  <span className="status-badge">
                    {isStudent ? dashboard?.applicationStatusLabel : "작성 중 제외"}
                  </span>
                </div>

                {isStudent ? (
                  <StudentDashboardMain dashboard={dashboard} currentNotice={currentNotice} />
                ) : (
                  <StaffDashboardMain dashboard={dashboard} />
                )}
              </article>

              <article className="status-panel secondary notice-detail">
                {isStudent ? (
                  <StudentDashboardSide dashboard={dashboard} currentNotice={currentNotice} />
                ) : (
                  <StaffDashboardSide dashboard={dashboard} />
                )}
              </article>
            </section>

          </>
        )}
      </section>
      )}
    </main>
  );
}

function NoticeGuide({ accessToken, onBack, onOpenCourses }) {
  const [notice, setNotice] = useState(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  async function handleOpenSourceNotice() {
    if (!notice) {
      return;
    }

    try {
      const response = await fetch(`${API_BASE_URL}/api/notices/${notice.id}/source`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const body = await response.json();

      if (!response.ok || !body.success || !body.data?.originalUrl) {
        throw new Error("원문 공지를 열 수 없습니다.");
      }

      window.open(body.data.originalUrl, "_blank", "noopener,noreferrer");
    } catch {
      setErrorMessage("원문 공지를 열 수 없습니다.");
    }
  }

  useEffect(() => {
    let isMounted = true;

    async function loadNotice() {
      setErrorMessage("");
      setIsLoading(true);

      try {
        const response = await fetch(`${API_BASE_URL}/api/notices/current`, {
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        const body = await response.json();

        if (!response.ok || !body.success || !body.data) {
          throw new Error(body.message ?? "신청 안내 공지를 불러오지 못했습니다.");
        }

        if (isMounted) {
          setNotice(body.data);
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "신청 안내 공지를 불러오지 못했습니다.",
          );
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    void loadNotice();

    return () => {
      isMounted = false;
    };
  }, [accessToken]);

  const noticeLines = (notice?.noticeNotes ?? "")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
  const requiredDocuments = displayRequiredDocuments(notice?.requiredDocuments);

  return (
    <section className="dashboard-page">
      <div className="screen-title">
        <span>S2</span>
        <div>
          <p className="eyebrow">학생 신청</p>
          <h1>개별연구 신청 안내</h1>
        </div>
      </div>

      {isLoading ? (
        <section className="status-panel">
          <p className="muted">신청 안내 공지를 불러오는 중입니다.</p>
        </section>
      ) : errorMessage ? (
        <section className="status-panel">
          <p className="error-message">{errorMessage}</p>
        </section>
      ) : (
        <>
          <section className="notice-guide-stack">
            <article className="status-panel current-notice-panel">
              <button className="source-pill" onClick={handleOpenSourceNotice}>
                원문 공지 확인
              </button>
              <p className="eyebrow">현재 공지</p>
              <h2>{notice.title}</h2>
              <dl>
                <dt>학기</dt>
                <dd>{notice.semester}</dd>
                <dt>신청 기간</dt>
                <dd>
                  {notice.startDate} ~ {notice.endDate}
                </dd>
                <dt>게시일</dt>
                <dd>{notice.publishedAt}</dd>
              </dl>
              <div className="actions-row">
                <button onClick={onBack}>대시보드로 돌아가기</button>
                <button className="primary-button" onClick={onOpenCourses}>개설 과목 확인하기</button>
              </div>
            </article>

            <div className="notice-guide-layout">
              <div className="notice-guide-main">
                <article className="status-panel notice-detail">
                  <h2>교과목 개요</h2>
                  <img
                    className="course-overview-image"
                    src="/course-overview.png"
                    alt="개별연구 교과목 개요 표"
                  />
                </article>

                <article className="status-panel notice-detail">
                  <h2>제출자료</h2>
                  <ul>
                    {requiredDocuments.map((document) => (
                      <li key={document}>{document}</li>
                    ))}
                  </ul>
                </article>

                <article className="status-panel notice-detail">
                  <h2>주의사항</h2>
                  {noticeLines.length ? (
                    <ul className="plain-list">
                      {noticeLines.map((line) => (
                        <li key={line}>{line}</li>
                      ))}
                    </ul>
                  ) : (
                    <p className="muted">등록된 주의사항이 없습니다.</p>
                  )}
                </article>
              </div>

              <aside className="status-panel notice-detail notice-guide-side">
                <section className="side-section">
                  <h2>일정</h2>
                  <InfoMap values={notice.scheduleInfo} />
                </section>
                <section className="side-section">
                  <h2>제출 방식</h2>
                  <InfoMap values={notice.submissionInfo} />
                </section>
              </aside>
            </div>
          </section>
        </>
      )}
    </section>
  );
}

function CourseList({ accessToken, onApplicationCreated }) {
  const [keyword, setKeyword] = useState("");
  const [submittedKeyword, setSubmittedKeyword] = useState("");
  const [courses, setCourses] = useState([]);
  const [selectedCourse, setSelectedCourse] = useState(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [applyingCourseId, setApplyingCourseId] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadCourses() {
      setErrorMessage("");
      setIsLoading(true);

      try {
        const params = submittedKeyword ? `?keyword=${encodeURIComponent(submittedKeyword)}` : "";
        const response = await fetch(`${API_BASE_URL}/api/courses${params}`, {
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        const body = await response.json();

        if (!response.ok || !body.success || !body.data) {
          throw new Error(body.message ?? "개설 과목 목록을 불러오지 못했습니다.");
        }

        if (isMounted) {
          setCourses(body.data.courses ?? []);
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "개설 과목 목록을 불러오지 못했습니다.",
          );
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    void loadCourses();

    return () => {
      isMounted = false;
    };
  }, [accessToken, submittedKeyword]);

  async function handleSearch(event) {
    event.preventDefault();
    setSubmittedKeyword(keyword.trim());
  }

  async function handleOpenDetail(courseId) {
    setErrorMessage("");
    setSuccessMessage("");
    setIsLoadingDetail(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const body = await response.json();

      if (!response.ok || !body.success || !body.data) {
        throw new Error(body.message ?? "개설 과목 상세 정보를 불러오지 못했습니다.");
      }

      setSelectedCourse(body.data);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "개설 과목 상세 정보를 불러오지 못했습니다.",
      );
    } finally {
      setIsLoadingDetail(false);
    }
  }

  async function handleCreateApplication(courseId) {
    setErrorMessage("");
    setSuccessMessage("");
    setApplyingCourseId(courseId);

    try {
      const response = await fetch(`${API_BASE_URL}/api/applications`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({ courseId }),
      });
      const body = await response.json();

      if (!response.ok || !body.success || !body.data) {
        throw new Error(body.message ?? "신청서를 생성하지 못했습니다.");
      }

      setSelectedCourse(null);
      setSuccessMessage("신청 과목을 선택하고 신청서를 생성했습니다.");
      onApplicationCreated?.();
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "신청서를 생성하지 못했습니다.",
      );
    } finally {
      setApplyingCourseId(null);
    }
  }

  return (
    <section className="dashboard-page">
      <div className="screen-title">
        <span>S3</span>
        <div>
          <p className="eyebrow">학생 신청</p>
          <h1>개설 과목 목록</h1>
        </div>
      </div>

      <section className="status-panel">
        <form className="course-filter" onSubmit={handleSearch}>
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="과목명, 담당 교수, 학수강좌번호, 자격사항 검색"
          />
          <button className="primary-button">검색</button>
        </form>
      </section>

      {errorMessage ? <p className="error-message">{errorMessage}</p> : null}
      {successMessage ? <p className="success-message">{successMessage}</p> : null}

      <section className="status-panel course-table-panel">
        {isLoading ? (
          <p className="muted">개설 과목 목록을 불러오는 중입니다.</p>
        ) : courses.length ? (
          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th>과목명</th>
                  <th>담당 교수</th>
                  <th>학수강좌번호</th>
                  <th>연구 내용 요약</th>
                  <th>동작</th>
                </tr>
              </thead>
              <tbody>
                {courses.map((course) => (
                  <tr key={course.id}>
                    <td>{course.courseName}</td>
                    <td>{course.professorName}</td>
                    <td>{course.courseCode || "-"}</td>
                    <td>{course.researchDescription || "-"}</td>
                    <td>
                      <div className="table-actions">
                        <button onClick={() => handleOpenDetail(course.id)} disabled={isLoadingDetail}>
                          상세보기
                        </button>
                        <button
                          className="primary-button"
                          onClick={() => handleCreateApplication(course.id)}
                          disabled={applyingCourseId === course.id}
                        >
                          {applyingCourseId === course.id ? "신청 중" : "이 과목 신청하기"}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="muted">표시할 개설 과목이 없습니다.</p>
        )}
      </section>

      {selectedCourse ? (
        <CourseDetailModal
          course={selectedCourse}
          isApplying={applyingCourseId === selectedCourse.id}
          onApply={() => handleCreateApplication(selectedCourse.id)}
          onClose={() => setSelectedCourse(null)}
        />
      ) : null}
    </section>
  );
}

function CourseDetailModal({ course, isApplying, onApply, onClose }) {
  return (
    <div className="modal-backdrop" role="presentation">
      <section className="course-modal" role="dialog" aria-modal="true" aria-labelledby="course-detail-title">
        <div className="modal-heading">
          <div>
            <p className="eyebrow">개설 과목 상세</p>
            <h2 id="course-detail-title">{course.courseName}</h2>
          </div>
          <button onClick={onClose}>닫기</button>
        </div>

        <dl className="detail-list">
          <dt>담당 교수</dt>
          <dd>{course.professorName}</dd>
          <dt>학수강좌번호</dt>
          <dd>{course.courseCode || "-"}</dd>
          <dt>개설 학부</dt>
          <dd>{course.department || "-"}</dd>
          <dt>기존/신설</dt>
          <dd>{course.courseType || "-"}</dd>
          <dt>연구 내용</dt>
          <dd>{course.researchDescription || "-"}</dd>
          <dt>수강 정원</dt>
          <dd>{course.capacity ? `${course.capacity}명` : "-"}</dd>
          <dt>인터뷰 일정</dt>
          <dd>{course.interviewSchedule || "-"}</dd>
          <dt>주당 연구 시간</dt>
          <dd>{course.weeklyHours ? `${course.weeklyHours}시간` : "-"}</dd>
          <dt>수강 자격</dt>
          <dd>{course.qualification || "-"}</dd>
          <dt>교수 이메일</dt>
          <dd>{course.professorEmails?.length ? course.professorEmails.join(", ") : "-"}</dd>
          <dt>필수 기술</dt>
          <dd>{course.requiredSkills?.length ? course.requiredSkills.join(", ") : "-"}</dd>
          <dt>언급 기술</dt>
          <dd>{course.mentionedTechnologies?.length ? course.mentionedTechnologies.join(", ") : "-"}</dd>
        </dl>

        {course.closedToAdditionalApplications ? (
          <p className="error-message">추가 신청이 제한된 과목입니다. 신청 전 담당 교수 안내를 확인해 주세요.</p>
        ) : null}

        <div className="actions-row">
          <button className="primary-button" onClick={onApply} disabled={isApplying}>
            {isApplying ? "신청 중" : "신청 과목으로 선택"}
          </button>
        </div>
      </section>
    </div>
  );
}

function TemplateManager({ accessToken }) {
  const [templates, setTemplates] = useState([]);
  const [file, setFile] = useState(null);
  const [templateName, setTemplateName] = useState("개별연구 수강신청원");
  const [noticeId, setNoticeId] = useState("");
  const [semester, setSemester] = useState("");
  const [result, setResult] = useState("");

  async function load() {
    const response = await fetch(`${API_BASE_URL}/api/staff/document-templates`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    const body = await response.json();
    if (response.ok && body.success) setTemplates(body.data);
  }
  useEffect(() => { void load(); }, [accessToken]);

  async function upload(event) {
    event.preventDefault();
    if (!file) return setResult("HWPX 파일을 선택해 주세요.");
    const form = new FormData();
    form.append("file", file); form.append("templateName", templateName);
    if (noticeId) form.append("noticeId", noticeId);
    if (semester) form.append("semester", semester);
    form.append("active", "true");
    const response = await fetch(`${API_BASE_URL}/api/staff/document-templates`, {
      method: "POST", headers: { Authorization: `Bearer ${accessToken}` }, body: form,
    });
    const body = await response.json();
    setResult(response.ok && body.success
      ? `검사 통과 · 발견한 필수 placeholder ${body.data.foundPlaceholders.length}개`
      : body.message ?? "템플릿 등록에 실패했습니다.");
    if (response.ok) void load();
  }

  async function change(id, method, suffix = "") {
    await fetch(`${API_BASE_URL}/api/staff/document-templates/${id}${suffix}`, {
      method, headers: { Authorization: `Bearer ${accessToken}` },
    });
    void load();
  }

  return (
    <section className="dashboard-page">
      <div className="screen-title"><span>A2</span><div><p className="eyebrow">교직원 관리</p><h1>HWPX 템플릿</h1></div></div>
      <form className="status-panel application-form" onSubmit={upload}>
        <h2>템플릿 등록</h2>
        <label>템플릿명<input value={templateName} onChange={(e) => setTemplateName(e.target.value)} /></label>
        <label>학기<input value={semester} onChange={(e) => setSemester(e.target.value)} placeholder="2026학년도 여름학기" /></label>
        <label>공지 ID<input value={noticeId} onChange={(e) => setNoticeId(e.target.value)} inputMode="numeric" /></label>
        <label>HWPX 파일<input type="file" accept=".hwpx" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></label>
        <button className="primary-button">검사 후 활성 템플릿으로 등록</button>
        {result ? <p className="notice-note">{result}</p> : null}
      </form>
      <section className="status-panel">
        <h2>등록된 템플릿</h2>
        <div className="table-wrap"><table><thead><tr><th>이름</th><th>학기</th><th>버전</th><th>상태</th><th>관리</th></tr></thead>
          <tbody>{templates.map((item) => <tr key={item.id}><td>{item.templateName}</td><td>{item.semester || "-"}</td><td>v{item.templateVersion}</td><td>{item.active ? "활성" : "비활성"}</td>
            <td className="actions-row">
              {!item.active ? <button onClick={() => change(item.id, "PATCH", "/activate")}>활성화</button> : null}
              <a href={`${API_BASE_URL}/api/staff/document-templates/${item.id}/download`} onClick={(event) => { event.preventDefault(); fetch(event.currentTarget.href, {headers:{Authorization:`Bearer ${accessToken}`}}).then(r=>r.blob()).then(blob=>{const u=URL.createObjectURL(blob);const a=document.createElement("a");a.href=u;a.download=item.originalFilename;a.click();URL.revokeObjectURL(u);}); }}>다운로드</a>
              <button className="danger-button" onClick={() => change(item.id, "DELETE")}>비활성화</button>
            </td></tr>)}</tbody>
        </table></div>
      </section>
    </section>
  );
}

function CurrentApplication({ accessToken, onOpenCourses }) {
  const [application, setApplication] = useState(null);
  const [contact, setContact] = useState("");
  const [applicationReason, setApplicationReason] = useState("");
  const [researchPurpose, setResearchPurpose] = useState("");
  const [relatedExperience, setRelatedExperience] = useState("");
  const [researchPlan, setResearchPlan] = useState("");
  const [interviewQuestions, setInterviewQuestions] = useState("");
  const [draftId, setDraftId] = useState(null);
  const [saveState, setSaveState] = useState("저장됨");
  const [lastSavedAt, setLastSavedAt] = useState("");
  const [generatedFiles, setGeneratedFiles] = useState({});
  const draftCreating = useRef(false);
  const [autofill, setAutofill] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [isValidating, setIsValidating] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationResult, setValidationResult] = useState(null);
  const [flowStep, setFlowStep] = useState("summary");
  const [applicationFiles, setApplicationFiles] = useState([]);
  const [selectedUpload, setSelectedUpload] = useState(null);
  const [isFileLoading, setIsFileLoading] = useState(false);
  const [isFileAction, setIsFileAction] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  useEffect(() => {
    let isMounted = true;

    async function loadApplication() {
      setErrorMessage("");
      setSuccessMessage("");
      setIsLoading(true);

      try {
        const response = await fetch(`${API_BASE_URL}/api/applications/me/current`, {
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        const body = await response.json();

        if (response.status === 404) {
          if (isMounted) {
            setApplication(null);
            setAutofill(null);
          }
          return;
        }
        if (!response.ok || !body.success || !body.data) {
          throw new Error(body.message ?? "신청서 정보를 불러오지 못했습니다.");
        }

        if (isMounted) {
          setApplication(body.data);
          setContact(body.data.student?.contact ?? body.data.student?.phone ?? "");
          setApplicationReason(body.data.applicationReason ?? "");
          setResearchPurpose(body.data.researchPurpose ?? "");
        }

        const autofillResponse = await fetch(`${API_BASE_URL}/api/applications/${body.data.id}/autofill`, {
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        const autofillBody = await autofillResponse.json();
        if (!autofillResponse.ok || !autofillBody.success || !autofillBody.data) {
          throw new Error(autofillBody.message ?? "자동채움 데이터를 불러오지 못했습니다.");
        }
        if (isMounted) {
          setAutofill(autofillBody.data);
        }
        await loadApplicationFiles(body.data.id, isMounted);
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "신청서 정보를 불러오지 못했습니다.",
          );
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    void loadApplication();

    return () => {
      isMounted = false;
    };
  }, [accessToken]);

  useEffect(() => {
    if (!application) return;
    const segment = window.location.pathname.split("/").filter(Boolean).at(-1);
    const known = new Set(["applicant", "content", "documents", "signature-guide", "files", "review", "complete"]);
    if (known.has(segment)) setFlowStep(segment);
  }, [application?.id]);

  async function loadApplicationFiles(applicationId = application?.id, apply = true) {
    if (!applicationId) return;
    setIsFileLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/applications/${applicationId}/files`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const body = await response.json();
      if (!response.ok || !body.success) throw new Error(body.message ?? "제출 파일 목록을 불러오지 못했습니다.");
      if (apply) setApplicationFiles(body.data?.items ?? []);
    } catch (error) {
      if (apply) setErrorMessage(error instanceof Error ? error.message : "제출 파일 목록을 불러오지 못했습니다.");
    } finally {
      if (apply) setIsFileLoading(false);
    }
  }

  function goStep(step) {
    setFlowStep(step);
    if (application) {
      const suffix = step === "summary" ? "" : `/${step}`;
      window.history.pushState({}, "", `/applications/${application.id}${suffix}`);
    }
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function draftPayload() {
    return {
      noticeId: application?.course?.noticeId ?? null,
      researchTopicId: application?.course?.id ?? null,
      semester: application?.course?.semester ?? "",
      studentName: application?.student?.name ?? "",
      studentNumber: application?.student?.loginId ?? "",
      department: application?.student?.department ?? "",
      grade: "",
      phone: contact,
      email: application?.student?.email ?? "",
      professorName: application?.course?.professorName ?? "",
      researchTitle: application?.course?.courseName ?? "",
      researchContent: "",
      courseName: application?.course?.courseName ?? "개별연구",
      applicationReason,
      researchPurpose,
      relatedExperience,
      researchPlan,
      interviewQuestions,
      status: "DRAFT",
    };
  }

  useEffect(() => {
    if (!application || draftId || draftCreating.current) return;
    draftCreating.current = true;
    fetch(`${API_BASE_URL}/api/drafts`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify(draftPayload()),
    }).then((response) => response.json().then((body) => ({ response, body })))
      .then(({ response, body }) => {
        if (!response.ok || !body.success) throw new Error(body.message);
        setDraftId(body.data.id);
        setLastSavedAt(new Date(body.data.updatedAt).toLocaleTimeString());
      })
      .catch(() => setSaveState("저장 실패"));
  }, [application, accessToken]);

  useEffect(() => {
    if (!draftId || !application) return;
    setSaveState("저장 중...");
    const timer = window.setTimeout(async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/drafts/${draftId}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
          body: JSON.stringify(draftPayload()),
        });
        const body = await response.json();
        if (!response.ok || !body.success) throw new Error(body.message);
        setSaveState("저장됨");
        setLastSavedAt(new Date(body.data.updatedAt).toLocaleTimeString());
      } catch {
        setSaveState("저장 실패");
      }
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [draftId, contact, applicationReason, researchPurpose, relatedExperience, researchPlan, interviewQuestions]);

  async function handleGenerate(kind) {
    if (!draftId) return;
    setIsDownloading(true);
    setErrorMessage("");
    try {
      const response = await fetch(`${API_BASE_URL}/api/drafts/${draftId}/documents/${kind}`, {
        method: "POST", headers: { Authorization: `Bearer ${accessToken}` },
      });
      const body = await response.json();
      if (!response.ok || !body.success) throw new Error(body.message ?? "문서를 생성하지 못했습니다.");
      setGeneratedFiles((value) => ({ ...value, [kind]: body.data }));
      setSuccessMessage(kind === "application-hwpx" ? "수강신청원이 생성되었습니다." : "인터뷰 자료가 생성되었습니다.");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "문서를 생성하지 못했습니다.");
    } finally {
      setIsDownloading(false);
    }
  }

  async function downloadGenerated(fileInfo) {
    const response = await fetch(`${API_BASE_URL}${fileInfo.downloadUrl}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!response.ok) return setErrorMessage("생성 파일을 다운로드하지 못했습니다.");
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url; anchor.download = fileInfo.filename; anchor.click();
    URL.revokeObjectURL(url);
  }

  async function handleSave(event) {
    event?.preventDefault();
    setErrorMessage("");
    setSuccessMessage("");
    setIsSaving(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/applications/me/current`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({ contact, applicationReason, researchPurpose }),
      });
      const body = await response.json();

      if (!response.ok || !body.success || !body.data) {
        throw new Error(body.message ?? "신청서를 저장하지 못했습니다.");
      }

      setApplication(body.data);
      setContact(body.data.student?.contact ?? body.data.student?.phone ?? "");
      setApplicationReason(body.data.applicationReason ?? "");
      setResearchPurpose(body.data.researchPurpose ?? "");
      setSuccessMessage("신청서를 임시 저장했습니다.");
      return true;
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "신청서를 저장하지 못했습니다.",
      );
      return false;
    } finally {
      setIsSaving(false);
    }
  }

  async function saveAndGo(step) {
    if (await handleSave()) goStep(step);
  }

  async function uploadApplicationFile() {
    if (!application || !selectedUpload) return;
    setIsFileAction(true);
    setErrorMessage("");
    const form = new FormData();
    form.append("documentType", "SIGNED_APPLICATION");
    form.append("file", selectedUpload);
    try {
      const response = await fetch(`${API_BASE_URL}/api/applications/${application.id}/files`, {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
        body: form,
      });
      const body = await response.json();
      if (!response.ok || !body.success) throw new Error(body.message ?? "파일을 업로드하지 못했습니다.");
      setSelectedUpload(null);
      setSuccessMessage("교수 서명본을 업로드했습니다.");
      await loadApplicationFiles(application.id);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "파일을 업로드하지 못했습니다.");
    } finally {
      setIsFileAction(false);
    }
  }

  async function replaceApplicationFile(fileId, file) {
    if (!file) return;
    setIsFileAction(true);
    const form = new FormData();
    form.append("file", file);
    try {
      const response = await fetch(`${API_BASE_URL}/api/application-files/${fileId}`, {
        method: "PUT",
        headers: { Authorization: `Bearer ${accessToken}` },
        body: form,
      });
      const body = await response.json();
      if (!response.ok || !body.success) throw new Error(body.message ?? "파일을 교체하지 못했습니다.");
      setSuccessMessage("제출 파일을 교체했습니다.");
      await loadApplicationFiles(application.id);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "파일을 교체하지 못했습니다.");
    } finally {
      setIsFileAction(false);
    }
  }

  async function deleteApplicationFile(fileId) {
    if (!window.confirm("이 제출 파일을 삭제할까요?")) return;
    setIsFileAction(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/application-files/${fileId}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const body = await response.json();
      if (!response.ok || !body.success) throw new Error(body.message ?? "파일을 삭제하지 못했습니다.");
      setSuccessMessage("제출 파일을 삭제했습니다.");
      await loadApplicationFiles(application.id);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "파일을 삭제하지 못했습니다.");
    } finally {
      setIsFileAction(false);
    }
  }

  async function downloadApplicationFile(file) {
    setIsFileAction(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/application-files/${file.id}/download`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.message ?? "파일을 다운로드하지 못했습니다.");
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = file.fileName;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "파일을 다운로드하지 못했습니다.");
    } finally {
      setIsFileAction(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm("임시저장된 신청서를 삭제할까요? 삭제 후 다른 과목을 다시 선택할 수 있습니다.")) {
      return;
    }

    setErrorMessage("");
    setSuccessMessage("");
    setIsDeleting(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/applications/me/current`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const body = await response.json();

      if (!response.ok || !body.success) {
        throw new Error(body.message ?? "신청서를 삭제하지 못했습니다.");
      }

      setApplication(null);
      setContact("");
      setApplicationReason("");
      setResearchPurpose("");
      setSuccessMessage("임시저장 신청서를 삭제했습니다.");
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "신청서를 삭제하지 못했습니다.",
      );
    } finally {
      setIsDeleting(false);
    }
  }

  async function handleDownloadHwp() {
    if (!application) {
      return;
    }

    setErrorMessage("");
    setSuccessMessage("");
    setIsDownloading(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/applications/${application.id}/document.hwp`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.message ?? "신청서 HWP를 다운로드하지 못했습니다.");
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `individual-research-application-${application.id}.hwp`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "신청서 HWP를 다운로드하지 못했습니다.",
      );
    } finally {
      setIsDownloading(false);
    }
  }

  async function handleDownloadInterviewImage() {
    if (!application) {
      return;
    }

    setErrorMessage("");
    setSuccessMessage("");
    setIsDownloading(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/applications/${application.id}/interview.png`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.message ?? "면담자료 이미지를 다운로드하지 못했습니다.");
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `individual-research-interview-${application.id}.png`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "면담자료 이미지를 다운로드하지 못했습니다.",
      );
    } finally {
      setIsDownloading(false);
    }
  }

  async function handleDownloadPdf() {
    if (!application) return;
    setErrorMessage("");
    setIsDownloading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/applications/${application.id}/document.pdf`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.message ?? "신청서 PDF를 다운로드하지 못했습니다.");
      }
      const blob = await response.blob();
      const disposition = response.headers.get("Content-Disposition") ?? "";
      const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
      const filename = encoded ? decodeURIComponent(encoded) : "개별연구_신청서.pdf";
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "신청서 PDF를 다운로드하지 못했습니다.");
    } finally {
      setIsDownloading(false);
    }
  }

  async function validateApplication() {
    if (!application) return null;
    setErrorMessage("");
    setIsValidating(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/applications/${application.id}/validate`, {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const body = await response.json();
      if (!response.ok || !body.success) throw new Error(body.message ?? "제출 전 검증에 실패했습니다.");
      setValidationResult(body.data);
      setSuccessMessage(body.data.valid ? "제출 가능한 상태입니다." : "");
      return body.data;
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "제출 전 검증에 실패했습니다.");
      return null;
    } finally {
      setIsValidating(false);
    }
  }

  async function handleSubmitApplication() {
    if (!application) return;
    const result = await validateApplication();
    if (!result?.valid) return;
    if (!window.confirm("제출 후에는 수정할 수 없습니다. 최종 제출하시겠습니까?")) return;
    setIsSubmitting(true);
    setErrorMessage("");
    try {
      const response = await fetch(`${API_BASE_URL}/api/applications/${application.id}/submit`, {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const body = await response.json();
      if (!response.ok || !body.success) throw new Error(body.message ?? "신청서를 제출하지 못했습니다.");
      setApplication((current) => ({
        ...current,
        status: body.data.status,
        submittedAt: body.data.submittedAt,
      }));
      setSuccessMessage("신청서 제출이 완료되었습니다.");
      goStep("complete");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "신청서를 제출하지 못했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const canEdit = application?.status === "DRAFT" || application?.status === "REVISION_REQUESTED";

  return (
    <section className="dashboard-page">
      <div className="screen-title">
        <span>{flowStep === "summary" ? "S9" : `S${["applicant", "content", "documents", "signature-guide", "files", "review", "complete"].indexOf(flowStep) + 2}`}</span>
        <div>
          <p className="eyebrow">학생 신청</p>
          <h1>{flowStep === "summary" ? "내 신청 현황" : "개별연구 신청"}</h1>
        </div>
      </div>

      {isLoading ? (
        <section className="status-panel">
          <p className="muted">신청서 정보를 불러오는 중입니다.</p>
        </section>
      ) : errorMessage ? (
        <p className="error-message">{errorMessage}</p>
      ) : !application ? (
        <section className="status-panel">
          <h2>아직 생성된 신청서가 없습니다.</h2>
          <p className="muted">개설 과목 목록에서 신청할 과목을 먼저 선택해 주세요.</p>
          <button className="primary-button" onClick={onOpenCourses}>개설 과목 보러가기</button>
        </section>
      ) : (
        <>
          {successMessage ? <p className="success-message">{successMessage}</p> : null}
          <ApplicationStepBar current={flowStep} submitted={application.status === "SUBMITTED"} />

          {flowStep === "summary" ? (
            <section className="status-panel application-summary">
              <h2>신청 상태 요약</h2>
              <dl>
                <dt>신청 ID</dt><dd>{application.id}</dd>
                <dt>학년도/학기</dt><dd>{application.course?.semester ?? "-"}</dd>
                <dt>담당교수</dt><dd>{application.course?.professorName ?? "-"}</dd>
                <dt>연구주제/교과목명</dt><dd>{application.course?.courseName ?? "-"}</dd>
                <dt>현재 상태</dt><dd>{application.status}</dd>
                <dt>현재 진행 단계</dt><dd>{application.status === "SUBMITTED" ? "S8 제출 완료" : applicationFiles.length ? "S7 제출 전 검증" : "S2 신청자 정보"}</dd>
                <dt>제출일시</dt><dd>{application.submittedAt ? new Date(application.submittedAt).toLocaleString() : "-"}</dd>
                <dt>업로드 파일</dt><dd>{isFileLoading ? "확인 중" : `${applicationFiles.length}개`}</dd>
              </dl>
              <div className="actions-row">
                <button className="primary-button" onClick={() => goStep(application.status === "SUBMITTED" ? "complete" : applicationFiles.length ? "review" : "applicant")}>
                  {application.status === "SUBMITTED" ? "제출 완료 상세보기" : "이어 작성하기"}
                </button>
                {canEdit ? <button className="danger-button" onClick={handleDelete} disabled={isDeleting}>{isDeleting ? "삭제 중" : "임시저장 삭제"}</button> : null}
              </div>
            </section>
          ) : null}

          {flowStep === "applicant" ? (
            <section className="status-panel application-form">
              <h2>S2. 신청자 정보 입력</h2>
              <div className="readonly-grid">
                <label>성명<input value={application.student?.name ?? ""} disabled /></label>
                <label>학번<input value={application.student?.loginId ?? ""} disabled /></label>
                <label>소속<input value={application.student?.department ?? ""} disabled /></label>
                <label>이메일<input value={application.student?.email ?? ""} disabled /></label>
                <label>학년도/학기<input value={application.course?.semester ?? ""} disabled /></label>
                <label>학년<input value="-" disabled /></label>
              </div>
              <label>연락처<input value={contact} onChange={(event) => setContact(event.target.value)} disabled={!canEdit || isSaving} /></label>
              <StepActions onPrevious={() => goStep("summary")} onNext={() => saveAndGo("content")} nextDisabled={!canEdit || !contact.trim() || isSaving} />
            </section>
          ) : null}

          {flowStep === "content" ? (
            <section className="status-panel application-form">
              <h2>S3. 신청 내용 작성</h2>
              <p className={saveState === "저장 실패" ? "error-message" : "muted"}>{saveState}{lastSavedAt ? ` · 마지막 저장 ${lastSavedAt}` : ""}</p>
              <div className="readonly-grid">
                <label>교과목명<input value={application.course?.courseName ?? ""} disabled /></label>
                <label>담당교수<input value={application.course?.professorName ?? ""} disabled /></label>
                <label>연구주제<input value={application.course?.courseName ?? ""} disabled /></label>
                <label>연구내용<textarea value={application.course?.researchDescription ?? ""} disabled rows={3} /></label>
              </div>
              <label>신청사유<textarea value={applicationReason} onChange={(event) => setApplicationReason(event.target.value)} disabled={!canEdit} rows={5} /></label>
              <label>연구목적<textarea value={researchPurpose} onChange={(event) => setResearchPurpose(event.target.value)} disabled={!canEdit} rows={5} /></label>
              <label>관련 경험<textarea value={relatedExperience} onChange={(event) => setRelatedExperience(event.target.value)} disabled={!canEdit} rows={3} /></label>
              <label>연구 수행 계획<textarea value={researchPlan} onChange={(event) => setResearchPlan(event.target.value)} disabled={!canEdit} rows={3} /></label>
              <label>면담 질문<textarea value={interviewQuestions} onChange={(event) => setInterviewQuestions(event.target.value)} disabled={!canEdit} rows={3} /></label>
              <StepActions onPrevious={() => goStep("applicant")} onNext={() => saveAndGo("documents")}
                           nextDisabled={!canEdit || !applicationReason.trim() || !researchPurpose.trim() || isSaving} />
            </section>
          ) : null}

          {flowStep === "documents" ? (
            <section className="status-panel">
              <h2>S4. 신청서 문서 생성 및 다운로드</h2>
              <p className="muted">제출 전 확인용 PDF와 수강신청원 HWPX를 내려받아 내용을 확인하세요.</p>
              <div className="document-actions">
                <button onClick={handleDownloadPdf} disabled={isDownloading}>{isDownloading ? "다운로드 중" : "신청서 PDF 다운로드"}</button>
                <button onClick={() => handleGenerate("application-hwpx")} disabled={isDownloading || !draftId}>수강신청원 HWPX 생성</button>
                {generatedFiles["application-hwpx"] ? <button onClick={() => downloadGenerated(generatedFiles["application-hwpx"])}>생성된 HWPX 다운로드</button> : null}
              </div>
              <StepActions onPrevious={() => goStep("content")} onNext={() => goStep("signature-guide")} />
            </section>
          ) : null}

          {flowStep === "signature-guide" ? (
            <section className="status-panel signature-guide">
              <h2>S5. 교수 서명본 준비</h2>
              <strong>교수 서명본을 PDF/JPG/PNG로 준비해주세요.</strong>
              <ol>
                <li>앞 단계에서 신청서 파일을 다운로드합니다.</li>
                <li>담당 교수님께 신청 내용 확인과 서명을 받습니다.</li>
                <li>서명된 문서를 PDF, JPG, JPEG 또는 PNG 파일로 준비합니다.</li>
                <li>파일 크기는 10MB 이하여야 합니다.</li>
              </ol>
              <StepActions onPrevious={() => goStep("documents")} onNext={() => goStep("files")} />
            </section>
          ) : null}

          {flowStep === "files" ? (
            <section className="status-panel">
              <h2>S6. 제출 파일 업로드</h2>
              {canEdit && !applicationFiles.some((file) => file.documentType === "SIGNED_APPLICATION") ? (
                <div className="file-upload-row">
                  <input type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={(event) => setSelectedUpload(event.target.files?.[0] ?? null)} />
                  <button className="primary-button" onClick={uploadApplicationFile} disabled={!selectedUpload || isFileAction}>교수 서명본 업로드</button>
                </div>
              ) : application.status === "SUBMITTED" ? (
                <p className="muted">제출 완료 후에는 파일을 변경할 수 없습니다.</p>
              ) : (
                <p className="muted">교수 서명본이 등록되어 있습니다. 새 파일은 기존 파일의 교체 버튼을 사용하세요.</p>
              )}
              {isFileLoading ? <p>파일 목록을 불러오는 중입니다.</p> : applicationFiles.length ? (
                <div className="table-wrap"><table><thead><tr><th>문서 종류</th><th>파일명</th><th>업로드 일시</th><th>관리</th></tr></thead>
                  <tbody>{applicationFiles.map((file) => <tr key={file.id}>
                    <td>{file.documentType === "SIGNED_APPLICATION" ? "교수 서명본" : "추가 파일"}</td>
                    <td>{file.fileName}</td><td>{new Date(file.uploadedAt).toLocaleString()}</td>
                    <td className="actions-row">
                      <button onClick={() => downloadApplicationFile(file)} disabled={isFileAction}>다운로드</button>
                      {canEdit ? <label className="file-replace-button">교체<input type="file" accept=".pdf,.jpg,.jpeg,.png" hidden onChange={(event) => replaceApplicationFile(file.id, event.target.files?.[0])} /></label> : null}
                      {canEdit ? <button className="danger-button" onClick={() => deleteApplicationFile(file.id)} disabled={isFileAction}>삭제</button> : null}
                    </td>
                  </tr>)}</tbody>
                </table></div>
              ) : <p className="muted">업로드된 제출 파일이 없습니다.</p>}
              <StepActions onPrevious={() => goStep("signature-guide")} onNext={() => goStep("review")}
                           nextDisabled={!applicationFiles.some((file) => file.documentType === "SIGNED_APPLICATION")} />
            </section>
          ) : null}

          {flowStep === "review" ? (
            <section className="status-panel">
              <h2>S7. 제출 전 최종 검증</h2>
              <button onClick={validateApplication} disabled={isValidating}>{isValidating ? "검증 중" : "제출 전 확인"}</button>
              {validationResult?.valid ? <p className="success-message">제출 가능한 상태입니다.</p> : null}
              {validationResult && !validationResult.valid ? (
                <div className="validation-summary"><strong>제출 전 확인이 필요합니다.</strong>
                  {validationResult.missingFields.length ? <><p>누락된 입력값:</p><ul>{validationResult.missingFields.map((field) => <li key={field}>{validationFieldLabel(field)}</li>)}</ul></> : null}
                  {validationResult.missingFiles.length ? <><p>누락된 파일:</p><ul>{validationResult.missingFiles.map((file) => <li key={file}>{file === "SIGNED_APPLICATION" ? "교수 서명본" : file}</li>)}</ul></> : null}
                </div>
              ) : null}
              <div className="step-actions">
                <button onClick={() => goStep("files")}>이전</button>
                <button className="primary-button" onClick={handleSubmitApplication} disabled={isSubmitting || isValidating || application.status === "SUBMITTED"}>
                  {isSubmitting ? "제출 중" : "최종 제출"}
                </button>
              </div>
            </section>
          ) : null}

          {flowStep === "complete" ? (
            <section className="status-panel completion-panel">
              <h2>S8. 제출 완료</h2>
              <p className="success-message">개별연구 신청서가 제출되었습니다.</p>
              <dl><dt>상태</dt><dd>{application.status}</dd><dt>제출일시</dt><dd>{application.submittedAt ? new Date(application.submittedAt).toLocaleString() : "-"}</dd><dt>제출 파일</dt><dd>{applicationFiles.length}개</dd></dl>
              <button onClick={() => goStep("summary")}>내 신청 현황으로</button>
            </section>
          ) : null}
        </>
      )}
    </section>
  );
}

function ApplicationStepBar({ current, submitted }) {
  const steps = [
    ["applicant", "신청자 정보"],
    ["content", "신청 내용"],
    ["documents", "문서 생성"],
    ["signature-guide", "서명 준비"],
    ["files", "파일 업로드"],
    ["review", "최종 검증"],
    ["complete", "제출 완료"],
  ];
  return (
    <ol className="application-step-bar" aria-label="신청 단계">
      {steps.map(([key, label], index) => (
        <li className={current === key || (submitted && key === "complete") ? "active" : ""} key={key}>
          <span>{index + 2}</span>{label}
        </li>
      ))}
    </ol>
  );
}

function StepActions({ onPrevious, onNext, nextDisabled = false }) {
  return (
    <div className="step-actions">
      <button type="button" onClick={onPrevious}>이전</button>
      <button className="primary-button" type="button" onClick={onNext} disabled={nextDisabled}>다음</button>
    </div>
  );
}

function InfoMap({ values }) {
  const entries = Object.entries(values ?? {});

  if (!entries.length) {
    return <p className="muted">표시할 정보가 없습니다.</p>;
  }

  return (
    <table className="transparent-info-table">
      <tbody>
      {entries.map(([label, value]) => (
        <tr key={label}>
          <th scope="row">{label}</th>
          <td>{value}</td>
        </tr>
      ))}
      </tbody>
    </table>
  );
}

function validationFieldLabel(field) {
  return {
    studentName: "성명",
    studentNumber: "학번",
    department: "소속",
    phone: "연락처",
    email: "이메일",
    semester: "학년도/학기",
    courseName: "교과목명",
    professorName: "담당교수",
    applicationReason: "신청사유",
    researchPurpose: "연구목적",
  }[field] ?? field;
}

function displayRequiredDocuments(documents = []) {
  const combinedApprovalEvidence =
    "담당교수 서명이 포함된 수강신청원 또는 서명이 어려운 경우 수강 허가 이메일 등 증빙자료";
  const hiddenDuplicates = new Set([
    "담당교수 승인 증빙",
    "담당교수 서명이 포함된 수강신청원",
    "수강 허가 이메일 등 증빙자료",
  ]);
  const normalized = documents.map((document) =>
    document === "담당교수 승인 증빙" ? combinedApprovalEvidence : document,
  );
  const hasCombinedApprovalEvidence = normalized.includes(combinedApprovalEvidence);

  return normalized
    .filter((document) => !(hasCombinedApprovalEvidence && hiddenDuplicates.has(document)))
    .filter((document, index, values) => values.indexOf(document) === index);
}

function StudentDashboardMain({ dashboard, currentNotice }) {
  return (
    <>
      <dl className="student-info-list">
        <dt>이름</dt>
        <dd>{dashboard?.studentSummary?.name}</dd>
        <dt>학번</dt>
        <dd>{dashboard?.studentSummary?.loginId}</dd>
        <dt>학과</dt>
        <dd>{dashboard?.studentSummary?.department}</dd>
        <dt>신청 학기</dt>
        <dd>{dashboard?.studentSummary?.semester}</dd>
        <dt>기간</dt>
        <dd>{dashboard?.studentSummary?.applicationPeriod}</dd>
      </dl>
      <button className="primary-button">{dashboard?.primaryAction}</button>
    </>
  );
}

function StudentDashboardSide({ dashboard, currentNotice }) {
  const requiredDocuments = displayRequiredDocuments(currentNotice?.requiredDocuments);

  return (
    <>
      <h2>최근 공지와 절차 요약</h2>
      {currentNotice ? (
        <>
          <section>
            <h3>최근 공지</h3>
            <p className="recent-notice">
              <span>{currentNotice.title}</span>
              <time>{currentNotice.publishedAt}</time>
            </p>
          </section>
          <section>
            <h3>신청 절차</h3>
            <ol>
              {dashboard?.nextSteps?.map((step) => (
                <li className={step.completed ? "completed" : ""} key={step.label}>
                  {step.label}
                </li>
              ))}
            </ol>
          </section>
          <section>
            <h3>제출자료</h3>
            <ul>
              {requiredDocuments.map((document) => (
                <li key={document}>{document}</li>
              ))}
            </ul>
          </section>
        </>
      ) : (
        <p className="muted">공지 상세 정보가 아직 없습니다.</p>
      )}
    </>
  );
}

function StaffDashboardMain({ dashboard }) {
  const firstPending = dashboard?.pendingApplications?.[0];

  return (
    <>
      {firstPending ? (
        <dl>
          <dt>신청 번호</dt>
          <dd>{firstPending.applicationNumber}</dd>
          <dt>학생</dt>
          <dd>
            {firstPending.studentName} / {firstPending.studentLoginId}
          </dd>
          <dt>현재 상태</dt>
          <dd>{firstPending.statusLabel}</dd>
        </dl>
      ) : (
        <p className="muted">현재 검토 대기 중인 신청이 없습니다.</p>
      )}
      <button className="primary-button">신청 상세보기</button>
    </>
  );
}

function StaffDashboardSide({ dashboard }) {
  return (
    <>
      <h2>최근 제출 및 크롤링 분석</h2>
      <section>
        <h3>최근 제출된 신청</h3>
        {dashboard?.recentApplications?.length ? (
          dashboard.recentApplications.map((application) => (
            <p className="recent-notice" key={application.id}>
              <span>
                {application.applicationNumber} {application.studentName}
              </span>
              <time>{application.statusLabel}</time>
            </p>
          ))
        ) : (
          <p className="muted">아직 제출된 신청이 없습니다.</p>
        )}
      </section>
      <section>
        <h3>크롤링 분석</h3>
        <p className="notice-note">
          검토 필요 {dashboard?.crawlerSummary?.needsReviewCount ?? 0}건 / 마감일{" "}
          {dashboard?.crawlerSummary?.deadline ?? "-"}
        </p>
      </section>
    </>
  );
}

createRoot(document.getElementById("root")).render(<App />);

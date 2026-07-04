import React, { useEffect, useState } from "react";
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
          setUser(body.data.user);
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
              <a>대시보드</a>
              <a>신청 안내</a>
              <a>개설 과목</a>
              <a>내 신청 현황</a>
            </>
          ) : (
            <>
              <a>대시보드</a>
              <a>신청 목록</a>
              <a>크롤링 결과</a>
            </>
          )}
        </nav>
        <div className="user-actions">
          <span>{displayUser}</span>
          <button onClick={onLogout}>로그아웃</button>
        </div>
      </header>

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
                  <p>{metric.description}</p>
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
    </main>
  );
}

function StudentDashboardMain({ dashboard, currentNotice }) {
  const noticeLines = (currentNotice?.noticeNotes ?? "신청 안내와 개설 과목을 확인한 뒤 신청서를 작성해 주세요.")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

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
      <section className="notice-note">
        <h3>공지사항 안내</h3>
        <ul>
          {noticeLines.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      </section>
      <button className="primary-button">{dashboard?.primaryAction}</button>
    </>
  );
}

function StudentDashboardSide({ dashboard, currentNotice }) {
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
              {currentNotice.requiredDocuments.map((document) => (
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

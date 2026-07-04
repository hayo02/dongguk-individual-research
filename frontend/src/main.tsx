import React, { FormEvent, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

type UserRole = "STUDENT" | "STAFF";

type LoginUser = {
  id: number;
  loginId: string;
  name: string;
  role: UserRole;
  department: string;
  email: string;
  phone: string;
};

type LoginResponse = {
  success: boolean;
  message?: string;
  data?: {
    user: LoginUser;
    accessToken: string;
  };
};

type Notice = {
  id: number;
  title: string;
  semester: string;
  startDate: string;
  endDate: string;
  originalUrl: string;
  needsReview: boolean;
  requiredDocuments: string[];
  scheduleInfo: Record<string, string>;
  submissionInfo: Record<string, string>;
  noticeNotes: string;
  publishedAt: string;
};

type StudentDashboardData = {
  student: LoginUser;
  currentNotice: Notice;
  applicationStatus: string;
  primaryAction: string;
  recentNotices: Array<{
    id: number;
    title: string;
    publishedAt: string;
    needsReview: boolean;
  }>;
  processSummary: string[];
};

type ApiResponse<T> = {
  success: boolean;
  message?: string;
  data?: T;
};

const API_BASE_URL = "http://127.0.0.1:8000";
const ACCESS_TOKEN_KEY = "individualResearchAccessToken";

function App() {
  const [user, setUser] = useState<LoginUser | null>(null);
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
        const body = (await response.json()) as ApiResponse<{ user: LoginUser }>;

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

function LoginPage({
  onLogin,
}: {
  onLogin: (user: LoginUser, accessToken: string) => void;
}) {
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");
    setIsSubmitting(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ loginId, password }),
      });
      const body = (await response.json()) as LoginResponse;

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

function Dashboard({
  user,
  accessToken,
  onLogout,
}: {
  user: LoginUser;
  accessToken: string;
  onLogout: () => void;
}) {
  const isStudent = user.role === "STUDENT";
  const [studentDashboard, setStudentDashboard] = useState<StudentDashboardData | null>(null);
  const [dashboardError, setDashboardError] = useState("");
  const [isLoadingDashboard, setIsLoadingDashboard] = useState(isStudent);

  useEffect(() => {
    if (!isStudent) {
      setIsLoadingDashboard(false);
      return;
    }

    let isMounted = true;

    async function loadStudentDashboard() {
      setDashboardError("");
      setIsLoadingDashboard(true);

      try {
        const response = await fetch(`${API_BASE_URL}/api/student/dashboard`, {
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        const body = (await response.json()) as ApiResponse<StudentDashboardData>;

        if (!response.ok || !body.success || !body.data) {
          throw new Error(body.message ?? "학생 홈 정보를 불러오지 못했습니다.");
        }

        if (isMounted) {
          setStudentDashboard(body.data);
        }
      } catch (error) {
        if (isMounted) {
          setDashboardError(
            error instanceof Error
              ? error.message
              : "학생 홈 정보를 불러오지 못했습니다.",
          );
        }
      } finally {
        if (isMounted) {
          setIsLoadingDashboard(false);
        }
      }
    }

    void loadStudentDashboard();

    return () => {
      isMounted = false;
    };
  }, [accessToken, isStudent]);

  const currentNotice = studentDashboard?.currentNotice;

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
          <span>{user.name}</span>
          <button onClick={onLogout}>로그아웃</button>
        </div>
      </header>

      <section className="dashboard-grid">
        <article className="status-panel notice-overview">
          <p className="eyebrow">{isStudent ? "학생 대시보드" : "교직원 대시보드"}</p>
          {isStudent ? (
            <>
              <div className="notice-heading">
                <h1>{currentNotice?.title ?? "신청 가능한 개별연구 공지를 확인해 주세요."}</h1>
                <span className="status-badge">
                  {currentNotice?.needsReview ? "확인 필요" : "신청 가능"}
                </span>
              </div>

              {isLoadingDashboard ? (
                <p className="muted">학생 홈 정보를 불러오는 중입니다.</p>
              ) : dashboardError ? (
                <p className="error-message">{dashboardError}</p>
              ) : currentNotice ? (
                <>
                  <dl>
                    <dt>학기</dt>
                    <dd>{currentNotice.semester}</dd>
                    <dt>신청기간</dt>
                    <dd>
                      {currentNotice.startDate} ~ {currentNotice.endDate}
                    </dd>
                    <dt>내 상태</dt>
                    <dd>{studentDashboard?.applicationStatus}</dd>
                  </dl>
                  <p className="notice-note">{currentNotice.noticeNotes}</p>
                  <button className="primary-button">{studentDashboard?.primaryAction}</button>
                </>
              ) : (
                <p className="muted">등록된 신청 안내 공지가 없습니다.</p>
              )}
            </>
          ) : (
            <>
              <h1>처리가 필요한 신청을 확인해 주세요.</h1>
              <dl>
                <dt>로그인 ID</dt>
                <dd>{user.loginId}</dd>
                <dt>역할</dt>
                <dd>{user.role}</dd>
                <dt>이메일</dt>
                <dd>{user.email}</dd>
              </dl>
              <button className="primary-button">전체 신청 보기</button>
            </>
          )}
        </article>

        <article className="status-panel secondary notice-detail">
          {isStudent ? (
            <>
              <h2>공지 상세</h2>
              {currentNotice ? (
                <>
                  <section>
                    <h3>제출자료</h3>
                    <ul>
                      {currentNotice.requiredDocuments.map((document) => (
                        <li key={document}>{document}</li>
                      ))}
                    </ul>
                  </section>
                  <section>
                    <h3>신청 절차</h3>
                    <ol>
                      {studentDashboard?.processSummary.map((step) => (
                        <li key={step}>{step}</li>
                      ))}
                    </ol>
                  </section>
                  <section>
                    <h3>최근 공지</h3>
                    {studentDashboard?.recentNotices.map((notice) => (
                      <p className="recent-notice" key={notice.id}>
                        <span>{notice.title}</span>
                        <time>{notice.publishedAt}</time>
                      </p>
                    ))}
                  </section>
                  <a className="text-link" href={currentNotice.originalUrl} target="_blank">
                    원문 공지 확인
                  </a>
                </>
              ) : (
                <p className="muted">공지 상세 정보가 아직 없습니다.</p>
              )}
            </>
          ) : (
            <>
              <h2>API 연결 확인</h2>
              <p>로그인 API 응답으로 받은 사용자 정보와 토큰을 프론트 상태에 저장했습니다.</p>
              <code>{accessToken.slice(0, 44)}...</code>
            </>
          )}
        </article>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(<App />);

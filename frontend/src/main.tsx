import React, { FormEvent, useState } from "react";
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

const API_BASE_URL = "http://127.0.0.1:8000";

function App() {
  const [user, setUser] = useState<LoginUser | null>(null);
  const [accessToken, setAccessToken] = useState("");

  if (user) {
    return (
      <Dashboard
        user={user}
        accessToken={accessToken}
        onLogout={() => {
          setUser(null);
          setAccessToken("");
        }}
      />
    );
  }

  return (
    <LoginPage
      onLogin={(nextUser, token) => {
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
        <article className="status-panel">
          <p className="eyebrow">{isStudent ? "학생 대시보드" : "교직원 대시보드"}</p>
          <h1>
            {isStudent
              ? "신청 가능한 개별연구 공지를 확인해 주세요."
              : "처리가 필요한 신청을 확인해 주세요."}
          </h1>
          <dl>
            <dt>로그인 ID</dt>
            <dd>{user.loginId}</dd>
            <dt>역할</dt>
            <dd>{user.role}</dd>
            <dt>이메일</dt>
            <dd>{user.email}</dd>
          </dl>
          <button className="primary-button">
            {isStudent ? "개별연구 신청하기" : "전체 신청 보기"}
          </button>
        </article>

        <article className="status-panel secondary">
          <h2>API 연결 확인</h2>
          <p>로그인 API 응답으로 받은 사용자 정보와 토큰을 프론트 상태에 저장했습니다.</p>
          <code>{accessToken.slice(0, 44)}...</code>
        </article>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(<App />);

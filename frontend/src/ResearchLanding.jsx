import React from "react";

export default function ResearchLanding({ children }) {
  return (
    <div className="research-landing">
      <header className="research-header">
        <a className="research-brand" href="#home" aria-label="개별연구 홈">
          <img src="/dongguk-logo.jpg" alt="동국대학교" />
          <span><strong>개별연구</strong><small>DONGGUK RESEARCH</small></span>
        </a>
        <nav aria-label="주요 메뉴">
          <a href="#guide">이용 안내</a>
          <a href="#journey">신청 절차</a>
          <a className="nav-login" href="#login-title">로그인 <span aria-hidden="true">↗</span></a>
        </nav>
      </header>

      <section className="research-hero" id="home">
        <div className="research-story">
          <p className="research-kicker"><span /> 동국대학교 컴퓨터·AI학부</p>
          <h1>작은 호기심이,<br /><em>나의 연구가 되는 곳.</em></h1>
          <p className="research-description">관심 있는 주제를 발견하고, 나만의 연구를 시작해 보세요.<br />첫 신청부터 승인까지, 아코와 함께 차근차근.</p>
          <a className="research-text-link" href="#journey">개별연구, 어떻게 시작하나요? <span aria-hidden="true">↗</span></a>
          <div className="ako-scene">
            <div className="ako-orbit" aria-hidden="true" />
            <span className="ako-spark spark-one" aria-hidden="true">✳</span>
            <span className="ako-spark spark-two" aria-hidden="true">✦</span>
            <div className="ako-caption"><span>HELLO, RESEARCHER!</span><strong>너의 첫 연구를 응원해!</strong></div>
            <img src="/ako-graduate.png" alt="졸업모를 던지며 응원하는 동국대학교 마스코트 아코" fetchPriority="high" />
            <span className="ako-signature">with AKO</span>
          </div>
        </div>
        <div className="research-login">{children}<p className="research-login-caption">YOUR NEXT CHAPTER STARTS HERE</p></div>
      </section>

      <section className="research-guide" id="guide">
        <div className="research-section-title"><p className="research-kicker">ALL IN ONE PLACE</p><h2>연구에 집중할 수 있도록.</h2><p>복잡한 신청 과정은 간결하게, 중요한 정보는 한눈에.</p></div>
        <div className="research-feature-grid">
          {[
            ["01", "나에게 맞는 연구 찾기", "개설 과목과 담당 교수, 연구 내용을 살펴보고 관심 있는 주제를 선택하세요.", "주제 탐색"],
            ["02", "작성부터 제출까지 한곳에서", "자동저장으로 편하게 작성하고, 신청서 PDF와 서명본을 한곳에서 관리하세요.", "간편한 신청"],
            ["03", "신청 결과를 한눈에", "제출 상태와 보완 요청, 최종 승인 결과까지 내 신청 현황에서 확인하세요.", "진행 상황 확인"],
          ].map(([number, title, description, tag]) => (
            <article key={number}><div className="feature-top"><span>{number}</span><small>{tag}</small></div><h3>{title}</h3><p>{description}</p></article>
          ))}
        </div>
      </section>

      <section className="research-journey" id="journey">
        <div><p className="research-kicker">STEP BY STEP</p><h2>시작은 가볍게,<br />한 단계씩.</h2><p>신청 일정과 세부 요건은<br />로그인 후 공지에서 확인하세요.</p></div>
        <ol>{["공지·연구 주제 확인", "신청서 작성", "교수 서명본 준비", "파일 업로드·제출", "검토 결과 확인"].map((step, index) => <li key={step}><span>0{index + 1}</span><strong>{step}</strong><span aria-hidden="true">↗</span></li>)}</ol>
      </section>
      <footer className="research-footer"><strong>DONGGUK <span>RESEARCH</span></strong><p>컴퓨터·AI학부 개별연구 신청 시스템</p><small>© 2026 Dongguk University · 2023112246 최하영</small></footer>
    </div>
  );
}

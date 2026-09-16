import React, { useEffect, useState } from "react";

const API = import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8000";
const labels = {
  application_start: "신청 시작", application_deadline: "신청 마감", interview_period: "교수 인터뷰",
  document_submission_period: "서류 제출", course_registration_period: "수강신청", research_period: "연구 기간",
  method: "제출 방식", location: "제출 장소", contact: "문의", email_submission_allowed: "이메일 제출",
  proxy_submission_allowed: "대리 제출", separate_course_registration_required: "별도 수강신청 필요",
};
const statusLabels = { SUCCESS: "수집 완료", NEEDS_REVIEW: "검토 필요", ERROR: "오류 확인 필요" };
const comparisonLabels = {
  NO_PREVIOUS_SNAPSHOT: "첫 수집 · 이전 결과 없음", NO_CHANGES: "이전 결과와 동일", UNCHANGED: "이전 결과와 동일", CHANGED: "이전 결과와 변경 사항 있음",
  REPROCESSED_BY_NEW_PARSER: "새 분석기로 재분석", SAME_SOURCE: "원문 변경 없음",
};
const display = value => value === null || value === undefined || value === "" ? "확인되지 않음"
  : typeof value === "boolean" ? value ? "예" : "아니요" : typeof value === "object" ? JSON.stringify(value) : String(value);
const date = value => value && !Number.isNaN(Date.parse(value)) ? new Date(value).toLocaleString("ko-KR") : "확인되지 않음";

function Evidence({ title, fields }) {
  return <section className="status-panel"><h2>{title}</h2><dl className="crawl-evidence">
    {Object.entries(fields).map(([key, item]) => <div key={key}><dt>{labels[key] ?? key}</dt><dd>
      <strong>{display(item.value)}</strong>
      {item.status && item.status !== "CONFIRMED" ? <small className="crawl-warning">원문 확인 필요</small> : null}
      {item.source_text ? <details><summary>추출 근거</summary><p>{item.source_text}</p></details> : null}
    </dd></div>)}
  </dl></section>;
}

const researchIssueCodes = new Set(["MISSING_PROFESSOR", "MISSING_COURSE_NAME", "MISSING_RESEARCH_DESCRIPTION", "MISSING_CAPACITY", "DUPLICATE_RESEARCH_ITEM"]);

function IssueContext({ issue, researchItems }) {
  // The research parser stores the spreadsheet's 순번 in issue.row, not its physical row number.
  const item = researchIssueCodes.has(issue.code) && issue.row != null
    ? researchItems.find(candidate => String(candidate["순번"]) === String(issue.row)) : null;
  if (!item) return <p className="muted">문제가 된 원문 내용을 연결할 수 없습니다. 원문 첨부파일을 확인해 주세요.</p>;
  const duplicates = issue.code === "DUPLICATE_RESEARCH_ITEM"
    ? researchItems.filter(candidate => candidate !== item && candidate["교원명"] === item["교원명"] && candidate["과목명"] === item["과목명"]) : [];
  const value = field => item[field] == null || String(item[field]).trim() === "" ? "비어 있음 (값 없음)" : display(item[field]);
  return <div className="crawl-issue-context">
    <p><b>연구 순번 {display(item["순번"])} · {display(item["교원명"])}</b><br />{display(item["과목명"])}</p>
    <dl>{(Array.isArray(issue.fields) ? issue.fields : []).map(field => <div key={field}><dt>{field}</dt><dd><mark>{value(field)}</mark></dd></div>)}</dl>
    {duplicates.length ? <div><b>중복된 항목 비교</b>{[item, ...duplicates].map((entry, index) => <p key={index}>순번 {display(entry["순번"])} · 학수번호: {entry["학수강좌번호"] || "없음"}<br />연구내용: {entry["연구내용"] || "비어 있음 (값 없음)"}</p>)}</div> : null}
  </div>;
}

export default function CrawlingResults({ accessToken, onBack }) {
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [issueFilter, setIssueFilter] = useState("ALL");
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError("");
    (async () => {
      try {
        const response = await fetch(`${API}/api/staff/crawling/latest`, {
          headers: { Authorization: `Bearer ${accessToken}` }, signal: controller.signal,
        });
        const body = await response.json();
        if (!response.ok || !body.success) throw new Error(body.message ?? "크롤링 결과를 불러오지 못했습니다.");
        if (!controller.signal.aborted) setResult(body.data);
      } catch (error) {
        if (!controller.signal.aborted) setError(error instanceof Error ? error.message : "크롤링 결과를 불러오지 못했습니다.");
      } finally { if (!controller.signal.aborted) setLoading(false); }
    })();
    return () => controller.abort();
  }, [accessToken, revision]);

  const issues = result?.available ? [
    ...result.errors.map(issue => ({ ...issue, severity: "ERROR" })),
    ...result.warnings.map(issue => ({ ...issue, severity: "WARNING" })),
  ] : [];
  const items = (result?.researchItems ?? []).filter(item =>
    [item["교원명"], item["과목명"], item["학수강좌번호"], item["연구내용"]].some(value =>
      String(value ?? "").toLowerCase().includes(keyword.trim().toLowerCase())));

  return <section className="dashboard-page crawling-page">
    <div className="crawl-page-heading"><div className="screen-title"><div><p className="eyebrow">교직원 · 공지 수집 관리</p><h1>크롤링 결과</h1></div></div>
      <div className="actions-row"><button onClick={onBack}>대시보드</button><button className="primary-button" disabled={loading} onClick={() => setRevision(value => value + 1)}>{loading ? "불러오는 중…" : "결과 새로고침"}</button></div>
    </div>
    <p className="crawl-intro">가장 최근 저장된 공지 수집 결과입니다. 새로고침은 저장된 결과를 다시 조회하며, 새 크롤링을 실행하지 않습니다.</p>
    {loading ? <section className="status-panel" role="status">크롤링 결과를 불러오는 중입니다.</section>
      : error ? <section className="status-panel" role="alert"><h2>조회하지 못했습니다.</h2><p className="error-message">{error}</p><button onClick={() => setRevision(value => value + 1)}>다시 시도</button></section>
      : !result?.available ? <section className="status-panel" role="status"><h2>{result?.status === "NOT_FOUND" ? "아직 수집 결과가 없습니다." : "수집 파일을 확인해 주세요."}</h2><p>{result?.message}</p><p className="muted">관리자가 공지 수집을 완료한 뒤 결과를 새로고침해 주세요.</p></section>
      : <>
        <section className="status-panel crawl-summary"><div><span className={`review-status ${result.status === "SUCCESS" ? "approved" : "revision_requested"}`}>{statusLabels[result.status] ?? result.status}</span><h2>{result.notice.title || "공지 제목 없음"}</h2><p>수집 일시 {date(result.crawledAt)} · 공지 작성일 {result.notice.written_at || "확인되지 않음"}</p></div>
          {result.notice.url ? <a className="button-link" href={result.notice.url} target="_blank" rel="noopener noreferrer">원문 공지 보기 ↗</a> : <span className="muted">원문 링크 없음</span>}
        </section>
        <section className="metric-row">{[["수집 연구 주제", result.researchItems.length], ["첨부파일", result.attachments.length], ["경고", result.warnings.length], ["오류", result.errors.length]].map(([label, count]) => <article className="metric-card" key={label}><span>{label}</span><strong>{count}건</strong></article>)}</section>
        <section className="status-panel"><div className="crawl-page-heading"><h2>검토가 필요한 항목</h2><label>표시 <select value={issueFilter} onChange={event => setIssueFilter(event.target.value)}><option value="ALL">전체</option><option value="WARNING">경고</option><option value="ERROR">오류</option></select></label></div>
          <p className="muted">연구 항목의 순번과 문제가 된 수집 내용을 함께 표시합니다. 강조된 값은 원문 확인이 필요한 부분입니다.</p>
          {issues.filter(issue => issueFilter === "ALL" || issue.severity === issueFilter).length ? <ul className="crawl-issues">{issues.filter(issue => issueFilter === "ALL" || issue.severity === issueFilter).map((issue, index) => <li key={index}><span className="crawl-warning">{issue.severity === "ERROR" ? "오류" : "경고"}{issue.row != null ? ` · ${researchIssueCodes.has(issue.code) ? "연구 순번" : "원본 행"} ${issue.row}` : ""}</span><strong>{issue.message || issue.code || "세부 내용 없음"}</strong><IssueContext issue={issue} researchItems={result.researchItems} /></li>)}</ul> : <p>표시할 경고·오류가 없습니다.</p>}
        </section>
        <div className="dashboard-grid"><Evidence title="추출된 일정" fields={result.schedule} /><Evidence title="제출 안내" fields={result.submission} /></div>
        <section className="status-panel"><h2>제출 자료</h2>{result.requiredDocuments.length ? <ul>{result.requiredDocuments.map((doc, index) => <li key={index}>{doc.name} {doc.requirement_type === "ONE_OF" ? "(대체 자료 중 하나 필요)" : doc.required ? "(필수)" : ""}</li>)}</ul> : <p className="muted">추출된 제출 자료가 없습니다.</p>}</section>
        <section className="status-panel"><h2>첨부파일 수집·분석</h2>{result.attachments.length ? <div className="crawl-files">{result.attachments.map((file, index) => <article key={index}><strong>{file.name}</strong><p>다운로드 {file.download_success ? "완료" : "미완료"} · 분석 {file.parse_success ? "완료" : "미완료"}{file.size ? ` · ${Math.ceil(file.size / 1024)} KB` : ""}</p>{file.message ? <p className="crawl-warning">{file.message}</p> : null}{file.url ? <a href={file.url} target="_blank" rel="noopener noreferrer">원문 첨부파일 받기 ↗</a> : null}</article>)}</div> : <p>첨부파일이 없습니다.</p>}</section>
        <section className="status-panel"><div className="crawl-page-heading"><h2>수집된 연구 주제 <small>{items.length} / {result.researchItems.length}건</small></h2><input aria-label="연구 주제 검색" type="search" placeholder="교수·과목·학수번호·연구 내용 검색" value={keyword} onChange={event => setKeyword(event.target.value)} /></div>
          <p className="muted">크롤러가 추출한 원본 기준이며, 현재 개설 과목 DB와는 다를 수 있습니다.</p>
          <div className="staff-table-scroll"><table className="staff-application-table crawl-table"><thead><tr><th>순번</th><th>교수 / 학부</th><th>과목 / 학수번호</th><th>정원</th><th>상세 내용</th></tr></thead><tbody>{items.length ? items.map((item, index) => <tr key={index}><td>{display(item["순번"])}</td><td>{display(item["교원명"])}<small>{item["개설학부"]}</small></td><td>{display(item["과목명"])}<small>{item["학수강좌번호"] || "학수번호 없음"}</small></td><td>{display(item["수강정원"])}</td><td><details><summary>연구 내용 보기</summary><p>{item["연구내용"] || "연구 내용 없음 · 확인 필요"}</p><p>지원 자격: {display(item["수강 자격사항"])}</p><p>인터뷰: {display(item["인터뷰 일정"])}</p><p>주당 연구시간: {display(item["주당 연구시간"])}</p></details></td></tr>) : <tr><td colSpan={5}>{keyword ? "검색 결과가 없습니다." : "수집된 연구 주제가 없습니다."}</td></tr>}</tbody></table></div>
        </section>
        <section className="status-panel"><h2>원문 및 수집 정보</h2><p>분석기 버전: {result.parserVersion || "알 수 없음"} · {comparisonLabels[result.comparisonStatus] ?? "이전 결과 비교 정보 확인 필요"}</p>{result.changes.length ? <ul>{result.changes.map((change, index) => <li key={index}>{change.message || change.field || change.type || "변경 항목"}</li>)}</ul> : null}<details><summary>수집한 공지 본문 펼치기</summary><p className="crawl-body">{result.notice.body_text || "저장된 본문이 없습니다."}</p></details></section>
      </>}
  </section>;
}

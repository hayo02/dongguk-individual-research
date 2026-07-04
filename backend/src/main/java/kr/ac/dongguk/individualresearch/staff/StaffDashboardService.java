package kr.ac.dongguk.individualresearch.staff;

import java.time.format.DateTimeFormatter;
import java.util.List;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.notice.Notice;
import kr.ac.dongguk.individualresearch.notice.NoticeService;
import kr.ac.dongguk.individualresearch.staff.StaffDashboardResponse.ApplicationSummary;
import kr.ac.dongguk.individualresearch.staff.StaffDashboardResponse.CrawlerSummary;
import kr.ac.dongguk.individualresearch.staff.StaffDashboardResponse.DashboardMetric;
import kr.ac.dongguk.individualresearch.staff.StaffDashboardResponse.DashboardPanel;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StaffDashboardService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final JdbcTemplate jdbcTemplate;
    private final NoticeService noticeService;

    public StaffDashboardService(JdbcTemplate jdbcTemplate, NoticeService noticeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.noticeService = noticeService;
    }

    public StaffDashboardResponse dashboard(PublicUser staff) {
        Notice notice = noticeService.currentNotice();
        long submittedCount = countByStatus(ApplicationStatus.SUBMITTED);
        long revisionRequestedCount = countByStatus(ApplicationStatus.REVISION_REQUESTED);
        long approvedCount = countByStatus(ApplicationStatus.APPROVED);
        long rejectedCount = countByStatus(ApplicationStatus.REJECTED);
        long totalVisibleCount = countVisibleApplications();
        List<ApplicationSummary> pendingApplications = findApplicationsByStatus(ApplicationStatus.SUBMITTED, 3);
        List<ApplicationSummary> recentApplications = findRecentApplications(3);

        return new StaffDashboardResponse(
                "A1",
                "교직원 대시보드",
                staff,
                List.of(
                        new DashboardMetric("제출 완료", submittedCount, "검토 대기 신청", "pending"),
                        new DashboardMetric("보완 요청", revisionRequestedCount, "학생 재제출 대기", "warning"),
                        new DashboardMetric("승인", approvedCount, "최종 승인 신청", "success"),
                        new DashboardMetric("반려", rejectedCount, "반려 처리 신청", "danger"),
                        new DashboardMetric("전체 신청", totalVisibleCount, "작성 중 제외", "neutral")
                ),
                List.of(
                        new DashboardPanel("처리가 필요한 신청", "작성 중 신청은 노출하지 않고 제출 완료 신청부터 검토합니다.", "신청 상세보기", "A3"),
                        new DashboardPanel("최근 제출된 신청", recentApplications.isEmpty() ? "아직 제출된 신청이 없습니다." : "최근 제출 순으로 신청을 확인합니다.", "전체 신청 보기", "A2"),
                        new DashboardPanel("크롤링 분석", "검토 필요 공지와 신청 마감일을 확인합니다.", "크롤링 결과 확인", "A4")
                ),
                pendingApplications,
                recentApplications,
                new CrawlerSummary(
                        notice.needsReview() ? 1 : 0,
                        notice.endDate().format(DATE_FORMAT),
                        notice.title(),
                        "A4"
                ),
                noticeService.toSummary(notice)
        );
    }

    private long countByStatus(ApplicationStatus status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE status = ?",
                Long.class,
                status.name()
        );
        return count == null ? 0 : count;
    }

    private long countVisibleApplications() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE status <> ?",
                Long.class,
                ApplicationStatus.DRAFT.name()
        );
        return count == null ? 0 : count;
    }

    private List<ApplicationSummary> findApplicationsByStatus(ApplicationStatus status, int limit) {
        return jdbcTemplate.query(
                """
                SELECT a.id, u.name, u.login_id, a.status, COALESCE(a.submitted_at, a.updated_at) AS submitted_at
                FROM applications a
                JOIN users u ON u.id = a.student_id
                WHERE a.status = ?
                ORDER BY COALESCE(a.submitted_at, a.updated_at) DESC, a.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> toSummary(rs.getLong("id"), rs.getString("name"), rs.getString("login_id"),
                        rs.getString("status"), rs.getTimestamp("submitted_at").toLocalDateTime()),
                status.name(),
                limit
        );
    }

    private List<ApplicationSummary> findRecentApplications(int limit) {
        return jdbcTemplate.query(
                """
                SELECT a.id, u.name, u.login_id, a.status, COALESCE(a.submitted_at, a.updated_at) AS submitted_at
                FROM applications a
                JOIN users u ON u.id = a.student_id
                WHERE a.status <> ?
                ORDER BY COALESCE(a.submitted_at, a.updated_at) DESC, a.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> toSummary(rs.getLong("id"), rs.getString("name"), rs.getString("login_id"),
                        rs.getString("status"), rs.getTimestamp("submitted_at").toLocalDateTime()),
                ApplicationStatus.DRAFT.name(),
                limit
        );
    }

    private ApplicationSummary toSummary(long id, String studentName, String studentLoginId, String status, java.time.LocalDateTime submittedAt) {
        return new ApplicationSummary(
                id,
                "IR-2026-%04d".formatted(id),
                studentName,
                studentLoginId,
                "개별연구 과목",
                "담당 교수",
                status,
                statusLabel(ApplicationStatus.valueOf(status)),
                submittedAt
        );
    }

    private String statusLabel(ApplicationStatus status) {
        return switch (status) {
            case NO_APPLICATION -> "신청 전";
            case DRAFT -> "작성 중";
            case SUBMITTED -> "제출 완료";
            case REVISION_REQUESTED -> "보완 요청";
            case APPROVED -> "승인";
            case REJECTED -> "반려";
        };
    }
}

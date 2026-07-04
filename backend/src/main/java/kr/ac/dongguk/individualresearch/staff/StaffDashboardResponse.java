package kr.ac.dongguk.individualresearch.staff;

import java.time.LocalDateTime;
import java.util.List;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.notice.NoticeSummary;

public record StaffDashboardResponse(
        String pageCode,
        String screenName,
        PublicUser staff,
        List<DashboardMetric> metrics,
        List<DashboardPanel> dashboardPanels,
        List<ApplicationSummary> pendingApplications,
        List<ApplicationSummary> recentApplications,
        CrawlerSummary crawlerSummary,
        NoticeSummary currentNotice
) {
    public record DashboardMetric(
            String label,
            long value,
            String description,
            String tone
    ) {
    }

    public record DashboardPanel(
            String title,
            String description,
            String actionLabel,
            String targetPage
    ) {
    }

    public record ApplicationSummary(
            long id,
            String applicationNumber,
            String studentName,
            String studentLoginId,
            String courseName,
            String professorName,
            String status,
            String statusLabel,
            LocalDateTime submittedAt
    ) {
    }

    public record CrawlerSummary(
            long needsReviewCount,
            String deadline,
            String description,
            String targetPage
    ) {
    }
}

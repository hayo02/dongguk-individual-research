package kr.ac.dongguk.individualresearch.student;

import java.util.List;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.notice.NoticeResponse;
import kr.ac.dongguk.individualresearch.notice.NoticeSummary;

public record StudentDashboardResponse(
        String pageCode,
        String screenName,
        PublicUser student,
        StudentSummary studentSummary,
        NoticeResponse currentNotice,
        ApplicationStatus applicationStatus,
        String applicationStatusLabel,
        String primaryAction,
        List<DashboardMetric> statusCards,
        List<DashboardPanel> dashboardPanels,
        List<NoticeSummary> recentNotices,
        List<String> processSummary,
        List<DashboardStep> nextSteps
) {
    public record StudentSummary(
            String name,
            String loginId,
            String department,
            String semester,
            String applicationPeriod
    ) {
    }

    public record DashboardMetric(
            String label,
            String value,
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

    public record DashboardStep(
            String label,
            boolean completed
    ) {
    }
}

package kr.ac.dongguk.individualresearch.student;

import java.util.List;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.notice.NoticeResponse;
import kr.ac.dongguk.individualresearch.notice.NoticeSummary;

public record StudentDashboardResponse(
        PublicUser student,
        NoticeResponse currentNotice,
        ApplicationStatus applicationStatus,
        String primaryAction,
        List<NoticeSummary> recentNotices,
        List<String> processSummary
) {
}

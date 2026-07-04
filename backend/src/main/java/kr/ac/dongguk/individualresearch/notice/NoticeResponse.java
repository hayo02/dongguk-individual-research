package kr.ac.dongguk.individualresearch.notice;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record NoticeResponse(
        long id,
        String title,
        String semester,
        LocalDate startDate,
        LocalDate endDate,
        String originalUrl,
        boolean needsReview,
        List<String> requiredDocuments,
        Map<String, String> scheduleInfo,
        Map<String, String> submissionInfo,
        String noticeNotes,
        String bodyText,
        LocalDate publishedAt
) {
}

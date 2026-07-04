package kr.ac.dongguk.individualresearch.notice;

import java.time.LocalDate;

public record Notice(
        long id,
        String title,
        String semester,
        LocalDate startDate,
        LocalDate endDate,
        String originalUrl,
        boolean needsReview,
        String requiredDocumentsJson,
        String scheduleInfoJson,
        String submissionInfoJson,
        String noticeNotes,
        String bodyText,
        LocalDate publishedAt
) {
}

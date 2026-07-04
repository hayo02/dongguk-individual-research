package kr.ac.dongguk.individualresearch.notice;

import java.time.LocalDate;

public record NoticeSummary(
        long id,
        String title,
        LocalDate publishedAt,
        boolean needsReview
) {
}

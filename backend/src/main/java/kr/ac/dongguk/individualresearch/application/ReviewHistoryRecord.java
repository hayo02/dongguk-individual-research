package kr.ac.dongguk.individualresearch.application;

import java.time.LocalDateTime;

public record ReviewHistoryRecord(
        long id,
        long applicationId,
        String previousStatus,
        String changedStatus,
        String comment,
        long reviewerId,
        String reviewerName,
        LocalDateTime reviewedAt
) {
}

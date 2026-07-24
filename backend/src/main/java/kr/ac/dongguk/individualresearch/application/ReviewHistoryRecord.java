package kr.ac.dongguk.individualresearch.application;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewHistoryRecord(
        long id,
        long applicationId,
        String previousStatus,
        String changedStatus,
        String comment,
        List<String> revisionItems,
        long reviewerId,
        String reviewerName,
        LocalDateTime reviewedAt
) {
}

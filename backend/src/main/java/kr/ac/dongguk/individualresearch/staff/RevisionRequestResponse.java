package kr.ac.dongguk.individualresearch.staff;

import java.time.LocalDateTime;
import java.util.List;

public record RevisionRequestResponse(
        long applicationId,
        String status,
        String statusLabel,
        String reason,
        boolean requireSignedApplication,
        List<String> revisionItems,
        LocalDateTime requestedAt
) {
}

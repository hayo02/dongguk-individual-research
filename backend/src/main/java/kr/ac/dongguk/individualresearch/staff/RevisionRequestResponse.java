package kr.ac.dongguk.individualresearch.staff;

import java.time.LocalDateTime;

public record RevisionRequestResponse(
        long applicationId,
        String status,
        String statusLabel,
        String reason,
        boolean requireSignedApplication,
        LocalDateTime requestedAt
) {
}

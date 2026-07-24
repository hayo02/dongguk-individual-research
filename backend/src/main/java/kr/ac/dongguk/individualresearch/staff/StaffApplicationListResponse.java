package kr.ac.dongguk.individualresearch.staff;

import java.time.LocalDateTime;
import java.util.List;

public record StaffApplicationListResponse(
        List<Item> applications,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public record Item(
            long id,
            String applicationNumber,
            String studentName,
            String studentLoginId,
            String courseName,
            String professorName,
            String status,
            String statusLabel,
            LocalDateTime submittedAt,
            LocalDateTime updatedAt
    ) {
    }
}

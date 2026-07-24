package kr.ac.dongguk.individualresearch.application;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationDetailResponse(
        long id,
        String status,
        StudentSummary student,
        CourseSummary course,
        String applicationReason,
        String researchPurpose,
        List<FileSummary> files,
        List<ReviewHistorySummary> reviewHistories,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record StudentSummary(
            long id,
            String loginId,
            String name,
            String department,
            String email,
            String phone,
            String contact
    ) {
    }

    public record CourseSummary(
            Long id,
            Long noticeId,
            String semester,
            String department,
            String professorName,
            String courseName,
            String courseType,
            String courseCode,
            String researchDescription,
            Integer weeklyHours
    ) {
    }

    public record FileSummary() {
    }

    public record ReviewHistorySummary(
            String previousStatus,
            String changedStatus,
            String comment,
            String reviewerName,
            LocalDateTime reviewedAt
    ) {
    }
}

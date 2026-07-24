package kr.ac.dongguk.individualresearch.staff;

import java.time.LocalDateTime;
import java.util.List;

public record StaffApplicationDetailResponse(
        long id,
        String applicationNumber,
        String status,
        String statusLabel,
        Student student,
        Application application,
        List<FileItem> files,
        List<ReviewHistoryItem> reviewHistories,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record Student(
            long id,
            String name,
            String loginId,
            String department,
            String email,
            String phone,
            String contact
    ) {
    }

    public record Application(
            Long courseId,
            Long noticeId,
            String semester,
            String department,
            String courseName,
            String courseType,
            String courseCode,
            String professorName,
            String researchDescription,
            Integer weeklyHours,
            String applicationReason,
            String researchPurpose
    ) {
    }

    public record FileItem(
            long id,
            String documentType,
            String fileName,
            long fileSize,
            String contentType,
            LocalDateTime uploadedAt
    ) {
    }

    public record ReviewHistoryItem(
            String previousStatus,
            String changedStatus,
            String comment,
            List<String> revisionItems,
            String reviewerName,
            LocalDateTime reviewedAt
    ) {
    }
}

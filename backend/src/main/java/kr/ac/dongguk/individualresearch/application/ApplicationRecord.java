package kr.ac.dongguk.individualresearch.application;

import java.time.LocalDateTime;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;

public record ApplicationRecord(
        long id,
        long studentId,
        String studentLoginId,
        String studentName,
        String studentDepartment,
        String studentEmail,
        String studentPhone,
        Long courseId,
        Long noticeId,
        String department,
        String courseName,
        String courseType,
        String courseCode,
        String professorName,
        ApplicationStatus status,
        String applicationReason,
        String researchPurpose,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

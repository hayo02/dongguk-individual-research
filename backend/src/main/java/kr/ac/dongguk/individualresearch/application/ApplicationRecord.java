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
        String contact,
        Long courseId,
        Long noticeId,
        String semester,
        String department,
        String courseName,
        String courseType,
        String courseCode,
        Integer weeklyHours,
        String professorName,
        ApplicationStatus status,
        String applicationReason,
        String researchPurpose,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

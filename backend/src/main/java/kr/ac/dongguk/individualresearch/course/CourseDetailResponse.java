package kr.ac.dongguk.individualresearch.course;

import java.util.List;

public record CourseDetailResponse(
        long id,
        long noticeId,
        int displayOrder,
        String department,
        String professorName,
        String courseName,
        String courseType,
        String courseCode,
        String researchDescription,
        Integer capacity,
        String interviewSchedule,
        Integer weeklyHours,
        String qualification,
        List<String> professorEmails,
        List<String> requiredSkills,
        List<String> mentionedTechnologies,
        boolean closedToAdditionalApplications
) {
}

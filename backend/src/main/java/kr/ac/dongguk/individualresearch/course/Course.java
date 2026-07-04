package kr.ac.dongguk.individualresearch.course;

public record Course(
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
        String professorEmailsJson,
        String requiredSkillsJson,
        String mentionedTechnologiesJson,
        boolean closedToAdditionalApplications
) {
}

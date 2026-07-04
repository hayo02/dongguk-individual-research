package kr.ac.dongguk.individualresearch.course;

public record CourseSummaryResponse(
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
        boolean closedToAdditionalApplications
) {
}

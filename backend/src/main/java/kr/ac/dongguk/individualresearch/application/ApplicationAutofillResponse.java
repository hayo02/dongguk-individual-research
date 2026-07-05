package kr.ac.dongguk.individualresearch.application;

public record ApplicationAutofillResponse(
        StudentAutofill student,
        CourseAutofill course
) {
    public record StudentAutofill(
            String name,
            String loginId,
            String department,
            String email,
            String phone
    ) {
    }

    public record CourseAutofill(
            String courseName,
            String professorName,
            String courseCode,
            String semester,
            String weeklyHours
    ) {
    }
}

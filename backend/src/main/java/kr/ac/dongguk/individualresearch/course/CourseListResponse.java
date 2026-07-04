package kr.ac.dongguk.individualresearch.course;

import java.util.List;

public record CourseListResponse(
        int totalCount,
        List<CourseSummaryResponse> courses
) {
}

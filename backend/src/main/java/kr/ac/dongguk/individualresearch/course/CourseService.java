package kr.ac.dongguk.individualresearch.course;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper;

    public CourseService(CourseRepository courseRepository, ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.objectMapper = objectMapper;
    }

    public CourseListResponse list(String keyword) {
        List<CourseSummaryResponse> courses = courseRepository.findAll(keyword).stream()
                .map(this::toSummary)
                .toList();
        return new CourseListResponse(courses.size(), courses);
    }

    public CourseDetailResponse detail(long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("개설 과목 정보를 찾을 수 없습니다."));
        return toDetail(course);
    }

    private CourseSummaryResponse toSummary(Course course) {
        return new CourseSummaryResponse(
                course.id(),
                course.noticeId(),
                course.displayOrder(),
                course.department(),
                course.professorName(),
                course.courseName(),
                course.courseType(),
                course.courseCode(),
                course.researchDescription(),
                course.capacity(),
                course.interviewSchedule(),
                course.weeklyHours(),
                course.qualification(),
                course.closedToAdditionalApplications()
        );
    }

    private CourseDetailResponse toDetail(Course course) {
        return new CourseDetailResponse(
                course.id(),
                course.noticeId(),
                course.displayOrder(),
                course.department(),
                course.professorName(),
                course.courseName(),
                course.courseType(),
                course.courseCode(),
                course.researchDescription(),
                course.capacity(),
                course.interviewSchedule(),
                course.weeklyHours(),
                course.qualification(),
                readList(course.professorEmailsJson()),
                readList(course.requiredSkillsJson()),
                readList(course.mentionedTechnologiesJson()),
                course.closedToAdditionalApplications()
        );
    }

    private List<String> readList(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }
}

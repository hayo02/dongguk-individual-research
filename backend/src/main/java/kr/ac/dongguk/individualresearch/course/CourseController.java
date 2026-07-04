package kr.ac.dongguk.individualresearch.course;

import kr.ac.dongguk.individualresearch.auth.AuthFacade;
import kr.ac.dongguk.individualresearch.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final AuthFacade authFacade;
    private final CourseService courseService;

    public CourseController(AuthFacade authFacade, CourseService courseService) {
        this.authFacade = authFacade;
        this.courseService = courseService;
    }

    @GetMapping
    public ApiResponse<CourseListResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        authFacade.currentUser(authorization);
        return ApiResponse.ok(courseService.list(keyword));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailResponse> detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long courseId
    ) {
        authFacade.currentUser(authorization);
        return ApiResponse.ok(courseService.detail(courseId));
    }
}

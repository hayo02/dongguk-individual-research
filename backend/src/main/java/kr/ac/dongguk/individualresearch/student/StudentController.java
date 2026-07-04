package kr.ac.dongguk.individualresearch.student;

import kr.ac.dongguk.individualresearch.auth.AuthFacade;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.auth.UserRole;
import kr.ac.dongguk.individualresearch.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    private final AuthFacade authFacade;
    private final StudentDashboardService dashboardService;

    public StudentController(AuthFacade authFacade, StudentDashboardService dashboardService) {
        this.authFacade = authFacade;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<StudentDashboardResponse> dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
        PublicUser student = authFacade.currentUser(authorization, UserRole.STUDENT);
        return ApiResponse.ok(dashboardService.dashboard(student));
    }
}

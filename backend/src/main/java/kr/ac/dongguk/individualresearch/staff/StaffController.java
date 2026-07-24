package kr.ac.dongguk.individualresearch.staff;

import kr.ac.dongguk.individualresearch.auth.AuthFacade;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.auth.UserRole;
import kr.ac.dongguk.individualresearch.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private final AuthFacade authFacade;
    private final StaffDashboardService dashboardService;
    private final StaffApplicationService applicationService;

    public StaffController(
            AuthFacade authFacade,
            StaffDashboardService dashboardService,
            StaffApplicationService applicationService
    ) {
        this.authFacade = authFacade;
        this.dashboardService = dashboardService;
        this.applicationService = applicationService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<StaffDashboardResponse> dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
        PublicUser staff = authFacade.currentUser(authorization, UserRole.STAFF);
        return ApiResponse.ok(dashboardService.dashboard(staff));
    }

    @GetMapping("/applications")
    public ApiResponse<StaffApplicationListResponse> applications(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String studentLoginId,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String professorName,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        authFacade.currentUser(authorization, UserRole.STAFF);
        return ApiResponse.ok(applicationService.list(
                status, studentName, studentLoginId, courseName, professorName,
                keyword, sort, page, size));
    }

    @GetMapping("/applications/{applicationId}")
    public ApiResponse<StaffApplicationDetailResponse> application(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long applicationId
    ) {
        authFacade.currentUser(authorization, UserRole.STAFF);
        return ApiResponse.ok(applicationService.detail(applicationId));
    }
}

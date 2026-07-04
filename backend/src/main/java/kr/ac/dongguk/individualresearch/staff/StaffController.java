package kr.ac.dongguk.individualresearch.staff;

import kr.ac.dongguk.individualresearch.auth.AuthFacade;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.auth.UserRole;
import kr.ac.dongguk.individualresearch.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private final AuthFacade authFacade;
    private final StaffDashboardService dashboardService;

    public StaffController(AuthFacade authFacade, StaffDashboardService dashboardService) {
        this.authFacade = authFacade;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<StaffDashboardResponse> dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
        PublicUser staff = authFacade.currentUser(authorization, UserRole.STAFF);
        return ApiResponse.ok(dashboardService.dashboard(staff));
    }
}

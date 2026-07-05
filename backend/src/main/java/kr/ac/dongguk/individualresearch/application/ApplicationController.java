package kr.ac.dongguk.individualresearch.application;

import kr.ac.dongguk.individualresearch.auth.AuthFacade;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.auth.UserRole;
import kr.ac.dongguk.individualresearch.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
    private final AuthFacade authFacade;
    private final ApplicationService applicationService;

    public ApplicationController(AuthFacade authFacade, ApplicationService applicationService) {
        this.authFacade = authFacade;
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApplicationCreateResponse>> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ApplicationRequest request
    ) {
        PublicUser student = authFacade.currentUser(authorization, UserRole.STUDENT);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(applicationService.create(student, request)));
    }

    @GetMapping("/me/current")
    public ApiResponse<ApplicationDetailResponse> current(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        PublicUser student = authFacade.currentUser(authorization, UserRole.STUDENT);
        return ApiResponse.ok(applicationService.current(student));
    }

    @PatchMapping("/me/current")
    public ApiResponse<ApplicationDetailResponse> updateCurrent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ApplicationUpdateRequest request
    ) {
        PublicUser student = authFacade.currentUser(authorization, UserRole.STUDENT);
        return ApiResponse.ok(applicationService.updateCurrent(student, request));
    }

    @DeleteMapping("/me/current")
    public ApiResponse<Void> deleteCurrent(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        PublicUser student = authFacade.currentUser(authorization, UserRole.STUDENT);
        applicationService.deleteCurrent(student);
        return ApiResponse.okMessage("임시저장 신청서를 삭제했습니다.");
    }
}

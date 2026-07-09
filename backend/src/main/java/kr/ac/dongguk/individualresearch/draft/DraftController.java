package kr.ac.dongguk.individualresearch.draft;

import kr.ac.dongguk.individualresearch.auth.AuthFacade;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.auth.UserRole;
import kr.ac.dongguk.individualresearch.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {
    private final AuthFacade auth;
    private final DraftService service;

    public DraftController(AuthFacade auth, DraftService service) {
        this.auth = auth;
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DraftResponse>> create(
            @RequestHeader(value="Authorization", required=false) String authorization,
            @RequestBody(required=false) DraftRequest request) {
        PublicUser user = auth.currentUser(authorization, UserRole.STUDENT);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(user, request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DraftResponse> get(
            @RequestHeader(value="Authorization", required=false) String authorization,
            @PathVariable long id) {
        return ApiResponse.ok(service.get(auth.currentUser(authorization, UserRole.STUDENT), id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<DraftResponse> update(
            @RequestHeader(value="Authorization", required=false) String authorization,
            @PathVariable long id, @RequestBody DraftRequest request) {
        return ApiResponse.ok(service.update(auth.currentUser(authorization, UserRole.STUDENT), id, request));
    }
}

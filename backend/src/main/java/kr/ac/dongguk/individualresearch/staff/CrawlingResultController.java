package kr.ac.dongguk.individualresearch.staff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import kr.ac.dongguk.individualresearch.auth.AuthFacade;
import kr.ac.dongguk.individualresearch.auth.UserRole;
import kr.ac.dongguk.individualresearch.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CrawlingResultController {
    private final AuthFacade auth;
    private final CrawlingResultService service;

    public CrawlingResultController(AuthFacade auth, CrawlingResultService service) {
        this.auth = auth;
        this.service = service;
    }

    @GetMapping("/api/staff/crawling/latest")
    public ApiResponse<ObjectNode> latest(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.currentUser(authorization, UserRole.STAFF);
        return ApiResponse.ok(service.latest());
    }
}

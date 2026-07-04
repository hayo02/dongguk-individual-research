package kr.ac.dongguk.individualresearch.notice;

import kr.ac.dongguk.individualresearch.auth.AuthFacade;
import kr.ac.dongguk.individualresearch.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final AuthFacade authFacade;
    private final NoticeService noticeService;

    public NoticeController(AuthFacade authFacade, NoticeService noticeService) {
        this.authFacade = authFacade;
        this.noticeService = noticeService;
    }

    @GetMapping("/current")
    public ApiResponse<NoticeResponse> current(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authFacade.currentUser(authorization);
        return ApiResponse.ok(noticeService.toResponse(noticeService.currentNotice()));
    }
}

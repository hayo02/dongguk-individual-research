package kr.ac.dongguk.individualresearch.student;

import java.util.List;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.notice.Notice;
import kr.ac.dongguk.individualresearch.notice.NoticeService;
import org.springframework.stereotype.Service;

@Service
public class StudentDashboardService {
    private final ApplicationRepository applicationRepository;
    private final NoticeService noticeService;

    public StudentDashboardService(ApplicationRepository applicationRepository, NoticeService noticeService) {
        this.applicationRepository = applicationRepository;
        this.noticeService = noticeService;
    }

    public StudentDashboardResponse dashboard(PublicUser student) {
        Notice notice = noticeService.currentNotice();
        ApplicationStatus status = applicationRepository.findLatestStatusByStudentId(student.id())
                .orElse(ApplicationStatus.NO_APPLICATION);
        return new StudentDashboardResponse(
                student,
                noticeService.toResponse(notice),
                status,
                primaryAction(status),
                List.of(noticeService.toSummary(notice)),
                List.of(
                        "신청 안내 확인",
                        "개설 과목 선택",
                        "신청서 작성",
                        "신청서 다운로드 및 교수 서명",
                        "서명본·증빙자료 업로드",
                        "최종 제출"
                )
        );
    }

    private String primaryAction(ApplicationStatus status) {
        return switch (status) {
            case NO_APPLICATION -> "개별연구 신청하기";
            case DRAFT -> "작성 중인 신청서 계속하기";
            case SUBMITTED -> "제출 내역 확인";
            case REVISION_REQUESTED -> "보완 내용 확인 및 수정";
            case APPROVED -> "승인 결과 확인";
            case REJECTED -> "반려 결과 확인";
        };
    }
}

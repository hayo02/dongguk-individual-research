package kr.ac.dongguk.individualresearch.student;

import java.time.format.DateTimeFormatter;
import java.util.List;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.notice.Notice;
import kr.ac.dongguk.individualresearch.notice.NoticeService;
import kr.ac.dongguk.individualresearch.student.StudentDashboardResponse.DashboardMetric;
import kr.ac.dongguk.individualresearch.student.StudentDashboardResponse.DashboardPanel;
import kr.ac.dongguk.individualresearch.student.StudentDashboardResponse.DashboardStep;
import kr.ac.dongguk.individualresearch.student.StudentDashboardResponse.StudentSummary;
import kr.ac.dongguk.individualresearch.student.StudentDashboardResponse.Notification;
import kr.ac.dongguk.individualresearch.application.ReviewHistoryRepository;
import org.springframework.stereotype.Service;

@Service
public class StudentDashboardService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final ApplicationRepository applicationRepository;
    private final NoticeService noticeService;
    private final ReviewHistoryRepository reviewHistoryRepository;

    public StudentDashboardService(
            ApplicationRepository applicationRepository,
            NoticeService noticeService,
            ReviewHistoryRepository reviewHistoryRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.noticeService = noticeService;
        this.reviewHistoryRepository = reviewHistoryRepository;
    }

    public StudentDashboardResponse dashboard(PublicUser student) {
        Notice notice = noticeService.currentNotice();
        ApplicationStatus status = applicationRepository.findLatestStatusByStudentId(student.id())
                .orElse(ApplicationStatus.NO_APPLICATION);
        String period = "%s - %s".formatted(notice.startDate().format(DATE_FORMAT), notice.endDate().format(DATE_FORMAT));
        List<String> processSummary = List.of(
                "신청 안내 확인",
                "개설 과목 선택",
                "신청서 작성",
                "신청서 다운로드 및 교수 서명",
                "서명본·증빙자료 업로드",
                "최종 제출"
        );
        return new StudentDashboardResponse(
                "S1",
                "학생 대시보드",
                student,
                new StudentSummary(
                        student.name(),
                        student.loginId(),
                        student.department(),
                        notice.semester(),
                        period
                ),
                noticeService.toResponse(notice),
                status,
                statusLabel(status),
                primaryAction(status),
                List.of(
                        new DashboardMetric("학생", "%s / %s".formatted(student.name(), student.loginId()), student.department(), "neutral"),
                        new DashboardMetric("신청 학기", notice.semester(), "", "neutral"),
                        new DashboardMetric("신청 가능 여부", availabilityLabel(status), statusDescription(status), statusTone(status))
                ),
                List.of(
                        new DashboardPanel("현재 신청 상태", statusDescription(status), primaryAction(status), targetPage(status)),
                        new DashboardPanel("최근 공지와 절차 요약", notice.title(), "신청 안내 확인", "S2"),
                        new DashboardPanel("내 신청 현황", "제출 후 검토 상태와 보완 요청 내용을 확인합니다.", "내 신청 현황 확인", "S9")
                ),
                List.of(noticeService.toSummary(notice)),
                processSummary,
                processSummary.stream()
                        .map(step -> new DashboardStep(step, isCompletedStep(status, step)))
                        .toList(),
                reviewHistoryRepository.findLatestByStudentId(student.id())
                        .filter(history -> "REVISION_REQUESTED".equals(history.changedStatus()))
                        .map(history -> new Notification(
                                "REVISION_REQUESTED",
                                "신청서 보완 요청이 도착했습니다.",
                                history.comment(),
                                history.reviewedAt(),
                                history.applicationId()
                        ))
                        .orElse(null)
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

    private String statusLabel(ApplicationStatus status) {
        return switch (status) {
            case NO_APPLICATION -> "신청 전";
            case DRAFT -> "작성 중";
            case SUBMITTED -> "제출 완료";
            case REVISION_REQUESTED -> "보완 요청";
            case APPROVED -> "승인";
            case REJECTED -> "반려";
        };
    }

    private String availabilityLabel(ApplicationStatus status) {
        return switch (status) {
            case NO_APPLICATION, DRAFT, REVISION_REQUESTED -> "신청 가능";
            case SUBMITTED -> "검토 중";
            case APPROVED -> "승인 완료";
            case REJECTED -> "반려";
        };
    }

    private String statusDescription(ApplicationStatus status) {
        return switch (status) {
            case NO_APPLICATION -> "아직 제출한 신청서가 없습니다.";
            case DRAFT -> "작성 중인 신청서를 이어서 작성할 수 있습니다.";
            case SUBMITTED -> "제출한 신청서가 교직원 검토를 기다리고 있습니다.";
            case REVISION_REQUESTED -> "교직원이 보완을 요청했습니다. 수정 후 다시 제출해 주세요.";
            case APPROVED -> "신청이 승인되었습니다.";
            case REJECTED -> "신청이 반려되었습니다. 반려 사유를 확인해 주세요.";
        };
    }

    private String statusTone(ApplicationStatus status) {
        return switch (status) {
            case NO_APPLICATION, DRAFT -> "ready";
            case SUBMITTED -> "pending";
            case REVISION_REQUESTED -> "warning";
            case APPROVED -> "success";
            case REJECTED -> "danger";
        };
    }

    private String targetPage(ApplicationStatus status) {
        return switch (status) {
            case NO_APPLICATION -> "S2";
            case DRAFT, REVISION_REQUESTED -> "S10";
            case SUBMITTED, APPROVED, REJECTED -> "S9";
        };
    }

    private boolean isCompletedStep(ApplicationStatus status, String step) {
        if (status == ApplicationStatus.NO_APPLICATION || status == ApplicationStatus.DRAFT) {
            return false;
        }
        if (status == ApplicationStatus.REVISION_REQUESTED) {
            return !step.equals("최종 제출");
        }
        return true;
    }
}

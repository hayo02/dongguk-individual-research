package kr.ac.dongguk.individualresearch.application;

import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.application.ApplicationAutofillResponse.CourseAutofill;
import kr.ac.dongguk.individualresearch.application.ApplicationAutofillResponse.StudentAutofill;
import kr.ac.dongguk.individualresearch.application.ApplicationDetailResponse.CourseSummary;
import kr.ac.dongguk.individualresearch.application.ApplicationDetailResponse.StudentSummary;
import kr.ac.dongguk.individualresearch.application.ApplicationDetailResponse.ReviewHistorySummary;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApplicationService {
    private final ApplicationRepository applicationRepository;
    private final ApplicationDocumentService applicationDocumentService;
    private final ReviewHistoryRepository reviewHistoryRepository;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationDocumentService applicationDocumentService,
            ReviewHistoryRepository reviewHistoryRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.applicationDocumentService = applicationDocumentService;
        this.reviewHistoryRepository = reviewHistoryRepository;
    }

    public ApplicationCreateResponse create(PublicUser student, ApplicationRequest request) {
        if (request == null || request.courseId() == null) {
            throw new IllegalArgumentException("신청할 과목을 선택해 주세요.");
        }
        long courseId = request.courseId();
        if (!applicationRepository.courseExists(courseId)) {
            throw new IllegalArgumentException("개설 과목 정보를 찾을 수 없습니다.");
        }
        if (applicationRepository.existsInSameNotice(student.id(), courseId)) {
            throw new ApplicationConflictException("이미 해당 학기 개별연구 신청서가 있습니다.");
        }

        long applicationId = applicationRepository.create(student.id(), courseId);
        return new ApplicationCreateResponse(applicationId, ApplicationStatus.DRAFT.name(), courseId);
    }

    public ApplicationDetailResponse current(PublicUser student) {
        return applicationRepository.findCurrentByStudentId(student.id())
                .map(this::toDetail)
                .orElseThrow(() -> new ApplicationNotFoundException("내 신청서 정보를 찾을 수 없습니다."));
    }

    public ApplicationDetailResponse updateCurrent(PublicUser student, ApplicationUpdateRequest request) {
        ApplicationRecord current = applicationRepository.findCurrentByStudentId(student.id())
                .orElseThrow(() -> new ApplicationNotFoundException("내 신청서 정보를 찾을 수 없습니다."));
        if (current.status() != ApplicationStatus.DRAFT && current.status() != ApplicationStatus.REVISION_REQUESTED) {
            throw new IllegalArgumentException("현재 상태에서는 신청서를 수정할 수 없습니다.");
        }

        applicationRepository.updateCurrent(
                current.id(),
                normalize(request == null ? null : request.contact()),
                normalize(request == null ? null : request.email()),
                normalize(request == null ? null : request.applicationReason()),
                normalize(request == null ? null : request.researchPurpose())
        );
        return applicationRepository.findCurrentByStudentId(student.id())
                .map(this::toDetail)
                .orElseThrow(() -> new ApplicationNotFoundException("내 신청서 정보를 찾을 수 없습니다."));
    }

    public void deleteCurrent(PublicUser student) {
        ApplicationRecord current = applicationRepository.findCurrentByStudentId(student.id())
                .orElseThrow(() -> new ApplicationNotFoundException("내 신청서 정보를 찾을 수 없습니다."));
        if (current.status() != ApplicationStatus.DRAFT) {
            throw new IllegalArgumentException("임시저장 상태의 신청서만 삭제할 수 있습니다.");
        }

        applicationRepository.delete(current.id());
    }

    public ApplicationAutofillResponse autofill(PublicUser student, long applicationId) {
        ApplicationRecord application = findOwnedApplication(student, applicationId);
        return new ApplicationAutofillResponse(
                new StudentAutofill(
                        application.studentName(),
                        application.studentLoginId(),
                        application.studentDepartment(),
                        application.studentEmail(),
                        contact(application)
                ),
                new CourseAutofill(
                        application.courseName(),
                        application.professorName(),
                        application.courseCode(),
                        application.semester(),
                        application.weeklyHours() == null ? null : "주 " + application.weeklyHours() + "시간"
                )
        );
    }

    public ApplicationDocumentResponse hwpDocument(PublicUser student, long applicationId) {
        ApplicationRecord application = findOwnedApplication(student, applicationId);
        return applicationDocumentService.hwp(application);
    }

    public ApplicationDocumentResponse interviewImage(PublicUser student, long applicationId) {
        ApplicationRecord application = findOwnedApplication(student, applicationId);
        return applicationDocumentService.interviewImage(application);
    }

    private ApplicationDetailResponse toDetail(ApplicationRecord record) {
        return new ApplicationDetailResponse(
                record.id(),
                record.status().name(),
                new StudentSummary(
                        record.studentId(),
                        record.studentLoginId(),
                        record.studentName(),
                        record.studentDepartment(),
                        record.studentEmail(),
                        record.studentPhone(),
                        contact(record)
                ),
                new CourseSummary(
                        record.courseId(),
                        record.noticeId(),
                        record.semester(),
                        record.department(),
                        record.professorName(),
                        record.courseName(),
                        record.courseType(),
                        record.courseCode(),
                        record.researchDescription(),
                        record.weeklyHours()
                ),
                record.applicationReason(),
                record.researchPurpose(),
                java.util.List.of(),
                reviewHistoryRepository.findByApplicationId(record.id()).stream()
                        .map(history -> new ReviewHistorySummary(
                                history.previousStatus(), history.changedStatus(), history.comment(),
                                history.reviewerName(), history.reviewedAt()))
                        .toList(),
                record.submittedAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String contact(ApplicationRecord record) {
        return StringUtils.hasText(record.contact()) ? record.contact() : record.studentPhone();
    }

    public ApplicationRecord findOwnedApplication(PublicUser student, long applicationId) {
        ApplicationRecord application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException("신청서 정보를 찾을 수 없습니다."));
        if (application.studentId() != student.id()) {
            throw new ApplicationForbiddenException("본인 신청서만 조회할 수 있습니다.");
        }
        return application;
    }
}

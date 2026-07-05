package kr.ac.dongguk.individualresearch.application;

import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.application.ApplicationDetailResponse.CourseSummary;
import kr.ac.dongguk.individualresearch.application.ApplicationDetailResponse.StudentSummary;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApplicationService {
    private final ApplicationRepository applicationRepository;

    public ApplicationService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
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
                        record.studentPhone()
                ),
                new CourseSummary(
                        record.courseId(),
                        record.noticeId(),
                        record.department(),
                        record.professorName(),
                        record.courseName(),
                        record.courseType(),
                        record.courseCode()
                ),
                record.applicationReason(),
                record.researchPurpose(),
                java.util.List.of(),
                java.util.List.of(),
                record.submittedAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

package kr.ac.dongguk.individualresearch.application;

import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationSubmitService {
    private final ApplicationService applications;
    private final ApplicationRepository repository;
    private final ApplicationValidationService validation;
    private final ReviewHistoryRepository reviewHistoryRepository;

    public ApplicationSubmitService(ApplicationService applications, ApplicationRepository repository,
                                    ApplicationValidationService validation,
                                    ReviewHistoryRepository reviewHistoryRepository) {
        this.applications = applications;
        this.repository = repository;
        this.validation = validation;
        this.reviewHistoryRepository = reviewHistoryRepository;
    }

    @Transactional
    public ApplicationSubmitResponse submit(PublicUser student, long applicationId) {
        ApplicationRecord application = applications.findOwnedApplication(student, applicationId);
        if (application.status() == ApplicationStatus.SUBMITTED) {
            throw new ApplicationFlowException("APPLICATION_ALREADY_SUBMITTED", "이미 제출된 신청서입니다.");
        }
        if (application.status() != ApplicationStatus.DRAFT &&
                application.status() != ApplicationStatus.REVISION_REQUESTED) {
            throw new ApplicationFlowException("APPLICATION_INVALID", "현재 상태에서는 신청서를 제출할 수 없습니다.");
        }
        ApplicationValidationResponse result = validation.validate(application);
        if (!result.valid()) {
            throw new ApplicationFlowException("APPLICATION_INVALID",
                    "필수 항목을 입력해 주세요: " + String.join(", ", result.missingFields()));
        }
        if (!repository.submit(applicationId)) {
            throw new ApplicationFlowException("APPLICATION_SUBMIT_FAILED", "신청서를 제출하지 못했습니다.");
        }
        if (application.status() == ApplicationStatus.REVISION_REQUESTED) {
            reviewHistoryRepository.insert(
                    applicationId,
                    ApplicationStatus.REVISION_REQUESTED.name(),
                    ApplicationStatus.SUBMITTED.name(),
                    "학생이 보완 내용을 반영하여 신청서를 다시 제출했습니다.",
                    student.id()
            );
        }
        ApplicationRecord submitted = applications.findOwnedApplication(student, applicationId);
        return new ApplicationSubmitResponse(submitted.id(), submitted.status().name(), submitted.submittedAt());
    }
}

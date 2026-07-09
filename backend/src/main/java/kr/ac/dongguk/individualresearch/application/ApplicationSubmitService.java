package kr.ac.dongguk.individualresearch.application;

import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.springframework.stereotype.Service;

@Service
public class ApplicationSubmitService {
    private final ApplicationService applications;
    private final ApplicationRepository repository;
    private final ApplicationValidationService validation;

    public ApplicationSubmitService(ApplicationService applications, ApplicationRepository repository,
                                    ApplicationValidationService validation) {
        this.applications = applications;
        this.repository = repository;
        this.validation = validation;
    }

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
        ApplicationRecord submitted = applications.findOwnedApplication(student, applicationId);
        return new ApplicationSubmitResponse(submitted.id(), submitted.status().name(), submitted.submittedAt());
    }
}

package kr.ac.dongguk.individualresearch.application;

import java.util.ArrayList;
import java.util.List;
import kr.ac.dongguk.individualresearch.application.file.ApplicationFileDocumentType;
import kr.ac.dongguk.individualresearch.application.file.ApplicationFileRepository;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApplicationValidationService {
    private final ApplicationService applications;
    private final ApplicationFileRepository files;

    public ApplicationValidationService(
            ApplicationService applications,
            ApplicationFileRepository files
    ) {
        this.applications = applications;
        this.files = files;
    }

    public ApplicationValidationResponse validate(PublicUser student, long applicationId) {
        return validate(applications.findOwnedApplication(student, applicationId));
    }

    ApplicationValidationResponse validate(ApplicationRecord application) {
        List<String> missing = new ArrayList<>();
        required(missing, "studentName", application.studentName());
        required(missing, "studentNumber", application.studentLoginId());
        required(missing, "department", application.studentDepartment());
        required(missing, "phone", contact(application));
        required(missing, "email", application.studentEmail());
        required(missing, "semester", application.semester());
        required(missing, "courseName", application.courseName());
        required(missing, "professorName", application.professorName());
        required(missing, "applicationReason", application.applicationReason());
        required(missing, "researchPurpose", application.researchPurpose());

        List<String> missingFiles = files.existsByType(
                application.id(), ApplicationFileDocumentType.SIGNED_APPLICATION)
                ? List.of()
                : List.of(ApplicationFileDocumentType.SIGNED_APPLICATION.name());
        return new ApplicationValidationResponse(missing.isEmpty() && missingFiles.isEmpty(), missing, missingFiles);
    }

    private void required(List<String> missing, String name, String value) {
        if (!StringUtils.hasText(value)) missing.add(name);
    }

    private String contact(ApplicationRecord application) {
        return StringUtils.hasText(application.contact()) ? application.contact() : application.studentPhone();
    }
}

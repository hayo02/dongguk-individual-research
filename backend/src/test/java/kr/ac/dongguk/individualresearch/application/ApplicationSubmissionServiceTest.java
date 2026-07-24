package kr.ac.dongguk.individualresearch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.auth.UserRole;
import kr.ac.dongguk.individualresearch.application.file.ApplicationFileDocumentType;
import kr.ac.dongguk.individualresearch.application.file.ApplicationFileRepository;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class ApplicationSubmissionServiceTest {
    private final PublicUser student = new PublicUser(
            10L, "2026123456", "홍길동", UserRole.STUDENT,
            "컴퓨터·AI학부", "student@example.com", "010-1111-2222");

    @Test
    void validationReturnsValidWhenRequiredValuesExist() {
        ApplicationService applications = mock(ApplicationService.class);
        when(applications.findOwnedApplication(student, 1L)).thenReturn(record(ApplicationStatus.DRAFT, true));
        ApplicationFileRepository files = mock(ApplicationFileRepository.class);
        when(files.existsByType(1L, ApplicationFileDocumentType.SIGNED_APPLICATION)).thenReturn(true);

        ApplicationValidationResponse result = new ApplicationValidationService(applications, files).validate(student, 1L);

        assertThat(result.valid()).isTrue();
        assertThat(result.missingFields()).isEmpty();
        assertThat(result.missingFiles()).isEmpty();
    }

    @Test
    void validationListsMissingRequiredValues() {
        ApplicationService applications = mock(ApplicationService.class);
        when(applications.findOwnedApplication(student, 1L)).thenReturn(record(ApplicationStatus.DRAFT, false));
        ApplicationFileRepository files = mock(ApplicationFileRepository.class);

        ApplicationValidationResponse result = new ApplicationValidationService(applications, files).validate(student, 1L);

        assertThat(result.valid()).isFalse();
        assertThat(result.missingFields()).contains("phone", "applicationReason", "researchPurpose");
        assertThat(result.missingFiles()).containsExactly("SIGNED_APPLICATION");
    }

    @Test
    void submitChangesStatusAfterServerValidation() {
        ApplicationService applications = mock(ApplicationService.class);
        ApplicationRepository repository = mock(ApplicationRepository.class);
        ApplicationFileRepository files = mock(ApplicationFileRepository.class);
        when(files.existsByType(1L, ApplicationFileDocumentType.SIGNED_APPLICATION)).thenReturn(true);
        ApplicationValidationService validation = new ApplicationValidationService(applications, files);
        ApplicationRecord draft = record(ApplicationStatus.DRAFT, true);
        ApplicationRecord submitted = record(ApplicationStatus.SUBMITTED, true);
        when(applications.findOwnedApplication(student, 1L)).thenReturn(draft, submitted);
        when(repository.submit(1L)).thenReturn(true);

        ApplicationSubmitResponse result =
                new ApplicationSubmitService(
                        applications, repository, validation, mock(ReviewHistoryRepository.class)
                ).submit(student, 1L);

        assertThat(result.status()).isEqualTo("SUBMITTED");
        verify(repository).submit(1L);
    }

    @Test
    void revisionRequestedApplicationCanBeResubmittedAndRecordsHistory() {
        ApplicationService applications = mock(ApplicationService.class);
        ApplicationRepository repository = mock(ApplicationRepository.class);
        ReviewHistoryRepository histories = mock(ReviewHistoryRepository.class);
        ApplicationFileRepository files = mock(ApplicationFileRepository.class);
        when(files.existsByType(1L, ApplicationFileDocumentType.SIGNED_APPLICATION)).thenReturn(true);
        ApplicationValidationService validation = new ApplicationValidationService(applications, files);
        when(applications.findOwnedApplication(student, 1L))
                .thenReturn(
                        record(ApplicationStatus.REVISION_REQUESTED, true),
                        record(ApplicationStatus.SUBMITTED, true)
                );
        when(repository.submit(1L)).thenReturn(true);

        ApplicationSubmitResponse result =
                new ApplicationSubmitService(applications, repository, validation, histories)
                        .submit(student, 1L);

        assertThat(result.status()).isEqualTo("SUBMITTED");
        verify(histories).insert(
                1L,
                "REVISION_REQUESTED",
                "SUBMITTED",
                "학생이 보완 내용을 반영하여 신청서를 다시 제출했습니다.",
                student.id()
        );
    }

    @Test
    void invalidOrAlreadySubmittedApplicationCannotBeSubmitted() {
        ApplicationService applications = mock(ApplicationService.class);
        ApplicationRepository repository = mock(ApplicationRepository.class);
        ApplicationFileRepository files = mock(ApplicationFileRepository.class);
        ApplicationValidationService validation = new ApplicationValidationService(applications, files);
        when(applications.findOwnedApplication(student, 1L)).thenReturn(record(ApplicationStatus.DRAFT, false));
        when(applications.findOwnedApplication(student, 2L)).thenReturn(record(ApplicationStatus.SUBMITTED, true));
        ApplicationSubmitService service = new ApplicationSubmitService(
                applications, repository, validation, mock(ReviewHistoryRepository.class)
        );

        assertThatThrownBy(() -> service.submit(student, 1L))
                .isInstanceOf(ApplicationFlowException.class)
                .hasMessageContaining("필수 항목");
        assertThatThrownBy(() -> service.submit(student, 2L))
                .isInstanceOf(ApplicationFlowException.class)
                .hasMessageContaining("이미 제출");
        verify(repository, never()).submit(anyLong());
    }

    @Test
    void pdfContainsValidPdfHeader() {
        ApplicationService applications = mock(ApplicationService.class);
        when(applications.findOwnedApplication(student, 1L)).thenReturn(record(ApplicationStatus.DRAFT, true));
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine templates = new SpringTemplateEngine();
        templates.setTemplateResolver(resolver);

        ApplicationDocumentResponse pdf =
                new ApplicationPdfService(applications, templates, "C:/Windows/Fonts/malgun.ttf")
                        .generate(student, 1L);

        assertThat(pdf.contentType()).isEqualTo("application/pdf");
        assertThat(pdf.content()).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void ownershipCheckDistinguishesForbiddenAndNotFound() {
        ApplicationRepository repository = mock(ApplicationRepository.class);
        ApplicationService applications = new ApplicationService(
                repository,
                mock(ApplicationDocumentService.class),
                mock(ReviewHistoryRepository.class)
        );
        ApplicationRecord anotherStudentsApplication = record(ApplicationStatus.DRAFT, true);
        when(repository.findById(1L)).thenReturn(Optional.of(anotherStudentsApplication));
        when(repository.findById(404L)).thenReturn(Optional.empty());
        PublicUser anotherStudent = new PublicUser(
                99L, "2026999999", "다른 학생", UserRole.STUDENT,
                "컴퓨터·AI학부", "other@example.com", "010-0000-0000");

        assertThatThrownBy(() -> applications.findOwnedApplication(anotherStudent, 1L))
                .isInstanceOf(ApplicationForbiddenException.class);
        assertThatThrownBy(() -> applications.findOwnedApplication(student, 404L))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    private ApplicationRecord record(ApplicationStatus status, boolean complete) {
        LocalDateTime now = LocalDateTime.now();
        return new ApplicationRecord(
                1L, student.id(), student.loginId(), student.name(), student.department(),
                student.email(), complete ? student.phone() : "", complete ? "010-9999-9999" : "",
                20L, 30L, "2026학년도 여름학기", "컴퓨터·AI학부", "개별연구",
                "기존", "CSE0000", "연구 내용", 3, "김교수", status,
                complete ? "신청 사유" : "", complete ? "연구 목적" : "",
                status == ApplicationStatus.SUBMITTED ? now : null, now, now
        );
    }
}

package kr.ac.dongguk.individualresearch.staff;

import java.util.Arrays;
import java.util.List;
import kr.ac.dongguk.individualresearch.application.ApplicationNotFoundException;
import kr.ac.dongguk.individualresearch.application.ApplicationRecord;
import kr.ac.dongguk.individualresearch.application.ApplicationRepository;
import kr.ac.dongguk.individualresearch.application.ReviewHistoryRepository;
import kr.ac.dongguk.individualresearch.application.file.ApplicationFileRecord;
import kr.ac.dongguk.individualresearch.application.file.ApplicationFileRepository;
import kr.ac.dongguk.individualresearch.staff.StaffApplicationDetailResponse.Application;
import kr.ac.dongguk.individualresearch.staff.StaffApplicationDetailResponse.FileItem;
import kr.ac.dongguk.individualresearch.staff.StaffApplicationDetailResponse.Student;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import kr.ac.dongguk.individualresearch.auth.PublicUser;

@Service
public class StaffApplicationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final StaffApplicationRepository staffRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationFileRepository fileRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;

    public StaffApplicationService(
            StaffApplicationRepository staffRepository,
            ApplicationRepository applicationRepository,
            ApplicationFileRepository fileRepository,
            ReviewHistoryRepository reviewHistoryRepository
    ) {
        this.staffRepository = staffRepository;
        this.applicationRepository = applicationRepository;
        this.fileRepository = fileRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
    }

    public StaffApplicationListResponse list(
            String status,
            String studentName,
            String studentLoginId,
            String courseName,
            String professorName,
            String keyword,
            String sort,
            int page,
            int size
    ) {
        String normalizedStatus = normalizeStatus(status);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        boolean ascending = "asc".equalsIgnoreCase(sort);
        long total = staffRepository.count(
                normalizedStatus, studentName, studentLoginId, courseName, professorName, keyword);
        var applications = staffRepository.findAll(
                normalizedStatus, studentName, studentLoginId, courseName, professorName, keyword,
                ascending, normalizedPage, normalizedSize);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalizedSize);
        return new StaffApplicationListResponse(
                applications, normalizedPage, normalizedSize, total, totalPages);
    }

    public StaffApplicationDetailResponse detail(long applicationId) {
        ApplicationRecord record = applicationRepository.findById(applicationId)
                .filter(application -> application.status() != ApplicationStatus.DRAFT)
                .orElseThrow(() -> new ApplicationNotFoundException("검토할 신청서를 찾을 수 없습니다."));
        List<FileItem> files = fileRepository.findByApplicationId(applicationId).stream()
                .map(this::toFileItem)
                .toList();

        return new StaffApplicationDetailResponse(
                record.id(),
                "IR-2026-%04d".formatted(record.id()),
                record.status().name(),
                statusLabel(record.status().name()),
                new Student(
                        record.studentId(), record.studentName(), record.studentLoginId(),
                        record.studentDepartment(), record.studentEmail(), record.studentPhone(),
                        StringUtils.hasText(record.contact()) ? record.contact() : record.studentPhone()),
                new Application(
                        record.courseId(), record.noticeId(), record.semester(), record.department(),
                        record.courseName(), record.courseType(), record.courseCode(),
                        record.professorName(), record.researchDescription(), record.weeklyHours(),
                        record.applicationReason(), record.researchPurpose()),
                files,
                reviewHistoryRepository.findByApplicationId(applicationId).stream()
                        .map(history -> new StaffApplicationDetailResponse.ReviewHistoryItem(
                                history.previousStatus(), history.changedStatus(), history.comment(),
                                history.reviewerName(), history.reviewedAt()))
                        .toList(),
                record.submittedAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    @Transactional
    public RevisionRequestResponse requestRevision(
            PublicUser reviewer,
            long applicationId,
            RevisionRequest request
    ) {
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new IllegalArgumentException("보완 요청 사유를 입력해 주세요.");
        }
        String reason = request.reason().trim();
        if (reason.length() > 2000) {
            throw new IllegalArgumentException("보완 요청 사유는 2,000자 이하로 입력해 주세요.");
        }
        ApplicationRecord application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException("검토할 신청서를 찾을 수 없습니다."));
        if (application.status() != ApplicationStatus.SUBMITTED) {
            throw new IllegalArgumentException("제출 완료 상태의 신청서에만 보완을 요청할 수 있습니다.");
        }
        if (!applicationRepository.requestRevision(applicationId)) {
            throw new IllegalArgumentException("신청 상태가 변경되어 보완 요청을 처리하지 못했습니다.");
        }
        String comment = request.requireSignedApplication()
                ? "[서명본 재업로드 필요] " + reason
                : reason;
        reviewHistoryRepository.insert(
                applicationId, ApplicationStatus.SUBMITTED.name(),
                ApplicationStatus.REVISION_REQUESTED.name(), comment, reviewer.id());
        var history = reviewHistoryRepository.findByApplicationId(applicationId).get(0);
        return new RevisionRequestResponse(
                applicationId,
                ApplicationStatus.REVISION_REQUESTED.name(),
                statusLabel(ApplicationStatus.REVISION_REQUESTED.name()),
                reason,
                request.requireSignedApplication(),
                history.reviewedAt()
        );
    }

    private FileItem toFileItem(ApplicationFileRecord file) {
        return new FileItem(
                file.id(), file.documentType().name(), file.originalFileName(), file.fileSize(),
                file.contentType(), file.uploadedAt());
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        boolean valid = Arrays.stream(ApplicationStatus.values())
                .anyMatch(candidate -> candidate.name().equals(normalized));
        if (!valid || ApplicationStatus.DRAFT.name().equals(normalized) ||
                ApplicationStatus.NO_APPLICATION.name().equals(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 신청 상태입니다.");
        }
        return normalized;
    }

    static String statusLabel(String status) {
        return switch (ApplicationStatus.valueOf(status)) {
            case NO_APPLICATION -> "신청 없음";
            case DRAFT -> "작성 중";
            case SUBMITTED -> "제출 완료";
            case REVISION_REQUESTED -> "보완 요청";
            case APPROVED -> "승인";
            case REJECTED -> "반려";
        };
    }
}

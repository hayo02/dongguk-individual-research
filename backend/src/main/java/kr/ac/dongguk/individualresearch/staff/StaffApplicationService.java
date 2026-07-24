package kr.ac.dongguk.individualresearch.staff;

import java.util.Arrays;
import java.util.List;
import kr.ac.dongguk.individualresearch.application.ApplicationNotFoundException;
import kr.ac.dongguk.individualresearch.application.ApplicationRecord;
import kr.ac.dongguk.individualresearch.application.ApplicationRepository;
import kr.ac.dongguk.individualresearch.application.file.ApplicationFileRecord;
import kr.ac.dongguk.individualresearch.application.file.ApplicationFileRepository;
import kr.ac.dongguk.individualresearch.staff.StaffApplicationDetailResponse.Application;
import kr.ac.dongguk.individualresearch.staff.StaffApplicationDetailResponse.FileItem;
import kr.ac.dongguk.individualresearch.staff.StaffApplicationDetailResponse.Student;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StaffApplicationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final StaffApplicationRepository staffRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationFileRepository fileRepository;

    public StaffApplicationService(
            StaffApplicationRepository staffRepository,
            ApplicationRepository applicationRepository,
            ApplicationFileRepository fileRepository
    ) {
        this.staffRepository = staffRepository;
        this.applicationRepository = applicationRepository;
        this.fileRepository = fileRepository;
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
                List.of(),
                record.submittedAt(),
                record.createdAt(),
                record.updatedAt()
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

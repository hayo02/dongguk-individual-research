package kr.ac.dongguk.individualresearch.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.dongguk.individualresearch.auth.AuthService;
import kr.ac.dongguk.individualresearch.auth.UserRepository;
import kr.ac.dongguk.individualresearch.auth.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final String noticeSnapshotPath;

    public DatabaseInitializer(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            AuthService authService,
            ObjectMapper objectMapper,
            @Value("${app.notice.snapshot-path:../data/snapshots/individual-research/latest.json}") String noticeSnapshotPath
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.noticeSnapshotPath = noticeSnapshotPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        createTables();
        seedUsers();
        importLatestNotice();
    }

    private void createTables() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    login_id VARCHAR(50) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    department VARCHAR(100),
                    email VARCHAR(100),
                    phone VARCHAR(50),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """
        );
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS notices (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    semester VARCHAR(50) NOT NULL,
                    start_date DATE NOT NULL,
                    end_date DATE NOT NULL,
                    original_url VARCHAR(500),
                    needs_review BOOLEAN NOT NULL DEFAULT FALSE,
                    required_documents TEXT,
                    schedule_info TEXT,
                    submission_info TEXT,
                    notice_notes TEXT,
                    published_at DATE,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """
        );
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS applications (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    student_id BIGINT NOT NULL,
                    course_id BIGINT,
                    status VARCHAR(30) NOT NULL,
                    application_reason TEXT,
                    research_purpose TEXT,
                    submitted_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """
        );
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS revoked_tokens (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    token_hash VARCHAR(64) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    revoked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """
        );
    }

    private void seedUsers() {
        if (!userRepository.existsByLoginId("2026123456")) {
            userRepository.insert(
                    "2026123456",
                    authService.hashPassword("1234"),
                    "테스트 학생",
                    UserRole.STUDENT,
                    "컴퓨터·AI학부",
                    "student@dongguk.edu",
                    "010-1234-5678"
            );
        }
        if (!userRepository.existsByLoginId("2025123456")) {
            userRepository.insert(
                    "2025123456",
                    authService.hashPassword("5678"),
                    "테스트 교직원",
                    UserRole.STAFF,
                    "컴퓨터·AI학부 행정실",
                    "staff@dongguk.edu",
                    "02-0000-0000"
            );
        }
    }

    private void importLatestNotice() {
        Path snapshotPath = resolveSnapshotPath();
        if (!Files.exists(snapshotPath)) {
            seedFallbackNotice();
            return;
        }

        try {
            JsonNode snapshot = objectMapper.readTree(snapshotPath.toFile());
            JsonNode notice = snapshot.path("notice");
            JsonNode autofill = snapshot.path("autofill_candidates");
            JsonNode schedule = snapshot.path("schedule");
            JsonNode submission = snapshot.path("submission");

            String title = text(notice.path("title"), "개별연구 신청 안내");
            String semester = "%s %s".formatted(
                    text(autofill.path("academic_year").path("value"), "2026"),
                    text(autofill.path("semester").path("value"), "여름학기")
            );
            LocalDate publishedAt = date(text(notice.path("written_at"), null), LocalDate.now());
            LocalDate startDate = date(text(schedule.path("application_start").path("value"), null), publishedAt);
            LocalDate endDate = date(text(schedule.path("application_deadline").path("value"), null), startDate);
            String originalUrl = text(notice.path("url"), "https://cs.dongguk.edu/");
            boolean needsReview = snapshot.path("warnings").size() > 0 || snapshot.path("errors").size() > 0;

            deleteLegacyFallbackNotice();
            upsertNotice(
                    title,
                    semester,
                    startDate,
                    endDate,
                    originalUrl,
                    needsReview,
                    objectMapper.writeValueAsString(requiredDocuments(submission)),
                    objectMapper.writeValueAsString(scheduleInfo(schedule)),
                    objectMapper.writeValueAsString(submissionInfo(submission)),
                    noticeNotes(submission),
                    publishedAt
            );
        } catch (Exception exception) {
            seedFallbackNotice();
        }
    }

    private Path resolveSnapshotPath() {
        Path configured = Path.of(noticeSnapshotPath);
        if (Files.exists(configured)) {
            return configured;
        }
        Path workspaceRelative = Path.of("data/snapshots/individual-research/latest.json");
        if (Files.exists(workspaceRelative)) {
            return workspaceRelative;
        }
        return configured;
    }

    private void upsertNotice(
            String title,
            String semester,
            LocalDate startDate,
            LocalDate endDate,
            String originalUrl,
            boolean needsReview,
            String requiredDocuments,
            String scheduleInfo,
            String submissionInfo,
            String noticeNotes,
            LocalDate publishedAt
    ) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notices WHERE original_url = ?",
                Integer.class,
                originalUrl
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    """
                    UPDATE notices
                    SET title = ?, semester = ?, start_date = ?, end_date = ?, needs_review = ?,
                        required_documents = ?, schedule_info = ?, submission_info = ?,
                        notice_notes = ?, published_at = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE original_url = ?
                    """,
                    title,
                    semester,
                    startDate,
                    endDate,
                    needsReview,
                    requiredDocuments,
                    scheduleInfo,
                    submissionInfo,
                    noticeNotes,
                    publishedAt,
                    originalUrl
            );
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO notices (
                    title, semester, start_date, end_date, original_url, needs_review,
                    required_documents, schedule_info, submission_info, notice_notes, published_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                title,
                semester,
                startDate,
                endDate,
                originalUrl,
                needsReview,
                requiredDocuments,
                scheduleInfo,
                submissionInfo,
                noticeNotes,
                publishedAt
        );
    }

    private List<String> requiredDocuments(JsonNode submission) {
        JsonNode documents = submission.path("required_documents");
        if (!documents.isArray() || documents.isEmpty()) {
            return List.of("개별연구 수강신청원", "담당교수 서명이 포함된 수강신청원 또는 서명 불가 시 수강 허가 이메일 등 증빙자료");
        }
        return documents.findValuesAsText("name").stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.equals("담당교수 승인 증빙")
                        ? approvalEvidenceLabel(documents)
                        : value)
                .distinct()
                .toList();
    }

    private String approvalEvidenceLabel(JsonNode documents) {
        for (JsonNode document : documents) {
            if (!"ONE_OF".equals(text(document.path("requirement_type"), ""))) {
                continue;
            }
            JsonNode alternatives = document.path("alternatives");
            if (!alternatives.isArray() || alternatives.isEmpty()) {
                return "담당교수 승인 증빙";
            }
            String signedForm = "";
            String fallbackEvidence = "";
            for (JsonNode alternative : alternatives) {
                String type = text(alternative.path("type"), "");
                if ("SIGNED_APPLICATION_FORM".equals(type)) {
                    signedForm = text(alternative.path("name"), "");
                }
                if ("EMAIL_PERMISSION_EVIDENCE".equals(type)) {
                    fallbackEvidence = text(alternative.path("name"), "");
                }
            }
            if (!signedForm.isBlank() && !fallbackEvidence.isBlank()) {
                return "%s 또는 서명이 어려운 경우 %s".formatted(signedForm, fallbackEvidence);
            }
        }
        return "담당교수 서명이 포함된 수강신청원 또는 서명 불가 시 수강 허가 이메일 등 증빙자료";
    }

    private Map<String, String> scheduleInfo(JsonNode schedule) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "신청 마감", schedule.path("application_deadline").path("value"));
        putIfPresent(values, "인터뷰 및 서류 제출", schedule.path("document_submission_period").path("value"));
        putIfPresent(values, "수강신청", schedule.path("course_registration_period").path("value"));
        putIfPresent(values, "연구 참여", schedule.path("research_period").path("value"));
        return values;
    }

    private Map<String, String> submissionInfo(JsonNode submission) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "제출 방법", submission.path("method").path("value"));
        putIfPresent(values, "제출 장소", submission.path("location").path("value"));
        putIfPresent(values, "문의", submission.path("contact").path("value"));
        values.put("교수 연락 필요", booleanLabel(submission.path("professor_contact_required").path("value").asBoolean(false)));
        values.put("이메일 제출 가능", booleanLabel(submission.path("email_submission_allowed").path("value").asBoolean(false)));
        return values;
    }

    private String noticeNotes(JsonNode submission) {
        JsonNode cautions = submission.path("cautions");
        if (!cautions.isArray() || cautions.isEmpty()) {
            return "담당 교수 면담 또는 승인 증빙을 준비한 뒤 신청서를 제출해 주세요.";
        }
        return cautions.findValues("value").stream()
                .map(node -> text(node, ""))
                .filter(value -> !value.isBlank())
                .limit(3)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("담당 교수 면담 또는 승인 증빙을 준비한 뒤 신청서를 제출해 주세요.");
    }

    private void putIfPresent(Map<String, String> values, String key, JsonNode node) {
        String value = text(node, "");
        if (!value.isBlank()) {
            values.put(key, value);
        }
    }

    private String booleanLabel(boolean value) {
        return value ? "예" : "아니오";
    }

    private String text(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? fallback : value;
    }

    private LocalDate date(String value, LocalDate fallback) {
        if (value == null || value.length() < 10) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.substring(0, 10).replace('.', '-'));
        } catch (Exception exception) {
            return fallback;
        }
    }

    private void deleteLegacyFallbackNotice() {
        jdbcTemplate.update(
                "DELETE FROM notices WHERE original_url = ? AND title = ?",
                "https://cs.dongguk.edu/",
                "2026학년도 여름학기 개별연구 신청 안내"
        );
    }

    private void seedFallbackNotice() {
        upsertNotice(
                "2026학년도 여름학기 개별연구 신청 안내",
                "2026 여름학기",
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-30"),
                "https://cs.dongguk.edu/",
                false,
                "[\"교수 서명 신청서\",\"증빙자료\"]",
                "{\"신청기간\":\"2026-07-20 ~ 2026-07-30\"}",
                "{\"제출방식\":\"온라인 신청 후 서명본 업로드\"}",
                "담당 교수 면담 후 서명을 받아 업로드해 주세요.",
                LocalDate.parse("2026-07-03")
        );
    }
}

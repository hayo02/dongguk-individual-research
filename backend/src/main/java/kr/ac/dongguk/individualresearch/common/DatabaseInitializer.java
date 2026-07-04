package kr.ac.dongguk.individualresearch.common;

import kr.ac.dongguk.individualresearch.auth.AuthService;
import kr.ac.dongguk.individualresearch.auth.UserRepository;
import kr.ac.dongguk.individualresearch.auth.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final AuthService authService;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate, UserRepository userRepository, AuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        createTables();
        seedUsers();
        seedNotice();
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

    private void seedNotice() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notices WHERE semester = ?",
                Integer.class,
                "2026 여름학기"
        );
        if (count != null && count > 0) {
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
                "2026학년도 여름학기 개별연구 신청 안내",
                "2026 여름학기",
                "2026-07-20",
                "2026-07-30",
                "https://cs.dongguk.edu/",
                false,
                "[\"교수 서명 신청서\",\"증빙자료\"]",
                "{\"신청기간\":\"2026-07-20 ~ 2026-07-30\"}",
                "{\"제출방식\":\"온라인 신청 후 서명본 업로드\"}",
                "담당 교수 면담 후 서명을 받아 업로드해 주세요.",
                "2026-07-03"
        );
    }
}

package kr.ac.dongguk.individualresearch.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository("applicationCommandRepository")
public class ApplicationRepository {
    private final JdbcTemplate jdbcTemplate;

    public ApplicationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ApplicationRecord> findCurrentByStudentId(long studentId) {
        return jdbcTemplate.query(
                """
                SELECT a.id, a.student_id,
                       u.login_id AS student_login_id, u.name AS student_name, u.department AS student_department,
                       COALESCE(a.email, u.email) AS student_email, u.phone AS student_phone,
                       a.course_id, c.notice_id, n.semester, c.department, c.course_name, c.course_type, c.course_code,
                       c.research_description,
                       c.weekly_hours, c.professor_name, a.status,
                       a.contact, a.email, a.application_reason, a.research_purpose, a.submitted_at, a.created_at, a.updated_at
                FROM applications a
                JOIN users u ON u.id = a.student_id
                LEFT JOIN courses c ON c.id = a.course_id
                LEFT JOIN notices n ON n.id = c.notice_id
                WHERE a.student_id = ?
                ORDER BY a.updated_at DESC, a.id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> mapRecord(rs),
                studentId
        ).stream().findFirst();
    }

    public Optional<ApplicationRecord> findById(long applicationId) {
        return jdbcTemplate.query(
                """
                SELECT a.id, a.student_id,
                       u.login_id AS student_login_id, u.name AS student_name, u.department AS student_department,
                       COALESCE(a.email, u.email) AS student_email, u.phone AS student_phone,
                       a.course_id, c.notice_id, n.semester, c.department, c.course_name, c.course_type, c.course_code,
                       c.research_description,
                       c.weekly_hours, c.professor_name, a.status,
                       a.contact, a.email, a.application_reason, a.research_purpose, a.submitted_at, a.created_at, a.updated_at
                FROM applications a
                JOIN users u ON u.id = a.student_id
                LEFT JOIN courses c ON c.id = a.course_id
                LEFT JOIN notices n ON n.id = c.notice_id
                WHERE a.id = ?
                """,
                (rs, rowNum) -> mapRecord(rs),
                applicationId
        ).stream().findFirst();
    }

    public long create(long studentId, long courseId) {
        jdbcTemplate.update(
                """
                INSERT INTO applications (student_id, course_id, status, application_reason, research_purpose, submitted_at)
                VALUES (?, ?, ?, NULL, NULL, NULL)
                """,
                studentId,
                courseId,
                ApplicationStatus.DRAFT.name()
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalArgumentException("신청서를 저장하지 못했습니다.");
        }
        return id;
    }

    public void updateCurrent(long applicationId, String contact, String email, String applicationReason, String researchPurpose) {
        jdbcTemplate.update(
                """
                UPDATE applications
                SET contact = ?,
                    email = ?,
                    application_reason = ?,
                    research_purpose = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                contact,
                email,
                applicationReason,
                researchPurpose,
                applicationId
        );
    }

    public boolean submit(long applicationId) {
        return jdbcTemplate.update(
                """
                UPDATE applications
                SET status = ?, submitted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN (?, ?)
                """,
                ApplicationStatus.SUBMITTED.name(),
                applicationId,
                ApplicationStatus.DRAFT.name(),
                ApplicationStatus.REVISION_REQUESTED.name()
        ) == 1;
    }

    public void delete(long applicationId) {
        jdbcTemplate.update("DELETE FROM applications WHERE id = ?", applicationId);
    }

    public boolean courseExists(long courseId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM courses WHERE id = ?",
                Integer.class,
                courseId
        );
        return count != null && count > 0;
    }

    public boolean existsInSameNotice(long studentId, long courseId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM applications a
                JOIN courses applied_course ON applied_course.id = a.course_id
                JOIN courses selected_course ON selected_course.notice_id = applied_course.notice_id
                WHERE a.student_id = ?
                  AND selected_course.id = ?
                """,
                Integer.class,
                studentId,
                courseId
        );
        return count != null && count > 0;
    }

    private ApplicationRecord mapRecord(ResultSet rs) throws SQLException {
        return new ApplicationRecord(
                rs.getLong("id"),
                rs.getLong("student_id"),
                rs.getString("student_login_id"),
                rs.getString("student_name"),
                rs.getString("student_department"),
                rs.getString("student_email"),
                rs.getString("student_phone"),
                rs.getString("contact"),
                nullableLong(rs, "course_id"),
                nullableLong(rs, "notice_id"),
                rs.getString("semester"),
                rs.getString("department"),
                rs.getString("course_name"),
                rs.getString("course_type"),
                rs.getString("course_code"),
                rs.getString("research_description"),
                nullableInt(rs, "weekly_hours"),
                rs.getString("professor_name"),
                ApplicationStatus.valueOf(rs.getString("status")),
                rs.getString("application_reason"),
                rs.getString("research_purpose"),
                nullableDateTime(rs, "submitted_at"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInt(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime nullableDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}

package kr.ac.dongguk.individualresearch.student;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationRepository {
    private final JdbcTemplate jdbcTemplate;

    public ApplicationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ApplicationStatus> findLatestStatusByStudentId(long studentId) {
        return jdbcTemplate.query(
                """
                SELECT status
                FROM applications
                WHERE student_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> ApplicationStatus.valueOf(rs.getString("status")),
                studentId
        ).stream().findFirst();
    }
}

package kr.ac.dongguk.individualresearch.course;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class CourseRepository {
    private final JdbcTemplate jdbcTemplate;

    public CourseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Course> findAll(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return jdbcTemplate.query(
                    """
                    SELECT id, notice_id, display_order, department, professor_name, course_name, course_type,
                           course_code, research_description, capacity, interview_schedule, weekly_hours,
                           qualification, professor_emails, required_skills, mentioned_technologies,
                           closed_to_additional_applications
                    FROM courses
                    ORDER BY display_order ASC, id ASC
                    """,
                    (rs, rowNum) -> mapCourse(rs)
            );
        }

        String likeKeyword = "%" + keyword.trim() + "%";
        return jdbcTemplate.query(
                """
                SELECT id, notice_id, display_order, department, professor_name, course_name, course_type,
                       course_code, research_description, capacity, interview_schedule, weekly_hours,
                       qualification, professor_emails, required_skills, mentioned_technologies,
                       closed_to_additional_applications
                FROM courses
                WHERE course_name LIKE ?
                   OR professor_name LIKE ?
                   OR course_code LIKE ?
                   OR research_description LIKE ?
                   OR qualification LIKE ?
                ORDER BY display_order ASC, id ASC
                """,
                (rs, rowNum) -> mapCourse(rs),
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword
        );
    }

    public Optional<Course> findById(long courseId) {
        return jdbcTemplate.query(
                """
                SELECT id, notice_id, display_order, department, professor_name, course_name, course_type,
                       course_code, research_description, capacity, interview_schedule, weekly_hours,
                       qualification, professor_emails, required_skills, mentioned_technologies,
                       closed_to_additional_applications
                FROM courses
                WHERE id = ?
                """,
                (rs, rowNum) -> mapCourse(rs),
                courseId
        ).stream().findFirst();
    }

    private Course mapCourse(ResultSet rs) throws SQLException {
        return new Course(
                rs.getLong("id"),
                rs.getLong("notice_id"),
                rs.getInt("display_order"),
                rs.getString("department"),
                rs.getString("professor_name"),
                rs.getString("course_name"),
                rs.getString("course_type"),
                rs.getString("course_code"),
                rs.getString("research_description"),
                nullableInt(rs, "capacity"),
                rs.getString("interview_schedule"),
                nullableInt(rs, "weekly_hours"),
                rs.getString("qualification"),
                rs.getString("professor_emails"),
                rs.getString("required_skills"),
                rs.getString("mentioned_technologies"),
                rs.getBoolean("closed_to_additional_applications")
        );
    }

    private Integer nullableInt(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }
}

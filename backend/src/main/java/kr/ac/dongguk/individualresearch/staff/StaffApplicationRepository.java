package kr.ac.dongguk.individualresearch.staff;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import kr.ac.dongguk.individualresearch.staff.StaffApplicationListResponse.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class StaffApplicationRepository {
    private final JdbcTemplate jdbcTemplate;

    public StaffApplicationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Item> findAll(
            String status,
            String studentName,
            String studentLoginId,
            String courseName,
            String professorName,
            String keyword,
            boolean ascending,
            int page,
            int size
    ) {
        QueryParts query = filters(status, studentName, studentLoginId, courseName, professorName, keyword);
        List<Object> parameters = new ArrayList<>(query.parameters());
        parameters.add(size);
        parameters.add(page * size);
        String direction = ascending ? "ASC" : "DESC";
        String sql = """
                SELECT a.id, u.name AS student_name, u.login_id AS student_login_id,
                       c.course_name, c.professor_name, a.status, a.submitted_at, a.updated_at
                FROM applications a
                JOIN users u ON u.id = a.student_id
                LEFT JOIN courses c ON c.id = a.course_id
                """ + query.whereClause()
                + " ORDER BY COALESCE(a.submitted_at, a.updated_at) " + direction
                + ", a.id " + direction
                + " LIMIT ? OFFSET ?";

        return jdbcTemplate.query(
                sql,
                this::mapItem,
                parameters.toArray()
        );
    }

    public long count(
            String status,
            String studentName,
            String studentLoginId,
            String courseName,
            String professorName,
            String keyword
    ) {
        QueryParts query = filters(status, studentName, studentLoginId, courseName, professorName, keyword);
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM applications a
                JOIN users u ON u.id = a.student_id
                LEFT JOIN courses c ON c.id = a.course_id
                """ + query.whereClause(),
                Long.class,
                query.parameters().toArray()
        );
        return count == null ? 0 : count;
    }

    private QueryParts filters(
            String status,
            String studentName,
            String studentLoginId,
            String courseName,
            String professorName,
            String keyword
    ) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        conditions.add("a.status <> ?");
        parameters.add("DRAFT");

        addEquals(conditions, parameters, "a.status", status);
        addLike(conditions, parameters, "u.name", studentName);
        addLike(conditions, parameters, "u.login_id", studentLoginId);
        addLike(conditions, parameters, "c.course_name", courseName);
        addLike(conditions, parameters, "c.professor_name", professorName);

        if (StringUtils.hasText(keyword)) {
            String value = like(keyword);
            conditions.add("""
                    (u.name LIKE ? OR u.login_id LIKE ? OR c.course_name LIKE ?
                     OR c.professor_name LIKE ? OR CAST(a.id AS CHAR) LIKE ?)
                    """);
            for (int index = 0; index < 5; index++) {
                parameters.add(value);
            }
        }

        return new QueryParts(" WHERE " + String.join(" AND ", conditions) + " ", parameters);
    }

    private void addEquals(List<String> conditions, List<Object> parameters, String column, String value) {
        if (StringUtils.hasText(value)) {
            conditions.add(column + " = ?");
            parameters.add(value.trim());
        }
    }

    private void addLike(List<String> conditions, List<Object> parameters, String column, String value) {
        if (StringUtils.hasText(value)) {
            conditions.add(column + " LIKE ?");
            parameters.add(like(value));
        }
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private Item mapItem(ResultSet rs, int rowNumber) throws SQLException {
        long id = rs.getLong("id");
        return new Item(
                id,
                "IR-2026-%04d".formatted(id),
                rs.getString("student_name"),
                rs.getString("student_login_id"),
                rs.getString("course_name"),
                rs.getString("professor_name"),
                rs.getString("status"),
                StaffApplicationService.statusLabel(rs.getString("status")),
                nullableDateTime(rs, "submitted_at"),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private LocalDateTime nullableDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private record QueryParts(String whereClause, List<Object> parameters) {
    }
}

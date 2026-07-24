package kr.ac.dongguk.individualresearch.application;

import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewHistoryRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(
            long applicationId,
            String previousStatus,
            String changedStatus,
            String comment,
            List<String> revisionItems,
            long reviewerId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO application_review_history(
                    application_id, previous_status, changed_status, comment, revision_items, reviewer_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                applicationId, previousStatus, changedStatus, comment,
                revisionItems == null ? "" : String.join(",", revisionItems), reviewerId
        );
    }

    public void insert(
            long applicationId,
            String previousStatus,
            String changedStatus,
            String comment,
            long reviewerId
    ) {
        insert(applicationId, previousStatus, changedStatus, comment, List.of(), reviewerId);
    }

    public List<ReviewHistoryRecord> findByApplicationId(long applicationId) {
        return jdbcTemplate.query(
                """
                SELECT h.id, h.application_id, h.previous_status, h.changed_status,
                       h.comment, h.revision_items, h.reviewer_id, u.name AS reviewer_name, h.reviewed_at
                FROM application_review_history h
                JOIN users u ON u.id = h.reviewer_id
                WHERE h.application_id = ?
                ORDER BY h.reviewed_at DESC, h.id DESC
                """,
                (rs, rowNumber) -> new ReviewHistoryRecord(
                        rs.getLong("id"),
                        rs.getLong("application_id"),
                        rs.getString("previous_status"),
                        rs.getString("changed_status"),
                        rs.getString("comment"),
                        parseItems(rs.getString("revision_items")),
                        rs.getLong("reviewer_id"),
                        rs.getString("reviewer_name"),
                        rs.getTimestamp("reviewed_at").toLocalDateTime()
                ),
                applicationId
        );
    }

    public Optional<ReviewHistoryRecord> findLatestByStudentId(long studentId) {
        return jdbcTemplate.query(
                """
                SELECT h.id, h.application_id, h.previous_status, h.changed_status,
                       h.comment, h.revision_items, h.reviewer_id, reviewer.name AS reviewer_name, h.reviewed_at
                FROM application_review_history h
                JOIN applications a ON a.id = h.application_id
                JOIN users reviewer ON reviewer.id = h.reviewer_id
                WHERE a.student_id = ?
                ORDER BY h.reviewed_at DESC, h.id DESC
                LIMIT 1
                """,
                (rs, rowNumber) -> new ReviewHistoryRecord(
                        rs.getLong("id"),
                        rs.getLong("application_id"),
                        rs.getString("previous_status"),
                        rs.getString("changed_status"),
                        rs.getString("comment"),
                        parseItems(rs.getString("revision_items")),
                        rs.getLong("reviewer_id"),
                        rs.getString("reviewer_name"),
                        rs.getTimestamp("reviewed_at").toLocalDateTime()
                ),
                studentId
        ).stream().findFirst();
    }

    private List<String> parseItems(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }
}

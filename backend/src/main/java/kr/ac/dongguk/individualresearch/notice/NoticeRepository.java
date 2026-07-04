package kr.ac.dongguk.individualresearch.notice;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NoticeRepository {
    private final JdbcTemplate jdbcTemplate;

    public NoticeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Notice> findCurrent() {
        return jdbcTemplate.query(
                """
                SELECT id, title, semester, start_date, end_date, original_url, needs_review,
                       required_documents, schedule_info, submission_info, notice_notes, body_text, published_at
                FROM notices
                ORDER BY start_date DESC, id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> new Notice(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("semester"),
                        rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date").toLocalDate(),
                        rs.getString("original_url"),
                        rs.getBoolean("needs_review"),
                        rs.getString("required_documents"),
                        rs.getString("schedule_info"),
                        rs.getString("submission_info"),
                        rs.getString("notice_notes"),
                        rs.getString("body_text"),
                        rs.getDate("published_at").toLocalDate()
                )
        ).stream().findFirst();
    }

    public Optional<Notice> findById(long noticeId) {
        return jdbcTemplate.query(
                """
                SELECT id, title, semester, start_date, end_date, original_url, needs_review,
                       required_documents, schedule_info, submission_info, notice_notes, body_text, published_at
                FROM notices
                WHERE id = ?
                """,
                (rs, rowNum) -> new Notice(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("semester"),
                        rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date").toLocalDate(),
                        rs.getString("original_url"),
                        rs.getBoolean("needs_review"),
                        rs.getString("required_documents"),
                        rs.getString("schedule_info"),
                        rs.getString("submission_info"),
                        rs.getString("notice_notes"),
                        rs.getString("body_text"),
                        rs.getDate("published_at").toLocalDate()
                ),
                noticeId
        ).stream().findFirst();
    }
}

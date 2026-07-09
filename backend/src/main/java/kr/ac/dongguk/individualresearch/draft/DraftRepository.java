package kr.ac.dongguk.individualresearch.draft;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DraftRepository {
    private final JdbcTemplate jdbc;

    public DraftRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long create(long userId, DraftRequest value) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO application_draft (
                      user_id, notice_id, research_topic_id, semester, student_name, student_number,
                      department, grade, phone, email, professor_name, research_title, research_content,
                      course_name, application_reason, research_purpose, related_experience,
                      research_plan, interview_questions, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            bind(ps, userId, value);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    public void update(long id, DraftRequest value) {
        jdbc.update("""
                UPDATE application_draft SET notice_id=?, research_topic_id=?, semester=?, student_name=?,
                  student_number=?, department=?, grade=?, phone=?, email=?, professor_name=?,
                  research_title=?, research_content=?, course_name=?, application_reason=?,
                  research_purpose=?, related_experience=?, research_plan=?, interview_questions=?,
                  status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, value.noticeId(), value.researchTopicId(), value.semester(), value.studentName(),
                value.studentNumber(), value.department(), value.grade(), value.phone(), value.email(),
                value.professorName(), value.researchTitle(), value.researchContent(), value.courseName(),
                value.applicationReason(), value.researchPurpose(), value.relatedExperience(),
                value.researchPlan(), value.interviewQuestions(),
                value.status() == null ? DraftStatus.DRAFT.name() : value.status().name(), id);
    }

    public Optional<DraftResponse> find(long id) {
        return jdbc.query("SELECT * FROM application_draft WHERE id=?", this::map, id).stream().findFirst();
    }

    public void markGenerated(long id) {
        jdbc.update("UPDATE application_draft SET status='GENERATED', updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }

    private void bind(java.sql.PreparedStatement ps, long userId, DraftRequest v) throws SQLException {
        Object[] values = {userId, v.noticeId(), v.researchTopicId(), v.semester(), v.studentName(),
                v.studentNumber(), v.department(), v.grade(), v.phone(), v.email(), v.professorName(),
                v.researchTitle(), v.researchContent(), v.courseName(), v.applicationReason(),
                v.researchPurpose(), v.relatedExperience(), v.researchPlan(), v.interviewQuestions(),
                v.status() == null ? DraftStatus.DRAFT.name() : v.status().name()};
        for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
    }

    private DraftResponse map(ResultSet rs, int row) throws SQLException {
        return new DraftResponse(rs.getLong("id"), rs.getLong("user_id"),
                nullableLong(rs, "notice_id"), nullableLong(rs, "research_topic_id"),
                rs.getString("semester"), rs.getString("student_name"), rs.getString("student_number"),
                rs.getString("department"), rs.getString("grade"), rs.getString("phone"),
                rs.getString("email"), rs.getString("professor_name"), rs.getString("research_title"),
                rs.getString("research_content"), rs.getString("course_name"),
                rs.getString("application_reason"), rs.getString("research_purpose"),
                rs.getString("related_experience"), rs.getString("research_plan"),
                rs.getString("interview_questions"), DraftStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }
}

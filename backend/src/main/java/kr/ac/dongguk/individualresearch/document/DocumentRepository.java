package kr.ac.dongguk.individualresearch.document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {
    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean hashExists(String hash) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM document_template WHERE file_hash=?", Integer.class, hash);
        return count != null && count > 0;
    }

    public int nextVersion(Long noticeId) {
        Integer version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(template_version),0)+1 FROM document_template WHERE notice_id <=> ?",
                Integer.class, noticeId);
        return version == null ? 1 : version;
    }

    public long insertTemplate(Long noticeId, String semester, String name, String original, String stored,
                               String path, int version, String hash, boolean active) {
        if (active) deactivateForNotice(noticeId);
        var key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("""
                    INSERT INTO document_template(notice_id,semester,template_name,document_type,
                    original_filename,stored_filename,file_path,template_version,file_hash,is_active,needs_review)
                    VALUES(?,?,?,'INDIVIDUAL_RESEARCH_APPLICATION',?,?,?,?,?,?,FALSE)
                    """, new String[]{"id"});
            ps.setObject(1, noticeId); ps.setString(2, semester); ps.setString(3, name);
            ps.setString(4, original); ps.setString(5, stored); ps.setString(6, path);
            ps.setInt(7, version); ps.setString(8, hash); ps.setBoolean(9, active);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    public List<TemplateRecord> templates() {
        return jdbc.query("SELECT * FROM document_template ORDER BY created_at DESC", this::mapTemplate);
    }

    public Optional<TemplateRecord> template(long id) {
        return jdbc.query("SELECT * FROM document_template WHERE id=?", this::mapTemplate, id).stream().findFirst();
    }

    public Optional<TemplateRecord> activeTemplate(Long noticeId) {
        return jdbc.query("""
                SELECT * FROM document_template
                WHERE is_active=TRUE AND (notice_id=? OR notice_id IS NULL)
                ORDER BY CASE WHEN notice_id=? THEN 0 ELSE 1 END, template_version DESC LIMIT 1
                """, this::mapTemplate, noticeId, noticeId).stream().findFirst();
    }

    public void activate(long id, Long noticeId) {
        deactivateForNotice(noticeId);
        jdbc.update("UPDATE document_template SET is_active=TRUE, updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }

    public void deactivate(long id) {
        jdbc.update("UPDATE document_template SET is_active=FALSE, updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }

    private void deactivateForNotice(Long noticeId) {
        jdbc.update("UPDATE document_template SET is_active=FALSE WHERE notice_id <=> ?", noticeId);
    }

    public long insertGenerated(long draftId, Long templateId, String type, String filename, String path, String hash) {
        var key = new GeneratedKeyHolder();
        java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusDays(7);
        jdbc.update(c -> {
            var ps = c.prepareStatement("""
                    INSERT INTO generated_document(draft_id,template_id,document_type,filename,file_path,file_hash,expires_at)
                    VALUES(?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            ps.setLong(1,draftId); ps.setObject(2,templateId); ps.setString(3,type);
            ps.setString(4,filename); ps.setString(5,path); ps.setString(6,hash);
            ps.setObject(7, expiresAt);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    public Optional<GeneratedDocument> generated(long id) {
        return jdbc.query("SELECT * FROM generated_document WHERE id=?", this::mapGenerated, id).stream().findFirst();
    }

    private TemplateRecord mapTemplate(ResultSet r, int row) throws SQLException {
        long notice = r.getLong("notice_id");
        Long noticeId = r.wasNull() ? null : notice;
        return new TemplateRecord(r.getLong("id"), noticeId, r.getString("semester"),
                r.getString("template_name"), r.getString("document_type"), r.getString("original_filename"),
                r.getString("stored_filename"), r.getString("file_path"), r.getInt("template_version"),
                r.getString("file_hash"), r.getBoolean("is_active"), r.getBoolean("needs_review"),
                r.getTimestamp("created_at").toLocalDateTime(), r.getTimestamp("updated_at").toLocalDateTime());
    }

    private GeneratedDocument mapGenerated(ResultSet r, int row) throws SQLException {
        long template = r.getLong("template_id");
        Long templateId = r.wasNull() ? null : template;
        Timestamp expires = r.getTimestamp("expires_at");
        return new GeneratedDocument(r.getLong("id"),r.getLong("draft_id"),templateId,
                r.getString("document_type"),r.getString("filename"),r.getString("file_path"),
                r.getString("file_hash"),r.getTimestamp("created_at").toLocalDateTime(),
                expires==null?null:expires.toLocalDateTime());
    }
}

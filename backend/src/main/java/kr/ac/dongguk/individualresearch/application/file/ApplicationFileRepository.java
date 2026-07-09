package kr.ac.dongguk.individualresearch.application.file;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationFileRepository {
    private final JdbcTemplate jdbc;

    public ApplicationFileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ApplicationFileRecord> findByApplicationId(long applicationId) {
        return jdbc.query(
                "SELECT * FROM application_files WHERE application_id=? ORDER BY uploaded_at DESC, id DESC",
                this::map, applicationId);
    }

    public Optional<ApplicationFileRecord> findById(long id) {
        return jdbc.query("SELECT * FROM application_files WHERE id=?", this::map, id)
                .stream().findFirst();
    }

    public boolean existsByType(long applicationId, ApplicationFileDocumentType type) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM application_files WHERE application_id=? AND document_type=?",
                Integer.class, applicationId, type.name());
        return count != null && count > 0;
    }

    public long insert(long applicationId, ApplicationFileDocumentType type, String originalName,
                       String storedName, String path, String contentType, long size, String hash,
                       long uploadedBy) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO application_files(
                      application_id, document_type, original_file_name, stored_file_name,
                      file_path, content_type, file_size, file_hash, uploaded_by
                    ) VALUES(?,?,?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            ps.setLong(1, applicationId);
            ps.setString(2, type.name());
            ps.setString(3, originalName);
            ps.setString(4, storedName);
            ps.setString(5, path);
            ps.setString(6, contentType);
            ps.setLong(7, size);
            ps.setString(8, hash);
            ps.setLong(9, uploadedBy);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    public void update(long id, String originalName, String storedName, String path,
                       String contentType, long size, String hash) {
        jdbc.update("""
                UPDATE application_files
                SET original_file_name=?, stored_file_name=?, file_path=?, content_type=?,
                    file_size=?, file_hash=?, uploaded_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, originalName, storedName, path, contentType, size, hash, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM application_files WHERE id=?", id);
    }

    private ApplicationFileRecord map(ResultSet rs, int row) throws SQLException {
        return new ApplicationFileRecord(
                rs.getLong("id"), rs.getLong("application_id"),
                ApplicationFileDocumentType.valueOf(rs.getString("document_type")),
                rs.getString("original_file_name"), rs.getString("stored_file_name"),
                rs.getString("file_path"), rs.getString("content_type"), rs.getLong("file_size"),
                rs.getString("file_hash"), rs.getLong("uploaded_by"),
                rs.getTimestamp("uploaded_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }
}

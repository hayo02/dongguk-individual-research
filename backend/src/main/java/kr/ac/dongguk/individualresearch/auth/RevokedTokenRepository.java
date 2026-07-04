package kr.ac.dongguk.individualresearch.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RevokedTokenRepository {
    private final JdbcTemplate jdbcTemplate;

    public RevokedTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByTokenHash(String tokenHash) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?",
                Integer.class,
                tokenHash
        );
        return count != null && count > 0;
    }

    public void revoke(String tokenHash, long userId, long expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO revoked_tokens (token_hash, user_id, expires_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id),
                    expires_at = VALUES(expires_at),
                    revoked_at = CURRENT_TIMESTAMP
                """,
                tokenHash,
                userId,
                expiresAt
        );
    }
}

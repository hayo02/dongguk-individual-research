package kr.ac.dongguk.individualresearch.auth;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByLoginId(String loginId) {
        return jdbcTemplate.query(
                """
                SELECT id, login_id, password_hash, name, role, department, email, phone
                FROM users
                WHERE login_id = ?
                """,
                (rs, rowNum) -> new User(
                        rs.getLong("id"),
                        rs.getString("login_id"),
                        rs.getString("password_hash"),
                        rs.getString("name"),
                        UserRole.valueOf(rs.getString("role")),
                        rs.getString("department"),
                        rs.getString("email"),
                        rs.getString("phone")
                ),
                loginId
        ).stream().findFirst();
    }

    public Optional<User> findById(long id) {
        return jdbcTemplate.query(
                """
                SELECT id, login_id, password_hash, name, role, department, email, phone
                FROM users
                WHERE id = ?
                """,
                (rs, rowNum) -> new User(
                        rs.getLong("id"),
                        rs.getString("login_id"),
                        rs.getString("password_hash"),
                        rs.getString("name"),
                        UserRole.valueOf(rs.getString("role")),
                        rs.getString("department"),
                        rs.getString("email"),
                        rs.getString("phone")
                ),
                id
        ).stream().findFirst();
    }

    public boolean existsByLoginId(String loginId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE login_id = ?",
                Integer.class,
                loginId
        );
        return count != null && count > 0;
    }

    public void insert(String loginId, String passwordHash, String name, UserRole role, String department, String email, String phone) {
        jdbcTemplate.update(
                """
                INSERT INTO users (login_id, password_hash, name, role, department, email, phone)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                loginId,
                passwordHash,
                name,
                role.name(),
                department,
                email,
                phone
        );
    }
}

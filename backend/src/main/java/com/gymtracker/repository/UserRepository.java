package com.gymtracker.repository;

import com.gymtracker.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/** users + auth_tokens tables. */
@Repository
public class UserRepository {

    /** A stored login token: which user it belongs to and when it was issued. */
    public record TokenRow(long userId, LocalDateTime createdAt) {
    }

    private static final RowMapper<User> USER = (rs, n) -> new User(
            rs.getLong("id"), rs.getString("username"), rs.getString("email"),
            rs.getString("password_hash"), rs.getString("display_name"), rs.getString("created_at"));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String username, String email, String passwordHash, String displayName, LocalDateTime now) {
        return SqlSupport.insert(jdbc,
                "INSERT INTO users (username, email, password_hash, display_name, created_at) VALUES (?, ?, ?, ?, ?) RETURNING id",
                username, email, passwordHash, displayName, now.toString());
    }

    public Optional<User> findById(long id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", USER, id).stream().findFirst();
    }

    /** Case-insensitive (column is COLLATE NOCASE). */
    public Optional<User> findByUsername(String username) {
        return jdbc.query("SELECT * FROM users WHERE username = ?", USER, username).stream().findFirst();
    }

    public boolean usernameTaken(String username) {
        return findByUsername(username).isPresent();
    }

    /** @param exceptUserId ignore this user (when they keep their own email); use -1 when registering */
    public boolean emailTaken(String email, long exceptUserId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ? AND id <> ?",
                Integer.class, email, exceptUserId);
        return count != null && count > 0;
    }

    public void updateProfile(long id, String displayName, String email) {
        jdbc.update("UPDATE users SET display_name = ?, email = ? WHERE id = ?", displayName, email, id);
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public void insertToken(String token, long userId, LocalDateTime now) {
        jdbc.update("INSERT INTO auth_tokens (token, user_id, created_at) VALUES (?, ?, ?)", token, userId, now.toString());
    }

    public Optional<TokenRow> findToken(String token) {
        return jdbc.query("SELECT user_id, created_at FROM auth_tokens WHERE token = ?",
                (rs, n) -> new TokenRow(rs.getLong("user_id"), LocalDateTime.parse(rs.getString("created_at"))),
                token).stream().findFirst();
    }

    public void deleteToken(String token) {
        jdbc.update("DELETE FROM auth_tokens WHERE token = ?", token);
    }

    /** Signs the user out everywhere except the given token (after a password change). */
    public void deleteOtherTokens(long userId, String keepToken) {
        jdbc.update("DELETE FROM auth_tokens WHERE user_id = ? AND token <> ?", userId, keepToken);
    }
}

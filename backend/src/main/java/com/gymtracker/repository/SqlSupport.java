package com.gymtracker.repository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Small helpers shared by the repositories: SQLite stores dates and timestamps as ISO text. */
final class SqlSupport {

    private SqlSupport() {
    }

    /** Runs "INSERT ... RETURNING id" and returns the new row id. */
    static long insert(JdbcTemplate jdbc, String sqlWithReturningId, Object... args) {
        Long id = jdbc.queryForObject(sqlWithReturningId, Long.class, args);
        if (id == null) {
            throw new IllegalStateException("Insert returned no id");
        }
        return id;
    }

    static LocalDate date(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : LocalDate.parse(value);
    }

    static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : LocalDateTime.parse(value);
    }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static String text(Object value) {
        return value == null ? null : value.toString();
    }
}

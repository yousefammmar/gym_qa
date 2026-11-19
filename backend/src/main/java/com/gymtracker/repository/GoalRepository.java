package com.gymtracker.repository;

import com.gymtracker.model.Goal;
import com.gymtracker.model.GoalStatus;
import com.gymtracker.model.GoalType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** goals table. Progress is never stored: GoalService calculates it from the workout tables. */
@Repository
public class GoalRepository {

    private static final RowMapper<Goal> GOAL = (rs, n) -> new Goal(
            rs.getLong("id"), rs.getLong("user_id"), rs.getString("title"), GoalType.valueOf(rs.getString("goal_type")),
            SqlSupport.nullableLong(rs, "exercise_id"), rs.getDouble("target_value"),
            SqlSupport.date(rs, "start_date"), SqlSupport.date(rs, "target_date"),
            GoalStatus.valueOf(rs.getString("status")), SqlSupport.dateTime(rs, "completed_at"));

    private final JdbcTemplate jdbc;

    public GoalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long userId, String title, GoalType type, Long exerciseId, double target,
                       LocalDate startDate, LocalDate targetDate, LocalDateTime now) {
        return SqlSupport.insert(jdbc, """
                        INSERT INTO goals (user_id, title, goal_type, exercise_id, target_value, start_date, target_date, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?) RETURNING id""",
                userId, title, type.name(), exerciseId, target, startDate.toString(), targetDate.toString(), now.toString());
    }

    /** Active goals first, then by target date. */
    public List<Goal> findAll(long userId) {
        return jdbc.query("SELECT * FROM goals WHERE user_id = ? ORDER BY status = 'COMPLETED', target_date, id",
                GOAL, userId);
    }

    public Optional<Goal> find(long userId, long goalId) {
        return jdbc.query("SELECT * FROM goals WHERE id = ? AND user_id = ?", GOAL, goalId, userId).stream().findFirst();
    }

    public void update(long goalId, String title, double target, LocalDate targetDate) {
        jdbc.update("UPDATE goals SET title = ?, target_value = ?, target_date = ? WHERE id = ?",
                title, target, targetDate.toString(), goalId);
    }

    public void markCompleted(long goalId, LocalDateTime now) {
        jdbc.update("UPDATE goals SET status = 'COMPLETED', completed_at = ? WHERE id = ?", now.toString(), goalId);
    }

    public void delete(long goalId) {
        jdbc.update("DELETE FROM goals WHERE id = ?", goalId);
    }
}

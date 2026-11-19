package com.gymtracker.repository;

import com.gymtracker.model.MuscleGroup;
import com.gymtracker.model.PlanDay;
import com.gymtracker.model.PlanExercise;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** plan_days + plan_exercises: the original 5-day program seeded by db/schema.sql (read-only). */
@Repository
public class PlanRepository {

    private final JdbcTemplate jdbc;

    public PlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlanDay> findAll() {
        // Read the days first, then their exercises: a query inside a RowMapper would need a second
        // connection while the first is still open, and the pool has only one (SQLite single writer).
        List<PlanDay> days = jdbc.query("SELECT * FROM plan_days ORDER BY position",
                (rs, n) -> new PlanDay(rs.getString("slug"), rs.getString("name"), rs.getString("focus"),
                        rs.getString("prescription"), rs.getInt("weekday"), List.of()));
        return days.stream()
                .map(d -> new PlanDay(d.slug(), d.name(), d.focus(), d.prescription(), d.weekday(), findExercises(d.slug())))
                .toList();
    }

    public Optional<PlanDay> findBySlug(String slug) {
        return findAll().stream().filter(day -> day.slug().equals(slug)).findFirst();
    }

    public Optional<PlanDay> findByWeekday(int weekday) {
        return findAll().stream().filter(day -> day.weekday() == weekday).findFirst();
    }

    private List<PlanExercise> findExercises(String slug) {
        return jdbc.query("""
                        SELECT pe.*, e.name AS exercise_name, e.muscle_group
                        FROM plan_exercises pe JOIN exercises e ON e.id = pe.exercise_id
                        WHERE pe.plan_slug = ? ORDER BY pe.position""",
                (rs, n) -> new PlanExercise(rs.getLong("id"), rs.getLong("exercise_id"), rs.getString("exercise_name"),
                        MuscleGroup.valueOf(rs.getString("muscle_group")), rs.getInt("target_sets"),
                        rs.getString("target_reps"), rs.getString("note")),
                slug);
    }
}

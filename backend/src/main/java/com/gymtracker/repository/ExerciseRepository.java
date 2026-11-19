package com.gymtracker.repository;

import com.gymtracker.model.Exercise;
import com.gymtracker.model.MuscleGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** exercises table. A user sees the built-in exercises plus their own custom ones. */
@Repository
public class ExerciseRepository {

    private static final RowMapper<Exercise> EXERCISE = (rs, n) -> new Exercise(
            rs.getLong("id"), rs.getString("name"), MuscleGroup.valueOf(rs.getString("muscle_group")),
            rs.getString("description"), SqlSupport.nullableLong(rs, "owner_id"));

    private final JdbcTemplate jdbc;

    public ExerciseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Exercise> findVisible(long userId) {
        return jdbc.query("SELECT * FROM exercises WHERE owner_id IS NULL OR owner_id = ? ORDER BY name COLLATE NOCASE, id",
                EXERCISE, userId);
    }

    public Optional<Exercise> findVisibleById(long userId, long id) {
        return jdbc.query("SELECT * FROM exercises WHERE id = ? AND (owner_id IS NULL OR owner_id = ?)",
                EXERCISE, id, userId).stream().findFirst();
    }

    public long insert(String name, MuscleGroup group, String description, long ownerId) {
        return SqlSupport.insert(jdbc,
                "INSERT INTO exercises (name, muscle_group, description, owner_id) VALUES (?, ?, ?, ?) RETURNING id",
                name, group.name(), description, ownerId);
    }
}

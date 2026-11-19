package com.gymtracker.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-to-database level: schema, seed data, constraints and foreign keys of the SQLite file.
 * Note: Spring does not map SQLite error codes, so constraint failures arrive as UncategorizedSQLException
 * (a DataAccessException); the tests check the SQLite constraint message instead.
 */
@DisplayName("SQLite schema and seed data")
class DatabaseIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    @DisplayName("the original plan is seeded: 27 exercises, 5 training days, 37 plan entries")
    void seedMatchesOriginalWebsite() {
        assertThat(count("SELECT COUNT(*) FROM exercises WHERE owner_id IS NULL")).isEqualTo(27);
        assertThat(count("SELECT COUNT(*) FROM plan_days")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM plan_exercises")).isEqualTo(37);
        assertThat(jdbc.queryForList("SELECT slug FROM plan_days ORDER BY position", String.class))
                .containsExactly("push", "pull", "legs", "upper", "lower");
    }

    @Test
    @DisplayName("running schema.sql again keeps data and does not duplicate seed rows")
    void schemaIsIdempotent() {
        jdbc.update("INSERT INTO users (username, email, password_hash, display_name, created_at) VALUES ('keep', 'k@x.io', 'h', 'k', 'now')");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
        assertThat(count("SELECT COUNT(*) FROM exercises WHERE owner_id IS NULL")).isEqualTo(27);
        assertThat(count("SELECT COUNT(*) FROM plan_exercises")).isEqualTo(37);
        assertThat(count("SELECT COUNT(*) FROM users WHERE username = 'keep'")).isEqualTo(1);
    }

    @Test
    @DisplayName("foreign keys are switched on for the connection")
    void foreignKeysOn() {
        assertThat(count("PRAGMA foreign_keys")).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workouts (user_id, name, workout_date, status, created_at) VALUES (999999, 'x', '2026-11-19', 'PLANNED', 'now')"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("SQLITE_CONSTRAINT_FOREIGNKEY");
    }

    @Test
    @DisplayName("CHECK constraints repeat the Java limits (reps 1-100, weight 0-500, status values)")
    void checkConstraints() throws Exception {
        String auth = register("dbuser");
        long workoutId = createWorkout(auth, java.util.Map.of("planSlug", "push")).get("id").asLong();
        long blockId = jdbc.queryForObject("SELECT MIN(id) FROM workout_exercises WHERE workout_id = ?", Long.class, workoutId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workout_sets (workout_exercise_id, reps, weight_kg, completed, created_at) VALUES (?, 101, 10, 1, 'now')", blockId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("reps BETWEEN 1 AND 100");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workout_sets (workout_exercise_id, reps, weight_kg, completed, created_at) VALUES (?, 5, 500.5, 1, 'now')", blockId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("weight_kg BETWEEN 0 AND 500");
        assertThatThrownBy(() -> jdbc.update("UPDATE workouts SET status = 'DONE' WHERE id = ?", workoutId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("SQLITE_CONSTRAINT_CHECK");
    }
}

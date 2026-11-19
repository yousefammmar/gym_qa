package com.gymtracker.repository;

import com.gymtracker.model.MuscleGroup;
import com.gymtracker.model.SetRecord;
import com.gymtracker.model.Workout;
import com.gymtracker.model.WorkoutExercise;
import com.gymtracker.model.WorkoutSet;
import com.gymtracker.model.WorkoutStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** workouts, workout_exercises and workout_sets tables. Every query is scoped to one user. */
@Repository
public class WorkoutRepository {

    private static final RowMapper<Workout> WORKOUT = (rs, n) -> new Workout(
            rs.getLong("id"), rs.getLong("user_id"), rs.getString("plan_slug"), rs.getString("name"),
            SqlSupport.date(rs, "workout_date"), WorkoutStatus.valueOf(rs.getString("status")),
            SqlSupport.dateTime(rs, "started_at"), SqlSupport.dateTime(rs, "completed_at"),
            SqlSupport.nullableInt(rs, "duration_minutes"), rs.getString("notes"));

    private static final RowMapper<WorkoutExercise> WORKOUT_EXERCISE = (rs, n) -> new WorkoutExercise(
            rs.getLong("id"), rs.getLong("workout_id"), rs.getLong("exercise_id"), rs.getString("exercise_name"),
            MuscleGroup.valueOf(rs.getString("muscle_group")), rs.getInt("position"), rs.getInt("target_sets"),
            rs.getString("target_reps"));

    private static final RowMapper<WorkoutSet> SET = (rs, n) -> new WorkoutSet(
            rs.getLong("id"), rs.getLong("workout_exercise_id"), rs.getInt("reps"), rs.getDouble("weight_kg"),
            rs.getInt("completed") == 1);

    private final JdbcTemplate jdbc;

    public WorkoutRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- workouts ------------------------------------------------------------------

    public long insertWorkout(long userId, String planSlug, String name, LocalDate date, String notes, LocalDateTime now) {
        return SqlSupport.insert(jdbc, """
                        INSERT INTO workouts (user_id, plan_slug, name, workout_date, status, notes, created_at)
                        VALUES (?, ?, ?, ?, 'PLANNED', ?, ?) RETURNING id""",
                userId, planSlug, name, date.toString(), notes, now.toString());
    }

    public Optional<Workout> findWorkout(long userId, long workoutId) {
        return jdbc.query("SELECT * FROM workouts WHERE id = ? AND user_id = ?", WORKOUT, workoutId, userId)
                .stream().findFirst();
    }

    /** Newest first. status == null returns every workout. */
    public List<Workout> findWorkouts(long userId, WorkoutStatus status) {
        if (status == null) {
            return jdbc.query("SELECT * FROM workouts WHERE user_id = ? ORDER BY workout_date DESC, id DESC",
                    WORKOUT, userId);
        }
        return jdbc.query("SELECT * FROM workouts WHERE user_id = ? AND status = ? ORDER BY workout_date DESC, id DESC",
                WORKOUT, userId, status.name());
    }

    public void updateWorkout(long workoutId, String name, LocalDate date, String notes, Integer durationMinutes) {
        jdbc.update("UPDATE workouts SET name = ?, workout_date = ?, notes = ?, duration_minutes = ? WHERE id = ?",
                name, date.toString(), notes, durationMinutes, workoutId);
    }

    public void markStarted(long workoutId, LocalDateTime startedAt) {
        jdbc.update("UPDATE workouts SET status = 'IN_PROGRESS', started_at = ? WHERE id = ?",
                startedAt.toString(), workoutId);
    }

    public void markCompleted(long workoutId, LocalDateTime completedAt, int durationMinutes) {
        jdbc.update("UPDATE workouts SET status = 'COMPLETED', completed_at = ?, duration_minutes = ? WHERE id = ?",
                completedAt.toString(), durationMinutes, workoutId);
    }

    /** Exercises and sets are removed by ON DELETE CASCADE. */
    public void deleteWorkout(long workoutId) {
        jdbc.update("DELETE FROM workouts WHERE id = ?", workoutId);
    }

    // ---- exercises inside a workout --------------------------------------------------

    public long insertWorkoutExercise(long workoutId, long exerciseId, int targetSets, String targetReps) {
        return SqlSupport.insert(jdbc, """
                        INSERT INTO workout_exercises (workout_id, exercise_id, position, target_sets, target_reps)
                        VALUES (?, ?, (SELECT COALESCE(MAX(position), 0) + 1 FROM workout_exercises WHERE workout_id = ?), ?, ?)
                        RETURNING id""",
                workoutId, exerciseId, workoutId, targetSets, targetReps);
    }

    public List<WorkoutExercise> findWorkoutExercises(long workoutId) {
        return jdbc.query("""
                        SELECT we.*, e.name AS exercise_name, e.muscle_group
                        FROM workout_exercises we JOIN exercises e ON e.id = we.exercise_id
                        WHERE we.workout_id = ? ORDER BY we.position""",
                WORKOUT_EXERCISE, workoutId);
    }

    public void deleteWorkoutExercise(long workoutExerciseId) {
        jdbc.update("DELETE FROM workout_exercises WHERE id = ?", workoutExerciseId);
    }

    // ---- sets -------------------------------------------------------------------------

    public long insertSet(long workoutExerciseId, int reps, double weightKg, boolean completed, LocalDateTime now) {
        return SqlSupport.insert(jdbc, """
                        INSERT INTO workout_sets (workout_exercise_id, reps, weight_kg, completed, created_at)
                        VALUES (?, ?, ?, ?, ?) RETURNING id""",
                workoutExerciseId, reps, weightKg, completed ? 1 : 0, now.toString());
    }

    /** All sets of one workout, in the order they were logged. */
    public List<WorkoutSet> findSets(long workoutId) {
        return jdbc.query("""
                        SELECT s.* FROM workout_sets s JOIN workout_exercises we ON we.id = s.workout_exercise_id
                        WHERE we.workout_id = ? ORDER BY s.id""",
                SET, workoutId);
    }

    public void updateSet(long setId, int reps, double weightKg, boolean completed) {
        jdbc.update("UPDATE workout_sets SET reps = ?, weight_kg = ?, completed = ? WHERE id = ?",
                reps, weightKg, completed ? 1 : 0, setId);
    }

    public void deleteSet(long setId) {
        jdbc.update("DELETE FROM workout_sets WHERE id = ?", setId);
    }

    /**
     * Every set of every workout of the user, with the workout's date and status.
     * Filtering (completed sets, completed workouts, dates) happens in the service classes,
     * so the business rules stay in Java where they can be unit tested.
     */
    // ponytail: loads the user's full set history; add SQL aggregation if one user ever has 100k+ sets
    public List<SetRecord> findSetRecords(long userId) {
        return jdbc.query("""
                        SELECT w.id AS workout_id, w.workout_date, w.status, we.exercise_id, s.reps, s.weight_kg, s.completed
                        FROM workout_sets s
                        JOIN workout_exercises we ON we.id = s.workout_exercise_id
                        JOIN workouts w ON w.id = we.workout_id
                        WHERE w.user_id = ? ORDER BY w.workout_date, s.id""",
                (rs, n) -> new SetRecord(rs.getLong("workout_id"), SqlSupport.date(rs, "workout_date"),
                        WorkoutStatus.valueOf(rs.getString("status")), rs.getLong("exercise_id"), rs.getInt("reps"),
                        rs.getDouble("weight_kg"), rs.getInt("completed") == 1),
                userId);
    }
}

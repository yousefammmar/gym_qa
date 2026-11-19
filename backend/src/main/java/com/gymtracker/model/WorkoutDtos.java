package com.gymtracker.model;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response bodies for /api/workouts. Boxed types (Integer, Double) so a missing value is null, not 0. */
public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    /** planSlug (push, pull, legs, upper, lower) copies that day's exercises into the new session. */
    public record CreateWorkoutRequest(String name, String planSlug, LocalDate workoutDate, String notes) {
    }

    public record UpdateWorkoutRequest(String name, LocalDate workoutDate, String notes, Integer durationMinutes) {
    }

    /** durationMinutes may be left out when the workout was started: it is then measured from the start time. */
    public record CompleteWorkoutRequest(Integer durationMinutes) {
    }

    public record AddExerciseRequest(Long exerciseId, Integer targetSets, String targetReps) {
    }

    /** completed defaults to true. */
    public record LogSetRequest(Long workoutExerciseId, Integer reps, Double weightKg, Boolean completed) {
    }

    public record UpdateSetRequest(Integer reps, Double weightKg, Boolean completed) {
    }

    /** One row of the workout history list. */
    public record WorkoutSummary(long id, String name, String planSlug, LocalDate workoutDate, WorkoutStatus status,
                                 Integer durationMinutes, int exerciseCount, int completedSets, double volume) {
    }

    public record ExerciseBlock(long id, long exerciseId, String exerciseName, MuscleGroup muscleGroup,
                                int targetSets, String targetReps, List<WorkoutSet> sets) {
    }

    public record WorkoutDetail(long id, String name, String planSlug, LocalDate workoutDate, WorkoutStatus status,
                                LocalDateTime startedAt, LocalDateTime completedAt, Integer durationMinutes,
                                String notes, int completedSets, double volume, List<ExerciseBlock> exercises) {
    }
}

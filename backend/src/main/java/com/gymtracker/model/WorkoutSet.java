package com.gymtracker.model;

/** One logged set. Only completed sets count toward volume and workout completion. */
public record WorkoutSet(long id, long workoutExerciseId, int reps, double weightKg, boolean completed) {
}

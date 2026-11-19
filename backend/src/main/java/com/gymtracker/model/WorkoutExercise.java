package com.gymtracker.model;

/** An exercise added to a workout session, with its target sets/reps. */
public record WorkoutExercise(long id, long workoutId, long exerciseId, String exerciseName,
                              MuscleGroup muscleGroup, int position, int targetSets, String targetReps) {
}

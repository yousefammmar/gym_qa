package com.gymtracker.model;

/** One line of a plan day, e.g. "Dumbbell Press 3 x 6-10". */
public record PlanExercise(long id, long exerciseId, String exerciseName, MuscleGroup muscleGroup,
                           int targetSets, String targetReps, String note) {
}

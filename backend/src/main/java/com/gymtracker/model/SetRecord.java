package com.gymtracker.model;

import java.time.LocalDate;

/** A logged set together with its workout's date and status. Input for all progress and goal calculations. */
public record SetRecord(long workoutId, LocalDate workoutDate, WorkoutStatus workoutStatus, long exerciseId,
                        int reps, double weightKg, boolean completed) {
}
